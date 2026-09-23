<div align="center">

# ⚙️ GrandPoem Paralegal Studio — 平台底座 (Backend)

**数智员工平台的统一后端：账号体系 · 知识与向量 · 内容运营 · 版本发布**

[![Version](https://img.shields.io/badge/version-1.5.0-blue)](#)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](#)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-6DB33F?logo=springboot&logoColor=white)](#)
[![Flyway](https://img.shields.io/badge/Flyway-11.4.0-CC0200?logo=flyway&logoColor=white)](#)
[![Port](https://img.shields.io/badge/port-8181-success)](#-服务端口对照表)

</div>

---

基于 Spring Boot 3.5.4 的平台底座，为全部前端提供 RESTful API 与 WebSocket 实时通信。围绕「数字 Agent → 数字人」的平台定位，聚焦四类能力：**智能体核心**（知识库 / 向量检索 / Skill / 渠道集成）、**账号与安全**、**内容与运营**、**交易配套与版本发布**。

## 🧰 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 运行时 |
| Spring Boot | 3.5.4 | 应用框架 |
| Spring Security | 6.x | 认证鉴权 |
| Spring Data JPA | 3.x | ORM 持久化 |
| Spring Session + Redis | - | 分布式 Session |
| Spring AI | 1.0.0 | AI 模型集成 |
| Spring AI Alibaba | 1.0.0.2 | 通义千问 DashScope |
| Flyway | 11.4.0 | 数据库迁移 |
| Apache POI | 5.4.0 | Office 文档处理 |
| Apache PDFBox | 3.0.0 | PDF 解析 |
| Milvus SDK | 2.3.4 | 向量存储客户端 |
| Springdoc OpenAPI | 2.8.15 | API 文档 |
| JaCoCo | 0.8.12 | 代码覆盖率 |
| Lombok | - | 代码生成 |

## 🗄 数据层

- **MySQL 8.0**（端口 13390）— 主数据库，HikariCP 连接池（max=20）
- **Redis 7.x**（端口 16380）— 缓存 / Session 存储（Lettuce 连接池）
- **Qdrant**（端口 6333）— 向量检索（知识库语义搜索）

## 🔌 服务端口对照表

本服务占用 **8181**（REST API + WebSocket），是全部前端的统一后端。平台全局端口分配如下（五个前端统一使用 3101~3105 连续端口）：

| 端口 | 服务 | 功能说明 |
|------|------|----------|
| **8181** | **后端 API 服务（本项目）** | Spring Boot REST API + WebSocket，Swagger UI：`/swagger-ui.html` |
| 3101 | 运营后台（admin-frontend） | 内容 / 用户 / 订单 / 营销 / 站点配置，`/api` 代理到本服务 |
| 3102 | 用户应用端（app-frontend） | 资讯 / 服务 / 活动 / 会员，`/api` 代理到本服务 |
| 3103 | 品牌官网（official-web） | 无认证静态门户，`/api` 代理到本服务 |
| 3104 | Studio 桌面端渲染层（studio-frontend） | Electron 渲染进程 Vite dev server |
| 3105 | Studio Web 前端（studio-web） | 浏览器版 Studio，经宿主服务对接本服务登录 |
| 8788 | Studio Web 宿主服务（studio-web） | Fastify + JWT，WebSocket 宿主 |
| 18789 | OpenClaw Gateway | Studio 内嵌网关进程 |
| 13390 | MySQL 8.0 | 主数据库（容器内 3306） |
| 16380 | Redis 7.x | 缓存 / Session（容器内 6379） |
| 6333 | Qdrant | 向量检索 |

## 🗂 模块结构

```
src/main/java/com/jyfc/backend/
├── Application.java              # 启动入口
├── config/                       # 全局配置
│   ├── AsyncConfig               # 异步线程池
│   ├── CacheConfig               # 缓存配置
│   ├── WebSocketConfig           # WebSocket 配置
│   └── ...
├── core/                         # 核心基础设施
│   ├── config/                   # Security、Redis、Session、WebClient 等
│   ├── exception/                # 全局异常处理
│   └── security/                 # 安全框架（JWT、Cookie、权限）
└── module/                       # 业务模块（16 个）
    ├── knowledge/                # 知识库（语义搜索）
    ├── vector/                   # 向量存储（Milvus / Qdrant）
    ├── skill/                    # Skill 技能管理
    ├── integration/              # 渠道集成（钉钉、飞书、企微）
    ├── auth/                     # 认证（短信、密码、组织架构）
    ├── news/                     # 新闻资讯
    ├── sitecontent/              # 官网站点内容
    ├── marketing/                # 营销活动
    ├── activity/                 # 活动报名
    ├── dashboard/                # 数据看板
    ├── product/                  # 产品管理
    ├── mall/                     # 商品选购、购物车
    ├── trade/                    # 订单支付
    ├── notification/             # 消息通知
    ├── app/                      # 应用端消息
    └── version/                  # 版本发布管理
```

## 🧠 核心模块说明

### 知识与向量 (`module/knowledge` + `module/vector`)

数智员工的"长期记忆"：

- 知识库管理与语义搜索（RAG 增强检索）
- 向量存储双后端：Milvus / Qdrant
- Embedding 生成与同步（DashScope Embedding）

### Skill 技能 (`module/skill`)

- 数智员工的技能装配与管理
- 与 Studio 工作台的 Skill 库联动

### 渠道集成 (`module/integration`)

- 钉钉（DingTalk）/ 飞书（Feishu）/ 企业微信（WeCom）
- 数智员工进驻企业沟通渠道的服务端支撑
- 集成指南见 [`docs/dingtalk-integration-guide.md`](docs/dingtalk-integration-guide.md)、[`docs/wecom-integration-guide.md`](docs/wecom-integration-guide.md)

### 认证与账号 (`module/auth`)

- 短信验证码 / 密码登录，JWT-Cookie + 分布式 Session
- 组织架构与用户管理

### 🔒 CSRF 防护策略（v1.4.65 更新）

- 基于 `CookieCsrfTokenRepository.withHttpOnlyFalse()` 的 Cookie Token 双提交校验，豁免 `/api/auth/**`、`/api/public/**`
- **JWT Bearer 请求豁免（v1.4.65 新增）**：`Authorization: Bearer` 开头的请求跳过 CSRF 校验——Bearer token 不会被浏览器自动携带，不存在跨站伪造风险；Studio 桌面端 / Web 端云同步（`/api/user/**`）均以 Bearer 调用，此前上传被 CSRF 拦截返回 403，现不再拦截
- Cookie Session 路径的 CSRF 防护保持不变（见 `core/security/SecurityConfig.java` 的 `ignoringRequestMatchers`）

### 运营与交易配套 (`module/news` / `sitecontent` / `marketing` / `activity` / `product` / `mall` / `trade`)

- 资讯与官网内容配置、营销活动与报名
- 产品、选购、订单与支付流程支撑

## 📖 API 文档

启动后访问 Swagger UI：

```
http://localhost:8181/swagger-ui.html
```

OpenAPI JSON：

```
http://localhost:8181/v3/api-docs
```

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- MySQL 8.0
- Redis 7.x

### 配置

编辑 `src/main/resources/application.yml` 或通过环境变量覆盖：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:13390/jy_financial
    username: jy_app
    password: ${DB_PASSWORD}
  data:
    redis:
      host: localhost
      port: 16380
```

### 启动

```bash
# 开发模式
mvn spring-boot:run

# 构建 JAR
mvn clean package -DskipTests

# 运行 JAR
java -jar target/backend-1.5.0.jar
```

### 构建产物

| 产物 | 路径 | 说明 |
|------|------|------|
| 可执行 JAR | `target/backend-1.5.0.jar` | Spring Boot Fat JAR，包含所有依赖 |
| 原始 JAR | `target/backend-1.5.0.jar.original` | 未打包的原始 JAR |

### 校验

测试基础设施已于 **v1.4.60** 移除（`pom.xml` 中无测试依赖，`mvn test` 不再执行任何测试）。提交前校验改用：

```bash
mvn clean package -DskipTests   # 编译 + 打包
```

重新引入测试的完整清单见根目录 [`docs/test-cleanup-list.md`](../docs/test-cleanup-list.md)。

### 数据库迁移（Flyway）

迁移脚本位于 `src/main/resources/db/migration/`，按功能模块分组，每个功能组占用连续 10 个版本号（全局上限 `V999`）：

| 版本区间 | 功能组 | 当前脚本 | 表 |
|----------|--------|----------|-----|
| V001–V009 | 账号与安全 | `V001__users_auth.sql` | users / admins / user_refresh_tokens / security_audit_logs |
| V010–V019 | 组织架构 | `V010__organization.sql` | companies / departments / positions |
| V020–V029 | 知识库（RAG 记忆） | `V020__knowledge.sql` | knowledge_categories / knowledge_entries |
| V030–V039 | 技能库（Skill 同步） | `V030__user_skills.sql` | user_skills |
| V040–V049 | 渠道集成 | `V040__channel_integration.sql` | 钉钉 / 飞书 / 企微绑定与映射（5 张） |
| V050–V059 | 商品 | `V050__products.sql` | products 系列（5 张） |
| V060–V069 | 交易 | `V060__trade.sql` | cart_items / orders / order_items / payment_transactions / order_refunds |
| V070–V079 | 营销 | `V070__marketing.sql` | activities / coupons / user_coupons / discounts / homepage_configs / import_jobs 等（7 张） |
| V080–V089 | 内容 | `V080__content.sql` | news 系列 / site_contents（含站点种子数据） |
| V090–V099 | 互动与通知 | `V090__interaction.sql` | consultations / favorites / user_messages / notifications |
| V100–V109 | 客户端版本发布 | `V100__app_version.sql` | app_version / release_history（种子 1.4.58，V113 升级至 1.4.59） |
| V110–V119 | 用户配置同步 + 索引 + 版本对齐 | `V110__user_config_sync.sql`、`V112__admin_indexes.sql`、`V113__bump_version_1_4_59.sql` | 用户配置跨端同步 / 管理后台索引 / 版本号升级 1.4.59 |
| V120–V129 | （预留） | - | 未使用 |

> [!IMPORTANT]
> **迁移规范**：新增迁移在所属功能组的版本区间内取下一个版本号（区间用尽时启用新区间），**禁止修改已应用的历史脚本**。所有建表脚本均为幂等（`IF NOT EXISTS` + 内联索引/约束 + `INSERT IGNORE` / `ON DUPLICATE KEY`），可对已有库安全重放。

> [!WARNING]
> **旧库升级（一次性）**：本次重构清空并重排了迁移历史。对已部署数据库升级前**必须先备份**，然后执行 `DROP TABLE flyway_schema_history;` 并重启应用——Flyway 会按新脚本序列重放：已有业务表被 `IF NOT EXISTS` 跳过（数据保持不变）。废弃模块（合同/工作流/财务/身份认证/法务风控等）的存量表不会被自动删除，作为孤立表保留不影响运行，可在备份后自行 DROP；另需手动清理存量废弃通知（`DELETE FROM notifications WHERE type IN ('CONTRACT_REVIEW','CONTRACT_APPROVED','CONTRACT_REJECTED','AI_REVIEW_DONE');`，否则旧行会导致枚举反序列化失败）。全新数据库无需任何额外操作。

## 🐳 Docker

```bash
# 构建镜像（在 backend/ 目录执行）
docker build -t jyfc-backend:1.5.0 .

# 运行容器
docker run -d \
  -p 8181:8181 \
  -e DB_PASSWORD=your_password \
  -e REDIS_PASSWORD=your_redis_password \
  --name jyfc-backend \
  jyfc-backend:1.5.0
```

镜像基于 `eclipse-temurin:21-jre`，JVM 参数默认 `-Xms256m -Xmx512m`。

## 🔗 外部集成

> [!NOTE]
> 实际后端实现仅含钉钉 / 飞书 / 企业微信三大渠道集成（`module/integration/`）。下表仅列已在代码中落地的服务，与 `AGENTS.md` 的"文档与代码状态差异说明"一致。

| 服务 | 用途 |
|------|------|
| 钉钉（`module/integration/dingtalk/`） | 审批映射、消息推送、用户绑定 |
| 飞书（`module/integration/feishu/`） | 审批、消息、用户绑定、签名校验 |
| 企业微信（`module/integration/wecom/`） | 审批、通讯录同步、JS-SDK、消息推送 |
| MySQL 8.0（端口 13390） | 主数据库（HikariCP 连接池 max=20） |
| Redis 7.x（端口 16380） | 缓存 / Session 存储（Lettuce 连接池） |
| Qdrant（端口 6333） | 向量检索（知识库语义搜索） |

集成配置与环境变量说明见 [`docs/dingtalk-integration-guide.md`](docs/dingtalk-integration-guide.md)、[`docs/feishu-integration-guide.md`](docs/feishu-integration-guide.md)、[`docs/wecom-integration-guide.md`](docs/wecom-integration-guide.md)。

## 📋 版本历史

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| **1.5.0** | 2026-09-23 | 全端版本号统一升级至 1.5.0。本后端侧：新增 Flyway 迁移 V130–V147（多租户基础层 / 法律签章核心 / 合同文档 / 审计 / 签署任务两段式 / e签宝流程号 + 回调幂等 / 出证记录 / 合同模板 / 证据条目）；接入控制平面 CP（身份与套餐权鉴上移，签章经 CP chokepoint）。README 徽章 / Docker 标签 / JAR 路径同步对齐 1.5.0 |
| **1.4.66** | 2026-08-17 | 本后端侧无 Java 代码改动（**保持与 1.4.65 CSRF Bearer 豁免同步**）；仅版本号推进（pom.xml 1.4.65 → 1.4.66）。本版重写要点：修正 README 历史遗留的 MySQL 端口不一致（端口对照表 / 数据层 / Docker 集成说明统一为 13390，与根 README、docker-compose.yml 对齐——docker-compose MySQL 宿主端口已于 1.4.65 改 13390，但 backend README 仍写 13306，本次修正）。本次版本号推进不涉及数据库迁移（Flyway 无新增脚本）。Studio 双端 1.4.66 改动（Gateway 端口双源修复、会话思考模式选择器、模型元数据管理 + ProviderSettings UI、Qwen3 VL 正则收紧、Qwen3 200k/400k/1M 三档）为前后端独立变更，与本服务无直接接口耦合，详见根 README 版本历史 |
| **1.4.65** | 2026-08-16 | 全端版本号 1.4.63 → 1.4.65（10 个 package.json + pom.xml + 7 个 README 徽章与 Docker 标签）。本后端侧代码改动：CSRF `ignoringRequestMatchers` 新增 JWT Bearer 请求豁免（`Authorization: Bearer` 开头的请求跳过 CSRF 校验，Bearer token 不会被浏览器自动携带、无跨站伪造风险），修复 Studio 双端云同步上传 403；Cookie Session 路径的 CSRF 防护保持不变（详见根 README 版本历史） |
| **1.4.63** | 2026-08-14 | 全端版本号 1.4.60 → 1.4.63（10 个 package.json + pom.xml + 6 个 README 徽章与 Docker 标签）。本后端侧无代码改动，仅跟随版本号推进并重写 README；同步纳入 1.4.63 studio-frontend/studio-web 的账户切换串扰修复与 Fleet 规模化部署基线（详见根 README 版本历史） |
| **1.4.60** | 2026-08-12 | 版本号 1.4.59 → 1.4.60；移除全部测试代码（8 个 Java 测试文件 + application-test.yml）；移除 pom.xml 测试依赖（spring-boot-starter-test、spring-security-test、testcontainers）及 JaCoCo 插件；清理测试空目录 |
| **1.4.59** | 2026-08-12 | 清理构建产物与临时文件；全端版本号 1.4.58 → 1.4.59；Flyway 迁移重整（废弃不存在的 V120–V122 文档引用，版本对齐迁移收归 V110–V119 区间）；新增 V113 迁移：更新 app_version 版本号及 release_history 记录 |
| **1.4.58** | 2026-08-10 | 版本号 1.4.56 → 1.4.58；新增 V122 版本对齐迁移；新增 V112 管理员索引迁移 |
| **1.4.56** | 2026-08-07 | 版本号 1.4.14 → 1.4.56；新增 V121 版本对齐迁移；V100/V080 种子数据内联版本号对齐逻辑 |
| **1.4.14** | — | 全端版本号统一对齐；Studio Web 移植版纳入 |
| **1.4.01** | — | 初始版本：16 业务模块上线；Flyway 迁移重排 |
