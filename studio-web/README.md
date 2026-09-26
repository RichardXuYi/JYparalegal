# GrandPoem Studio（Web）

浏览器里的同一套工作台。Fastify 宿主替代 Electron，WebSocket 替代 IPC，账号走后端 `8181`。桌面项目 `studio-frontend/` 单独维护。

一个地址 `/`：宽屏用桌面壳，窄屏或触控设备用 `src/mobile/`。旧地址 `/m/` 会 301 到 `/`。

## 日常命令

在 `studio-web/` 下执行。需要 pnpm 11.8.0。

| 命令 | 作用 |
|------|------|
| `pnpm install` | 安装依赖 |
| `pnpm run dev` | Vite，默认 3105 |
| `pnpm run dev:server` | 宿主热重载，默认 8788 |
| `pnpm run start:server` | 宿主，无热重载 |
| `pnpm run typecheck` | 前端与宿主类型检查 |
| `pnpm run lint:check` | ESLint 与界面约定 |

本地免登录：`DISABLE_AUTH=1 pnpm run dev:server`。

完整对话需要宿主和 Vite 同时运行。前端把 `/api`、`/ws` 代理到宿主。Gateway 由宿主拉起，就绪大约需要十几秒；只改界面时可以不等它。

## 目录

```
src/                 React。移动壳在 src/mobile/
server/src/          Fastify、Cookie 会话、WebSocket
server/host-core/    Gateway、会话与 Provider 同步
shared/              host-api、文案
resources/           CLI 与预装技能
```

渲染层走 `src/lib/web-host-bridge.ts`，不直接连接 Gateway。

## 模型与账号

模型切换与桌面端相同：当前会话用 `sessions.patch`，默认模型热应用，不因为换模型重启 Gateway。

换账号时 `src/lib/user-session-reset.ts` 清掉聊天、Gateway、技能、智能体等内存状态，并重建 WebSocket，避免上一账号的连接留在新账号上。

## 许可

私有软件，版权归 GrandPoem。不授予公开许可。第三方依赖保留其各自许可。
