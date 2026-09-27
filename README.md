# JY Paralegal（竞远法律科技平台）

面向法律业务的多租户 SaaS 平台，提供知识库、技能、渠道集成、商品交易、营销内容、合同签署、电子签名（e签宝）、证据管理、模拟法庭等能力。

## 项目结构

```
.
├── backend/            子模块 → sub/backend     数据平面（DP）：Spring Boot 业务主服务，端口 8181
├── control-plane/      子模块 → sub/control-plane  控制平面（CP）：e签宝 SaaS API V3 桥接与签章配额服务，端口 8281
├── studio-frontend/    子模块 → sub/studio-frontend  GrandPoem Studio 桌面端（Electron + React）
├── studio-web/         子模块 → sub/studio-web   GrandPoem Studio Web 版（React + Fastify host server）
├── docs/               PRD、方案与核验文档
└── README.md
```

## 分支维护模型

本仓库采用 **submodule（子模块）** 结构维护，main 顶层的四个模块目录均为子模块指针（GitHub 上显示为 `目录 @ 提交哈希`），各自指向本仓库对应的"内容分支"（分支根目录即该模块的完整内容）：

| main 中的子模块 | 指向分支 | 维护方 |
|---|---|---|
| `backend/` | `sub/backend` | 服务器端（上游为 `houduan` 分支） |
| `control-plane/` | `sub/control-plane` | 服务器端（上游为 `houduan` 分支） |
| `studio-web/` | `sub/studio-web` | 前端工作流（上游为 `qianduan` 分支） |
| `studio-frontend/` | `sub/studio-frontend` | 前端工作流（上游为 `qianduan` 分支） |

`docs/`、`README.md`、`.gitignore`、`.gitmodules` 为 main 上的普通文件。

日常流程：后端改动照旧推 `houduan`、前端推 `qianduan`；同步时把最新内容刷新到对应 `sub/*` 分支，再在 main 上提交子模块指针更新。克隆本仓库请用 `git clone --recurse-submodules`，或对已有克隆执行 `git submodule update --init --recursive`。结构性节点用带注释 tag 标记（当前：`structure-v1`，2026-09-27）。

## 架构概览

- **backend（DP）**：Spring Boot 3.5.4 / Java 21，REST + WebSocket，多租户。数据层为 MySQL（Flyway 迁移）+ Redis（会话 / 缓存），业务代码在 `com.jyfc.backend.module` 下按模块划分（account、agent、auth、dashboard、knowledge、moot（模拟法庭）、sign、signdoc、template、tenant、trade、version 等）。
- **control-plane（CP）**：独立小服务，作为与 e签宝 的唯一出口（chokepoint）。负责签发 RS256 passport JWT、执行送签配额扣减、接收并转发 e签宝 回调到 DP。
- **studio-frontend / studio-web**：同一套 Studio 前端的桌面与浏览器两种形态（代码近似镜像，改动需同步）。React + TypeScript + Vite + Zustand；桌面端基于 Electron 40，Web 端附带 Fastify host server 桥接本地能力。
- **信任模型**：CP 用 RSA 私钥签发 passport，私钥永不出 CP；DP 通过 `/cp/.well-known/jwks.json` 获取公钥验签。DP 侧 `jy.cp.mode` 三态可配：
  - `required`：送签必须经 CP 且 CP 不可达即失败（生产默认，fail-closed）；
  - `local`：DP 用 `tenant_quota` 本地兜底（单机 / 演示）；
  - `off`：不做计量（仅测试）。

## 技术栈

| | backend / control-plane | studio-frontend / studio-web |
|---|---|---|
| 框架 | Spring Boot 3.5.4 | React + TypeScript + Vite |
| 运行时 | Java 21 | Node.js（桌面端 Electron 40；Web 端含 Fastify 5 服务） |
| 认证 | Session Cookie + JWT（java-jwt 4.5.0，跨端 Bearer） | 经 DP 登录，Bearer / Cookie 双通道 |
| 数据 | JPA + MySQL + Redis + Flyway 11.4（CP 无数据库） | 状态：Zustand；构建：pnpm workspace |
| 其他 | springdoc OpenAPI、POI、PDFBox、WebFlux、e签宝 SaaS API V3 | i18n（zh/en/de/fr）、共享 `shared/` 契约层 |

当前版本：**1.5.0**（backend jar、两个 Studio 包一致）。

## 快速开始

### 依赖服务

本地开发默认连接：

- MySQL：`localhost:13306`，库名 `jy_financial`
- Redis：`localhost:16380`

### 启动 backend

```bash
cd backend
mvn spring-boot:run
```

- 接口文档：http://localhost:8181/swagger-ui.html
- OpenAPI：http://localhost:8181/v3/api-docs

打包运行：

```bash
mvn clean package -DskipTests
java -jar target/backend-1.5.0.jar
```

### 启动 control-plane

```bash
cd control-plane
export ESIGN_APP_ID=...          # e签宝 应用凭证（必填，不入库存明文）
export ESIGN_APP_SECRET=...
export CP_SERVICE_PASSWORD=...   # CP 服务账号口令（必填，启动时校验）
mvn spring-boot:run
```

服务运行在 http://localhost:8281。可选配置项（`CP_JWT_AUDIENCE`、`CP_DATA_DIR`、`DP_BASE_URL` 等）见 `control-plane/src/main/resources/application.yml`。

> 多实例部署 CP 时，必须通过环境变量注入同一份 RSA PEM 密钥（`CP_JWT_RSA_PRIVATE_KEY*`），否则跨实例验签失败；`cp.data.dir` 需挂持久卷。

### 启动 Studio（Web / 桌面）

```bash
# Web 版
cd studio-web
pnpm install && pnpm dev

# 桌面版（Electron）
cd studio-frontend
pnpm install && pnpm dev
```

## 配置与环境变量

所有敏感配置均由环境变量注入，不在仓库中内置默认值。常用项：

| 变量 | 作用 | 默认 |
|---|---|---|
| `DB_PASSWORD` | DP MySQL 口令 | `jy_password`（仅开发） |
| `REDIS_PASSWORD` | DP Redis 口令 | 空 |
| `JWT_SECRET` | DP 跨端 JWT 密钥（生产须 ≥32 随机字符，`prod` profile 启动时 fail-fast 校验） | 空 |
| `CP_MODE` | DP 对 CP 的强制点模式 | `required` |
| `CP_BASE_URL` | DP 访问 CP 的地址 | `http://localhost:8281` |
| `CP_SERVICE_USERNAME/PASSWORD` | DP↔CP 服务账号 | 无默认，需注入 |
| `APP_CORS_ALLOWED_ORIGINS` | CORS 白名单 | 仅本地开发源 |
| `ESIGN_APP_ID/SECRET` | e签宝 凭证（CP） | 空，需注入 |
| `CP_ESIGN_CALLBACK_TOKEN` | CP 回调 DP 的令牌 | 空 |
| `ARCHIVE_DIR` | 模拟法庭案卷档案目录 | `./data/archive` |

## 数据库迁移

迁移脚本在 `backend/src/main/resources/db/migration/`，由 Flyway 在 DP 启动时执行；`db_migration_backup_20260923_234002/` 为历史备份快照。脚本号段按模块分配（账号 `V001`、组织 `V010`、知识 `V020` …… 法律与租户 `V130` 及以后），详见 [backend/README.md](backend/README.md)。

## 安全说明

- 带 Bearer 的请求不走 Cookie CSRF；浏览器 Cookie 会话仍做 CSRF 校验。
- 管理员 / 测试账号引导（`ADMIN_BOOTSTRAP_*`、`USER_BOOTSTRAP_*`）默认关闭或需环境变量注入口令，生产应设置 `*_ENABLED=false`。
- 支付默认仅启用 `simulated` 模拟通道，不涉及真实资金。
- 密钥曾入库过的（如 e签宝 原明文配置）应视为泄露并轮换。

## 部署

`backend/Dockerfile` 为多阶段构建（Maven 构建 + `eclipse-temurin:21-jre` 运行）。control-plane 用 `mvn package` 产出可执行 jar。Studio 桌面端经 Electron 打包（`studio-frontend`），Web 端部署 `studio-web` 构建产物与 Fastify host server。

## 文档

产品与验收文档在 `docs/`：`docs/prd/`（三期 PRD）、`docs/二期方案-模拟法庭与档案库.md`、`docs/二期审核/`（核验报告与差距清单）、`docs/demo/`（演示页）。
