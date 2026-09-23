# 飞书集成开发文档

## 一、概述

本文档描述 GrandPoem 平台对接飞书（Feishu/Lark）的集成方案。飞书集成模块位于 `module/integration/feishu/` 目录下，遵循与 `dingtalk` 模块一致的编码风格。

### 功能清单

| 功能 | 状态 | 说明 |
|------|------|------|
| OAuth 登录 | ✅ 已完成 | 扫码/应用内登录，获取用户身份 |
| 消息推送 | ✅ 已完成 | 文本/卡片/批量消息，合同审批通知 |
| 审批流 | ✅ 已完成 | 创建审批实例、查询状态、事件解析 |
| 多维表格 | ✅ 已完成 | 合同台账同步到飞书 Bitable |
| 事件回调 | ✅ 已完成 | URL challenge 验证、签名验证、AES 解密、防重放 |
| Token 管理 | ✅ 已完成 | tenant_access_token 缓存与自动刷新 |

---

## 二、前置准备：飞书开放平台申请

### 2.1 创建应用

1. 登录 [飞书开放平台](https://open.feishu.cn)
2. 点击「开发者后台」→「创建应用」
3. 选择「企业自建应用」，填写应用名称（如"GrandPoem 平台"）
4. 创建完成后，记录 **App ID** 和 **App Secret**

### 2.2 配置权限

在「应用发布」→「权限管理」中，根据需要开通以下权限：

| 权限 | 用途 |
|------|------|
| `contact:user.base` | 获取用户基本信息（union_id, open_id） |
| `contact:user.email` | 获取用户邮箱 |
| `contact:user.phone` | 获取用户手机号 |
| `im:message` | 发送消息 |
| `im:resource` | 上传图片/文件素材 |
| `approval:instance` | 创建/查询审批实例 |
| `approval:instance:readonly` | 读取审批实例 |
| `bitable:app` | 操作多维表格 |
| `bitable:app:readonly` | 读取多维表格 |
| `contact:contact:readonly` | 读取通讯录（可选） |

### 2.3 配置事件订阅

1. 进入「事件与回调」→「事件配置」
2. 添加事件，推荐订阅：
   - `审批实例状态变更事件`（`approval_instance`）
   - 其他业务需要的事件
3. 设置回调地址：`https://your-domain.com/api/integration/feishu/callback`
4. 配置 **Verification Token** 和 **Encrypt Key**（推荐开启加密）

### 2.4 发布应用

1. 在「版本管理与发布」中创建版本
2. 填写版本说明后提交审核
3. 审核通过后「发布」，应用生效

---

## 三、系统配置

### 3.1 application.yml 配置

将以下配置添加到 `application.yml`（不要修改已有配置）：

```yaml
integration:
  feishu:
    enabled: true
    app-id: ${FEISHU_APP_ID:cli_xxxxxxxxxxxx}
    app-secret: ${FEISHU_APP_SECRET:xxxxxxxxxxxxxxxxxxxx}
    verification-token: ${FEISHU_VERIFICATION_TOKEN:xxxxxxxxxxxx}
    encrypt-key: ${FEISHU_ENCRYPT_KEY:}               # 可选，不配置则不加密
    base-url: https://open.feishu.cn/open-apis
    redirect-uri: ${FEISHU_REDIRECT_URI:http://localhost:3103/api/integration/feishu/callback}
```

### 3.2 环境变量推荐

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `FEISHU_APP_ID` | 应用 ID | `cli_xxxxxxxxxxxx` |
| `FEISHU_APP_SECRET` | 应用 Secret | `xxxxxxxxxxxxxxxx` |
| `FEISHU_VERIFICATION_TOKEN` | 事件验证 Token | 从飞书后台复制 |
| `FEISHU_ENCRYPT_KEY` | 事件加密 Key（可选） | 从飞书后台复制 |
| `FEISHU_REDIRECT_URI` | OAuth 回调地址 | `https://your-domain.com/api/integration/feishu/callback` |

---

## 四、API 接口清单

### 4.1 OAuth 流程

```
# 1. 前端获取登录 URL
GET /api/integration/feishu/login-url?redirectUri=xxx&state=xxx
→ { code: 0, data: { loginUrl: "https://open.feishu.cn/..." } }

# 2. 用户扫码登录后跳转到 redirectUri，携带 code 参数
#    前端拿到 code 后调用登录接口
POST /api/integration/feishu/login
Body: { "code": "xxxxx" }
→ { code: 0, data: { unionId, openId, name, avatar, alreadyBound } }

# 3. 绑定本地账号
POST /api/integration/feishu/bind
Body: { "userId": 1, "unionId": "xxx", "openId": "xxx", "email": "...", "mobile": "..." }
→ { code: 0, msg: "飞书账号绑定成功" }
```

### 4.2 消息发送

```
POST /api/integration/feishu/message/send
Body: {
  "receiveId": "ou_xxxxx",
  "receiveIdType": "open_id",   // open_id / union_id / user_id / chat_id
  "msgType": "text",            // text / post / image / interactive
  "content": "{\"text\":\"你好\"}"
}
```

### 4.3 审批创建

```
POST /api/integration/feishu/approval/create
Body: {
  "approvalCode": "XXXXXXXXX",  // 飞书后台创建的审批定义 code
  "userId": "xxxxx",            // 发起人飞书 user_id
  "formData": { "field1": "value1", "field2": "value2" }
}
```

### 4.4 事件回调

```
POST /api/integration/feishu/callback
Headers: X-Lark-Signature, X-Lark-Request-Timestamp, X-Lark-Request-Nonce
Body: 原始 JSON 字符串（飞书开放平台推送）
```

---

## 五、回调验证流程

### 5.1 URL Challenge 验证流程

当在飞书后台配置事件回调 URL 时，飞书会发送验证请求：

1. 飞书 POST 到回调 URL，body 包含 `type: "url_verification"`, `challenge`, `token`
2. 系统验证 token 是否匹配 `verificationToken`
3. 验证通过后返回 `{ "challenge": "xxx" }`
4. 飞书确认回调地址有效，配置完成

### 5.2 事件签名验证流程

每次事件推送时：
1. 飞书在请求头中携带 `X-Lark-Signature`, `X-Lark-Request-Timestamp`, `X-Lark-Request-Nonce`
2. 系统计算 `hmac_sha256(timestamp + nonce + raw_body, verification_token)`
3. 比较计算结果与 `X-Lark-Signature` 是否一致
4. 验证通过后进入业务处理

### 5.3 AES 解密流程

当配置了 `encrypt_key` 时，事件 payload 被加密：

1. 飞书推送 `{ "encrypt": "base64_encrypted_string" }`
2. 系统用 `encrypt_key` 的 Base64 解码值作为 AES-256-CBC 密钥
3. 密文前 16 字节作为 IV，解密后获得明文 JSON
4. 解析明文事件内容进行业务处理

### 5.4 防重放机制

- 系统缓存最近 5 分钟的 `timestamp:nonce` 组合
- 同一组合重复推送直接拦截
- 缓存自动过期，防止内存泄漏

---

## 六、核心类说明

| 类 | 说明 |
|----|------|
| `FeishuProperties` | 飞书配置属性，从 `integration.feishu.*` 读取 |
| `FeishuTokenService` | Token 管理：缓存 tenant_access_token，定时 90 分钟刷新，提供 OAuth code → token 交换 |
| `FeishuAuthService` | OAuth 登录：构造登录 URL，code 换用户信息，token 查用户详情 |
| `FeishuMessageService` | 消息服务：文本/卡片/批量消息，图片/文件上传 |
| `FeishuApprovalService` | 审批服务：创建审批实例，查询状态，解析事件 |
| `FeishuBitableService` | 多维表格：CRUD 记录，合同台账同步 |
| `FeishuSignatureVerifier` | 安全模块：URL challenge 验证、HMAC-SHA256 签名验证、AES-256-CBC 解密、防重放 |
| `FeishuController` | REST API：暴露所有集成接口 |
| `FeishuApiException` | 飞书 API 异常（包含 errcode） |
| `UserFeishuBinding` | 用户绑定表实体 |
| `FeishuApprovalMapping` | 审批映射表实体 |

---

## 七、错误码说明

飞书 API 返回 `code` 和 `msg`，系统通过 `FeishuApiException` 统一封装。常见错误码：

| code | 说明 |
|------|------|
| 0 | 成功 |
| 99991663 | tenant_access_token 无效或过期 |
| 99991664 | 请求频率超限 |
| 99991665 | 应用无权限 |
| 99991666 | 参数错误 |
| 99991668 | 应用不存在或已停用 |
| 10003 | 无效的 code |
| 230001 | OAuth scope 不匹配 |
| 230010 | 用户拒绝授权 |

---

## 八、与钉钉集成对比

| 特性 | 飞书 | 钉钉 |
|------|------|------|
| Token API | `/auth/v3/tenant_access_token/internal` | `/gettoken` |
| OAuth URL | `/authen/v1/index` | `/connect/oauth2/sns_authorize` |
| 用户信息 | `/authen/v1/user_info` | `/topapi/v2/user/getuserinfo` |
| 消息发送 | `/im/v1/messages` | `/chat/send` |
| 审批创建 | `/approval/v4/instances` | `/topapi/processinstance/create` |
| 多维表格 | `/bitable/v1/apps/{token}/tables/{id}/records` | 无内置类比 |
| 事件签名 | HMAC-SHA256(timestamp+nonce+body, token) | 自定义签名 |
| 加密方式 | AES-256-CBC | 不要求加密 |

---

## 九、特别说明

### 9.1 安全性
- 所有配置文件中的敏感信息（app-secret, verification-token）通过环境变量注入
- 事件回调端点 `POST /callback` 应设置为公开（无需 session 认证）
- OAuth 流程使用 `state` 参数防 CSRF 攻击

### 9.2 性能
- `tenant_access_token` 缓存 2 小时（实际有效期 2 小时，提前 5 分钟刷新）
- WebClient 配置了超时（10s-60s 视接口而定）
- 批量消息逐个发送，失败不影响其他用户

### 9.3 扩展
- 事件处理在 `FeishuController.callback()` 中有 TODO 标记
- 新增事件类型只需在 callback 中添加相应处理逻辑
- Bitable 服务可扩展为数据同步服务
