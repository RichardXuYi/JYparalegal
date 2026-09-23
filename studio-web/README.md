<div align="center">

# 🌐 GrandPoem Paralegal Studio

**GrandPoem Paralegal Studio 的浏览器版 — 数智员工工作台，随处可达**

[![Version](https://img.shields.io/badge/version-1.5.0-blue)](#)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](#)
[![Fastify](https://img.shields.io/badge/Fastify-host-000000?logo=fastify&logoColor=white)](#)
[![OpenClaw](https://img.shields.io/badge/OpenClaw-Gateway-8A2BE2)](#)
[![Ports](https://img.shields.io/badge/dev%203105%20·%20host%208788-success)](#-服务端口对照表)
[![License](https://img.shields.io/badge/license-MIT-green)](#-许可证)

**桌面壳** · **移动壳** · **单 URL 自适应**

</div>

---

GrandPoem Paralegal Studio 的浏览器版：移除 Electron，渲染层跑在浏览器里，宿主逻辑由 Node 服务承载，IPC 由 WebSocket 替代，可对接 GrandPoem 后端（8181）完成账号登录。桌面版项目 `studio-frontend/` 保持独立、互不影响。

> [!IMPORTANT]
> **统一自适应应用**：单一 URL（`/`）同时服务桌面与移动设备——运行时按多信号设备检测（Client Hints → UA → iPadOS 识别 → 触控指针，`src/lib/device-shell.ts`）自动选择桌面壳（`MainLayout`）或移动壳（`src/mobile/`），支持设置页 / More 页手动切换（localStorage `ui-shell`，无刷新即时生效）；旧移动版 URL `/m/` 由 server 301 重定向到 `/`（原 `studio-phone/` 包已退役合并入本包）。

## 🧰 技术栈

| 技术 | 说明 |
|------|------|
| React 19 + Vite 7 | 渲染层（浏览器 SPA） |
| Fastify + @fastify/websocket | 宿主服务（HTTP + /ws + 认证） |
| tsx | 直接运行 TypeScript 服务端源码 |
| Tailwind CSS 4 / Radix UI / Zustand | UI 与状态 |
| OpenClaw | AI 网关运行时（Gateway 子进程） |
| jsonwebtoken | JWT Cookie 会话 |
| Vitest | 单元测试 |

## 🏗 架构

```
studio-web/
├── src/                   # 渲染层（React SPA，桌面/移动双壳）
│   ├── mobile/            # 移动壳（MobileLayout / BottomTabBar / More 页，web-only）
│   ├── lib/device-shell.ts      # 设备检测与壳选择（手动覆盖 > 触屏信号 > 视口）
│   └── lib/web-host-bridge.ts   # window.electron shim + WS hostInvoke
├── server/                # Node 宿主服务
│   ├── src/               # Fastify 入口、/ws、认证、legacy 路由、shims
│   │   └── shims/         # electron / electron-store / electron-updater 别名实现
│   └── host-core/         # 复用的宿主服务层（由桌面版 electron/ 迁移而来）
│       ├── gateway/       # OpenClaw 网关子进程管理
│       ├── main/          # ipc-handlers、updater、host-invoke 调度
│       ├── services/      # app/gateway/chat/skills/... 服务 API
│       ├── shared/        # provider 注册表等共享代码
│       └── utils/         # 路径、存储、日志等工具
├── shared/                # 渲染层 & 服务端共享协议（host-api / host-events / i18n）
├── scripts/               # 构建辅助脚本
├── resources/             # 静态资源（CLI、Skill、插件）
└── deploy/                # Nginx 反代示例
```

> [!NOTE]
> `server/host-core/` 通过 `server/tsconfig.json` 的 paths 别名把 `electron` / `electron-store` / `electron-updater` 指到 `server/src/shims/`，从而零改写复用桌面版服务层逻辑（单一维护点，源自 `studio-frontend/electron/`）。

## 🛡️ 账户切换与会话清理（v1.4.63 新增）

与桌面端双端镜像：修复快速登出→登录时 Gateway 留在错误 OpenClaw 目录、Web 端会话/设置/技能跨用户串货的串扰问题。

- **统一调度器 `src/lib/user-session-reset.ts`**：提供 `resetAllUserStores()`，账户变更时一次性重置 9 个 store（chat / gateway / skills / agents / cron / channels / artifact-panel / providers / settings）的 `resetForUserSwitch()`、调用 `useConnectionStatusStore.endModelSwitch()` 取消进行中的模型切换、移除 per-user localStorage 键。
- **SsoBridge 跨账户清理** `src/pages/SsoBridge/index.tsx`：SSO 账号与本地会话不一致时，调 `resetAllUserStores()` + `window.__grandpoemBridge?.reconnect()` 强制重建 WS（服务器在握手时固定 scope），避免旧 socket 仍绑定旧用户。
- **Web 桥握手控制** `src/lib/web-host-bridge.ts`：
  - 登录成功后调 `reconnect()`，丢弃可能仍绑定旧账号的 socket。
  - 登出成功后调 `disconnect()`，主动关闭 socket、阻止 4401 未授权重连风暴。
  - `socket.onmessage` 检查 `ws !== socket` 丢弃过期 socket 的迟到帧，避免旧账号事件串入。
  - 心跳重连增加抖动退避（base × 0.7–1.3），避免接入层重启时多客户端重连雪崩。
  - `/auth/logout` 请求 body 由空改为 `'{}'`（Fastify 不再 400）。
  - 通过 `window.__grandpoemBridge = { disconnect, reconnect }` 暴露给共享 store（桌面端 optional chain 降级为 no-op）。
- **App.tsx 账户 ID 监听** `src/App.tsx`：监听 `authUser?.id` 变化，对 `initSettings / initGateway / initProviders` 重新触发初始化（首次 mount 不重复）。
- **per-account 语言隔离** `src/stores/settings.ts`：与桌面端同步，新增 `grandpoem-studio-language-explicit:<userId>` 显式语言标记，账户变更后回退到浏览器 locale。
- **Gateway 进程堆上限** `server/host-core/gateway/config-sync.ts`：与桌面端镜像，启动 env 注入 `NODE_OPTIONS=--max-old-space-size=<JY_GATEWAY_MAX_OLD_SPACE_MB, 默认 1024>`。
- **GatewayManager 冷却重试** `server/host-core/gateway/manager.ts`：与桌面端镜像，`restart()` 被 governor 冷却抑制后等待 `decision.retryAfterMs + 100ms` 并重试一次。

**运行时回归脚本**（双端镜像，与桌面端路径不同、逻辑一致）：

```bash
# GatewayManager 冷却重试验证（双端镜像）—
# stub stop/start 不起真实进程，断言冷却期内二次 restart 被抑制后重试并执行
node node_modules/tsx/dist/cli.mjs --tsconfig server/tsconfig.json scripts/verify-manager-restart.ts
```

## ☁️ 云同步错误明细与设备撤销修复（v1.4.65 新增）

与桌面端双端镜像 + Web 独有修复：

- **上传失败明细（镜像）** `server/host-core/services/sync/config-sync-service.ts`：`pushBundle` / `pushConfig` 由返回 `boolean` 改为 `{ ok, error }`；agents / skills / preferences 上传时收集所有失败项，失败时返回 `{ scope, ok: false, pushed, error }`，错误信息包含具体 slug 与后端原因（不再静默吞错）。
- **设备撤销 400 修复（Web 独有）** `src/lib/web-host-bridge.ts`：`/auth/devices/:id` DELETE 请求发送 `'{}'` body——Fastify 对 `Content-Type: application/json` 的空 body 返回 400（`FST_ERR_CTP_EMPTY_JSON_BODY`），补上合法空对象后撤销设备不再报错。

## 🧠 会话思考模式选择器（v1.4.66 新增，与桌面端镜像）

让用户在不切换模型的前提下，按会话粒度控制 OpenClaw Gateway 的 `/think` 行为：

- **顶栏按钮** `src/pages/Chat/ChatInput.tsx`：模型选择器右侧新增 Brain 图标按钮，悬停提示「此聊天的思考模式」；点击展开 8 级浮层（`Inherit / Off / Low / Medium / High / XHigh / Adaptive / Max`）。
- **状态与持久化** `shared/chat/types.ts` + `src/stores/chat.ts`：`ChatState` 新增 `thinkingLevel: string | null` + `setThinkingLevel(level)` action（乐观更新；调用 `hostApi.gateway.rpc('sessions.patch', { key, thinkingLevel })` 写入 gateway；失败回滚）。
- **i18n**：en/zh/de/fr 同步新增 11 个翻译 key（`composer.pickThinkingLevel` / `thinkingPickerTitle` / `thinkingLevelFailed` / `thinkingLevelInherit` / `thinkingLevelOff` / `thinkingLevelLow` / `thinkingLevelMedium` / `thinkingLevelHigh` / `thinkingLevelXHigh` / `thinkingLevelAdaptive` / `thinkingLevelMax`）。
- **关闭菜单互斥**：与 Agent / Skill / Model 选择器共用同一个 outside-click 与 Escape 关闭路径。

## ⚙️ 模型元数据管理（v1.4.66 新增，与桌面端镜像）

让用户在「模型编辑」面板直接持久化 `openclaw.json` 中 `models.providers.<key>.models[]` 的 `contextWindow` / `reasoning` 字段：

- **新 host API** `shared/host-api/contract.ts`：`providers.getModelsMeta(providerKey, modelId)` / `providers.updateModelsMeta({ providerKey, modelId, contextWindow?, reasoning? })`——后者写回 openclaw.json 与所有 agent 的 `models.json`，仅在值实际变化时调度 `gatewayManager.debouncedReload()`。
- **读写工具** `server/host-core/utils/openclaw-auth.ts`：导出 `readModelEntryMetadata` / `updateModelEntryMetadata`（与 `electron/utils/openclaw-auth.ts` 同源代码路径 alias 映射，路径别名见 `server/tsconfig.json`）。
- **能力推断** `server/host-core/shared/providers/model-capabilities.ts` + `model-context-table.ts`（v1.4.67 重构为精确表）：
  - `inferCustomModelReasoning(modelId)`：默认 true（2026 主流模型均支持思考），白名单排除 gpt-4o / gpt-4.1 系列 / gpt-4-turbo / gpt-4 / gpt-3.5 等旧非思考模型。
  - `model-context-table.ts`：70 个知名模型的精确上下文清单（供应商 / ID / 别名 / 官方精确值 / 来源，逐个官网核实于 2026-08-18，详见 `docs/model-context-window.md`），匹配为精确 ID 查表，无正则。
  - `CONTEXT_WINDOW_PRESETS = [131_072, 200_000, 262_144, 400_000, 524_288, 1_048_576, 2_097_152]` + `formatContextWindowLabel(tokens)`（≥1M 保留一位小数，如 1.5M）。
  - `resolveContextWindowPresets(modelId)`：档位 = 全部 ≤ 模型最大上下文的标准档 + 特殊档（GPT-5.4 的 272K、GPT-5.6 Sol 的 1.5M）；1M 模型可选 128k–1M 小档省 token，200K 模型只出现 128k/200k；未知模型保守 fallback 200k。
- **ProviderSettings UI** `src/components/settings/ProvidersSettings.tsx`：13 个本地管理供应商（`custom` / `ollama` / `MiniMax` 系列 / `moonshot` / `ark` / `siliconflow` / `deepseek` / `modelstudio` / `qwen` / `zhipu`）的每个模型新增「上下文窗口（k/M 下拉）+ 思考模式 Switch」编辑卡；debounce 300ms 加载元数据，保存按钮仅在有未保存差异时启用。

## 🔧 Gateway 端口双源修复（v1.4.66 新增，与桌面端镜像）

修复多 Worker 端口争抢导致的切换用户重连死循环与 token 验证失败：

- **症状**：Fleet 模式下每个用户对应一个独立 Worker + OpenClaw Gateway 子进程；之前 `GatewayStateController` 初始化端口来自 `PORTS.OPENCLAW_GATEWAY`（硬编码常量），而 supervisor 在 `CLAWX_PORT_OPENCLAW_GATEWAY` 中为每个 Worker 分配了独立端口。两条端口来源并存导致：
  - Worker B 启动后 status 仍为 Worker A 的旧端口，子进程尝试 spawn 在被占用的端口上失败；
  - 前端 WebSocket 重新连接时携带的 token 与实际进程不匹配，`/auth/verify` 返回失败；
  - 客户端不停触发重连风暴（4401 → reconnect → 再次 4401）。
- **状态初始化去硬编码** `server/host-core/gateway/state.ts`：`GatewayStateController` 默认端口由 `PORTS.OPENCLAW_GATEWAY` 改为 `getPort('OPENCLAW_GATEWAY')`，与 supervisor 分配端口同源。
- **start 路径端口断言** `server/host-core/gateway/manager.ts`：
  - `start()` 在 `startLock = true` 之后再次强制 `this.setStatus({ port: getPort('OPENCLAW_GATEWAY') })`，防止后续链路再次覆盖。
  - `startProcess()` 启动前比对 `this.status.port` 与 `getPort('OPENCLAW_GATEWAY')`，不一致时打印 `Gateway port drift detected: <a> != <b>; using dynamic port` 警告并纠正。
- **依赖收敛** `server/host-core/services/gateway-api.ts` + `server/host-core/utils/control-ui-device-pairing.ts`：将 `PORTS.OPENCLAW_GATEWAY` 全部替换为 `getPort('OPENCLAW_GATEWAY')`，消除最后两处硬编码回退。
- **崩溃日志可观测性** `server/host-core/utils/logger.ts`：`flushBuffer()` 写文件失败时降级输出到 `process.stderr`，gateway 崩溃日志不再静默丢失。
- **环境变量清理**：删除 `.env` 中未使用的 `OPENCLAW_GATEWAY_PORT` 污染变量。
- **文档清理**：本次删除 `docs/gateway-port-fix-report.md`（252 行排查报告）与 `docs/layout-restructure-proposal.md`（786 行 layout 提案）——前者报告内容已并入本节与根 README 版本历史，后者 layout 提案已在 v1.4.63 之前实施完成。如需历史排查细节，可从 git 历史 `git show 0708c32 -- docs/gateway-port-fix-report.md` 检索。

## 🚀 Fleet 规模化部署（v1.4.63 新增）

Web 端独有的 Fleet 模式（每用户独立 Worker + OpenClaw Gateway），为千级并发提供容量 / 端口 / 心跳 / 进程生命周期基线。完整文档见 [`docs/fleet-scale-deployment.md`](docs/fleet-scale-deployment.md)；以下是核心要点：

- **容量模型**：单用户 ~250–500MB（worker ~80–150MB + Gateway ~150–400MB）。单节点建议上限 150–250 用户（64–96GB 机型），1000 用户部署 4–8 节点。
- **端口规划**：注册表 `BASE=19000 / STEP=20 / MAX_SLOTS=2000`（`gateway-ports.json`，按节点 DATA_DIR 隔离）。Linux 必须收紧临时端口：`sysctl -w net.ipv4.ip_local_port_range="30000 32767"`（避免 EADDRINUSE）。
- **心跳保活**：接入层 `/ws` 服务端 25s ping，连续 2 次未 pong 即断；前端桥按抖动退避重连（0.5–10s × 0.7–1.3）；`4401` 未授权不自动重连（等登录触发）。
- **进程生命周期**：崩溃隔离 60s/5 次/5min 退避；堆上限让失控账号只 OOM 自己、由崩溃机制重启，爆炸半径限定在单用户；worker 日志按 `<DATA_DIR>/logs/worker-<scope>.log/.err.log` 单文件轮转（20MB 阈值）；`/healthz` 新增 `rssMb`（节点 RSS）与 `spawnQueueDepth`（启动队列深度）。

### 环境变量基线（Fleet 模式下与 `studio-web` 服务端相关）

完整列表见 `docs/fleet-scale-deployment.md` §4 与 `.env.example`，新增（v1.4.63）：

| 变量 | 默认 | 说明 |
|------|------|------|
| `FLEET_MAX_CONCURRENT_SPAWNS` | 20 | 每批最大 spawn 数（启动风暴闸门） |
| `FLEET_SPAWN_STAGGER_MS` | 50 | 批间间隔 |
| `FLEET_MEM_RECLAIM_THRESHOLD_MB` | 0 | 节点 RSS 超阈值时办公时段也回收无客户端闲置 worker（0=关闭）。建议设为物理内存的 80% |
| `FLEET_WORKER_MAX_OLD_SPACE_MB` | 512 | worker 进程 V8 堆上限 |
| `FLEET_GATEWAY_MAX_OLD_SPACE_MB` | 1024 | Gateway 进程 V8 堆上限（经 `JY_GATEWAY_MAX_OLD_SPACE_MB` 传给 config-sync） |

### 压测验收脚本 `scripts/fleet-load-test.mjs`

自研脚本（依赖本仓库 `node_modules` 的 `ws` / `jsonwebtoken`），三种模式：

```bash
# 早高峰并发登录风暴：N 用户并发建连 + 首个 invoke 延迟
JWT_SECRET=<与服务器一致> node scripts/fleet-load-test.mjs login-storm --users 100

# 长连接保持率：空闲挂机，服务端 25s 心跳，统计意外断开
JWT_SECRET=<与服务器一致> node scripts/fleet-load-test.mjs keep-alive --users 20 --duration 300

# 登出→登录串扰回归：A 断开后 B 建连，断言各自 Worker 的 gateway pid 不同
JWT_SECRET=<与服务器一致> node scripts/fleet-load-test.mjs switch-regression --cycles 5
```

验收指标（详见 [`docs/fleet-scale-deployment.md` §6](docs/fleet-scale-deployment.md#6-压测验收-scriptsfleet-load-testmjs)）：登录风暴 `failures=0` / p95 首响应延迟稳定；8h 长连接保持率意外断开 <0.1%/h；串扰回归每轮 A/B 的 gateway pid 必须不同。

## ✨ 技能与提供商

- **上传式技能安装**（与桌面版同步）：技能页支持上传 `.md` / `.markdown` / `.zip`，由宿主服务自动解包至 `~/.openclaw/skills/<slug>/`（可确认覆盖）
- **上传安全扫描**：校验 SKILL.md front-matter（name/description），拒绝路径穿越 / 符号链接 / 超大文件（单文件 ≤2MB、总量 ≤20MB、条目 ≤200），非法文件直接拒绝并 toast 提示
- **技能卡片**不再展示服务器本地路径，仅保留来源 Badge
- **提供商同步防护**：写入 / 回读 `openclaw.json` 时循环剥离运行时前缀，避免模型 ID 前缀叠加污染；支持 MiniMax 等 OpenAI 兼容接入

## 🚀 快速开始

> [!NOTE]
> 浏览器端的 Setup 向导已移除（服务器部署一次即可，无需每次引导）；服务器安装与部署请见 **[docs/SERVER-INSTALL.md](docs/SERVER-INSTALL.md)**（中英双语）。桌面版 `studio-frontend/` 仍保留内置向导。

### 开发

```bash
pnpm install

# 终端 1：宿主服务（默认 8788）
pnpm run dev:server

# 终端 2：前端（Vite dev 默认 3105，/ws 代理到 8788，/api 代理到 8181）
pnpm run dev
```

> [!TIP]
> 本地免登录调试：设置 `DISABLE_AUTH=1` 后启动 dev:server。

### 生产

```bash
pnpm run build          # vite build → dist/（server 会直接托管）
pnpm run start:server   # tsx 运行 server（托管 dist/ + /ws + 认证）
```

Docker 部署见 `Dockerfile` 与仓库根 `docker-compose.yml`（服务名 `studio-web`）；Nginx 反代示例见 `deploy/nginx.conf.example`。

### 环境变量

见 `.env.example`：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `PORT` | `8788` | 宿主服务端口 |
| `DATA_DIR` | - | 数据目录 |
| `BACKEND_URL` | `http://localhost:8181` | GrandPoem 后端地址 |
| `JWT_SECRET` | - | JWT 签名密钥 |
| `DISABLE_AUTH` | - | 置 `1` 免登录调试 |

### 校验

```bash
pnpm run typecheck:web      # 渲染层类型检查
pnpm run typecheck:server   # 服务端（含 host-core）类型检查
pnpm run lint:check         # ESLint
```

> 单元测试（Vitest）已于 **v1.4.60** 移除，清理清单见根目录 [`docs/test-cleanup-list.md`](../docs/test-cleanup-list.md)。

## 🔌 服务端口对照表

本项目占用两个端口：前端 dev server **3105**、宿主服务 **8788**（生产模式下由 8788 直接托管 `dist/`，不再使用 3105）。平台全局端口分配如下（五个前端统一使用 3101~3105 连续端口）：

| 端口 | 服务 | 功能说明 |
|------|------|----------|
| 8181 | 后端 API 服务（backend） | Spring Boot REST API + WebSocket，提供账号登录 |
| 3101 | 运营后台（admin-frontend） | 内容 / 用户 / 订单 / 营销 / 站点配置 |
| 3102 | 用户应用端（app-frontend） | 资讯 / 服务 / 活动 / 会员 |
| 3103 | 品牌官网（official-web） | 无认证静态门户 |
| 3104 | Studio 桌面端渲染层（studio-frontend） | Electron 渲染进程 Vite dev server |
| **3105** | **Studio Web 前端（本项目）** | Vite dev server，`/api`→8181、`/ws`→8788 |
| **8788** | **Studio Web 宿主服务（本项目）** | Fastify + JWT，WebSocket 宿主；生产托管 `dist/`（`PORT` 可调） |
| 18789 | OpenClaw Gateway | 内嵌网关进程 |
| 13390 | MySQL 8.0 | 主数据库 |
| 16380 | Redis 7.x | 缓存 / Session |

## 🔒 已知安全说明

### xlsx (CVE-2023-30533)

`xlsx@0.18.5` 存在原型污染漏洞（CVE-2023-30533）。由于 `xlsx@0.20.x` 未在 npm 发布，目前无法通过升级修复。已采取以下缓解措施：

- `sheet_to_json` 调用时使用 `raw: true` 选项（见 `src/components/file-preview/SheetViewer.tsx`），避免触发原型污染路径
- xlsx 仅用于前端电子表格预览，不用于解析不可信的外部上传文件
- 后续如 npm 发布修复版本或项目迁移至 `exceljs`，将移除该依赖

## 📋 版本历史

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| **1.5.0** | 2026-09-23 | 全端版本号统一升级至 1.5.0，与 backend / studio-frontend 对齐。后端接入控制平面 CP（身份/套餐权鉴上移，签章 chokepoint），新增多租户层 / 法律域 / e签宝流程等 18 个 Flyway 迁移 |
| **1.4.67** | 2026-08-18 | **上下文窗口精确模型表（双端镜像）**：`shared/providers/model-context-table.ts` 新增 70 个知名模型的精确上下文清单（Qwen 3.5–3.8 / DeepSeek V3.2–V4 / GLM 4.5–5.3 / MiniMax M2–M3 / GPT-5.6–4o / Claude 3.5–Sonnet 5 / Gemini 2.5–3.5 / Grok 4–4.6 / Kimi K2.5–K3，逐个官网核实于 2026-08-18）；`model-capabilities.ts` 废弃正则规则，`inferCustomModelContextWindow` / `resolveContextWindowPresets` 改为精确 ID 查表（normalize 去 provider 前缀，未知模型保守 fallback 200k）；档位规则统一为「≤ 最大上下文的标准档 + 特殊档」：1M 模型可选 128k/200k/256k/400k/512k/1M 小档省 token，200K 模型仅 128k/200k，500K（Grok 4.5/4.6）、1.5M（GPT-5.6 Sol）、2M（Grok 4 Fast）、10M（qwen-long）等特殊档自动出现；`formatContextWindowLabel` / `formatContextTier` 对 ≥1M 保留一位小数（1.5M/2M/10M 不混淆）；ProviderSettings meta 缺失时 fallback 档位由 [200k, 400k] 收紧为 [200k]；新增 `docs/model-context-window.md` 精确清单文档。**版本号**：两端 1.4.66 → 1.4.67（studio-web + studio-frontend） |
| **1.4.66** | 2026-08-17 | **Gateway 端口双源修复（Web 核心）**：Fleet 模式下 Worker B 启动后 status 仍保留 Worker A 的旧端口，子进程在已占用端口 spawn 失败 + 前端 WebSocket 重连时 token 与实际进程不匹配 + 客户端触发 4401 重连风暴的链路被一次性切断。`GatewayStateController` 初始化由硬编码 `PORTS.OPENCLAW_GATEWAY` 改为动态 `getPort('OPENCLAW_GATEWAY')`（与 supervisor 分配端口同源）；`GatewayManager.start()` / `startProcess()` 加固端口断言与漂移告警日志；`services/gateway-api.ts` + `utils/control-ui-device-pairing.ts` 收敛到 `getPort('OPENCLAW_GATEWAY')`；`utils/logger.ts` 写文件失败时降级到 stderr；删除 `.env` 中未使用的 `OPENCLAW_GATEWAY_PORT` 污染变量（与 studio-frontend 双端镜像）。**会话思考模式选择器（双端镜像）**：ChatInput 顶栏新增 Brain 图标的思考模式按钮（Inherit / Off / Low / Medium / High / XHigh / Adaptive / Max 共 8 级），通过 `gateway.rpc('sessions.patch', { key, thinkingLevel })` 持久化到当前会话；`ChatState` 新增 `setThinkingLevel` action（乐观更新 + 失败回滚）；i18n 同步新增 11 个翻译 key。**模型元数据管理（双端镜像）**：`shared/host-api/contract.ts` 新增 `providers.getModelsMeta` / `providers.updateModelsMeta`；`utils/openclaw-auth.ts` 导出 `readModelEntryMetadata` / `updateModelEntryMetadata` 读写 `openclaw.json` 中 `models.providers.<key>.models[]` 的 `contextWindow` / `reasoning` 字段并同步所有 agent `models.json`；`shared/providers/model-capabilities.ts` 新增 `inferCustomModelReasoning` + `CONTEXT_WINDOW_PRESETS`（128k/200k/400k/512k/1M）+ `resolveContextWindowPresets`；默认上下文窗口 131072 → 200000，GPT-4.1 128k → 1_047_576，GPT-5 272k → 400k；Qwen3 VL 正则收紧（避免误匹配）。**ProviderSettings UI**：13 个本地管理供应商（custom/ollama/MiniMax 系列/Moonshot/Ark/SiliconFlow/DeepSeek/ModelStudio/Qwen/Zhipu）的每个模型新增「上下文窗口（k/M 下拉）+ 思考模式 Switch」编辑卡。**文档清理**：删除 `docs/gateway-port-fix-report.md`（252 行排查报告，内容并入本节）与 `docs/layout-restructure-proposal.md`（786 行 layout 提案，已实施完成）。**版本号**：全端 1.4.65 → 1.4.66（9 个 package.json + pom.xml + 7 个 README 徽章与 Docker 标签） |
| **1.4.63** | 2026-08-14 | **账户切换串扰修复（双端镜像）**：引入 `src/lib/user-session-reset.ts` 统一调度 9 个 store 的 `resetForUserSwitch()` + 清理 per-user localStorage；SsoBridge SSO 路径强制重建 WS；`web-host-bridge` 登录 `reconnect()` / 登出 `disconnect()`、丢弃过期 socket 迟到帧、心跳重连增加抖动退避（0.7–1.3）、`/auth/logout` body 由空改为 `'{}'`；Gateway 进程注入 `NODE_OPTIONS=--max-old-space-size=<JY_GATEWAY_MAX_OLD_SPACE_MB, 默认 1024>` 限定爆炸半径；GatewayManager.restart() 冷却抑制后等待冷却期+100ms 重试一次；App.tsx 监听 `authUserId` 变化重新执行 settings/gateway/providers init；settings store 新增 per-account 显式语言标记 `grandpoem-studio-language-explicit:<userId>`，浏览器 locale 优先于服务端 en 默认。**Fleet 规模化部署基线（Web 独有）**：新增 `docs/fleet-scale-deployment.md`（千级并发容量模型 / 端口规划 / 心跳保活 / 环境变量 / 进程生命周期 / 压测验收）+ `scripts/fleet-load-test.mjs`（login-storm / keep-alive / switch-regression 三模式）+ `scripts/verify-manager-restart.ts`（GatewayManager 冷却重试运行时验证）+ 5 个 Fleet 环境变量（`FLEET_MAX_CONCURRENT_SPAWNS` / `FLEET_SPAWN_STAGGER_MS` / `FLEET_MEM_RECLAIM_THRESHOLD_MB` / `FLEET_WORKER_MAX_OLD_SPACE_MB` / `FLEET_GATEWAY_MAX_OLD_SPACE_MB`）；`/healthz` 新增 `rssMb` + `spawnQueueDepth` 指标；worker 日志按 `<DATA_DIR>/logs/worker-<scope>.log/.err.log` 单文件 20MB 轮转。**版本号**：全端 1.4.60 → 1.4.63（10 个 package.json + pom.xml + 6 个 README 徽章与 Docker 标签） |
| **1.4.60** | 2026-08-12 | 全端版本号 1.4.59 → 1.4.60；移除全部测试代码与配置（vitest），提交前校验改用 `pnpm run typecheck:web` + `pnpm run typecheck:server` + `pnpm run lint:check` |
