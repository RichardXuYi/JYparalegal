# JY Paralegal 全项目代码审计报告

> **审计日期**：2026-09-25
> **审计范围**：backend（323 个 Java 文件 / 约 2.9 万行）、control-plane（8 个类）、studio-frontend（364 个 TS/TSX / 约 8.6 万行）、studio-web（354 个 TS/TSX / 约 8.5 万行）、部署与数据迁移
> **审计方法**：静态代码审阅（安全配置 / 认证鉴权 / 租户隔离 / 事务并发 / 依赖与打包 / 仓库卫生），Critical 与多数 High 项已由主审计人二次复核代码原文
> **未覆盖**：运行时渗透、依赖 CVE 扫描（npmmirror 源不支持 audit 端点）、e签宝真实环境联调

---

## 一、总体结论

工程质量呈现明显的**两极分化**：细节打磨到位（Electron 安全基线、参数化 SQL、RBAC、异常处理、代码卫生都属于良好水准，团队在注释里留下了 `BE-001`、`STU-004` 这类加固编号，说明做过一轮有意识的安全整改），但**结构性短板非常突出**——整个仓库零自动化测试、零 CI，而两个前端是把"本机即可信"的桌面宿主逻辑搬到了多租户服务器上。这个搬迁是本次审计最严重的系统性风险来源。

| 维度 | 评价 |
|------|------|
| 后端安全基线 | 中（鉴权/RBAC 尚可，密钥与租户隔离有洞） |
| 后端业务正确性 | 中（事务与并发是主要短板） |
| 控制平面 | **差**（完全无鉴权） |
| 桌面端 Electron | 中（基线正确，但提权链完整） |
| Web 宿主 | **差**（多租户隔离被跨端复用击穿） |
| 数据迁移 | 中 |
| 测试与 CI | **缺失**（0 测试文件、0 CI 配置） |
| 仓库卫生 | 良好，有 2 处露口 |
| **综合** | **不可按现状对公网发布**，需先关闭 7 项 Critical |

Critical 7 项、High 14 项、Medium 约 20 项。以下按严重度排列。

---

## 二、Critical（发布阻断项）

### C1 · Web 宿主任意文件读 → 跨租户完全接管

- **位置**：`studio-web/server/host-core/main/ipc-handlers.ts:1389-1426`（`resolveSandboxedPath`），入口 `file:readText/readBinary/stat/listDir/listTree`（同文件 `:1438-1620`），经 `server/src/index.ts:169-180` 原样透传 `message.args`
- **问题**：写路径有沙箱，**读路径没有**。代码注释自述 *"Read-only preview should work for any real local path"*：
  ```ts
  if (mode === 'write') { throw new Error('readOnlyRoot'); }
  return { realPath: real, readOnly: true };   // 任意真实路径直接放行
  ```
- **等价第二通道**：`media-api.ts:64-98,186-198` 的 `media.thumbnails` 对客户端传入的 `paths[].filePath` 同样无沙箱、无大小上限，谎报 mimeType 即回传文件 base64。
- **影响**：`FleetSupervisor` 按 JWT `sub` 分用户目录（`fleet/layout.ts:91-99`），本设计的前提是"每用户一个目录"。但读取无沙箱意味着登录用户 A 可读 `/data/users/<B>/auth.json`（内含 B 的 Java 后端 accessToken）、服务器 `.env`（`JWT_SECRET`）、`/etc/passwd`、全部 OpenClaw 会话记录。**租户隔离在此失效**。
- **修复**：读取也必须做 `realpath` 后的包含校验，限定在 `getUserDataDir(scope)` 根内；`media` 路径纳入同一沙箱。

### C2 · Web 端 `JWT_SECRET` 随镜像分发 → 伪造 JWT 冒充任意账号

- **位置**：`studio-web/Dockerfile:32,57` `COPY studio-web studio-web`；仓库**无任何 `.dockerignore`**（根目录与 `studio-web/` 均确认不存在）；`studio-web/.env:4` `JWT_SECRET=local-dev-only-0123456789abcdef-...`
- **问题**：本地 `.env`（含真实可用的 32 位以上密钥）随构建上下文被打入运行时镜像。`env.ts:29-34` 只校验长度 ≥32，不校验来源。另有仓库内弱兜底 `docker-compose.yml:68` `${JWT_SECRET:-change-me-in-production}`。
- **影响**：任何拿到镜像（或猜到仓库内密钥）的人可签发 `{sub:"<他人 userId>"}`，`supervisor.ts:115-122` 会为其拉起该用户的 worker 并加载该用户数据目录 → **完整跨租户接管**。与 C1 独立成立，两者叠加后隔离形同虚设。
- **修复**：补 `.dockerignore`（至少含 `.env`、`node_modules`）；移除弱兜底；生产密钥强制外部注入并在启动日志中断言非仓库内已知值。

### C3 · 桌面端：渲染层持有全量宿主能力 + 应用页无 CSP + 导航放行任意 https

三者叠加构成完整提权链，任一环单独看都"可接受"。

- **无 CSP**：`electron/main/index.ts:372-373` 的 `onHeadersReceived` 只对 `http://127.0.0.1:18789/*` 生效，**应用自身的 `file://` 页面没有任何 CSP**（已复核）。
- **导航放行**：`electron/main/index.ts:672-674` 允许导航到**任意 `https:`**（`isHttps = parsed.protocol === 'https:'`，无域名白名单）；preload 对所有顶层导航生效且无来源校验（已复核）。用户点一个外链即可把受信窗口切到攻击者站点。
- **宿主能力过宽**（`electron/preload/index.ts:173` 暴露 `hostInvoke`）：
  - 任意路径读文件（同 C1 的 `readOnly:true` 放行，上限 2MB/50MB，足够外带 `~/.openclaw/openclaw.json`、`~/.ssh/*`）
  - 明文密钥回传：`services/providers-api.ts:526,539`（`getApiKey`）、`services/gateway-api.ts:56-62`（gateway token）
  - 任意 Gateway RPC：`services/gateway-api.ts:63-75` 无 method 白名单，可调 `config.apply`
  - `services/shell-api.ts:36` `openPath` 任意路径
- **影响**：渲染层渲染的是**模型返回的合同与 Markdown**（不可信内容）。一次注入即等同 RCE（可用 `file:writeText` 改写 `~/.openclaw/openclaw.json` 后重启 Gateway 执行任意代码，见 H14）。
- **修复**：应用页加严格 CSP（`default-src 'self'`）；主窗口导航白名单（只允许 `file://` 与 dev server，其余走已正确限制协议的 `openExternal`）；`getApiKey` 只回掩码；`gateway.rpc` 加方法白名单。

### C4 · 控制平面零鉴权，持有 e签宝凭证

- **位置**：`control-plane/pom.xml` 依赖中**无 `spring-boot-starter-security`**（已复核）；`controller/CpSigningController.java:34,98,112,127,146` 全部 `/cp/v1/signing/*` 无任何认证注解或令牌校验；`CpAuthController.java:25-32` 默认账号 `cp-service/cp-secret`
- **影响**：能访问 8281 的任何人可代平台创建/撤销/延期/下载 e签宝签署流程，并可直接 `<...>` 调用真实签章能力。这是**资金与法律责任面**的暴露。
- **修复**：CP 引入 Spring Security，`/cp/v1/signing/**` 强制校验 DP 服务令牌；仅监听 `127.0.0.1`，由 DP 内网调用；`CpAuthController` 默认口令改 fail-fast。

### C5 · 自动更新无签名校验 + 裸 IP 更新源

- **位置**：`electron-builder.yml:114`、`electron-builder.obfuscated.yml:125` `verifyUpdateCodeSignature: false`；`electron/main/updater.ts:19` feed 为裸 IP `https://47.116.163.57:8082`（无域名、无证书固定）；`ipc-handlers.ts:501-507` 更新通道可由渲染层修改（仅判非空）
- **影响**：更新服务器失陷或中间人可向全体客户投递任意安装包，直接攻破全部客户机。这是桌面产品的最高权限入口。
- **修复**：启用代码签名并恢复签名校验；更新源域名化 + 证书固定；通道名加白名单。

### C6 · 三方回调验签 fail-open → 可伪造"已签署"

- **CP**：`control-plane/.../EsignCallbackController.java:60` `if (timestamp != null && signature != null)` —— **缺头即跳过验签**，随后 `forwardToDp` 驱动 DP 状态机
- **飞书**：`module/integration/feishu/controller/FeishuController.java:276,299` 三个 header 均 `required=false`，任一缺失即跳过
- **企微**：`module/integration/wecom/controller/WeComController.java:306-347` 无解密/验签，回调为空实现
- **钉钉**：`DingTalkController.java:279-290` 无签名校验且直接写审批状态
- **影响**：任意登录用户即可把合同置为已签署、把审批置为通过。签署状态机是业务核心，此处失守等于合同可被单方面"完成"。
- **修复**：验签改为 fail-closed（缺头即拒绝），并补充幂等（`sign_provider_event` 已有唯一键兜底，但需捕获 `DuplicateKeyException` 返回成功而非 500）。

### C7 · 非 `prod` profile 下 JWT 密钥可为空 → 令牌可伪造

- **位置**：`application.yml:69` `secret: ${JWT_SECRET:}`（**默认空串，已复核**）；`core/security/jwt/JwtService.java:36` 直接 `Algorithm.HMAC256("")`；`core/config/ProductionConfigValidator.java:31-47` 仅在 profile **精确含 `prod`** 时校验，其余情况 `return`
- **影响**：`SPRING_PROFILES_ACTIVE=staging`（或 `uat`、`test`、`demo`）时跳过全部校验 → 以空密钥签发 HS256 → 任何人可伪造任意用户名令牌。同时 `application-dev.yml:48` 的开发密钥 `dev-only-jwt-secret-not-for-production-32b+` 已入库，所有 dev 部署共享一个已知密钥。
- **修复**：密钥校验移出 profile 分支，任何环境缺失/过短即 fail-fast；dev 密钥改随机生成写入本地未跟踪文件。

---

## 三、High

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| H1 | **知识库跨租户检索**：`searchByKeywordPaged` 只过滤 `is_active=1`，**无 `tenant_id` 条件**；而租户版 `searchScopedForAgent:103-118` 明确带 `tenant_id`（已复核两方法同文件对照） | `knowledge/repository/KnowledgeEntryRepository.java:25-47` vs `:100-118` | 任意登录用户可检索他租户私有知识条目（标题+内容片段） |
| H2 | **配额与报名上限 TOCTOU**：`assertCanSign` 为 count-then-compare 读改写，无锁无原子扣减（方法注释自述 *"honor-system"*，已复核）；`ActivitySignupController.java:39-46` 的 `maxParticipants` **从请求体读取**（已复核），不传或传大值即绕过容量限制 | `account/service/SignQuotaService.java:51-56`、`activity/controller/ActivitySignupController.java:39-46` | 并发可超额送签；活动容量可被客户端任意放宽 |
| H3 | **支付与退款幂等缺失**：`PaymentService.java:41-61` 流水不存在时静默返回但回调端仍回 `SUCCESS`；`OrderRefundService.java:124-159` 无锁无 `@Version`（全仓仅 `Sku.java:42`、`SignTaskEntity.java:72` 有乐观锁）→ 并发重复退款/重复回补库存 | 见左 | 资金重复出账、库存错乱 |
| H4 | **密钥明文落盘**：全仓**无 `safeStorage` 命中**。access+refresh JWT、Provider API Key、渠道凭证、gateway token 均明文写 JSON | `services/backend-auth-api.ts:53-77`、`services/secrets/secret-store.ts:32-51`、`utils/store.ts:94`、`utils/openclaw-auth.ts:2494-2510` | 同机任意进程 / 备份 / 云同步即可窃取 |
| H5 | **Web 端密钥未脱敏（落后于桌面端）**：`provider:getApiKey` 直接返回明文，而桌面端同文件已脱敏为 `****last4` | `studio-web/server/host-core/main/ipc-handlers.ts:1009-1012` vs `studio-frontend/electron/main/ipc-handlers.ts:997-1005` | 前端/XSS 可拿到全部 Provider 密钥 |
| H6 | **无服务端登出失效**：JWT 无状态，登出只 `clearCookie` + 停 worker；改密不失效既有令牌；同一 JWT 还存入 `localStorage` | `server/src/auth.ts:109-118,19-20`、`src/lib/web-host-bridge.ts:53-56` | 令牌泄露后 7/30 天内持续可用 |
| H7 | **`/ws` 无 Origin 校验 + 令牌进 URL**：仅校验 JWT，全仓无 Origin 白名单；支持 `?token=` | `server/src/index.ts:83-95` | CSWSH；令牌写入浏览器历史/代理/Nginx access_log |
| H8 | **登录无限流 + 用户枚举**：无 `@fastify/rate-limit`；后端按"用户不存在"与"密码错误（剩余尝试 N 次）"区分响应 | `studio-web/package.json:29-49`、`fleet/backend-login.ts:84-97`；`auth/controller/AuthController.java:166,173` | 撞库爆破、批量探测有效账号 |
| H9 | **镜像以 root 运行 + 无 `.dockerignore` + `node_modules` 入镜像** | `studio-web/Dockerfile:41-74`（无 `USER`）；`Dockerfile:32,57` | 容器逃逸后果放大；镜像膨胀 |
| H10 | **`docker-compose.yml` 的 backend 服务无法启动**：未设 `SPRING_PROFILES_ACTIVE`，而 `ProductionConfigValidator:34-38` 在无 profile 时 `throw`；同时 compose 里 `useSSL=false` 会违反 `validateDbTls` | `docker-compose.yml:36-47`、`ProductionConfigValidator.java:34-38,88-105` | `docker compose up backend` 必然失败；文档只引导用 mysql/redis，掩盖了此问题 |
| H11 | **审批流程可被绕过**：`approval_flow_id` 全仓**只有写入点、无读取点** | `sign/service/SignDraftService.java:109`（唯一引用） | 三方回调写入的审批状态是死数据，DRAFT→SUBMIT→SIGNING 可完全跳过审批 |
| H12 | **无 SKU 商品不扣库存 → 无限超卖**：仅 `SkuRepository.java:20-22` 做原子扣减；skuId 非空但 SKU 不存在时静默回退 product 价且不校验库存 | `trade/service/OrderService.java:167-183` | 无 SKU 商品可无限下单 |
| H13 | **迁移脚本缺陷**：`contract_template`/`evidence_item` 缺 `IF NOT EXISTS` 且未带 `COLLATE`（落 `utf8mb4_0900_ai_ci`，与全库 `utf8mb4_unicode_ci` 不一致 → JOIN 字符集冲突）；`provider_flow_id` 无索引但被高频 `findByProviderFlowId` 查询；`DATETIME(3)`/`DATETIME(6)` 混用 | `V130__legal.sql:390,407,477-479`、`esign/EsignCallbackService.java:54` | 迁移不可重跑、跨表比较异常、回调路径全表扫描 |
| H14 | **`file:writeText` 可改写 `~/.openclaw/openclaw.json`**：写根包含 OpenClaw 配置目录 | `electron/main/ipc-handlers.ts:1370-1381,1524` | 配合 Gateway 重启 = 用配置注入代码执行 |

---

## 四、Medium（择要）

**架构性**
1. **跨端复用是本次审计的核心系统性风险**：`studio-web/server/host-core` 的 125 个文件中有 **80 个与 `studio-frontend/electron` 逐字节相同**（实测）。桌面端"路径认领制 / 本机即可信"的假设（`host-core/utils/paths.ts:44-52`、`user-scope.ts`）在服务器上直接变成越权（即 C1）。
2. **`FLEET_DISABLED=1` 逃生开关**会让所有用户共享一个 Gateway（`server/src/env.ts:45-50`），一旦误设即完全破坏多租户隔离，且无环境护栏。
3. **同一 UID 下无 OS 级隔离**：`supervisor.ts:304-313,356-368` fork worker 仅注入 env，无 uid/gid 降权；任意用户可在自己 worker 内通过 `skills.upload`/`skills.clawhubInstall` 执行代码后遍历 `/data/users/*`。

**后端**
4. CSRF 全面豁免 + refresh/logout 走 cookie（`SecurityConfig.java:69-79`、`AuthController.java:590-649`）。
5. 刷新令牌 30 天滑动、无限续期、无重用检测（`TokenService.java:73-90`）。
6. 限流依赖 XFF，但部署文档未把生产代理加入可信白名单 → 限流失效或误伤（`core/config/RequestUtils.java:20-24`）。
7. 上传仅校验扩展名不校验魔数（`signdoc/service/SignDocService.java:40-41,124-127`）。
8. Excel 导入：`ExcelImportService.java:85-88` `@Async` 自调用致代理失效；`ProductExcelImportHandler.java:132,134` 单行 `BigDecimal` 转换异常致整批回滚；preview 为桩（`:73-75`）；无行数上限。
9. 事务内发起外部 HTTP（长事务）：`SignFlowService.java:376-379,391-393,413-415,521-527`、`WeComContactService.java:63,106-139`。
10. 部分 `WebClient` **无读超时**：`DingTalkAuthService.java:49-52` 等三处 + `CpSigningClient.java:31-32`；全程无重试/熔断。
11. N+1 与全量加载：`SignFlowService.java:256-348` 全量入内存 + per-task 查 party 且无分页。
12. 死数据/半成品：`AlipayGateway.java:34-47`、`WechatPayGateway.java:34-47` 未实现；`AdminImportController.java:55` 信任客户端 `X-User-Id` 作审计归属。

**Web/前端**
13. Cookie 缺 `Secure`（`auth.ts:78-84` `secure:'auto'` + Fastify 无 `trustProxy`），`deploy/nginx.conf.example:10` 仅 `listen 80`、无 HSTS/安全头/上传限制。
14. WS `maxPayload=32MB`、HTTP `bodyLimit=16MB`，无连接数上限，`send()` 不检查 `bufferedAmount`（慢客户端内存无界增长）。
15. 遥测硬编码第三方 PostHog key 并以用户 id 上报（`host-core/utils/telemetry.ts:11,60-107`）——数据出境合规问题。
16. `HtmlPreview.tsx:60-66` 的 sandbox 实含 `allow-popups` 与 `allow-popups-to-escape-sandbox`，与注释宣称"不授予 popups"矛盾。
17. `SsoBridge/index.tsx:71-86` 接受 `#/sso#tk=` 触发会话交换，存在登录 CSRF 面。

**仓库与文档**
18. **`backend/jy_financial_predrop_20260923_234601.sql` 含真实用户与 bcrypt 哈希（实测捕获 `$2a$10$TFxgUok4pF...`）**，连同 `backend/db_migration_backup_20260923_234002/`（10 个迁移副本）均未被 `.gitignore` 覆盖 → 误提交风险高。
19. `e签宝` 密钥：当前 `application.yml` 已改为环境变量注入，但注释自述 *"该密钥曾入库，建议轮换"* ——**该凭证需在 e签宝后台确认轮换**（本次未在本仓库历史中找到明文，属历史遗留待办）。
20. 渠道插件版本漂移：`openclaw 2026.9.6` 而 `@larksuite/openclaw-lark`、`@openclaw/qqbot` 仍 `2026.6.10`。
21. i18n 不一致：locale 目录为 `de/en/fr/zh`，`AGENTS.md` 声称 `en/zh/ja/ru`；`SsoBridge/index.tsx:57-117` 硬编码中文文案。

---

## 五、Low 与文档失真

- `TEST-REPORT.md` **数据失真**：声称"333 个 Java 源文件"（实际 323）、"30 个迁移脚本 V001–V147"（实际 13 个，V001–V130）。该文件标题为"全项目测试报告"，但内容全是编译/类型检查/lint，**没有任何功能测试**，却给出"通过率 6/6"的结论，容易造成虚假信心。
- `docs/` 目录已被清空（3 个文件处于未提交删除状态），而 README 仍把 `docs/` 列为"方案说明"来源；`studio-frontend/AGENTS.md` 引用的 `docs/test-cleanup-list.md` 不存在。
- 死代码：`SignFlowService.java:307-309` 中 `BATCH_SENT`/`QR_SIGNED` 恒空。
- `@Valid` 覆盖偏低（25 处 vs 105 个 `@RequestBody`）。
- `UserService.java:88` 每次登录成功/失败都记录密码校验结果日志，噪音且有轻微泄露面。
- TODO/FIXME 全仓仅 13 处，属良好；无 `System.out`/`printStackTrace`/空 catch 大面积问题（3 处空 catch）。

---

## 六、做得好的部分（避免整改时误伤）

这些点在同规模项目里属于上游水准，**不要**在整改中破坏：

- **Electron 基线正确**：`contextIsolation:true` + `sandbox:true` + `nodeIntegration:false`（`main/index.ts:207-213`），无 `--no-sandbox`、无 `webSecurity:false`。
- **Gateway 攻击面收敛**：默认 `bind=loopback`，端口仅回环；`utilityProcess.fork` 传数组参数无 shell 注入；插件安装走文件拷贝而非 npm shell。
- **架构声称成立**：渲染层确实不直连 Gateway（`server/src` 无任何转发 Gateway 的 HTTP 路由，`registerPlatformProxy` 仅转发 Java 8181）。
- **进程管理完备**：孤儿进程清理、`before-quit` 超时强杀、`uncaughtException` 兜底、双实例锁。
- **SQL 无注入**：原生查询全部具名绑定参数；全仓 `Runtime`/`ProcessBuilder` 零命中。
- **RBAC 覆盖良好**：56 个 Controller 中 129 处 `@PreAuthorize`，含完整 Admin 系列接口（此前担心的"管理端无角色校验"不成立）。
- **异常处理克制**：`GlobalExceptionHandler` 不回显堆栈/SQL/路径；actuator 未开 `exposure.include=*`。
- **租户 ID 来源正确**：取自 DB `users.tenant_id` / CP claim，**无**从请求头或参数直采 `tenantId`（grep 零命中）——横向越权的洞在具体查询（H1）与 CP 硬编码（见下），不在取值方式。
- **前端安全卫生好**：全仓无 `dangerouslySetInnerHTML` / `eval` / `new Function` / `rehype-raw`；0 处 `@ts-ignore`；定时器均有清理；skill 上传的 zip-slip/软链/体积防护完善。
- **LLM 密钥治理正确**：`jy.llm.api-key` 环境变量注入，空值即降级为纯规则通道（`LlmReviewChannel.java:58-60`）。

---

## 七、修复优先级路线图

**P0 — 阻断发布，建议立即（1–2 天）**
1. C4 控制平面加鉴权 + 仅回环监听（暴露面最直接，改动最小）
2. C2 补 `.dockerignore` + 轮换 Web 端 `JWT_SECRET`
3. C1 host-core 读路径加沙箱（与桌面端 `getUserDataDir` 根对齐）
4. C7 密钥校验移出 profile 分支
5. C6 回调验签改 fail-closed
6. 清理仓库内备份 SQL（移出工作区 + 补 `.gitignore`）

**P1 — 一至两周**
7. C3 桌面端 CSP + 导航白名单 + `getApiKey` 脱敏 + Gateway RPC 白名单
8. C5 代码签名 + 更新链路加固
9. H2 配额改原子扣减、报名上限服务端取值
10. H3 支付/退款幂等与加锁
11. H1 知识库检索补 `tenant_id`；H5 Web 端密钥脱敏对齐桌面
12. H7/H6/H8 `/ws` Origin 校验、服务端令牌失效、登录限流

**P2 — 结构性投入（月度）**
13. **建立测试与 CI**：当前 0 测试文件、0 CI 配置。优先给配额、支付、签署状态机、租户隔离四条路径补集成测试，并把 `typecheck`/`lint`/`mvn package` 纳入 CI 门禁。
14. **收敛跨端重复**：80/125 文件逐字节相同的双宿主是 C1 类问题的根因。建议抽出平台无关核心，把"宿主任职范围"（渲染层能力边界、文件沙箱策略）参数化为显式策略，桌面与服务器各注入一份。
15. H11 审批流程补齐读取与强制点；H12 库存路径统一；H13 迁移脚本修复并加索引。
16. 修正 `TEST-REPORT.md` 失实数据，恢复或明确弃用 `docs/`。

---

## 附：审计覆盖统计

| 模块 | 文件数 | 代码行 | 测试文件 | 发现 Critical/High |
|------|--------|--------|----------|-------------------|
| backend | 323 | ~28,900 | 0 | 3 / 8 |
| control-plane | 8 | ~1,054 | 0 | 2 / 0 |
| studio-frontend | 364 | ~86,100 | 0 | 3 / 6 |
| studio-web | 354 | ~85,400 | 0 | 2 / 9 |
| 迁移脚本 | 13 | — | — | 0 / 1 |

（跨端重复项在 studio-frontend 与 studio-web 各计一次。）