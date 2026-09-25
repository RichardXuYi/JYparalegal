# JY Paralegal

**版本 1.5.0** · 私有软件，未经授权不得复制、分发或用于商业用途。

面向律所和法务团队的工作台。律师在 Studio 里和智能体对话、起草并送签合同、管理模板与证据；账号、租户套餐、组织、内容和交易由 Java 后端统一保存。桌面安装包内置 OpenClaw **2026.9.6**，客户安装后登录即可使用，没有单独的 OpenClaw 安装向导。

## 项目在做什么

平台把两件事放在一起：

- **业务数据面。** 用户与组织、租户配额、合同签署、合同模板、证据、商品与订单、资讯与营销。这些数据进 MySQL，会话进 Redis，接口在 `8181`。签章调用受套餐约束，e签宝经控制平面（`8281`）出去，业务库不直接保管对方的应用密钥。
- **智能体工作台。** 对话、模型、技能、渠道进驻跑在 OpenClaw Gateway 上。桌面端由 Electron 拉起 Gateway；网页端由 Fastify 宿主拉起。渲染层只走宿主 API，不直接连 Gateway 端口。

当前产品界面包括聊天、签署任务、合同模板、证据、企业信息和比对。签署草稿里的「任务设置」是填任务单，不是安装向导。

## 版本

| 组件 | 版本 | 来源 |
|------|------|------|
| 平台 / 后端 / 两个 Studio | **1.5.0** | `backend/pom.xml`、两个 `package.json` |
| Java | 21 | `maven.compiler.release` |
| Spring Boot | 3.5.4 | 父 POM |
| Flyway | 11.4.0 | `flyway.version` |
| java-jwt | 4.5.0 | 访问令牌与刷新令牌 |
| springdoc | 2.8.15 | Swagger UI |
| Apache POI | 5.4.0 | 表格导入 |
| Electron | ^40.6.0 | 桌面运行时 |
| electron-builder | ^26.8.1 | 安装包 |
| React | ^19.2.4 | 两端界面 |
| TypeScript | ^5.9.3 | |
| Vite | ^7.3.1 | |
| Tailwind CSS | ^4.1.18 | |
| Fastify | ^5.7.4 | Web 宿主 |
| OpenClaw | **2026.9.6** | `openclaw` 依赖；Discord、WhatsApp 插件同版本 |
| 飞书插件 `@larksuite/openclaw-lark` | 2026.6.10 | 未随 9.6 源码树一起升 |
| QQ 插件 `@openclaw/qqbot` | 2026.6.10 | 同上 |
| 企业微信插件 | ^2026.6.23 | `@wecom/wecom-openclaw-plugin` |
| pnpm | 11.8.0 | 两个 Studio 的 `packageManager` |

## 架构

```
律师
  ├─ 桌面安装包（studio-frontend）
  │     Electron 主进程
  │       ├─ 宿主 API（host-api）
  │       └─ OpenClaw Gateway 子进程（随包，2026.9.6）
  │     React 渲染层只调用宿主，不直连 Gateway
  │
  └─ 浏览器（studio-web）
        Vite 页面  →  WebSocket / HTTP
        Fastify 宿主（8788）
          └─ 同一套 host-core + Gateway 子进程

两端登录
  → 后端 8181（Spring Security + JWT）
       MySQL    业务与 Flyway 迁移
       Redis    Session
       控制平面 8281 → e签宝
```

配置里的智能体花名册是 OpenClaw 9.6 的 `agents.entries.<id>`。旧的 `agents.list` 只在读取遗留文件时兼容，写回时收成 entries。

聊天顶栏换模型走 `sessions.patch { key, model }`，只钉当前会话，Gateway 进程不重启，也不再盖全屏「正在切换模型」。`model: null` 清掉会话 pin，新开会话回到该智能体的默认模型。设置页改默认模型或默认 Provider 时，先落盘，Gateway 在线则 `config.apply` 热应用。删除 Provider 仍会重启 Gateway。非 Windows 上，宿主若仍要做进程内重启，信号是 `SIGUSR2`（`commands.restart`）；`SIGUSR1` 在 9.6 里属于 Node 调试器。

## 仓库

| 路径 | 说明 |
|------|------|
| [backend](backend/README.md) | API、安全、Flyway、签署与租户 |
| [studio-frontend](studio-frontend/README.md) | Electron、打包、Gateway 进程 |
| [studio-web](studio-web/README.md) | 浏览器壳、Fastify 宿主 |
| [deploy](deploy/README-部署教程.md) | 服务器上的 Nginx、后端与控制平面 |
| `docs/` | 方案说明。截图不入库 |

`案例图/`、`法大大截图/` 以及 `docs/` 下的图片已在根目录 `.gitignore` 中排除。

## 技术栈

**后端。** Spring Boot 3.5.4（Web、Security、Data JPA、Validation、Actuator、WebFlux 的 WebClient）、Spring Session Redis、MySQL Connector/J、Flyway MySQL、springdoc。代码在 `com.jyfc.backend`：`core` 放安全、租户和异常，`module` 放业务。

主要业务包：`auth`（登录、注册、组织）、`tenant` / `account`（租户与配额）、`sign` / `signdoc` / `template` / `evidence`（签署、文档、模板、证据）、`knowledge`、`skill`、`integration`（钉钉、飞书、企微）、`product` / `mall` / `trade`、`marketing` / `activity` / `news` / `notification` / `version`。

**桌面。** Electron 40、vite-plugin-electron、React 19、Radix、Zustand、i18next、Monaco、electron-updater。主进程在 `electron/`（Gateway 管理、`services`、`utils`）。渲染层在 `src/`，协议在 `shared/host-api`。渠道依赖包括钉钉 `@soimy/dingtalk`、飞书、企微、Discord、Telegram（grammy）、WhatsApp（Baileys）、QQ。

**Web。** 同一套 React 界面。`server/src` 是 Fastify 5、`@fastify/websocket`、HttpOnly JWT Cookie。`server/host-core` 复用桌面宿主逻辑，用 shim 替换 `electron` 与 `electron-store`。`/` 按设备选桌面壳或 `src/mobile/`，`/m/` 301 到 `/`。

## 端口

| 端口 | 进程 |
|------|------|
| 8181 | 后端 API 与 WebSocket。Swagger：`/swagger-ui.html` |
| 8281 | 控制平面（e签宝桥接，部署时） |
| 3104 | 桌面 Vite |
| 3105 | Web Vite |
| 8788 | Web 宿主 |
| 18789 | 开发机上的 OpenClaw Gateway |
| 13390 / 16380 | 本地 Docker 常见的 MySQL、Redis 映射（容器内 3306 / 6379） |

## 本地开发

需要 JDK 21、Maven 3.9+、Node.js 22、pnpm 11.8.0（`corepack enable`）、MySQL 8、Redis 7。

```bash
docker compose up -d mysql redis

cd backend
mvn spring-boot:run

cd studio-frontend
pnpm run init
pnpm run dev

cd studio-web
pnpm install
pnpm run dev:server
pnpm run dev
```

Web 要同时开宿主和 Vite。只改界面可以先等 Gateway；对话依赖宿主拉起的 Gateway，就绪大约十几秒。本地免登录：`DISABLE_AUTH=1 pnpm run dev:server`。

校验：

```bash
cd backend && mvn -q -DskipTests package
cd studio-frontend && pnpm run typecheck && pnpm run lint:check
cd studio-web && pnpm run typecheck && pnpm run lint:check
```

Windows 安装包：`cd studio-frontend && pnpm run package:win`。该命令会准备 Windows 二进制并把 OpenClaw 打进安装包。

## 数据迁移

脚本在 `backend/src/main/resources/db/migration/`。号段：账号 `V001`，组织 `V010`，知识 `V020`，技能 `V030`，渠道 `V040`，商品 `V050`，交易 `V060`，营销 `V070`，内容 `V080`，互动 `V090`，版本 `V100`，同步 `V110`，法律与租户从 `V130` 起。

新库启动即迁移。已经跑过、后来又改过校验和的库，在 `validate-on-migrate=true` 时会启动失败，需要按备份处理 `flyway_schema_history` 或重建空库。

## 许可

私有。版权归 GrandPoem。本仓库及安装包不是开源软件，不授予 MIT 或其它公开许可。第三方依赖保留其各自许可。
