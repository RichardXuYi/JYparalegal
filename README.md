<div align="center">

# ⚖️ JY Paralegal — 数智员工平台

**从数字 Agent 到数字人：法律行业的 AI 数智员工平台**

[![Version](https://img.shields.io/badge/version-1.5.0-blue)](#)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](#)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-6DB33F?logo=springboot&logoColor=white)](#)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](#)
[![Electron](https://img.shields.io/badge/Electron-40-47848F?logo=electron&logoColor=white)](#)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript&logoColor=white)](#)
[![License](https://img.shields.io/badge/license-MIT-green)](#-许可证)

</div>

---

JY Paralegal 是面向法律行业的数智员工平台，提供从「数字 Agent」到「数字人」的完整能力。平台由三个核心子系统组成：**统一后端**（账号 · 知识 · 向量 · 内容 · 交易）、**桌面工作台**（Electron + OpenClaw 网关）、**Web 工作台**（浏览器版，支持 Fleet 规模化部署）。

## 🏗 项目架构

```
JYparalegal/
├── backend/                 # Java Spring Boot 统一后端
├── studio-frontend/         # Electron 桌面工作台
├── studio-web/              # Web 工作台（浏览器版）
└── README.md                # 本文件
```

### 子系统概览

| 子系统 | 技术栈 | 端口 | 说明 |
|--------|--------|------|------|
| **backend** | Java 21 · Spring Boot 3.5.4 · MySQL 8.0 · Redis 7.x · Qdrant | **8181** | 统一后端：账号体系 · 知识库 · 向量检索 · 内容运营 · 交易支付 · 版本发布 |
| **studio-frontend** | Electron 40 · React 19 · Vite 7 · OpenClaw Gateway | **3104** (dev) / **18789** (gateway) | 桌面工作台：跨平台（Win/Mac/Linux），内嵌 AI 网关，渠道进驻（钉钉/飞书/企微/Discord/Telegram/WhatsApp） |
| **studio-web** | React 19 · Fastify 5 · OpenClaw Gateway | **3105** (dev) / **8788** (host) | Web 工作台：浏览器版，统一自适应（桌面/移动），Fleet 模式支持千级并发 |

### 🔌 全局端口分配

| 端口 | 服务 | 功能说明 |
|------|------|----------|
| **8181** | 后端 API 服务（backend） | Spring Boot REST API + WebSocket，Swagger UI：`/swagger-ui.html` |
| 3101 | 运营后台（admin-frontend） | 内容 / 用户 / 订单 / 营销 / 站点配置 |
| 3102 | 用户应用端（app-frontend） | 资讯 / 服务 / 活动 / 会员 |
| 3103 | 品牌官网（official-web） | 无认证静态门户 |
| 3104 | Studio 桌面端渲染层 | Electron 渲染进程 Vite dev server |
| 3105 | Studio Web 前端 | 浏览器版 Studio Vite dev server |
| 8788 | Studio Web 宿主服务 | Fastify + JWT，WebSocket 宿主 |
| **18789** | OpenClaw Gateway | Studio 内嵌网关进程 |
| 13390 | MySQL 8.0 | 主数据库（容器内 3306） |
| 16380 | Redis 7.x | 缓存 / Session（容器内 6379） |
| 6333 | Qdrant | 向量检索（知识库语义搜索） |

## ✨ 核心能力

### 🧠 知识库与向量检索

数智员工的"长期记忆"：

- 知识库管理与语义搜索（RAG 增强检索）
- 向量存储双后端：Milvus / Qdrant
- Embedding 生成与同步（DashScope Embedding）

### 🤖 AI 网关（OpenClaw）

- 多模型路由（DeepSeek / GLM / Qwen / MiniMax / GPT / Claude / Gemini 等）
- 流式响应处理
- 会话管理与思考模式控制（8 级：Off / Low / Medium / High / XHigh / Adaptive / Max）
- 模型元数据管理（上下文窗口精确查表，70+ 知名模型）

### 📱 渠道进驻

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

### 🧩 Skill 技能库

- 上传式技能安装（`.md` / `.zip`，自动解包至 `~/.openclaw/skills/<slug>/`）
- 上传前安全扫描（路径穿越 / 符号链接 / 超大文件拒绝）
- 预装 Skill 打包 + OpenClaw 插件集成

### 🏢 多租户与法律域（v1.5.0）

- 多租户基础层（租户隔离 / 配额管理）
- 法律签章核心（合同文档 / 出证记录 / 证据条目）
- e签宝集成（流程号 / 回调幂等）
- 控制平面 CP（身份与套餐权鉴上移，签章 chokepoint）

### 🔄 账户切换与会话清理

修复快速登出→登录时的串扰问题：

- 统一调度器 `resetAllUserStores()`：一次性重置 9 个 store
- Gateway 冷却重试 + 堆上限（爆炸半径限定在单用户）
- per-account 语言隔离

### ☁️ 云同步与设备管理

- 上传失败明细（agents / skills / preferences 失败时返回具体 slug 与原因）
- 设备列表接口适配 + 设备撤销
- Fleet 规模化部署（Web 端，每用户独立 Worker + Gateway，支持千级并发）

## 🚀 快速开始

### 环境要求

- **JDK 21+** / **Maven 3.9+**
- **Node.js 22+** / **pnpm 11.8.0**（`corepack enable`）
- **MySQL 8.0**（端口 13390）/ **Redis 7.x**（端口 16380）/ **Qdrant**（端口 6333）

### 1. 启动基础设施

```bash
# 使用 Docker Compose 启动 MySQL + Redis + Qdrant
docker-compose up -d mysql redis qdrant
```

### 2. 启动后端

```bash
cd backend

# 开发模式
mvn spring-boot:run

# 或构建 JAR 后运行
mvn clean package -DskipTests
java -jar target/backend-1.5.0.jar
```

后端启动后访问 Swagger UI：http://localhost:8181/swagger-ui.html

### 3. 启动桌面工作台

```bash
cd studio-frontend

# 安装依赖 + 下载捆绑运行时
pnpm run init

# 启动开发模式（自动启动 OpenClaw Gateway）
pnpm run dev
# → http://localhost:3104
```

### 4. 启动 Web 工作台

```bash
cd studio-web

# 安装依赖
pnpm install

# 终端 1：宿主服务（默认 8788）
pnpm run dev:server

# 终端 2：前端（Vite dev 默认 3105）
pnpm run dev
```

> **本地免登录调试**：设置 `DISABLE_AUTH=1` 后启动 `dev:server`。

## 🐳 Docker 部署

### 后端

```bash
cd backend
docker build -t jyfc-backend:1.5.0 .

docker run -d \
  -p 8181:8181 \
  -e DB_PASSWORD=your_password \
  -e REDIS_PASSWORD=your_redis_password \
  --name jyfc-backend \
  jyfc-backend:1.5.0
```

### Web 工作台

```bash
# 从仓库根目录构建
node studio-web/scripts/prepare-linux-binaries.mjs   # 一次：捆绑 Linux uv/agent-browser
docker build -f studio-web/Dockerfile -t jy-studio-web .

docker run -d \
  -p 8788:8788 \
  -e BACKEND_URL=http://host.docker.internal:8181 \
  -e JWT_SECRET=change-me \
  -v studio_web_data:/data \
  --name jy-studio-web \
  jy-studio-web
```

### Fleet 规模化部署（Web 端）

Web 端支持 Fleet 模式（每用户独立 Worker + OpenClaw Gateway），为千级并发提供容量基线。详见 [`studio-web/docs/fleet-scale-deployment.md`](studio-web/docs/fleet-scale-deployment.md)。

**容量模型**：单用户 ~250–500MB，单节点建议上限 150–250 用户，1000 用户部署 4–8 节点。

**压测验收**：

```bash
cd studio-web

# 早高峰并发登录风暴
JWT_SECRET=<secret> node scripts/fleet-load-test.mjs login-storm --users 100

# 长连接保持率
JWT_SECRET=<secret> node scripts/fleet-load-test.mjs keep-alive --users 20 --duration 300

# 登出→登录串扰回归
JWT_SECRET=<secret> node scripts/fleet-load-test.mjs switch-regression --cycles 5
```

## 📖 API 文档

后端启动后访问：

- **Swagger UI**：http://localhost:8181/swagger-ui.html
- **OpenAPI JSON**：http://localhost:8181/v3/api-docs

## 🗄 数据库迁移（Flyway）

后端使用 Flyway 管理数据库迁移，脚本位于 `backend/src/main/resources/db/migration/`。

| 号段 | 功能组 | 说明 |
|------|--------|------|
| V001–V009 | 账号与安全 | users / admins / user_refresh_tokens / security_audit_logs |
| V010–V019 | 组织架构 | companies / departments / positions |
| V020–V029 | 知识库 | knowledge_categories / knowledge_entries |
| V030–V039 | 技能库 | user_skills |
| V040–V049 | 渠道集成 | 钉钉 / 飞书 / 企微绑定与映射 |
| V050–V059 | 商品 | products 系列 |
| V060–V069 | 交易 | cart_items / orders / payment_transactions |
| V070–V079 | 营销 | activities / coupons / discounts |
| V080–V089 | 内容 | news / site_contents |
| V090–V099 | 互动与通知 | consultations / favorites / notifications |
| V100–V109 | 版本发布 | app_version / release_history |
| V110–V119 | 用户配置同步 | 跨端同步 / 索引 / 版本对齐 |
| **V130–V147** | **多租户 / 法律域 / e签** | 租户层 / 签章核心 / 合同模板 / 证据条目 / e签宝流程 |

> **迁移规范**：新增迁移在所属功能组的版本区间内取下一个版本号，**禁止修改已应用的历史脚本**。所有脚本均为幂等（`IF NOT EXISTS` + `INSERT IGNORE`），可对已有库安全重放。

## 🧪 测试与校验

### 后端

```bash
cd backend
mvn clean package -DskipTests   # 编译 + 打包
```

### 桌面工作台

```bash
cd studio-frontend
pnpm run typecheck        # TypeScript 类型检查（Node + Web）
pnpm run lint:check       # ESLint 代码规范检查
```

### Web 工作台

```bash
cd studio-web
pnpm run typecheck:web      # 渲染层类型检查
pnpm run typecheck:server   # 服务端类型检查
pnpm run lint:check         # ESLint + UI 规范门禁
```

## 🔒 安全说明

### xlsx (CVE-2023-30533)

`xlsx@0.18.5` 存在原型污染漏洞。已采取缓解措施：

- `sheet_to_json` 调用时使用 `raw: true` 选项
- xlsx 仅用于前端电子表格预览，不用于解析不可信的外部上传文件
- 后续如 npm 发布修复版本或迁移至 `exceljs`，将移除该依赖

### CSRF 防护

- 基于 `CookieCsrfTokenRepository.withHttpOnlyFalse()` 的 Cookie Token 双提交校验
- **JWT Bearer 请求豁免**：`Authorization: Bearer` 开头的请求跳过 CSRF 校验（Bearer token 不会被浏览器自动携带，无跨站伪造风险）

## 📋 版本历史

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| **1.5.0** | 2026-09-23 | 全端版本号统一升级至 1.5.0。后端新增 Flyway 迁移 V130–V147（多租户基础层 / 法律签章核心 / 合同文档 / 审计 / 签署任务两段式 / e签宝流程号 + 回调幂等 / 出证记录 / 合同模板 / 证据条目）；接入控制平面 CP（身份与套餐权鉴上移，签章经 CP chokepoint） |
| **1.4.67** | 2026-08-18 | 上下文窗口精确模型表（70 个知名模型精确清单，逐个官网核实）；`model-capabilities.ts` 废弃正则规则，改为精确 ID 查表 |
| **1.4.66** | 2026-08-17 | Gateway 端口双源修复（Fleet 模式下多 Worker 端口争抢导致的重连死循环）；会话思考模式选择器（8 级）；模型元数据管理（上下文窗口 + 思考模式 Switch）；ProviderSettings UI |
| **1.4.65** | 2026-08-16 | CSRF Bearer 请求豁免（修复 Studio 双端云同步上传 403） |
| **1.4.63** | 2026-08-14 | 账户切换串扰修复（统一调度器 + Gateway 冷却重试 + 堆上限）；Fleet 规模化部署基线（Web 端） |
| **1.4.60** | 2026-08-12 | 移除全部测试代码，提交前校验改用 typecheck + lint |
| **1.4.14** | — | 全端版本号统一对齐；Studio Web 移植版纳入 |
| **1.4.01** | — | 初始版本：16 业务模块上线；Flyway 迁移重排 |

详细版本历史见各子系统 README：

- [`backend/README.md`](backend/README.md)
- [`studio-frontend/README.md`](studio-frontend/README.md)
- [`studio-web/README.md`](studio-web/README.md)

## 📂 项目结构

详见各子系统 README：

- **backend**：[`backend/README.md`](backend/README.md) — 16 业务模块 · Flyway 迁移 · Docker 部署
- **studio-frontend**：[`studio-frontend/README.md`](studio-frontend/README.md) — Electron 桌面工作台 · OpenClaw 网关 · 渠道进驻
- **studio-web**：[`studio-web/README.md`](studio-web/README.md) — Web 工作台 · Fleet 部署 · 统一自适应

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。

## 📄 许可证

MIT License
