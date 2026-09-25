# JY Paralegal 代码审计

静态阅读 1.5.0 源码，2026-09-25。范围是 `backend`、`studio-frontend`、`studio-web` 与 `deploy`。没有做渗透测试，也没有连运行中的数据库或控制平面。严重级别按「已登录用户或错误配置就能碰到」来排，不是 CVSS。

先堵读文件、套餐门禁和跨租户拉人。已登录的网页会话可以读服务器上任意路径。没有配额行的租户会被写成 PRO，法律接口的购买门禁因此放开。企业里的普通成员还能把任意用户划进自己的租户。默认支付仍是模拟确认，生产校验只在 profile 精确为 `prod` 时生效。

| 级别 | 数量 |
|------|------|
| 严重 | 3 |
| 高 | 9 |
| 中 | 11 |
| 低 | 4 |
| 合计 | 27 |

## 系统怎么叠在一起

律师用两套壳：桌面 Electron（`studio-frontend`）或浏览器加 Fastify 宿主（`studio-web`，8788）。两边的渲染层都只调宿主，不直连 OpenClaw Gateway（18789）。账号、租户、合同、证据和订单在 Spring Boot（8181，Java 21）。e签宝密钥在控制平面（8281），业务库默认不存对方应用密钥。Web 宿主默认按账号 fork 工人进程，各自一份数据目录和 Gateway。

后端包在 `com.jyfc.backend`：`core` 放 JWT、租户上下文和生产校验；`module` 放 auth、tenant、account、sign、signdoc、template、evidence，以及仍在仓库里的商品、交易、营销和资讯。Flyway 从 V001 到法律域 V130 以后。两端 Studio 各有一份 host-core，桌面在 `electron/`，网页在 `server/host-core/`，行为靠人工对齐。

## 严重

### C1 已登录会话可以读服务器上的任意文件

位置：`studio-web/server/host-core/main/ipc-handlers.ts`（读路径约 1419–1426、`file:readText` 约 1438）；`legacy-router.ts`；`fleet/worker-entry.ts`。

`file:readText`、`file:readBinary`、`file:stat`、`file:listDir`、`file:listTree` 在读模式下不限制根目录：realpath 之后，写根目录之外的路径仍当作只读放行。`files-api` 的 `stagePaths` 会按调用方给的路径复制文件；工作区读取只检查 `relativePath` 是否留在调用方传入的 `workspaceRoot` 里，把根设成 `/` 或 `C:\` 即可。Fleet 工人是同一台机器上的子进程，因此可以读到其他账号的 `auth.json` 和设置。桌面端同一段逻辑允许渲染进程读本机任意路径。写文件仍限制在 OpenClaw / userData 根内。

处理：读和写都限制在该账号的数据根，以及服务端保存的工作区根。不要信任客户端传来的 `workspaceRoot`。拒绝符号链接跳出。

### C2 没有配额行时自动写成 PRO，购买门禁被绕开

位置：`SignQuotaService.quotaOf`；`EntitlementInterceptor.preHandle`。

法律域拦截器只拦 plan 为 FREE 的租户。`quotaOf` 在找不到 `tenant_quota` 行时插入 `plan=PRO`、`sign_quota=11000`，拦截器自己的注释也写了这是默认放行。于是没买套餐、但配额行缺失的租户可以通过 `/api/sign`、证据、模板和审查。注册简单接口会写 FREE，创建企业的供给路径不一定写。

处理：缺行时插入 FREE 且签署额度为 0。不要在读路径上升级套餐。门禁认显式的已购买标记，而不是「不是 FREE 就算买过」。

### C3 企业普通成员可以把任意用户划进自己的租户

位置：`OrganizationController.hasCompanyAccess` 与 `assignUserToCompany`；`TenantProvisioningService.bindUserToCompany`。

`hasCompanyAccess` 对 owner 和 `companyId` 相同的成员都返回真。`POST /api/organization/users/assign` 按请求体里的 `userId` 加载任意用户，设成 ENTERPRISE，再调用 `bindUserToCompany`。只要目标用户当前 `tenantId` 不同，就会改成这家企业的租户。之后该用户的请求落在攻击者的租户里。

处理：分配、改部门、删成员只允许企业所有者或明确的组织管理员。拒绝把已属于另一个非空租户的用户划过来。不要信任请求体里的任意 `userId`。

## 高

### H1 默认支付通道是模拟确认，生产校验不看它

位置：`application.yml` 的 `payment.provider`；`OrderPaymentController` 的 `/api/app/pay/simulate/confirm`；`ProductionConfigValidator`。

未设置 `PAYMENT_PROVIDER` 时通道是 `simulated`。任意已登录用户对自己的待支付流水调用模拟确认，就会走 `processPaymentSuccess`，把订单标成已付。生产配置校验只检查数据库、Redis 和 JWT，不要求关闭模拟通道。微信和支付宝网关仍是空壳：预下单直接抛 `UnsupportedOperationException`，回调在未验签时返回失败，所以真正能把订单标成已付的路径就是模拟确认。

处理：`profile=prod` 时拒绝 `simulated`。模拟确认接口在非模拟通道和测试 profile 之外直接 404。真实回调必须验签通过才改订单状态。

### H2 生产护栏只认名为 prod 的 profile，部署文档没有设置它

位置：`ProductionConfigValidator.run`；`deploy/README-部署教程.md`。

只有激活的 profile 列表里精确包含 `prod`，才会拒绝空 JWT、默认库密码 `jy_password`、空 Redis 密码和未开 SSL 的 JDBC。部署教程列出了 `DB_*`、`JWT_SECRET`、`CP_MODE`，没有 `SPRING_PROFILES_ACTIVE`。不设 profile 时进程会拒绝启动；若运维改成 `dev`、`production` 或其他名字，护栏整段跳过，开发默认密钥会生效。`SecurityConfig` 还把名称里带 `dev` / `test` / `local` 的 profile 当成非生产，Swagger 匿名可访问。

处理：部署文档和启动脚本固定 `SPRING_PROFILES_ACTIVE=prod`。校验改为「不是 prod 就不按生产规则」，或对任何非 dev 的 profile 都跑同一套 fail-fast。

### H3 带凭证的 CORS 默认放行一台公网 IP 的任意端口

位置：`application.yml` 的 `app.cors.allowed-origins`；`SecurityConfig.corsConfigurationSource`。

默认源包括 `http://101.96.206.254:*`，以及 localhost / 127.0.0.1 的 http 与 https 任意端口。允许携带凭证，允许任意请求头。该 IP 上任意端口的页面都可以向 8181 发带 Cookie 的跨站请求。CSRF 对带 Bearer 的请求整段豁免，对 Session Cookie 仍生效，但源放得这么宽，Cookie 会话的浏览器面仍然过大。

处理：生产只保留真实站点源，去掉 IP 通配和 localhost。用环境变量注入，缺省为空并在 prod 启动时失败。

### H4 关掉 Fleet 后所有账号共用一个 Gateway 和一份设置

位置：`studio-web/server/src/env.ts` 的 `FLEET_DISABLED`；`index.ts` 约 204–208。

默认是按账号起工人进程。`FLEET_DISABLED=1` 时 legacy 路由忽略 scope，所有 WebSocket 打到同一个 host。Provider API Key（`provider:getApiKey` 在白名单内）、会话和 Gateway 状态会串到一起。这是文档化的逃生开关，生产若被打开就是跨账号数据面。

处理：生产启动时若 `FLEET_DISABLED=1` 则拒绝启动，或至少拒绝第二个不同的登录 scope。

### H5 下单时 SKU 不必属于所填商品

位置：`OrderService.buildOrderItem`。

客户端的 `totalAmount` 没有被信任。`skuId` 只按主键加载，没有核对 `sku.productId` 是否等于本次 `productId`。廉价 SKU 可以配上另一个商品的名称，成交价用的是那条 SKU 的价格。SKU 不存在时退回商品价，并且不扣库存。

处理：只接受 `productId` 一致的 SKU。`skuId` 有值但查不到，或商品不匹配，直接拒绝订单。

### H6 签署列表按整租户返回，不按当事人

位置：`SignFlowService.listByView` 的 `COMPLETED`、`ALL_SIGNING`、`EXPIRING_SOON`、`PENDING_OTHERS`。

这几个视图调用 `findAllByTenantIdOrderByIdDesc`，再按状态过滤。同一租户里知道接口的用户能看到并非自己发起、也未被邀请的合同。`PENDING_ME` 和抄送走的是当事人或邀请关系，范围是对的。

处理：默认只返回调用者创建的、或作为当事人、被邀请、被抄送的任务。文档读写沿用同一条 `taskForActor` 检查，不要把绝对 `file_path` 序列化出去。

### H7 Windows 更新不校验签名，更新源是公网 IP

位置：`studio-frontend/electron-builder.yml` 的 `publish.url` 与 `win.verifyUpdateCodeSignature`。

更新源是 `https://47.116.163.57:8082/latest`。Windows 配置把 `verifyUpdateCodeSignature` 设为 false，注释写明没有代码签名证书，校验会失败所以关掉。自动下载默认关着，但一旦安装，装的是该地址给出的包。

处理：给安装包签名并打开校验。更新源改到自己的域名。不要安装未签名载荷。

### H8 Gateway 令牌出现在进程命令行；免登录监听所有网卡

位置：`host-core/gateway/config-sync.ts` 的 gateway 参数；`studio-web/server/src/index.ts` 监听 `0.0.0.0`；`env.ts` 的 `DISABLE_AUTH`。

拉起 Gateway 的参数包含 `--token` 和设置里的 `gatewayToken`，同机用户能从进程列表看到。`DISABLE_AUTH=1` 时会话被当成 `sub=dev`，WebSocket 不再以 4401 关掉。宿主监听 `0.0.0.0`，能连上该端口的人就拿到完整宿主能力，包括 C1 的读文件。

处理：令牌用环境变量或权限为 0600 的文件传递，不要放在 argv。`DISABLE_AUTH` 只允许绑在 `127.0.0.1`，否则拒绝启动。默认监听地址改为本机。

### H9 租户内任意成员可以改企业的 e签宝机构号

位置：`CompanyManageController.bindEsignOrgId`。

`PUT /api/companies/{id}/esign-org-id` 只检查公司属于当前租户。这个字段用于企业章的 `orgSignerInfo.orgId`。同事可以把章指到别的机构，或传空字符串解绑。

处理：只允许企业所有者或组织管理员修改。记录修改前后的机构号。

## 中

### M1 租户隔离靠调用约定，不靠数据库或 Hibernate 过滤器

位置：`TenantContextFilter`；`UserEntity` / `CompanyEntity` 的 `tenant_id` 可空；法律域仓库的 `findByIdAndTenantId`。

请求里解析租户，未登录或查不到用户时落到根租户 0。法律域写入有监听器，读取靠仓库方法记得带 `tenantId`。没有行级安全策略，也没有全局 `@Filter`。漏写一个 `findById` 就是跨租户读取。公司、用户的 `tenant_id` 仍可空。

处理：法律与账号表的 `tenant_id` 改为非空。仓库禁止暴露无租户的 `findById`。用两个租户互相拿对方主键做集成测试。

### M2 本地签署配额是先查后写，并发可以打穿

位置：`SignQuotaService.assertCanSign`。

配额等于本月 CREATED 事件数，检查和送签不在同一把行锁里。`jy.cp.mode=local` 时这是强制点；默认 `mode=required`，控制平面不可达会失败关闭，这条只在本地兜底时成立。缺失配额行时还会按 PRO / 11000 自动插入。

处理：送签与计数放在同一事务，对 `tenant_quota` 行 `SELECT … FOR UPDATE`，或改为原子扣减。自动插入的默认额度不要用生产套餐。

### M3 Flyway 忽略尚未执行的迁移仍能启动

位置：`application.yml` 的 `spring.flyway.ignore-pending-migrations: true`。

`validate-on-migrate` 和 `ddl-auto: none` 是对的。`ignore-pending-migrations` 让进程在迁移文件已经在 classpath、但还没进库时继续跑。

处理：生产设为 false。迁移失败就不要接流量。

### M4 e签宝回调令牌不在生产校验里，比较也不是常量时间

位置：`EsignCallbackController`；`jy.sign.callback-token`。

令牌为空或头不匹配会拒绝，这是失败关闭。令牌用 `String.equals` 比较。`ProductionConfigValidator` 不要求 `ESIGN_CALLBACK_TOKEN`。漏配时签署状态永远不会从回调推进，而且启动不会报警。

处理：prod 启动时要求非空且足够长。用 `MessageDigest.isEqual` 比较。回调来源再限制为控制平面地址。

### M5 微信支付回调把异常信息回给调用方

位置：`OrderPaymentController.wechatCallback` 约 176–179。

验签失败会返回 FAIL。`catch` 里把 `e.getMessage()` 写进 `return_msg`。匿名回调因此能看到内部异常文本。支付宝分支只返回 fail。

处理：对调用方固定返回 FAIL / 通用文案，细节只进日志。

### M6 后端没有测试，两端 Studio 的自动化测试已撤掉

位置：backend 下无 `*Test.java`；两端 AGENTS.md（v1.4.60 移除单测与 E2E）。

签署状态机、租户查询、支付确认、配额和控制平面模式都没有回归网。当前能用的检查是后端打包以及两端的 typecheck 与 lint。

处理：先补四组测试：双租户越权、模拟支付在 prod 被拒绝、回调令牌、配额并发。

### M7 WebSocket 接受放在查询串里的会话令牌

位置：`studio-web/server/src/index.ts` 约 84–90。

iframe 场景用 `?token=` 调 `jwt.verify`。查询串会进代理日志、浏览器历史和 Referer。拿到它就能在该连接上调用 host invoke 和 legacy 通道，包括 C1 的读文件。

处理：iframe 用一次性、短时的票据，用完即废。不要把长期会话放进 URL。

### M8 邮箱验证码写了已发送，实际没有发出去

位置：`EmailVerificationService.sendEmailMessage`；`MockSmsServiceImpl`。

发送函数是空的，只打日志 “Email verification code sent”。短信实现类不发短信，验证码仅在 `sms.mock.log-code` 为真时打印，默认是关的。

处理：没有邮件或短信供应商时让请求失败，不要记成功日志。验证码不要进日志。

### M9 工作区里有整库转储和一套对不上的迁移备份

位置：`backend/jy_financial_predrop_20260923_234601.sql`；`backend/db_migration_backup_20260923_234002/`；`.gitignore`。

转储是 mysqldump，含用户口令哈希，目前未跟踪，但 `.gitignore` 没有忽略 `*.sql` 和这份备份目录。现行 Flyway 已收到 `V130__legal.sql`。备份目录还有 V131 到 V147。若把备份拷回 classpath，会和现有校验和冲突。

处理：把转储移出仓库目录。gitignore 忽略备份目录和本地 sql。只保留一套迁移历史。

### M10 Compose 把数据库和后端端口公布出去，并带弱默认值

位置：`docker-compose.yml`。

MySQL 13390、Redis 16380、Qdrant 6333、后端 8181、网页宿主 8788 都映射到宿主机。根密码默认 `root_password`，应用密码 `app_password`，JWT 默认 `change-me-in-production`，Redis 无密码，JDBC 写了 `useSSL=false`。没有设置 `SPRING_PROFILES_ACTIVE`，按现有生产校验，后端在无 profile 时会拒绝启动。

处理：本地开发显式使用 dev profile，不要公布数据库端口。密码和 JWT 只从环境变量读取，删掉仓库里的默认弱值。

### M11 法律界面在桌面和网页两端已经分叉

位置：`studio-web/src/components/layout/SigningSidebar.tsx`；两端 Overview 与 Chat。

网页端的 `SigningSidebar` 有实现，但没有任何引用，嵌入的签署树不会出现。桌面总览会进 `/signing/{id}/setup` 和带 view 的列表，网页总览的指标卡没有跳转。网页聊天欢迎语是写死的中文，输入框不听桌面那套预填事件。`stores/chat.ts` 两边各约 4800 行，`openclaw-auth.ts` 两边各约 3956 行，改一处要手抄另一处。

处理：法律界面放进一份共享模块，或每次改路由时对照两端。不用的侧栏删掉，避免改到不会运行的副本。

## 低

### L1 仓库里仍有引导账号口令 123456

位置：`application.yml` 的 `ADMIN_BOOTSTRAP_*` / `USER_BOOTSTRAP_*`。

开关默认 false，不会在启动时写入。口令和用户名仍在配置里。有人把 ENABLED 打开就会得到已知口令。

处理：删掉默认口令。引导只接受环境变量，且 prod 禁止 ENABLED。

### L2 管理员实体的口令哈希没有 JsonIgnore

位置：`AdminEntity.password`；对照 `UserEntity` 已有 `@JsonIgnore`。

现有管理员接口是手写 Map 返回，没有把哈希带出去。实体一旦被直接序列化，`password` 字段会出去。

处理：同样加上 `@JsonIgnore`，并避免把实体当 API 体。

### L3 部署文档的健康检查和 Actuator 鉴权对不上

位置：`deploy/README-部署教程.md` 的 `curl /actuator/health`；`SecurityConfig` 对 `/actuator/**` 要求管理员。

未带管理员身份时这个地址返回 401，不能当作进程探活。Swagger 只在 profile 名称含 dev / test / local 时匿名开放。

处理：探活用独立的、不暴露指标的端口，或在文档里写明要带管理员令牌。

### L4 法律工作台仍挂着商城、营销、资讯一整面接口

位置：`module/product`、`trade`、`marketing`、`mall`、`news`、`activity`。

当前产品界面是聊天、签署、模板、证据、企业和比对。商品、优惠券、资讯评论仍在已登录 API 上。订单按 `userId` 而不是 `tenantId`。

处理：若律所交付不卖货，把这组路由从生产装配里拿掉，或单独标成未交付模块。

## 已经站住的控制

- 桌面壳关闭了 `nodeIntegration`，打开了 `contextIsolation` 和 `sandbox`。文件写入限制在应用数据根里。
- 用户口令是 BCrypt。登录有 IP 与账号次数限制（5 次锁定，单 IP 10 次），成功后轮换 Session。用户实体的 `password` 标了 `JsonIgnore`。
- 方法安全开着，`/api/**` 需要登录。e签宝回调在令牌为空时直接拒绝。`/internal/tools` 要求 `ROLE_USER`。
- `jy.cp.mode` 默认 `required`：送签要过控制平面，连不上就失败。`ddl-auto` 为 none。profile 精确为 `prod` 时，空 JWT、默认库密码和未加密 JDBC 会让进程起不来。Web 宿主的 `JWT_SECRET` 短于 32 字符会直接退出。

## 建议的修复顺序

1. 收紧 `file:read*` 和 `workspaceRoot`。Web 与桌面同一处改。`DISABLE_AUTH` 不得在 `0.0.0.0` 上启动。
2. 缺配额行改为 FREE。分配企业成员只允许所有者，并拒绝跨租户改 `tenantId`。企业 e签宝机构号同样收权。
3. 生产脚本固定 `SPRING_PROFILES_ACTIVE=prod`。该 profile 下关闭模拟支付、空回调令牌和 `FLEET_DISABLED`。给 Windows 更新包签名。
4. 签署列表改回当事人范围。下单时 SKU 必须属于该商品。收紧 CORS，WebSocket 不再接受查询串里的长期令牌。
