# AGENTS.md — studio-web

## Overview

`jy-studio-web` 是 GrandPoem Studio 的 **Web 移植版**：移除 Electron 壳，渲染层跑在浏览器里（React 19 + Vite + TypeScript），宿主逻辑由 **Fastify + JWT Cookie 会话** 的 Node 服务承载，IPC 由 WebSocket 替代。可对接 GrandPoem 后端（8181）完成账号登录。桌面版 `studio-frontend/` 保持独立、互不影响。

应用为**统一自适应 SPA**：单一 URL（`/`）运行时按设备选择桌面壳（`MainLayout`）或移动壳（`src/mobile/` 下的 `MobileLayout`），检测与手动覆盖逻辑见 `src/lib/device-shell.ts` + `src/hooks/use-device-shell.ts`；旧移动版 URL `/m/` 由 server 301 到 `/`（原 `studio-phone/` 包已退役并入本包）。

> **与 studio-frontend 同步比对口径**：移动壳与设备检测为 web-only 新增——`src/mobile/`、`src/lib/device-shell.ts`、`src/hooks/use-device-shell.ts`、`src/hooks/use-is-mobile.ts`、`src/hooks/use-media-query.ts`、`src/styles/mobile.css`——不参与与 `studio-frontend/` 的逐文件同步比对；`App.tsx` 的双壳路由与 lazy 加载也是 web-only 差异。

技术栈：Fastify 5 + `@fastify/websocket` + `@fastify/cookie`、`jsonwebtoken`、`tsx`、React 19 + Vite 7、Tailwind CSS 4 / Radix UI / Zustand、OpenClaw Gateway 子进程。

## Quick reference

所有命令均在 `studio-web/` 目录下执行，或以 `pnpm --filter jy-studio-web run <script>` 从仓库根调用。

| Task | Command |
|------|---------|
| 前端开发服务器（Vite HMR） | `pnpm dev` |
| 宿主服务开发（tsx watch，热重载） | `pnpm run dev:server` |
| 宿主服务生产启动（tsx，无 watch） | `pnpm run start:server` |
| 生成 Extension Bridge 桩文件 | `pnpm run ext:bridge` |
| 构建前端（Vite production build） | `pnpm run build` |
| 构建服务端（tsc 编译） | `pnpm run build:server` |
| Lint（ESLint，自动修复） | `pnpm run lint` |
| Lint（仅检查，不修复） | `pnpm run lint:check` |
| 类型检查（前端 + 服务端） | `pnpm run typecheck` |
| 类型检查（仅前端） | `pnpm run typecheck:web` |
| 类型检查（仅服务端） | `pnpm run typecheck:server` |

> `postinstall` 和 `predev` 为生命周期钩子，`pnpm install` / `pnpm dev` 时自动执行，无需手动调用。

## Non-obvious caveats

- **pnpm 版本**：`packageManager` 字段已锁定 pnpm `11.8.0`。使用 `corepack enable` 激活正确版本后再安装。
- **双进程开发**：完整功能需要同时运行 `pnpm dev`（Vite 前端，默认端口 5173）和 `pnpm run dev:server`（Fastify 宿主服务）。前端通过 Vite proxy 将 `/api`、`/ws` 请求转发到宿主服务。
- **无 Electron / 无数据库**：宿主服务使用内存状态 + JSON 文件（`electron-store` shim），不依赖外部数据库。
- **JWT Cookie 会话**：认证由 Fastify 服务端 `jsonwebtoken` 签发 HttpOnly Cookie，替代桌面版的 Electron IPC 鉴权路径。
- **OpenClaw Gateway**：宿主服务启动后自动拉起 Gateway 子进程。Gateway 就绪约需 10–30 秒，前端开发（纯 UI）不强依赖 Gateway。
- **AI Provider 密钥**：实际 AI 对话需要在设置页配置至少一个 Provider API Key；无密钥时应用可正常导航和测试。

## 原 Electron 版 comms / E2E 防护在本移植版中的替代口径

桌面版 `studio-frontend/` 有以下专项防护，在 `studio-web` 中的对应做法：

| 桌面版机制 | studio-web 替代方案 |
|------------|---------------------|
| `pnpm run comms:replay` / `comms:compare`（通信路径回归） | 宿主服务通信层（`server/src/shims/electron.ts`、`server/src/host.ts`）改动后，运行 `pnpm run typecheck`（含服务端）+ `pnpm run lint:check`；手动验证 WebSocket 连接与 hostInvoke 调度正常 |
| `pnpm run test:e2e`（Playwright Electron E2E） | 已随 v1.4.60 移除（单元测试一并移除）；渲染层以 `pnpm run typecheck:web` + `pnpm run lint:check` 校验，宿主服务逻辑由 `pnpm run typecheck:server` 覆盖 |
| `pnpm run harness:ci`（Harness 基线检查） | 已随 v1.4.60 移除；如需对宿主服务做结构校验，直接运行 `pnpm run typecheck:server` + `pnpm run lint:check` |
| Renderer/Main API boundary（`host-api.ts` / `api-client.ts`） | 对应为 `src/lib/web-host-bridge.ts`（window.electron shim + WS hostInvoke）+ `server/src/host.ts`（Fastify 侧调度）；改动需同步两端 |

## 高风险区与校验路由

| 高风险区 | 主要位置 | 改动后必须运行 |
|----------|----------|----------------|
| **宿主服务通信** | `server/src/shims/electron.ts`、`server/src/host.ts`、`src/lib/web-host-bridge.ts` | `pnpm run typecheck`；`pnpm run lint:check`；手动验证 WS 连接 |
| **认证 / JWT** | `server/src/auth.ts`、`server/src/env.ts` | `pnpm run typecheck`；手动验证登录 / Cookie 签发 / 权限拦截 |
| **Gateway 子进程管理** | `server/src/index.ts`、`shared/` 下 gateway 相关模块 | `pnpm run typecheck`；确认 Gateway 启动 / 就绪 / 关闭流程 |
| **前端 UI** | `src/` | `pnpm run lint`；`pnpm run typecheck:web` |
