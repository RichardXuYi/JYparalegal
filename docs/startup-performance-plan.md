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

---

## 9. 运行时版本核对与按需下载（已实现）

### 9.1 背景与结论（调研结论）

- **WorkBuddy（腾讯，同量级 Electron AI 应用）实测**：安装后 3,069 文件 / 1.34 GB，其中 `resources/app.asar` 是 **284 MB 单文件**（把 JS 全塞进 asar → 文件数塌缩），`resources/vendor/` 放 **node.zip 107 MB + PortableGit.zip 53.8 MB + python.zip 17.9 MB**（重运行时以压缩包随包分发、按需解压），另有 `RepairApp.exe` + `install-manifest.json`（113 条清单）做自检修复，以及 `update-progress.ps1` + `launch-transition-window.vbs` 过渡窗口。**三个 exe 全部腾讯有效签名**，`%LOCALAPPDATA%\WorkBuddy` 只有日志 → 运行期不下载任何东西。
  → 他们靠 **签名 + asar 塌缩文件数 + 运行时 zip 化** 解决首启，**不是**下载式安装。
- **ClawX（我们的上游）**：`electron-builder.yml` 与我们的打包模型同源，`win.verifyUpdateCodeSignature: false`（**上游也没有签名证书**），`asarUnpack` 显式解包 `**/node_modules/@trycua/**`（那 203 MB 的来源），**没有任何首启优化**。发布走 `oss.intelli-spectrum.com` + GitHub 双通道。
- **官方机制**：未签名/无信誉 exe 首次运行会被 **Block at first sight** 扣住等云端判定，[cloud block timeout 默认 10 秒、最多再加 50 秒](https://learn.microsoft.com/en-us/defender-endpoint/configure-cloud-block-timeout-period-microsoft-defender-antivirus)（上限 60 秒），叠加本机代理不通 → 每次首启吃满超时。这正是"大概一分钟"的来源。同类报告：[Windows Defender slowing down Electron startup](https://stackoverflow.com/questions/67982430/windows-defender-slowing-down-electron-startup)。

### 9.2 已实现

| 部分 | 文件 | 说明 |
|---|---|---|
| 运行时定位 | `electron/utils/paths.ts` | `getOpenClawDir()` 优先级：`GRANDPOEM_RUNTIME_DIR` → `runtime/current.json`（校验可用性，失效即回退）→ 包内 `resources/openclaw`；新增 `getRuntimeRootDir()`（`%LOCALAPPDATA%\grandpoem-studio\runtime`，**不放 Roaming**）、`read/write/clearActiveRuntimePointer()`、`getOpenClawDirSource()`；`getOpenClawStatus()` 增加 `source` |
| 清单与版本模型 | `electron/utils/runtime-manifest.ts` | `runtime-manifest.json` 读取（BOM 容错）、版本规范化与精确比对、按版本目录的安装标记、已安装版本列表/清理、consent 读写、`getRuntimeReadiness()` |
| 归档产物 | `scripts/pack-openclaw-runtime.mjs`、`package.json` | `runtime:pack` 产出 `.tar.gz` + `runtime-manifest.json` + `runtime-required.txt`（`--archive-url`、`--copy-to` 暂存）；`runtime:verify` 校验 sha256/size/版本一致性 |
| 下载/校验/解压/切换 | `electron/services/runtime-service.ts` | 下载用 Electron **`net`**（自动继承 `session.defaultSession` 代理，避开 devDependency `https-proxy-agent`）；`Range` 断点续传 + 空闲超时；流式 sha256；`tar.x` 解压（`preservePaths:false` 防穿越）→ 写标记 → 原子 rename → 写指针 → 保留 2 个版本；`cancel()` 真正 abort 请求；导入本地归档；回滚到上一版本/包内 |
| Host 契约 | `shared/host-api/contract.ts`、`shared/host-events/contract.ts`、`electron/services/runtime-api.ts`、`electron/main/ipc-handlers.ts`、`src/lib/host-api.ts`、`src/lib/host-events.ts` | 新增 `runtime` 模块（status/manifest/install/cancel/rollback/importArchive/revealFolder）与 `runtime:{progress,stateChanged,log}` 事件组（preload 白名单由 `HOST_EVENT_CHANNELS` 自动覆盖） |
| 启动屏 | `src/components/common/GatewayBootScreen.tsx` | 运行时未就绪时改为"运行时准备"分支：确定进度条、阶段文案、已下载/总量、取消/重试/选择本地安装包；consent 未决定时先问一次 |
| 安装期检测 | `scripts/installer.nsh` | 读 `resources/runtime-required.txt` → 命中包内运行时或 `%LOCALAPPDATA%\...\runtime\<版本>\.grandpoem-runtime.json` 则跳过；否则 `MessageBox` 询问，结果写 `runtime-consent.json`（应用不再重复询问）。**下载主体仍在应用内**（进度/续传/校验/代理都可控） |
| WorkBuddy 借鉴 ② | `resources/cli/win32/transition-window.ps1`、`launch-transition-window.vbs`、`scripts/installer.nsh` | 过渡窗口的 VBS+PS1 实现（`wscript` 隐藏中继，关闭条件=窗口出现/进程退出/硬超时）；快捷方式优先指向编译版 `grandpoem-launcher.exe`，**csc 缺失时自动回退**到 PS1/VBS |
| 下载模式打包 | `scripts/make-download-config.mjs`、`scripts/after-pack.cjs`、`package.json` | `config:download` 从权威 `electron-builder.yml` 派生下载模式配置（去掉 `build/openclaw`、加清单/版本文件），避免第三份手工复制的配置漂移；after-pack 从**传入的配置**判定模式并跳过全部 bundled-runtime 步骤；`package:win:download` |
| Defender 补强 | `resources/cli/win32/set-defender-exclusion.ps1`、`electron/utils/defender-exclusion.ts` | 排除目标加入 `%LOCALAPPDATA%\grandpoem-studio`（覆盖下载的运行时与懒加载缓存），否则刚下载的 4 万文件会在首次网关启动时被重新扫描 |

### 9.3 实测数据（本次）

对**未瘦身**的旧产物（40,721 文件 / 850.8 MB）打包：

| 项 | 数值 |
|---|---|
| 归档 `openclaw-runtime-2026.9.6-win32-x64.tar.gz` | **255.3 MB** |
| 打包耗时（gzip level 6） | 127 秒 |
| 现有安装包（含运行时） | 422.1 MB |
| 下载模式安装包（预估） | **约 165–175 MB** |

即：用户总下载量基本不变（安装包 −255 MB，运行时 +255 MB），收益在**安装包体积、更新粒度、镜像/CDN 分发**，以及"把 4 万文件的写盘与首扫从首次点开挪到安装期"。瘦身后的运行时再打包会更小。

### 9.4 与计划的偏差（有意为之）

1. **下载在主进程用 `net`，解压也在主进程用 `tar`**，而不是计划里的 `utilityProcess` worker：worker 无法访问 Electron `net`（拿不到会话代理），而 `https-proxy-agent` 只是 devDependency；新增一个构建入口换来的隔离度不值这个复杂度。解压是异步流式的，进度按 250 ms 节流。
2. **`--upload` 改为 `--copy-to`**：仓库里没有任何上传实现/凭据（`publish` 用的是自建 generic 源，electron-builder 的 generic provider 不支持上传），所以产物脚本只负责生成 + 校验 + 暂存，上传仍按现有发布流程手工/CI 完成。
3. **`runtime:verify` 合并进同一脚本的 `--verify`**（少一个文件，覆盖相同）。
4. **`assert:pack-size` 只挂在 `package:win:dir` 末尾**，不改 `package:win` 发布链路。

### 9.5 尚未实现的计划项

- **网关失败对话框的"下载运行时"动作**：`GatewaySuggestedAction`（`shared/types/gateway.ts:35`）目前是 `'retry' | 'viewLogs' | 'copyReport' | 'runDoctor'`，需要新增 `'installRuntime'`，在 `failure-taxonomy.ts` 增加匹配 `OpenClaw package not found at:` / `OpenClaw entry script not found at:` 的分类分支，并在 `GatewayFailureDialog.tsx` 加按钮 + 四语言文案。用户在主路径（启动屏）已经能完成下载；只有"跳过启动屏后"才需要这个入口。
- **安装器内 BITS 预取变体**（计划里的 P3 可选路径）未做。

### 9.6 插件依赖重复：实测与结论（未实施）

早期口头分析里的"206 个重复包目录"是**按包名**统计的，把同名不同版本也算进去了，高估了可去重量。按"同名同版本"精确实测（`release/win-unpacked`，未瘦身树）：

| 项 | 数值 |
|---|---|
| 运行时 `openclaw/node_modules` | 431 个包 / 487.9 MB |
| 7 个插件镜像 node_modules 合计 | ≈ 255 MB |
| **与运行时"同名同版本"→ 理论可去重** | **148 个包 / 42.2 MB** |
| 与运行时"同名不同版本"→ 不能合并 | 17 个包 / 4.9 MB |
| 插件独有（dingtalk 131.1 MB、wecom 45.3 MB 等自家 SDK） | 92 个包 / 207.5 MB |

**为什么不能直接删**：插件镜像会被复制到 `~/.openclaw/extensions/<id>/` 运行，从该路径向上解析只能到 `~/.openclaw/extensions/node_modules` → `~/.openclaw/node_modules`，**到不了 app 内的 `resources/openclaw/node_modules`**。`after-pack.cjs` 里 macOS 分支那种"复用顶层依赖"的裁剪只对**跑在原地**的 built-in extension 成立，对复制出去的镜像是无效的。要去重就必须：

1. 改成共享根 `~/.openclaw/extensions/node_modules`（要处理上面 17 个版本冲突），或
2. 打包期把"与运行时同名同版本"的依赖从镜像里裁掉，并在安装插件时把共享依赖写到该共享根。

两种方案的风险都落在"某个渠道运行时静默缺模块"——最难排查的那类故障，而收益只有 42–80 MB（占 1.63 GB 安装体积的 3–5%）。

**结论（建议）**：不去重。改用已经建好的懒加载/下载通路——**把 7 个插件镜像也改成按需下载**（它们现在已经是单个 zip，只差从安装包挪到远端）：

- 下载模式安装包再瘦约 **180 MB**；
- 重复代码在"用哪个渠道装哪个"的前提下不再构成成本，**完全不动插件的依赖解析，零风险**；
- 离线/内网走已实现的 `runtime.importArchive`（本地归档导入）；
- 代价：添加渠道时多一次下载，需要进度 UI（可复用启动屏那套）。

> 若将来仍要启用依赖去重，必须先在每个已配置渠道上跑通冒烟测试，并加一个启动自检（共享依赖缺包直接告警，而不是等渠道跑挂）。

---

## 11. 强制去重：同一包名只保留一份（已实现）

### 11.1 目标与原则

**目标**：最终安装态下，任意包名在运行时 bundle 与 7 个插件镜像之间**只存在一份物理副本**。

**原则（用户明确要求）**：
1. 只改 studio-frontend 仓库内的源码；不改 openclaw 本身（外部 npm 包）。
2. **强行**按包名去重，**不看版本号**——运行时（openclaw）的副本永远胜出。
3. 不做预设例外；插件若因版本替换而损坏，才修，修到能用为止。

### 11.2 两段式实现

**构建期**（`scripts/after-pack.cjs` → `deduplicatePluginAgainstRuntime()`，在每个插件 `bundlePlugin()` 之后调用）：

1. 扫描运行时 bundle 顶层 `resources/openclaw/node_modules/` 得到包名集合（scoped 记为 `scope/name`）；
2. 遍历插件镜像 `node_modules/`，**凡包名与运行时重合即删除**（`DEDUP_EXCEPTIONS` 除外，当前为空）；
3. 删除后清掉空的 `@scope/` 目录；
4. 把被删的包名写入镜像根的 `shared-deps.json`（`{ schema: 1, sharedDeps: [...] }`）；
5. 从镜像自己的 `package.json` 的 `dependencies` / `optionalDependencies` / `peerDependencies` 中移除这些名字——镜像不再声明它没带的依赖。

**安装期**（`electron/utils/plugin-install.ts` → `provisionSharedDeps()`，在镜像复制到 `~/.openclaw/extensions/<id>/` 之后调用）：

1. 读镜像的 `shared-deps.json`；
2. 把每个包从 `getOpenClawDir()/node_modules/` 复制到 **`~/.openclaw/extensions/node_modules/`**（共享根）；
3. 幂等：目标已存在则跳过（不比较版本——强制去重的前提就是运行时版本唯一）；
4. 任一包在运行时 bundle 里找不到时记录 `missing` 并告警（打包不一致，而非运行时故障），不抛异常、不阻断渠道配置流程。

**为什么共享根能被解析到**：插件从 `~/.openclaw/extensions/<id>/` 运行，Node 的向上查找会依次访问 `<id>/node_modules` → `~/.openclaw/extensions/node_modules`，正好是共享根。一份副本服务所有已安装镜像。

**为什么不用 symlink**：Windows 建符号链接需要提权或开发者模式；本仓库此前已因同样原因移除过基于 symlink 的 skill 安装方式。

### 11.3 构建期断言

`scripts/assert-single-copy.mjs`（接入 `package:win:dir` 末尾，脚本名 `assert:single-copy`）：

- 比较运行时 bundle 与每个插件镜像的包名集合，**交集必须为空**；
- 额外检查：凡在 `shared-deps.json` 里声明为"共享"的包名，**不得**再出现在镜像目录中（防止回填）；
- `DEDUP_EXCEPTIONS` 中列出的名字按预期出现两次 → 只报告不失败；`--strict` 时即使有例外也失败；
- 下载模式（无插件镜像）视为通过。

这样将来升级 openclaw 或插件、或改动去重逻辑而引入重复时，构建会直接失败，而不是悄悄把重复打回包里。

### 11.4 实测效果（对现有未瘦身产物模拟）

| 插件 | 去重前 | 去重后 | 删除包数 | 保留私有包数 |
|---|---|---|---|---|
| dingtalk | 139.5 MB | 132.8 MB | 45 | 21 |
| discord | 17.3 MB | 6.7 MB | 15 | 44 |
| feishu-openclaw-plugin | 21.7 MB | 3.0 MB | 53 | 3 |
| openclaw-weixin | 4.0 MB | 0.5 MB | 2 | 0 |
| qqbot | 3.9 MB | 0.6 MB | 9 | 0 |
| wecom | 50.2 MB | 46.8 MB | 40 | 15 |
| whatsapp | 26.6 MB | 25.8 MB | 1 | 2 |
| **合计** | **263.3 MB** | **216.2 MB** | **165** | **85** |

- **节省 47.1 MB**；断言结果：**零重复包名**（同一包名只出现一次）。
- 镜像总体积下降有限，是因为保留的私有依赖（dingtalk 自带 SDK/PDF 栈、wecom 的 `@wecom/*` CLI、whatsapp 的 baileys、discord 的 micromark 链）本来就不在运行时里——这些是插件的真实功能依赖，不是重复。
- 与 §9.6 的差异说明：那里按"同名同版本"统计只有 42.2 MB 可安全去重；本方案按**包名强行去重**（含 17 条同名不同版本），因此受益面更大——这正是不看版本带来的收益。

### 11.5 风险与已知边界

1. **同名不同版本的替换**：17 条冲突中，`undici`(7→8)、`file-type`(21→22，v22 为纯 ESM) 等被强行替换为运行时版本。若某渠道因此挂掉，按既定协议处理：先修插件源码；只有确实无法修复时才把该包名加进 `scripts/openclaw-bundle-config.mjs` 的 `DEDUP_EXCEPTIONS`，并在注释里写清错误信息、渠道与复现步骤。
2. **平台相关的原生包**：去重只看运行时**顶层**包名；同一目标平台下发包与运行时平台裁剪一致，因此 `missing` 预期为空。若出现，会在安装日志中以 `[plugin-shared-deps] Missing from the runtime bundle: ...` 告警。
3. **必须逐渠道冒烟**：7 个渠道各连通一条消息。这是强统一的必要验证，`typecheck`/单测无法覆盖运行时 API 兼容性。
4. **下载模式同样生效**：去重发生在插件镜像打包阶段，与是否随包分发无关；按需下载的插件包也因此更小。

### 11.6 改动文件

| 文件 | 改动 |
|---|---|
| `scripts/openclaw-bundle-config.mjs` | 新增 `DEDUP_EXCEPTIONS`（当前为空） |
| `scripts/after-pack.cjs` | 新增 `deduplicatePluginAgainstRuntime()`、`prunePackageJsonDependencies()`、`directorySize()`；在插件打包循环中调用；导出到 `__test` |
| `electron/utils/plugin-shared-deps.ts` | 新增：清单读取、共享依赖补给、已补给列表 |
| `electron/utils/plugin-install.ts` | 安装成功后调用 `provisionSharedDeps(targetDir, extensionsRoot)` |
| `scripts/assert-single-copy.mjs` | 新增断言 |
| `package.json` | 新增 `assert:single-copy`，接入 `package:win:dir` |
| `tests/unit/plugin-dedup.test.ts` | 新增 6 项 |
| `tests/unit/plugin-shared-deps.test.ts` | 新增 17 项 |

### 11.7 运行验证（A/B 对照，已实跑）

用打包产物里的 `node.exe` + `openclaw.mjs`，把 `HOME` 指向一个模拟的 `~/.openclaw/extensions/`，对**去重版**与**原始版**各跑一遍真实网关：

| 项 | 去重版 | 原始版 |
|---|---|---|
| 网关启动 | `http server listening (14 plugins)` | 相同 |
| `doctor --json` | exit 0 / 35 checks | 相同 |
| 渠道插件相关日志 | 无差异 | — |
| 唯一告警 | `auth token missing`（无害） | 相同 |

补充验证：
- 7 个镜像去重后，**安装期从 runtime 补给 93 个包，缺失 0**；
- feishu 被删的 **53 个包 100% 可从共享根解析**（含 `protobufjs`）；
- 在插件目录内做真实 `import()`：dingtalk / wecom / qqbot / whatsapp / openclaw-weixin **全部依赖可导入**；discord 仅 `discord-api-types`（types-only，无运行时 import）、feishu 仅 `tsdown`/`vitest`（只在 `tsdown.config.js`，`dist/` 干净）两个探针误报。

**结论**：去重前后运行时行为一致，机制本身不在运行时引入回归。仍未覆盖的是"用真实账号逐渠道收发消息"（强统一版本的兼容性风险只能在那里暴露）。

### 11.8 已确认的上游缺陷（不在本次范围，勿重复排查）

A/B 对照顺带确认两个**既存**问题，**原始版和去重版都存在**，属于 OpenClaw 侧缺陷，本次不修（后续可能需要删掉渠道再重新添加来规避）：

1. **feishu 插件（openclaw-lark）注册失败**：原始版报 `Plugin dependency @types/node is missing from .../feishu-openclaw-plugin/node_modules/protobufjs`；去重版报 doctor capture 树里的 `ERR_PACKAGE_PATH_NOT_EXPORTED: './plugin-sdk' is not defined by "exports" in .../openclaw/package.json`。即该插件在本次改动**之前就已经装不起来**，本次只是让报错文本变了。
2. **wecom 插件 id 不匹配**：`plugin id mismatch (config uses "wecom-openclaw-plugin", export uses "wecom")`，两版完全一致。

因此 `DEDUP_EXCEPTIONS` **保持为空**——这两个问题都与去重无关，不应为此添加例外。

### 11.9 独立测试结论（本机实跑）

本轮由 AI 代替人工执行测试，结论如下。

**（1）发现并修复一个真实的构建期测试缺陷：comms 门禁在 Windows 上静默空转**

`scripts/comms/{replay,compare,baseline}.mjs` 用 `new URL(import.meta.url).pathname` 推导项目根目录。在 Windows 上该表达式返回 `/D:/Projects/.../replay.mjs`（且对非 ASCII 路径做百分号转义），`path.resolve()` 会把它拼成 `D:\D:\Projects\...`。

- `baseline.mjs` 没有入口守卫，因此**报错退出**；
- `replay.mjs` / `compare.mjs` 把 `main()` 放在 `isEntrypoint` 守卫之后，而该守卫由同一个损坏的路径计算，**恒为 false**——脚本打印没有任何输出、**退出码 0**，但什么都没做。

即 `pnpm run comms:replay && pnpm run comms:compare` 这条 AGENTS.md 明确要求的通信改动门禁，在本机**从未真正执行过**，却一直"通过"。这是回归门禁最糟糕的失效方式：静默假绿。

修复方式：三个脚本统一改用 `fileURLToPath(import.meta.url)`。修复后 `replay` 正常写出 `artifacts/comms/current-metrics.json`，`compare` 输出 42 项全 PASS 的报告。

新增 `tests/unit/comms-gate.test.ts`（4 个用例）作为回归守卫：断言不再使用 `URL.pathname`、replay 必须产物落盘、指标跨次运行确定性一致、缺少必需场景时 compare 必须非 0 退出。该用例已反向验证——把 `replay.mjs` 还原成旧写法时 **4 个用例全部失败**，确认不是空转断言。

**（2）comms 门禁覆盖不到本次改动（重要边界）**

核对后确认：`replay.mjs` / `compare.mjs` / `baseline.mjs` **均不 import 任何 `electron/`、`src/`、`shared/` 代码**，只是对静态 JSONL 夹具做纯计算，因此改动前后指标逐字节相同。

结论：本次去重/运行时改动 **不能**靠 comms 门禁证明。它的价值是守住通信路径不退化，而不是验证本次改动。本次改动的真实覆盖来自下面 (3)(4) 与既有单测。

**（3）构建期去重：用真实产物做端到端验证（106 → 0）**

从本机真实构建输入（`build/openclaw` 运行时 499 个包 + 7 个真实插件镜像）拼出与 `release/win-unpacked` 同构的产物目录，然后：

| 步骤 | 结果 |
|---|---|
| 去重前 `assert:single-copy` | **FAIL，106 个包名同时存在于运行时与镜像** → 退出码 1 |
| 执行真实 `deduplicatePluginAgainstRuntime()` | **dropped 178 / kept 91，释放 104.8 MB** |
| `shared-deps.json` 与磁盘一致性 | 7/7 镜像均生成，**0 处不一致**（无"已声明共享却仍在盘上"） |
| 去重后 `assert:single-copy` | **OK: every package name exists at most once** → 退出码 0 |
| 去重后 `--strict` | 同样 PASS |

镜像包数 **269 → 91**。这证明断言不是空转，也证明去重函数在真实依赖图上确实收敛到零重复。

各镜像 dropped/kept：dingtalk 45/27（10.2MB）、discord 28/44（36.0MB）、feishu 53/3（41.3MB）、wecom 40/15（4.7MB）、qqbot 9/0（5.2MB）、openclaw-weixin 2/0（6.0MB）、whatsapp 1/2（1.5MB）。

**（4）门禁全绿**

| 门禁 | 结果 |
|---|---|
| `pnpm run test` | **80 passed / 11 files**（原 76 + 新增 4） |
| `pnpm run typecheck`（node + web） | exit 0 |
| `pnpm run lint:check` | exit 0（UI 规范门禁通过，扫描 16 个页面文件） |
| `pnpm run comms:replay` + `comms:compare` | PASS（修复后**首次真正执行**） |

**（5）尚未覆盖（需人工/真实账号）**

- `release/win-unpacked` 是**改动前**的旧产物（无 `runtime-required.txt`、无插件归档），因此不能在它上面验证新逻辑；真实产物验证仍需 `pnpm run package:win:dir`。
- 未做安装器真机安装测试（NSIS 运行时探测、Defender 排除项、过渡窗口）。
- 未做各渠道真实账号收发消息的连通性冒烟，这是强制版本统一后唯一的剩余风险。

**（6）测试过程备注**

首次用目录联接（junction）构造夹具时，去重函数通过联接**删除了真实 `build/openclaw-plugins` 里的包**（该目录已被 gitignore，可重新生成）。已用 `pnpm run bundle:openclaw-plugins` 完整重建（feishu 恢复至 56 依赖、dingtalk 72、wecom 55，且无残留 `shared-deps.json`），并改为**复制而非联接**镜像目录，避免再次发生。该目录是构建中间产物，不影响仓库内容。




