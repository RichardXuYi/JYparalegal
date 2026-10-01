# 桌面端冷启动优化实施方案（Defender / 打包瘦身 / 启动链路）

> 目标：把「打包安装后首次点击 → 出现可用界面」的约 60 秒压到可接受范围；在没有代码签名证书的前提下，用工程手段替代证书带来的信誉加速。
> 范围：本文覆盖方案 1–5 的具体改动（文件、代码、风险、验证、顺序）。不含证书采购。

---

## 0. 基线与结论

### 0.1 实测基线（`studio-frontend/release/win-unpacked`，v1.5.0）

| 对象 | 文件数 | 体积 |
|---|---:|---:|
| 整包 | 50,773 | 1.63 GB |
| `resources/openclaw` | 40,721 | 851 MB |
| ├ `dist` | 24,343 | 347 MB |
| ├ `node_modules` | 15,045 | 488 MB |
| └ `docs` | 1,287 | 15.3 MB |
| `resources/openclaw-plugins`（7 个渠道插件） | 9,763 | 263 MB |
| `resources/bin`（node/uv/agent-browser） | 3 | 153 MB |
| `GrandPoem Studio.exe`（未签名） | 1 | 204 MB |
| 目录总数 | 5,823 | — |

### 0.2 已确认的三个事实

1. **应用代码不是主因。** 日志（`%APPDATA%\grandpoem-studio\logs\grandpoem-studio-2026-10-01.log`，14:40 那一次）显示：`whenReady` 之后 → 扩展初始化 0.24 s → 网关 prelaunch 519 ms。也就是说从「JS 开始跑」到「窗口已创建」只有约 0.3 s。60 秒几乎全部发生在 `app.whenReady()` **之前**（进程/系统层）。
2. **安装器想加 Defender 白名单，但必然失败。** `scripts/installer.nsh:364-371` 已调用 `Add-MpPreference -ExclusionPath $INSTDIR`，而 `electron-builder.yml:127` 是 `perMachine: false`（每用户安装、不提权），该调用被 `-ErrorAction SilentlyContinue` 静默吞掉。注释里也承认了这一点。
3. **跨平台清理漏了 `@trycua`。** `scripts/after-pack.cjs:152-162` 的 `PLATFORM_NATIVE_SCOPES` 没有 `@trycua`，导致 Windows 包里带着 mac/Linux 驱动二进制 5 个目录 / 20 文件 / **约 203 MB**。

### 0.3 两段拆分（后续所有方案都按这个分类）

| 段 | 区间 | 现象 | 主要成本 | 对应方案 |
|---|---|---|---|---|
| **A 段** | 进程启动 → `whenReady` | 完全无窗口、无反应 | Defender 实时扫描 + 云信誉查询、冷文件系统缓存、204 MB 未签名 PE 首次执行 | 1、2、3b、5 |
| **B 段** | `whenReady` → 界面可用 | 有窗口/占位页但迟迟不可用 | 主进程串行 await、首帧、网关冷启动（~10k 模块 V8 冷编译） | 3a、4、5 |

> 判定 A 段真实长度的决定性实验（方案 4.1 埋点后一次点击即可得到）：`process.uptime()` 在 `whenReady` 时的取值 = A 段耗时。

---

## 方案 1：让 Defender 白名单真正生效

### 1.1 抽一个可复用、可卸载、可诊断的 PowerShell 脚本

**新增** `resources/cli/win32/set-defender-exclusion.ps1`：

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory)][ValidateSet('add','remove')] [string]$Action,
  [Parameter(Mandatory)][string]$InstallDir,
  [string]$ResultFile = "$env:TEMP\grandpoem-studio-defender-result.json",
  # 安装器路径专用：未提权时不弹 UAC，直接以退出码 3 返回
  [switch]$NonElevated
)
$ErrorActionPreference = 'Stop'

function Test-Admin {
  $id = [Security.Principal.WindowsIdentity]::GetCurrent()
  (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-Admin)) {
  if ($NonElevated) { exit 3 }
  $argList = @('-NoProfile','-ExecutionPolicy','Bypass','-File', "`"$PSCommandPath`"",
               '-Action', $Action, '-InstallDir', "`"$InstallDir`"", '-ResultFile', "`"$ResultFile`"")
  Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $argList -Wait
  exit $LASTEXITCODE
}

# 只排除必要目标，不要扩大范围
$paths = @($InstallDir,
           (Join-Path $env:APPDATA 'grandpoem-studio'),
           (Join-Path $env:USERPROFILE '.openclaw')) |
         Where-Object { $_ -and (Test-Path -LiteralPath $_) }
$procs = @('GrandPoem Studio.exe','openclaw-gateway.exe')

foreach ($p in $paths) {
  if ($Action -eq 'add') { Add-MpPreference -ExclusionPath $p -ErrorAction SilentlyContinue }
  else                   { Remove-MpPreference -ExclusionPath $p -ErrorAction SilentlyContinue }
}
foreach ($n in $procs) {
  if ($Action -eq 'add') { Add-MpPreference -ExclusionProcess $n -ErrorAction SilentlyContinue }
  else                   { Remove-MpPreference -ExclusionProcess $n -ErrorAction SilentlyContinue }
}

$pref = Get-MpPreference
$result = [pscustomobject]@{
  ok               = $true
  action           = $Action
  installDir       = $InstallDir
  tamperProtected  = (Get-MpComputerStatus).IsTamperProtected
  exclusionPath    = @($pref.ExclusionPath)
  exclusionProcess = @($pref.ExclusionProcess)
  verified         = (@($pref.ExclusionPath) -contains $InstallDir)
}
$result | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

if ($Action -eq 'add' -and -not $result.verified) { exit 4 }  # 提权成功但写入被拒（Tamper Protection 等）
exit 0
```

退出码约定：`0` 成功并已验证；`3` 未提权（安装器静默路径）；`4` 提权成功但未写入 → UI 需给出「手动添加」兜底指引；`2` 脚本异常。

> 注意：`Get-MpPreference` 读取排除项**需要管理员**，所以状态查询不能走这条脚本的普通路径，见 1.2 的缓存策略。

### 1.2 安装器：静态提权路径 + Finish 页可选提权

**改动** `scripts/installer.nsh`：

(a) `customInstall` 里替换现有那段（`installer.nsh:364-371`）：

```nsis
DetailPrint "Configuring Windows Defender exclusion..."
File "/oname=$PLUGINSDIR\set-defender-exclusion.ps1" "${PROJECT_DIR}\resources\cli\win32\set-defender-exclusion.ps1"
nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\set-defender-exclusion.ps1" -Action add -InstallDir "$INSTDIR" -NonElevated'
Pop $0
Pop $1
StrCmp $0 "0" 0 +2
  DetailPrint "Defender exclusion added."
StrCmp $0 "3" 0 +2
  DetailPrint "Not elevated: skipped Defender exclusion (can be added later from Settings)."
```

(b) 新增 `customFinishPage`（**必须重新声明** `MUI_FINISHPAGE_RUN`，因为 electron-builder 的 `assistedInstaller.nsh:47-63` 一旦检测到 `customFinishPage` 就不再注入内置的「运行」复选框）：

```nsis
!macro customFinishPage
  Function StartApp
    ${if} ${isUpdated}
      StrCpy $1 "--updated"
    ${else}
      StrCpy $1 ""
    ${endif}
    ${StdUtils.ExecShellAsUser} $0 "$launchLink" "open" "$1"
  FunctionEnd

  Function GrandPoemStudioAddDefenderExclusion
    nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\set-defender-exclusion.ps1" -Action add -InstallDir "$INSTDIR"'
    Pop $0
    Pop $1
  FunctionEnd

  !define MUI_FINISHPAGE_RUN
  !define MUI_FINISHPAGE_RUN_FUNCTION "StartApp"
  !define MUI_FINISHPAGE_SHOWREADME ""     ; 复用 readme 勾选位做「加速启动」opt-in
  !define MUI_FINISHPAGE_SHOWREADME_TEXT "加速启动：将安装目录加入 Windows Defender 白名单（需要管理员权限）"
  !define MUI_FINISHPAGE_SHOWREADME_FUNCTION GrandPoemStudioAddDefenderExclusion
  !insertmacro MUI_PAGE_FINISH
!macroend
```

(c) `customUnInstall` 里把现有那段裸 `Remove-MpPreference` 换成同一个脚本的 `-Action remove`（保持只删自己加的三个路径，避免误删用户既有排除项）。

**风险**：Finish 页勾选会弹一次 UAC；拒绝 → 安装照常继续，不影响完成。

### 1.3 应用内「启动加速」按钮（可选提权 + 状态展示）

按 `AGENTS.md` 的 Renderer/Main 边界，必须走 host-api，不得在渲染层直接 `ipcRenderer.invoke`。

**(a) host 契约** — `shared/host-api/contract.ts:887` 的 `app:` 模块追加（该模块是扁平 action 形式）：

```ts
app: {
  openClawDoctor: (payload: OpenClawDoctorPayload) => Omit<OpenClawDoctorResult, 'mode'>;
  startupAccelerationStatus: () => StartupAccelerationStatus;
  applyStartupAcceleration: () => StartupAccelerationResult;
  removeStartupAcceleration: () => StartupAccelerationResult;
};
```

类型定义放到 `shared/host-api/`（新增 `startup-acceleration.ts`）：

```ts
export type StartupAccelerationStatus = {
  supported: boolean;          // 仅 win32 为 true
  applied: boolean;            // 来自本地缓存 + 上次脚本结果
  lastVerifiedAt?: string;     // ISO
  manualRequired?: boolean;    // 退出码 4：需用户手动加排除项
  tamperProtected?: boolean;
  installDir?: string;
};
export type StartupAccelerationResult =
  | { ok: true; status: StartupAccelerationStatus }
  | { ok: false; code: 'NOT_ELEVATED' | 'BLOCKED' | 'UNSUPPORTED' | 'INTERNAL'; message: string };
```

**(b) Main 实现** — 新增 `electron/utils/defender-exclusion.ts`：

- `getStartupAccelerationStatus()`：不调用 `Get-MpPreference`（需管理员），改为读 `electron-store` 缓存键 `defenderExclusion`（`{ applied, lastVerifiedAt, tamperProtected }`）并 `existsSync` 校验脚本与安装目录。
- `applyStartupAcceleration()` / `removeStartupAcceleration()`：`spawn('powershell.exe', ['-NoProfile','-ExecutionPolicy','Bypass','-File', script, '-Action', action, '-InstallDir', app.getAppPath() 的安装目录, '-ResultFile', tmp])`，等待退出后读 JSON → 更新缓存 → 返回结果。退出码 3/4 映射为 `NOT_ELEVATED` / `BLOCKED`。
- 支持 `GRANDPOEM_DEFENDER_DRY_RUN=1`（开发期跳过提权，直接返回成功），便于本地联调。
- 安装目录推导：`path.dirname(app.getPath('exe'))`（packaged）；dev 下返回 `UNSUPPORTED`。

**(c) 服务层** — `electron/services/app-api.ts` 的 `createAppApi()` 增加三个 action，注册方式与 `openClawDoctor` 一致（`ipc-handlers.ts` / `host-invoke.ts` 的注册是集中式的，无需额外改动）。

**(d) 渲染层** — `src/lib/host-api.ts` 增加封装；UI 放在 `src/components/settings/sections/DeveloperSection.tsx`（参考其中 `runDoctor` 的写法，新增一张 "启动加速" 卡片）：
- 状态：已启用 / 未启用 / 需要管理员权限手动配置
- 按钮：`添加到 Defender 白名单`、`移除`
- 失败兜底：调用 `powershell Start-Process "windowsdefender://threatsettings"` 打开系统排除项设置页（不要在渲染层直接 `openExternal` 自定义协议）。

**(e) i18n** — `shared/i18n/locales/{de,en,fr,zh}/settings.json` 增加 `developer.startupAcceleration*` 全部键（四语言全覆盖，禁止硬编码中文）。

**(f) harness** — 因为改动了 host-api 契约，按 `AGENTS.md` 需新增 `harness/specs/tasks/add-startup-acceleration-host-api.md` 并跑 `pnpm harness validate --spec <spec>`。

### 1.4 预期与前提

- 生效时：A 段中「扫描 + 云信誉查询」部分可降到接近 0（需 4.1 埋点实测确认降幅）。
- 前提：用户是管理员，且 Tamper Protection 不阻断（脚本已验证 `verified`，失败会明确回报）。
- **不要做**：关闭实时保护、排除整个 `C:\` 或整个用户目录、`perMachine: true`（强制 UAC 并破坏静默升级）。
- 卸载时必须移除排除项（1.2(c) 已覆盖）。

---

## 方案 2：打包瘦身（降低 Defender 要扫描的文件/体积）

统一改动点：`scripts/after-pack.cjs`（在 electron-builder 打包完成、生成安装包之前运行，因此这里改动会进安装包——现有 `node_modules` 手动拷贝就是这么做的）。

### 2.1 `@trycua` 跨平台清理（零风险，先做）

在 `after-pack.cjs:152` 的 `PLATFORM_NATIVE_SCOPES` 增加一项：

```js
'@trycua': /^cua-driver-(darwin|linux|win32)-(x64|arm64)(?:-[a-z]+)?$/,
```

- 该正则符合现有约定（group1 = 平台、group2 = 基础架构），`baseArch()` 会把 `arm64-msvc` 归一成 `arm64`。
- 无平台后缀的 `@trycua/cua-driver` 本体**不会**被匹配（正则要求平台段），不会被误删。
- win32-x64 构建下保留 `cua-driver-win32-x64-msvc`，删除其余 6 个目录（含 win32-arm64）。
- **收益：-20 文件 / -203 MB。**

### 2.2 渠道插件改为「归档 + 首次按需解压」

现状：`after-pack.cjs:696-725` 把 7 个插件整体展开到 `resources/openclaw-plugins/<pluginId>/`（9,763 文件 / 263 MB），而它们只在用户真正添加对应渠道时才被复制到 `~/.openclaw/extensions/`（`electron/utils/plugin-install.ts:658-711`）。**首次启动完全用不到。**

**构建侧**：

在 `after-pack.cjs` 末尾（`platform` 任意）新增 `archiveBundledPlugins()`：

```
resources/openclaw-plugins/dingtalk/**   →  resources/lazy-assets/openclaw-plugins/dingtalk.zip
resources/openclaw-plugins/wecom/**      →  .../wecom.zip
（whatsapp / feishu-openclaw-plugin / discord / openclaw-weixin / qqbot 同理）
然后 rmSync 原目录
```

- 用 `adm-zip`（已是 `package.json` 的**运行时依赖**，构建脚本与主进程都可用）：`new AdmZip().addLocalFolder(dir, '')` + `writeZip()`。
- 预期归档后总量 70–90 MB（需实测），安装目录减少 **9,763 文件 / 约 180 MB**。

**运行侧**：

新增 `electron/utils/lazy-asset.ts`：

```ts
/** 把 resources/lazy-assets/<name>.zip 解到 userData/lazy-cache/<name>，返回解压目录 */
export function ensureLazyAssetExtracted(archiveName: string, destName?: string): string | null;
```

- 缓存目录：`join(app.getPath('userData'), 'lazy-cache', destName)`
- 失效标记：`<dest>/.grandpoem-archive.json` 记录 zip 的 `{ size, mtimeMs }`，不匹配则重新解压（覆盖升级后自动失效）。
- 归档不存在时（dev / 未启用该优化）返回 `null`，调用方按原逻辑走目录候选。

改造 `plugin-install.ts:buildCandidateSources()`：

```ts
export function buildCandidateSources(pluginDirName: string): string[] {
  const lazyDir = resolveLazyPluginDir(pluginDirName);   // 命中归档时触发解压并返回目录
  if (lazyDir) return [lazyDir];
  return app.isPackaged ? [ ...原列表... ] : [ ...原列表... ];
}
```

- 8 个 `ensureXxxPluginInstalled()` 全部自动受益，无需逐个改。
- 解压 dingtalk（139 MB）约需数秒——发生在「用户添加钉钉渠道」时，不在启动路径上；建议在对应 IPC handler 返回前给渲染层一个 loading 态（现有添加渠道流程已有进度反馈，确认即可）。

### 2.3 `uv.exe` / `agent-browser.exe` 归档（P2，可选）

`resources/bin`（153 MB / 3 文件）中：

| 文件 | 体积 | 首启是否需要 | 处理 |
|---|---:|---|---|
| `node.exe` | 81.3 MB | **需要**（`config-sync.ts:690` 等把它注入网关 PATH） | 保持原样 |
| `uv.exe` | 60.7 MB | 否（技能装 Python 时才用） | 归档，按需解压 |
| `agent-browser.exe` | 10.7 MB | 否（浏览器自动化技能才用） | 归档，按需解压 |

- 新增 `electron/utils/bundled-tool.ts`：`resolveBundledTool('node'|'uv'|'agent-browser'): string | null`，内部对 uv/agent-browser 走 `ensureLazyAssetExtracted`。
- 收敛目前散落的 **7 处** `resources/bin` 解析：
  `uv-setup.ts:19`、`openclaw-cli.ts:36`、`control-ui-device-pairing.ts:108`、`config-sync.ts:690`、`supervisor.ts:277`、`openclaw-doctor.ts:64`、`uv-env.ts`（PATH 注入）。
- 注意：其中 4 处是把 `resources/bin` **整个目录塞进 PATH**；归档后必须改成注入解压目录，否则网关找不到 `uv`。
- 收益：-2 文件 / -72 MB；风险中等（PATH 语义变化），建议单独一个 PR。

### 2.4 静态文件清理（改 `after-pack.cjs:69 cleanupUnnecessaryFiles()`）

实测可清理项：

| 类别 | 文件数 | 体积 | 建议 |
|---|---:|---:|---|
| `openclaw/docs/`（仅根目录） | 1,287 | 15.3 MB | 直接删（用显式路径，不要往 `REMOVE_DIRS` 加 `docs` 名字，避免误删 `dist/extensions/*/docs`） |
| `node_modules/**/*.md` + `dist/**/*.md` | 1,290 | 4.0 MB | 删；但**保留** `skills/**`、`custodian-skills/**`、`context/**`、`preinstalled-skills/**` 下的 `.md`（这些是产品内容） |
| `**/*.{ts,mts,cts}`（非 `.d.ts/.d.mts/.d.cts`） | 6,060 | 47.2 MB | **先 report-only**：日志输出命中数量，跑一轮 doctor + 网关冒烟后再开启删除（个别包可能以 `.ts` 为运行时入口） |
| `win-unpacked/locales/*.pak` 只留 `en-US.pak`、`zh-CN.pak` | -53 | ~5 MB | 删（不影响应用内 i18n，只影响 Chromium 自身 UI 语言，保留 en-US 兜底） |

预期累计：**约 -18,000 文件（51k → 约 33k）、约 -600 MB（1.63 GB → 约 1.0 GB）**。

### 2.5 防回归

新增 `scripts/assert-pack-size.mjs`，在 `package:win:dir` 之后运行，断言：

```
文件数 < 35,000   且   安装目录体积 < 1.2 GB
```

超阈值即失败并打印 top-10 大目录，避免后续版本又把体积涨回去。

---

## 方案 3：让等待可见

### 3a. 窗口立即显示（P0，几行代码）

`electron/main/index.ts:230-251`：

```ts
import { nativeTheme } from 'electron';
// ...
const win = new BrowserWindow({
  // ...原参数
  show: true,                                                    // 原来是 false
  backgroundColor: nativeTheme.shouldUseDarkColors ? '#0f0f23' : '#f8fafc',
});
```

- `index.html:22-44` 已经有 `.app-init-loading` 首屏占位（"正在配置环境…"），`public/bootstrap/loading.css` 提供样式，因此**不会白屏**；`backgroundColor` 与占位页背景一致，避免创建到首次 paint 之间的闪白。
- `ready-to-show` 里的 `focusWindow(win)` / `win.show()` 保留（幂等），第二实例聚焦逻辑不变。
- 只覆盖 B 段。

### 3b. 原生 splash 启动器（P2，覆盖 A 段体感）

A 段的 60 秒里应用自己无法绘制任何东西，只能靠一个**小体积**进程先给反馈。

- 新增 `scripts/launcher/Splash.cs`（WinForms，无边框置顶，显示 logo + "正在启动…"，`Process.Start` 真程序后 1–2 s 自退）。
- 新增 `scripts/build-launcher.mjs`：用系统自带编译器（`C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe`，Win10/11 默认存在，无需 SDK）产出 `resources/bin/win32-x64/grandpoem-launcher.exe`（约 10–20 KB）。**仅 win32 构建**；找不到 `csc.exe` → 跳过并保持原快捷方式（降级安全）。
- `installer.nsh` 的 `customInstall` 里用 `CreateShortCut` 覆盖桌面/开始菜单快捷方式，指向 launcher。
- 说明：这**不减少真实耗时**，只把小体积进程的快速启动变成用户可见反馈。与方案 1/2/5 正交，可最后做。

---

## 方案 4：缩短应用自身冷启动路径

### 4.1 先加启动计时埋点（P0，必须最先做）

新增 `electron/utils/startup-timeline.ts`：

```ts
import { logger } from './logger';
const T0 = Date.now();
export function markStartup(phase: string, extra?: Record<string, unknown>): void {
  logger.info(
    `[startup] ${phase} delta=${Date.now() - T0}ms uptime=${Math.round(process.uptime() * 1000)}ms` +
    (extra ? ` ${JSON.stringify(extra)}` : ''),
  );
}
```

埋点位置：

| 位置 | 说明 |
|---|---|
| `initialize()` 开头（`index.ts:366` 之后） | `markStartup('whenReady')` —— `uptime` 即 **A 段耗时** |
| `createMainWindow()` 返回后 | `markStartup('window-created')` |
| `win.once('ready-to-show')`（`index.ts:324`） | `markStartup('window-shown')` |
| `win.webContents.on('did-finish-load')` | `markStartup('renderer-loaded')` |
| `registerIpcHandlers` / `extensionRegistry.initialize` 之后 | `markStartup('ipc-ready' / 'extensions-ready')` |
| `gatewayManager.on('status')` 首次 `running` | `markStartup('gateway-ready')` |

产出：一次真实点击就能在日志里读出 A / B 段分解，后续每项优化都能量化。

### 4.2 重排 `initialize()`（`electron/main/index.ts:364-390`）

现在窗口创建之前串行 await 了 4 件事，其中 `initTelemetry()` 在首次运行时还会 `machineIdSync()`（`utils/telemetry.ts:61`，Windows 上实际是 `execSync` 拉起 `%windir%\System32\REG.exe`，见 `node-machine-id` 实现）——这是**首启独有**的一段同步子进程开销。

目标顺序：

```ts
async function initialize(): Promise<void> {
  logger.init();
  markStartup('whenReady');                    // ← A 段到此为止
  logger.info('=== GrandPoem Studio Application Starting ===');

  if (!isE2EMode) {
    void warmupNetworkOptimization();          // 已是非阻塞
  }

  await createMenu();
  const window = await createMainWindow();     // ← 提前：先建窗口
  markStartup('window-created');

  if (!isE2EMode) {
    await applyProxySettings();                // 仍 await：必须在任何网络请求前生效
    void initTelemetry();                      // 原来 await，首启会 spawn reg.exe
    void syncLaunchAtStartupSettingFromStore();
  }
  // ...后续（tray / IPC / 扩展 / 网关自动启动）保持原顺序
}
```

要点：
- `applyProxySettings()` 保持 `await` 且仍在网关启动之前；窗口加载的是本地 `file://`，所以放到窗口之后是安全的。
- `initTelemetry()` / `syncLaunchAtStartupSettingFromStore()` 变 `void`，异常仍由内部 try/catch 兜住。
- `machineIdSync()` 进一步惰性化（可选）：把 machineId 的生成挪到第一次真正 `capture()` 时。

预期 B 段 -0.3 ~ -2 s（首启更明显）。

### 4.3 首启维护任务延后到首帧之后（`index.ts:452-497`）

现在这 5 个任务在启动瞬间全部并发跑，与首帧渲染和网关冷启动抢 I/O：
`ensureGrandPoemStudioDefaultIdentity` / `repairGrandPoemStudioOnlyBootstrapFiles` / `ensureBuiltinSkillsInstalled` / `trimBundledOpenClawSkillsAndConfigs` / `ensurePreinstalledSkillsInstalled`。

改为：

```ts
function scheduleDeferredStartupTasks(window: BrowserWindow): void {
  const run = () => {
    void ensureGrandPoemStudioDefaultIdentity().catch((e) => logger.warn('identity seed failed:', e));
    void repairGrandPoemStudioOnlyBootstrapFiles().catch((e) => logger.warn('bootstrap repair failed:', e));
    void ensureBuiltinSkillsInstalled().catch((e) => logger.warn('builtin skills failed:', e));
    void ensurePreinstalledSkillsInstalled().catch((e) => logger.warn('preinstalled skills failed:', e));
    void trimBundledOpenClawSkillsAndConfigs().catch((e) => logger.warn('skill trim failed:', e));
  };
  if (window.webContents.isLoadingMainFrame()) {
    window.webContents.once('did-finish-load', () => setTimeout(run, 2500));
  } else {
    setTimeout(run, 2500);
  }
}
```

- `ensureGrandPoemStudioContext()` 已经是网关 `running` 事件驱动，不需要改。
- 注意 `trimBundledOpenClawSkillsAndConfigs` 会**写安装目录**，延后执行也能减少与 Defender 首扫的冲突。

---

## 方案 5：把部分冷启动成本挪到安装阶段（确定性预热）

### 5.1 推荐：预热 V8 编译缓存 + 触发整棵树首扫（安全、不抢单实例锁/端口）

`electron/gateway/process-launcher.ts:141-150` 的注释已经指出：`NODE_COMPILE_CACHE` 被清空时，网关会退化成对约 1 万个模块做完整冷编译；缓存目录是 `userData/openclaw-compile-cache`（`getOpenClawCompileCacheDir()`）。

在 `scripts/installer.nsh` 的 `customInstall` 末尾追加一条**分离进程、不阻塞安装**的预热：

```nsis
DetailPrint "Pre-warming OpenClaw runtime cache (background)..."
ExecShell "" "cmd.exe" `/c set "NODE_COMPILE_CACHE=$APPDATA\grandpoem-studio\openclaw-compile-cache" & "$INSTDIR\resources\bin\node.exe" "$INSTDIR\resources\openclaw\openclaw.mjs" doctor --json >nul 2>&1` SW_HIDE
```

- 用一个不监听 18789 的轻量子命令（`doctor --json`，应用自身也用它）加载 openclaw 的模块图并填充编译缓存。
- 同时让 Defender 完成对 `resources/openclaw` 整棵树的首次扫描——这正是「首次点开慢」的主要来源。
- 因为不启动网关、不抢单实例锁、不建窗口，**与用户之后的手动启动零冲突**。
- 命令需实测确认耗时（预期 20–60 s，在后台跑）。

### 5.2 可选更强版：`--grandpoem-prewarm` 完整预热

若 5.1 收益不够，可加完整预热模式，但**必须**遵守以下规则，否则会和用户启动打架：

1. `electron/main/index.ts` 解析 `const isPrewarmMode = process.argv.includes('--grandpoem-prewarm')`。
2. 预热模式下：**跳过** `app.requestSingleInstanceLock()` 与 `acquireProcessInstanceFileLock()`（否则会占用锁导致用户启动失败），不建窗口/托盘/菜单，不注册 IPC，不初始化 telemetry 与 updater。
3. 照常 `gatewayManager.start()`，直到 `status === 'running'` 或 60 s 超时，然后 `await gatewayManager.stop()` 并 `app.exit(0)`。
4. 写 `userData/prewarm-state.json`（开始/结束时间、结果）便于诊断。
5. 正常启动路径增加容忍：检测到本安装目录残留的网关进程时，复用现有 `startup-recovery` / orphan 逻辑处理（`gateway/startup-recovery.ts`、`manager.ts` 已有基于安装目录归属的判断）。
6. 安装器侧同样用 `ExecShell ... SW_HIDE` 分离调用，不阻塞到 Finish 页。

### 5.3 现状说明

electron-builder 的辅助安装器（`oneClick:false`）Finish 页**本来就有**默认勾选的「运行 GrandPoem Studio」（`app-builder-lib/.../assistedInstaller.nsh:50-62`）。所以：
- 用户保持勾选 → 首次点开其实已经是第二次运行；
- 用户取消勾选或直接双击 exe → 没有任何预热。
方案 5.1/5.2 的价值就是把预热变成**确定性**的，而不是依赖用户勾选。

---

## 6. 验证与回归

### 6.1 每项改动必须通过

```
pnpm run typecheck
pnpm run lint:check
pnpm run harness validate --spec <对应 spec>
```

### 6.2 打包与体积断言

```
pnpm run package:win:dir      # 快速产出 win-unpacked，验证结构与文件数
node scripts/assert-pack-size.mjs
pnpm run package:win          # 完整安装包（含 NSIS 模板补丁）
```

### 6.3 冒烟清单

1. 首次启动：窗口立即出现（3a）→ 日志时间线可读（4.1）→ 界面可用（含网关 `running`）。
2. `openclaw doctor`（设置 → 高级 → 开发者）无新增失败项。
3. 逐个渠道添加 → 验证插件按需解压安装成功（至少 DingTalk 139 MB 与 WeChat 4 MB 各一次，覆盖大/小归档）。
4. 卸载 → 确认 Defender 排除项被移除（管理员 PowerShell：`Get-MpPreference | Select-Object -ExpandProperty ExclusionPath`）。
5. 覆盖升级一条路径（旧版本 → 新版本），确认 `_stale_*` 清理与插件缓存失效重解正常。

### 6.4 计时对比

冷启动 / 二次启动各 3 次，记录 A 段（`[startup] whenReady uptime=`）与 B 段（到 `gateway-ready`）：

| 场景 | A 段 | 窗口出现 | 界面可用 |
|---|---|---|---|
| 优化前（基线） | 待测 | 待测 | 待测 |
| 仅加白名单 | | | |
| 白名单 + 瘦身 | | | |
| 全部 + 预热 | | | |

> 若「仅加白名单」就把 A 段从约 60 s 降到数秒，则结论坐实，方案 2 的收益主要体现在无管理员权限的用户和高频冷启动场景（升级、换盘、清理缓存后）。

---

## 7. 风险清单与实施顺序

### 7.1 风险与缓解

| 项 | 风险 | 缓解 |
|---|---|---|
| Defender 白名单（1） | Tamper Protection 拒绝写入；用户反感改安全设置 | 只排除 3 个路径 + 2 个进程名；提权前中文明确说明；写入后立即 `Get-MpPreference` 复核（退出码 4）；卸载时移除；失败给手动指引 |
| 插件归档（2.2） | 渠道插件首次安装多花 2–5 s；zip 损坏 | 解压到 `userData/lazy-cache` + `{size,mtime}` 失效标记；失败回退到现有候选目录并报错提示重装；dev 流程完全不受影响 |
| `.ts` 清理（2.4） | 个别包以 `.ts` 为运行时入口 | 先 report-only 跑一轮 + doctor 冒烟，再开启；必要时加 allowlist |
| locales 精简（2.4） | Chromium UI 语言缺失 | 保留 `en-US.pak` 兜底；与业务 i18n 无关 |
| `show:true`（3a） | 极小概率闪白 | `backgroundColor` 跟随 `nativeTheme` |
| 预热（5.1） | 安装期后台进程读配置 | 只用 `doctor --json`（只读诊断）；不建窗口、不抢锁；失败不影响安装 |
| 完整预热（5.2） | 抢单实例锁 / 占 18789 端口 | 严格按 5.2 的 6 条规则；默认不启用 |
| uv/agent-browser 归档（2.3） | PATH 注入点分散，容易漏改导致网关找不到 `uv` | 收敛到单一 `bundled-tool.ts`；独立 PR + 单独回归 |

### 7.2 实施顺序（每步独立可发布）

| 阶段 | 内容 | 工作量 | 预期 |
|---|---|---|---|
| P0 | 4.1 计时埋点 | 0.5 d | 能量化 A/B 段，后续所有优化可验证 |
| P1 | 2.1 `@trycua` + 2.4 docs/.md/locales + 2.5 断言脚本 | 1 d | -2,650 文件 / -224 MB，零风险 |
| P1 | 1.1 脚本 + 1.2 安装器（静态段）+ 1.3 应用内按钮 | 1 d | 首启 A 段大幅下降（待实测） |
| P2 | 3a + 4.2 + 4.3 + 4.4 | 1 d | B 段 -0.3~2 s，I/O 竞争下降，窗口立即可见 |
| P2 | 2.2 插件归档 | 1.5 d | -9,763 文件 / -180 MB |
| P3 | 5.1 安装期预热 | 0.5 d | 首次可用时间显著下降 |
| P3 | 2.3 bin 归档（1 d）/ 3b splash（1 d）/ 5.2 完整预热（0.5 d） | 2.5 d | 体积与体感进一步改善 |

### 7.3 明确不做

- 关闭 Defender 实时保护或云保护。
- 把排除范围扩大到整个磁盘 / 用户目录。
- `perMachine: true` 强制管理员安装（破坏静默升级与普通用户安装）。
- 用 `compression: maximum` 之类安装包级别的改动来换启动速度（只影响安装包体积，不影响首启）。

---

## 8. 实施状态

方案 1–5 已全部落到代码；**尚未打包**（按交付要求，打包与实测留给下一轮）。

### 8.1 改动清单

| 方案 | 文件 | 说明 |
|---|---|---|
| 4.1 | `electron/utils/startup-timeline.ts`（新增）、`electron/main/index.ts` | `markStartup()` 埋点：`whenReady`（含 `process.uptime()` = A 段真值）、`menu-ready`、`window-created`、`window-shown`、`renderer-loaded`、`ipc-ready`、`extensions-ready`、`gateway-spawn-requested`、`gateway-ready` + 汇总 |
| 3a / 4.2 / 4.3 / 4.4 | `electron/main/index.ts`、`electron/utils/telemetry.ts` | 窗口 `show: true` + `backgroundColor`（跟随 `nativeTheme`）、`initialize()` 重排（窗口优先、`applyProxySettings` 仍在网关前）、首启维护任务延后 2.5 s、`machineIdSync()` → 异步 `machineId()` |
| 2.1 | `scripts/after-pack.cjs` | `PLATFORM_NATIVE_SCOPES` 增加 `@trycua`（实测 -5 目录 / 约 -203 MB，保留 `cua-driver` 与 `cua-driver-win32-x64-msvc`） |
| 2.4 | `scripts/after-pack.cjs` | 删除 `openclaw/docs`；清理 `node_modules/**`、`dist/**` 下的 `.md`；`.ts/.mts/.cts` 默认 **report-only**（`GRANDPOEM_PRUNE_TS=1` 才删除）；`locales` 只留 `en-US.pak`、`zh-CN.pak` |
| 2.2 | `scripts/after-pack.cjs`、`electron/utils/lazy-asset.ts`（新增）、`electron/utils/plugin-install.ts` | 7 个插件打成 `resources/lazy-assets/openclaw-plugins/<id>.zip`，`buildCandidateSources()` 首次使用时解压到 `userData/lazy-cache` |
| 2.3 | `electron/utils/bundled-tool.ts`（新增）、`uv-setup.ts`、`openclaw-cli.ts`、`openclaw-doctor.ts`、`control-ui-device-pairing.ts`、`config-sync.ts`、`supervisor.ts` | `uv`/`agent-browser` 打进 `lazy-assets/bin/tools.zip`，`node` 保持解包；6 处 PATH 注入收敛到 `getBundledBinDirs()` / `withBundledBinPath()` |
| 2.5 | `scripts/assert-pack-size.mjs`（新增）、`package.json` | 文件数 < 35,000、体积 < 1.2 GB 断言（`pnpm run assert:pack-size`） |
| 1.1 | `resources/cli/win32/set-defender-exclusion.ps1`（新增） | 只排除 3 个路径 + 2 个进程名；退出码 0/2/3/4；未提权时 `-NonElevated` 直接返回 3 |
| 1.2 / 5.1 | `scripts/installer.nsh` | 安装段调用 `-NonElevated`；新增 `customFinishPage`（重新声明 `MUI_FINISHPAGE_RUN`，用 ShowReadme 位做 Defender opt-in，默认勾选）；卸载段走同一脚本 `-Action remove`；安装末尾 detached 预热 `node openclaw.mjs doctor --json` + `NODE_COMPILE_CACHE` |
| 1.3 | `shared/host-api/contract.ts`、`electron/services/app-api.ts`、`electron/utils/defender-exclusion.ts`（新增）、`src/lib/host-api.ts`、`src/components/settings/sections/DeveloperSection.tsx`、`shared/i18n/locales/{de,en,fr,zh}/settings.json` | 新增 4 个 host action；状态取自上次已验证的运行结果（`Get-MpPreference` 读取本身需要管理员）；15 个 i18n key × 4 语言 |
| 5.2 | `electron/main/index.ts` | `--grandpoem-prewarm` / `GRANDPOEM_PREWARM=1` 预热模式：不抢单实例锁与文件锁、不建窗口/托盘/IPC、起网关后 60 s 内停止并退出 |
| 3b | `scripts/launcher/Splash.cs`（新增）、`scripts/build-launcher.mjs`（新增）、`resources/bin/win32-x64/grandpoem-launcher.exe`、`package.json`、`scripts/installer.nsh` | 用系统自带 `csc.exe` 编译 splash 启动器（约 430 KB）；安装器把桌面/开始菜单快捷方式指向它 |
| 1f | `harness/specs/tasks/add-startup-acceleration-host-api.md`（新增） | host-api 契约变更的 task spec（`requiredProfiles: fast, comms`） |

### 8.2 已执行的验证

| 检查 | 结果 |
|---|---|
| `pnpm run typecheck`（node + web） | 通过 |
| `pnpm run lint:check`（eslint + UI 规范门禁） | 通过 |
| `pnpm test`（vitest） | 41 通过（含新增 `tests/unit/after-pack-cleanup.test.ts` 3 项） |
| `pnpm harness validate --spec ... --no-diff` | Spec is valid |
| `node scripts/assert-pack-size.mjs --dir release/win-unpacked` | 对**优化前**的旧产物按预期失败（50,773 文件 / 1.63 GB），可作为优化后的对照基线 |
| `node scripts/build-launcher.mjs` | 编译成功（431.5 KB） |
| `set-defender-exclusion.ps1` 语法解析 | 通过 |

### 8.3 尚未做（下一轮）

1. **打包与实测**：`pnpm run package:win:dir` → `pnpm run assert:pack-size` → `pnpm run package:win`，对比 A/B 段耗时。
2. **整树 diff 校验**：`pnpm harness validate --spec <spec>`（不带 `--no-diff`）目前会被工作区里其它在途改动（`src/App.tsx`、`GatewayBootScreen.tsx`、`electron/gateway/manager.ts` 等，非本次改动）挡住；需要那些改动先提交或单独成 spec。
3. **人工确认项**：splash 启动器的视觉与交接时机；Finish 页勾选后的 UAC 流程；安装器在真机上的完整走查（含覆盖升级与卸载）。
4. **可选开关**：`GRANDPOEM_PRUNE_TS=1` 需要先跑一轮 doctor + 网关冒烟再开启；`GRANDPOEM_DEFENDER_DRY_RUN=1` 可在开发期跳过提权联调 UI。

