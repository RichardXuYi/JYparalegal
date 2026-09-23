<div align="center">

# 🖥 GrandPoem Studio

**数智员工的桌面工作台 — 从数字 Agent 到数字人的驾驶舱**

[![Version](https://img.shields.io/badge/version-1.5.0-blue)](#)
[![Electron](https://img.shields.io/badge/Electron-40-47848F?logo=electron&logoColor=white)](#)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](#)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript&logoColor=white)](#)
[![OpenClaw](https://img.shields.io/badge/OpenClaw-Gateway-8A2BE2)](#)
[![Port](https://img.shields.io/badge/dev%20port-3104-success)](#-服务端口对照表)
[![License](https://img.shields.io/badge/license-MIT-green)](#-许可证)

**Windows** · **macOS** · **Linux**

</div>

---

基于 Electron + OpenClaw 的桌面端智能体应用，是数智员工的图形化工作台：通过内嵌 OpenClaw 网关调度多模型能力，让数智员工进驻钉钉 / 飞书 / 企业微信 / Discord / Telegram / WhatsApp 等沟通渠道，并以 Skill 库为其装配技能。

## 🧰 技术栈

<details>
<summary><b>点击展开完整依赖清单</b></summary>

| 技术 | 版本 | 说明 |
|------|------|------|
| Electron | 40.6.0 | 桌面运行时 |
| React | 19.2.4 | UI 框架 |
| TypeScript | 5.9.3 | 类型系统 |
| Vite | 7.3.1 | 构建工具 |
| Tailwind CSS | 4.1.18 | 原子化样式 |
| Radix UI | 最新 | 无障碍基础组件 |
| Zustand | 5.0.11 | 状态管理 |
| React Router DOM | 7.13.0 | 路由 |
| React Markdown | 10.1.0 | Markdown 渲染 |
| Monaco Editor | 0.55.1 | 代码编辑器 |
| i18next | 25.8.11 | 国际化 |
| electron-builder | 26.8.1 | 桌面打包 |
| electron-updater | 6.8.3 | 自动更新 |
| sharp | 0.34.5 | 图像处理 |
| KaTeX | 0.16.45 | 数学公式渲染 |
| PDF.js | 5.7.284 | PDF 预览 |
| XLSX | 0.18.5 | Excel 处理 |

</details>

## 🏗 架构

```
studio-frontend/
├── electron/              # Electron 主进程
│   ├── main/              # 主进程入口
│   ├── preload/           # 预加载脚本
│   ├── gateway/           # AI 网关服务（27 文件）
│   ├── services/          # 后台服务（22 文件）
│   ├── extensions/        # 平台扩展
│   ├── shared/            # 主进程共享代码
│   └── utils/             # 工具函数（50 文件）
├── src/                   # 渲染进程（React）
│   ├── components/        # UI 组件
│   ├── hooks/             # 自定义 Hooks
│   ├── extensions/        # 前端扩展
│   └── pages/             # 页面
├── shared/                # 主进程 & 渲染进程共享
│   ├── chat/              # 聊天协议
│   ├── host-api/          # 宿主 API
│   ├── host-events/       # 宿主事件
│   ├── i18n/              # 国际化资源
│   └── types/             # 共享类型
├── scripts/               # 构建 & 打包脚本
├── resources/             # 静态资源（图标、CLI、Skill）
└── harness/               # Harness 规范校验工具
```

## ✨ 核心能力

### 🧠 AI 网关 (`electron/gateway/`)
- OpenClaw 网关通信（内嵌进程，端口 18789）
- 多模型路由（DeepSeek / GLM / Qwen 等）
- 流式响应处理
- 会话管理

### ☁️ 提供商管理 (`electron/shared/providers/`)
- 内置提供商注册表：新增 **MiniMax**、**通义千问 (Qwen)**、**智谱 GLM** 官方接入（OpenAI 兼容协议）
- 计划类型选择：API / Coding Plan / Token Plan
- 模型列表 Chips 编辑器（`ModelChipsEditor`）：回车添加模型 ID，自动去重
- 同步防护：写入 / 回读 `openclaw.json` 时循环剥离运行时前缀，避免模型 ID 前缀叠加污染

### 💬 渠道进驻 (`electron/extensions/`)

数智员工像真实同事一样进驻沟通工具：

| 渠道 | 实现 |
|------|------|
| 钉钉 | DingTalk Stream |
| 飞书 | Lark SDK |
| 企业微信 | WeCom 回调 |
| Discord | discord.js |
| Telegram | Grammy |
| WhatsApp | Baileys |
| QQ | QQ Bot |

### 🧩 Skill 库管理
- 预装 Skill 打包
- **上传式技能安装**：支持 `.md` / `.markdown` / `.zip`，自动解包至 `~/.openclaw/skills/<slug>/`（可确认覆盖）
- 上传前强制安全扫描：校验 SKILL.md front-matter（name/description）、拒绝路径穿越 / 符号链接 / 超大文件（单文件 ≤2MB、总量 ≤20MB、条目 ≤200）
- 技能卡片不再展示本地路径，仅保留来源 Badge
- OpenClaw 插件集成

### 🔄 自动更新
- 基于 `electron-updater` 的自托管更新
- 更新服务器：`https://47.116.163.57:8082`

### 🛡️ 账户切换与会话清理（v1.4.63 新增）

修复快速登出→登录时 Gateway 留在错误 OpenClaw 目录、用户串货看到上一个账户会话的串扰问题：

- **统一调度器 `src/lib/user-session-reset.ts`**：提供 `resetAllUserStores()`，账户变更时一次性重置 9 个 store（chat / gateway / skills / agents / cron / channels / artifact-panel / providers / settings）的 `resetForUserSwitch()`、调用 `useConnectionStatusStore.endModelSwitch()` 取消进行中的模型切换、移除 per-user localStorage 键（`studio-jwt` / `grandpoem-studio-last-session` / `grandpoem-studio:image-cache` / `grandpoem-studio.artifact-panel`）。
- **Scope 重启串行化** `electron/services/backend-auth-api.ts`：将 `applyScopeChange` 改为 `scopeChangeEpoch` + `scopeRestartChain` 串行链；快速连续多次 scope 变更在链上排队，每一变更都保证 Gateway 重启落地且永不并发（避免多账户争夺同一 OpenClaw 目录）。
- **Gateway 冷却重试** `electron/gateway/manager.ts`：`restart()` 被 governor 冷却抑制后等待 `decision.retryAfterMs + 100ms` 并重试一次，防止快速 logout→login 第二次重启被静默丢弃。
- **App.tsx 账户 ID 监听** `src/App.tsx`：监听 `authUser?.id` 变化，对 `initSettings / initGateway / initProviders` 重新触发初始化。
- **per-account 语言隔离** `src/stores/settings.ts`：新增 `grandpoem-studio-language-explicit:<userId>` 显式语言标记，账户变更后回退到浏览器 locale（而非服务端 en 默认）；`setLanguage` 时写入显式标记。
- **Gateway 进程堆上限** `electron/gateway/config-sync.ts`：启动 env 注入 `NODE_OPTIONS=--max-old-space-size=<JY_GATEWAY_MAX_OLD_SPACE_MB, 默认 1024>`，失控账号只 OOM 自己的进程，由崩溃机制重启。

**运行时回归脚本** `scripts/verify-scope-switch.ts`：USERPROFILE 重定向到临时目录、不触碰真实用户配置，断言 scope 重启链串行收敛且不重叠。运行（桌面端目录内）：

```bash
node node_modules/tsx/dist/cli.mjs --tsconfig tsconfig.node.json scripts/verify-scope-switch.ts
```

### ☁️ 云同步错误明细与设备管理（v1.4.65 新增）

- **上传失败明细** `electron/services/sync/config-sync-service.ts`：`pushBundle` / `pushConfig` 由返回 `boolean` 改为 `{ ok, error }`；agents / skills / preferences 上传时收集所有失败项，失败时返回 `{ scope, ok: false, pushed, error }`，错误信息包含具体 slug 与后端原因（不再静默吞错，便于用户定位单个包/技能上传失败的原因）。
- **设备列表接口适配** `electron/services/backend-auth-api.ts`：`/api/auth/devices` 返回结构由 `{ devices: [...] }` 适配为直接数组（后端直出数组），修复设备列表读取失败。

### 🧠 会话思考模式选择器（v1.4.66 新增）

让用户在不切换模型的前提下，按会话粒度控制 OpenClaw Gateway 的 `/think` 行为：

- **顶栏按钮** `src/pages/Chat/ChatInput.tsx`：模型选择器右侧新增 Brain 图标按钮，悬停提示「此聊天的思考模式」；点击展开 8 级浮层（`Inherit / Off / Low / Medium / High / XHigh / Adaptive / Max`），选中项保持当前会话的覆盖。
- **状态与持久化** `shared/chat/types.ts` + `src/stores/chat.ts`：`ChatState` 新增 `thinkingLevel: string | null` + `setThinkingLevel(level)` action（乐观更新；调用 `hostApi.gateway.rpc('sessions.patch', { key, thinkingLevel })` 写入 gateway；失败回滚）。
- **i18n**：en/zh/de/fr 同步新增 11 个翻译 key（`composer.pickThinkingLevel` / `thinkingPickerTitle` / `thinkingLevelFailed` / `thinkingLevelInherit` / `thinkingLevelOff` / `thinkingLevelLow` / `thinkingLevelMedium` / `thinkingLevelHigh` / `thinkingLevelXHigh` / `thinkingLevelAdaptive` / `thinkingLevelMax`）。
- **关闭菜单互斥**：与 Agent / Skill / Model 选择器共用同一个 outside-click 与 Escape 关闭路径，避免多弹层叠加。

### ⚙️ 模型元数据管理（v1.4.66 新增）

让用户在「模型编辑」面板直接持久化 `openclaw.json` 中 `models.providers.<key>.models[]` 的 `contextWindow` / `reasoning` 字段，避免每次升级 OpenClaw 后被默认推断值拖累：

- **新 host API** `shared/host-api/contract.ts`：`providers.getModelsMeta(providerKey, modelId)` 返回 `contextWindow / contextTokens / reasoning` + `inferredContextWindow / inferredReasoning / presets`；`providers.updateModelsMeta({ providerKey, modelId, contextWindow?, reasoning? })` 写回 openclaw.json 与所有 agent 的 `models.json`，仅在值实际变化时调度 `gatewayManager.debouncedReload()`。
- **读写工具** `electron/utils/openclaw-auth.ts`：导出 `readModelEntryMetadata` / `updateModelEntryMetadata`；后者通过 `withConfigLock` 串行化，先 patch `openclaw.json` 的 provider entry，再遍历 `agents/<id>/agent/models.json` upsert 行；保留其他字段不动，缺失行自动创建。
- **能力推断** `electron/shared/providers/model-capabilities.ts` + `model-context-table.ts`（v1.4.67 重构为精确表）：
  - 新增 `inferCustomModelReasoning(modelId)`：GPT-5/o1-4/Claude/Gemini/Kimi-K2/DeepSeek-R1/GLM-4.5+/Qwen3/MiniMax-M2-3 等被识别为可思考（避免模型行缺失 `reasoning` 字段时静默关闭 thinking 能力）。
  - `model-context-table.ts`：70 个知名模型的精确上下文清单（供应商 / ID / 别名 / 官方精确值 / 来源，逐个官网核实于 2026-08-18，详见 `docs/model-context-window.md`），匹配为精确 ID 查表，无正则。
  - `CONTEXT_WINDOW_PRESETS = [131_072, 200_000, 262_144, 400_000, 524_288, 1_048_576, 2_097_152]` + `formatContextWindowLabel(tokens)`（≥1M 保留一位小数，如 1.5M）。
  - `resolveContextWindowPresets(modelId)`：档位 = 全部 ≤ 模型最大上下文的标准档 + 特殊档（GPT-5.4 的 272K、GPT-5.6 Sol 的 1.5M）；1M 模型可选 128k–1M 小档省 token，200K 模型只出现 128k/200k；未知模型保守 fallback 200k（默认值）。
- **ProviderSettings UI** `src/components/settings/ProvidersSettings.tsx`：13 个本地管理供应商（`custom` / `ollama` / `MiniMax` 系列 / `moonshot` / `ark` / `siliconflow` / `deepseek` / `modelstudio` / `qwen` / `zhipu`）的每个模型在 chips 编辑器下方新增「上下文窗口（k/M 下拉）+ 思考模式 Switch」编辑卡；debounce 300ms 加载元数据，保存按钮仅在有未保存差异时启用；保存调用 `hostApi.providers.updateModelsMeta` + 可选的 `onSaveEdits(payload)`，互不阻塞。

### 🔧 Gateway 端口双源修复（v1.4.66 新增）

修复多 Worker 端口争抢导致的切换用户重连死循环与 token 验证失败（双端镜像修复，详见 `studio-web/README.md` 对应章节）：

- **状态初始化去硬编码** `electron/gateway/state.ts`：`GatewayStateController` 默认端口由 `PORTS.OPENCLAW_GATEWAY` 改为 `getPort('OPENCLAW_GATEWAY')`，消除 Fleet 模式下 supervisor 分配端口被覆盖的"双源问题"。
- **start 路径端口断言** `electron/gateway/manager.ts`：
  - `start()` 在 `startLock = true` 之后再次强制 `this.setStatus({ port: getPort('OPENCLAW_GATEWAY') })`，防止后续链路再次覆盖动态端口。
  - `startProcess()` 启动前比对 `this.status.port` 与 `getPort('OPENCLAW_GATEWAY')`，不一致时打印 `Gateway port drift detected: <a> != <b>; using dynamic port` 警告并纠正。
- **依赖收敛** `electron/services/gateway-api.ts` + `electron/utils/control-ui-device-pairing.ts`：将 `PORTS.OPENCLAW_GATEWAY` 全部替换为 `getPort('OPENCLAW_GATEWAY')`，消除最后两处硬编码回退。
- **崩溃日志可观测性** `electron/utils/logger.ts`：`flushBuffer()` 写文件失败时降级输出到 `process.stderr`，gateway 崩溃日志不再静默丢失。
- **环境变量清理**：删除 `.env` 中未使用的 `OPENCLAW_GATEWAY_PORT` 污染变量，避免子进程环境继承时被误读。

## 🚀 快速开始

### 开发

```bash
# 安装依赖
pnpm install

# 下载捆绑运行时（Windows）
pnpm run prep:win-binaries

# 启动开发模式
pnpm run dev
# → http://localhost:3104
```

> [!NOTE]
> `pnpm dev` 会自动启动内嵌 OpenClaw Gateway（端口 18789），就绪需约 10-30s。

### 构建

```bash
# Vite 构建
pnpm run build:vite

# 完整构建（含 Electron 打包）
pnpm run build
```

### 打包

```bash
# Windows NSIS 安装包
pnpm run package:win

# macOS DMG
pnpm run package:mac

# Linux AppImage / deb / rpm
pnpm run package:linux
```

### 通信回归

```bash
# 通信路径回归（基线 / 回放 / 对比）
pnpm run comms:replay
pnpm run comms:compare
pnpm run comms:baseline
```

> 单元测试（Vitest）、E2E（Playwright）与 `harness:ci` 已于 **v1.4.60** 移除，清理清单见根目录 [`docs/test-cleanup-list.md`](../docs/test-cleanup-list.md)；提交前校验改用 `pnpm run typecheck`（下节）与 `pnpm run lint:check`。

### 类型检查

```bash
pnpm run typecheck        # Node + Web
pnpm run typecheck:node   # 仅 Node（主进程）
pnpm run typecheck:web    # 仅 Web（渲染进程）
```

### 版本管理

```bash
pnpm run version:patch    # 1.1.1 → 1.1.2
pnpm run version:minor    # 1.1.1 → 1.2.0
pnpm run version:major    # 1.1.1 → 2.0.0
```

## 🔌 服务端口对照表

本端渲染层 dev server 占用 **3104**（Electron 通过 `VITE_DEV_SERVER_URL` 自动加载）。平台全局端口分配如下（五个前端统一使用 3101~3105 连续端口）：

| 端口 | 服务 | 功能说明 |
|------|------|----------|
| 8181 | 后端 API 服务（backend） | Spring Boot REST API + WebSocket |
| 3101 | 运营后台（admin-frontend） | 内容 / 用户 / 订单 / 营销 / 站点配置 |
| 3102 | 用户应用端（app-frontend） | 资讯 / 服务 / 活动 / 会员 |
| 3103 | 品牌官网（official-web） | 无认证静态门户 |
| **3104** | **Studio 桌面端渲染层（studio-frontend）** | **本项目**—Electron 渲染进程 Vite dev server |
| 3105 | Studio Web 前端（studio-web） | 浏览器版 Studio，宿主服务 8788 |
| 8788 | Studio Web 宿主服务 | Fastify + JWT，WebSocket 宿主 |
| **18789** | **OpenClaw Gateway** | 本项目内嵌网关进程，`pnpm dev` 时自动启动（就绪需 ~10-30s） |
| 13390 | MySQL 8.0 | 主数据库 |
| 16380 | Redis 7.x | 缓存 / Session |

## 📦 支持平台

| 平台 | 格式 | 架构 |
|------|------|------|
| Windows | NSIS 安装包 | x64 |
| macOS | DMG + ZIP | x64, arm64 |
| Linux | AppImage, deb, rpm | x64, arm64 |

## 🔒 已知安全说明

### xlsx (CVE-2023-30533)

`xlsx@0.18.5` 存在原型污染漏洞（CVE-2023-30533）。由于 `xlsx@0.20.x` 未在 npm 发布，目前无法通过升级修复。已采取以下缓解措施：

- `sheet_to_json` 调用时使用 `raw: true` 选项（见 `src/components/file-preview/SheetViewer.tsx`），避免触发原型污染路径
- xlsx 仅用于前端电子表格预览，不用于解析不可信的外部上传文件
- 后续如 npm 发布修复版本或项目迁移至 `exceljs`，将移除该依赖

## 📋 版本历史

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| **1.5.0** | 2026-09-23 | 全端版本号统一升级至 1.5.0，与 backend / studio-web 对齐。后端接入控制平面 CP（身份/套餐权鉴上移，签章 chokepoint），新增多租户层 / 法律域 / e签宝流程等 18 个 Flyway 迁移 |
| **1.4.67** | 2026-08-18 | **上下文窗口精确模型表（双端镜像）**：`shared/providers/model-context-table.ts` 新增 70 个知名模型的精确上下文清单（Qwen 3.5–3.8 / DeepSeek V3.2–V4 / GLM 4.5–5.3 / MiniMax M2–M3 / GPT-5.6–4o / Claude 3.5–Sonnet 5 / Gemini 2.5–3.5 / Grok 4–4.6 / Kimi K2.5–K3，逐个官网核实于 2026-08-18）；`model-capabilities.ts` 废弃正则规则，`inferCustomModelContextWindow` / `resolveContextWindowPresets` 改为精确 ID 查表（normalize 去 provider 前缀，未知模型保守 fallback 200k）；档位规则统一为「≤ 最大上下文的标准档 + 特殊档」：1M 模型可选 128k/200k/256k/400k/512k/1M 小档省 token，200K 模型仅 128k/200k，500K（Grok 4.5/4.6）、1.5M（GPT-5.6 Sol）、2M（Grok 4 Fast）、10M（qwen-long）等特殊档自动出现；`formatContextWindowLabel` / `formatContextTier` 对 ≥1M 保留一位小数（1.5M/2M/10M 不混淆）；ProviderSettings meta 缺失时 fallback 档位由 [200k, 400k] 收紧为 [200k]；新增 `docs/model-context-window.md` 精确清单文档。**版本号**：两端 1.4.66 → 1.4.67（studio-web + studio-frontend） |
| **1.4.66** | 2026-08-17 | **Gateway 端口双源修复**：`GatewayStateController` 初始化由硬编码 `PORTS.OPENCLAW_GATEWAY` 改为动态 `getPort('OPENCLAW_GATEWAY')`；`GatewayManager.start()` / `startProcess()` 加固端口断言与漂移告警日志；`electron/services/gateway-api.ts` 与 `control-ui-device-pairing.ts` 收敛到 `getPort('OPENCLAW_GATEWAY')`；`utils/logger.ts` 写文件失败时降级到 stderr；删除 `.env` 中未使用的 `OPENCLAW_GATEWAY_PORT` 污染变量；解决多 Worker 端口争抢导致的切换用户重连死循环与 token 验证失败（与 studio-web 双端镜像）。**会话思考模式选择器**：ChatInput 顶栏新增 Brain 图标的思考模式按钮（Inherit / Off / Low / Medium / High / XHigh / Adaptive / Max 共 8 级），通过 `gateway.rpc('sessions.patch', { key, thinkingLevel })` 持久化到当前会话；`ChatState` 新增 `setThinkingLevel` action（乐观更新 + 失败回滚）；i18n 同步新增 11 个翻译 key（双端镜像）。**模型元数据管理**：`shared/host-api/contract.ts` 新增 `providers.getModelsMeta` / `providers.updateModelsMeta`；`utils/openclaw-auth.ts` 导出 `readModelEntryMetadata` / `updateModelEntryMetadata` 读写 `openclaw.json` 中 `models.providers.<key>.models[]` 的 `contextWindow` / `reasoning` 字段并同步所有 agent `models.json`；`shared/providers/model-capabilities.ts` 新增 `inferCustomModelReasoning` + `CONTEXT_WINDOW_PRESETS`（128k/200k/400k/512k/1M）+ `resolveContextWindowPresets`；默认上下文窗口 131072 → 200000，GPT-4.1 128k → 1_047_576，GPT-5 272k → 400k；Qwen3 VL 正则收紧（避免误匹配）。**ProviderSettings UI**：13 个本地管理供应商（custom/ollama/MiniMax 系列/Moonshot/Ark/SiliconFlow/DeepSeek/ModelStudio/Qwen/Zhipu）的每个模型新增「上下文窗口（k/M 下拉）+ 思考模式 Switch」编辑卡，保存时 `hostApi.providers.updateModelsMeta` 仅在值变化时调度 `gatewayManager.debouncedReload()`（双端镜像）。**版本号**：全端 1.4.65 → 1.4.66（9 个 package.json + pom.xml + 7 个 README 徽章与 Docker 标签） |
| **1.4.63** | 2026-08-14 | **账户切换串扰修复**：引入 `src/lib/user-session-reset.ts` 统一调度 9 个 store 的 `resetForUserSwitch()` + 清理 per-user localStorage（SsoBridge/SSO 路径）；Gateway 进程注入 `NODE_OPTIONS=--max-old-space-size=<JY_GATEWAY_MAX_OLD_SPACE_MB, 默认 1024>` 限定爆炸半径；`applyScopeChange` 改为串行链（`scopeChangeEpoch` + `scopeRestartChain`），快速登出→登录时第二次重启不再被 governor 静默丢弃；GatewayManager.restart() 冷却抑制后等待冷却期+100ms 重试一次；App.tsx 监听 `authUserId` 变化重新执行 settings/gateway/providers init；settings store 新增 per-account 显式语言标记 `grandpoem-studio-language-explicit:<userId>`，浏览器 locale 优先于服务端 en 默认；新增 `scripts/verify-scope-switch.ts` 运行时回归（双端镜像）。**版本号**：全端 1.4.60 → 1.4.63（10 个 package.json + pom.xml + 6 个 README 徽章与 Docker 标签） |
| **1.4.60** | 2026-08-12 | 全端版本号 1.4.59 → 1.4.60；移除全部测试代码与配置（vitest / playwright / harness:ci），提交前校验改用 `pnpm run typecheck` + `pnpm run lint:check` |
