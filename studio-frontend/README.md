# GrandPoem Studio（桌面）

Electron 工作台。OpenClaw 2026.9.6 随安装包分发，登录后直接使用，没有首次安装向导。

开发时 `pnpm run dev` 用 Vite 拉起 Electron，页面在 http://localhost:3104，Gateway 默认 `18789`。

## 日常命令

在 `studio-frontend/` 下执行。需要 pnpm 11.8.0。

| 命令 | 作用 |
|------|------|
| `pnpm run init` | 安装依赖并下载随包的 uv、agent-browser |
| `pnpm run dev` | 开发 |
| `pnpm run typecheck` | 主进程与渲染进程类型检查 |
| `pnpm run lint:check` | ESLint |
| `pnpm test` | Vitest 单元测试（`tests/unit/`） |
| `pnpm run package:win` | 打 Windows 安装包（会先准备 Windows 二进制并打入 OpenClaw） |

`pnpm run dev` 只适合开发。发给客户的是 `package:win` / `package:mac` / `package:linux` 的安装包。

## 目录

```
electron/     主进程、Gateway 进程管理、宿主 API
src/          React 界面
shared/       渲染层与主进程共用的协议和文案
resources/    图标、CLI、预装技能
scripts/      打包与 OpenClaw 捆绑
```

渲染层通过 `src/lib/host-api.ts` 调用主进程，不直接请求 Gateway 端口。

## Gateway 连接状态

Gateway 生命周期有终态：确定性启动故障（如遗留状态目录迁移失败 exit 78）在**一个启动流程内**进入 `failed` 终态，不再空转 3×10 次重试；终态只响应显式重试，不自动恢复。界面按 `src/lib/connection-status.ts` 的判定函数呈现：启动期（网关尚未就绪且未进终态）由全屏品牌加载动画 `InitializingScreen` 承载，就绪/终态/45 秒上限后整页进入主界面；运行期波动走非阻塞状态横幅（重连计数 + 倒计时）；终态故障走对话框（本地化原因、就绪层级、日志末尾、重试 / 查看日志 / 复制启动报告 / Doctor 修复）。顶栏状态胶囊常驻可点击。**不存在压在已渲染页面上的连接模态。**

确定性复现故障路径（仅 dev 构建生效）：

```bash
GP_GATEWAY_ENTRY_OVERRIDE="scripts/dev/fake-gateway-exit78.mjs" pnpm dev
```

## 模型

聊天顶栏切换模型调用 `sessions.patch`，只影响当前会话，Gateway 进程保持连接。设置里的默认模型写入 `agents.entries.<id>.model` 或 `agents.defaults.model`，由 Gateway 热应用。删除 Provider 仍会重启 Gateway。

依赖声明为 `openclaw@2026.9.6`。`@openclaw/qqbot` 与飞书包装仍可能停在更早版本，升级时单独核对。

## 界面范围

聊天、签署任务、合同模板、证据、企业、比对。签署草稿的「任务设置」是业务步骤，不是安装向导。

## 许可

私有软件，版权归 GrandPoem。不授予公开许可。第三方依赖保留其各自许可。
