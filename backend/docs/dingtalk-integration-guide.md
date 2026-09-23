# 钉钉集成指南 - GrandPoem 平台

## 概述

GrandPoem 平台通过钉钉 Open API 实现以下功能：

- **OAuth 扫码登录** - 用户通过钉钉扫码登录 GrandPoem 平台
- **账号绑定** - 将钉钉账号与 GrandPoem 用户绑定
- **消息推送** - 合同审批通知、到期提醒、归档通知等
- **审批流对接** - 将 GrandPoem 合同审批流程映射到钉钉审批

## 申请步骤

### 1. 注册钉钉开放平台

1. 访问 [钉钉开放平台](https://open.dingtalk.com/)
2. 使用企业管理员账号登录
3. 进入「应用开发」→「企业内部应用」

### 2. 创建企业内部应用

1. 点击「创建应用」
2. 填写应用名称：`GrandPoem 合同管理平台`
3. 填写应用描述
4. 上传应用图标（可选）
5. 创建完成后，记录以下信息：
   - **AppKey** (应用 Key)
   - **AppSecret** (应用密钥)
   - **AgentId** (微应用 AgentId)

### 3. 配置应用权限

在应用管理页面，进入「权限管理」，申请以下权限：

| 权限名称 | 权限标识 | 用途 |
|---------|---------|------|
| 通讯录部门信息读取 | Contact.Group.Read | 获取用户信息 |
| 成员信息读取 | Contact.User.Read | 获取用户详情 |
| 企业会话消息 | Message.CorpConversation.Send | 发送工作通知 |
| 审批流程 | Workflow.Create | 创建审批实例 |
| 审批实例读取 | Workflow.Read | 查询审批状态 |

### 4. 配置回调地址

在应用管理页面，进入「开发配置」→「回调配置」：

- **请求地址**：`https://your-domain.com/api/integration/dingtalk/callback`
- **Token**：自定义一个安全字符串（用于验证回调来源）
- **数据加密密钥**：点击自动生成

### 5. 配置登录回调

在应用管理页面，进入「登录配置」：

- **回调域名**：`your-domain.com`（你的服务器域名）
- **PC端登录地址**：`https://your-domain.com/login`

## 配置说明

### 环境变量配置

在服务器上设置以下环境变量：

```bash
# Windows PowerShell
$env:DINGTALK_CORP_ID="your_corp_id"
$env:DINGTALK_AGENT_ID="your_agent_id"
$env:DINGTALK_APP_KEY="your_app_key"
$env:DINGTALK_APP_SECRET="your_app_secret"
$env:DINGTALK_REDIRECT_URI="https://your-domain.com/api/integration/dingtalk/callback"

# Linux/Mac
export DINGTALK_CORP_ID="your_corp_id"
export DINGTALK_AGENT_ID="your_agent_id"
export DINGTALK_APP_KEY="your_app_key"
export DINGTALK_APP_SECRET="your_app_secret"
export DINGTALK_REDIRECT_URI="https://your-domain.com/api/integration/dingtalk/callback"
```

### application.yml 配置

在 `application.yml` 中添加（**不要提交到 Git**）：

```yaml
integration:
  dingtalk:
    enabled: true  # 配置好 key 后改为 true
    corp-id: ${DINGTALK_CORP_ID:}
    agent-id: ${DINGTALK_AGENT_ID:}
    app-key: ${DINGTALK_APP_KEY:}
    app-secret: ${DINGTALK_APP_SECRET:}
    redirect-uri: ${DINGTALK_REDIRECT_URI:http://localhost:3103/api/integration/dingtalk/callback}
    base-url: https://oapi.dingtalk.com
```

### 安全建议

1. **不要将密钥提交到代码仓库**，使用环境变量或配置中心
2. **生产环境使用 HTTPS**
3. **定期轮换 AppSecret**
4. **限制 API 调用频率**，避免触发钉钉限流

## API 使用示例

### 1. 获取扫码登录 URL

```bash
curl -X GET "http://localhost:8080/api/integration/dingtalk/qrcode?state=grandpoem"
```

响应：
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "qrCodeUrl": "https://login.dingtalk.com/oauth2/auth?response_type=code&client_id=xxx&scope=openid&state=grandpoem&redirect_uri=xxx"
  }
}
```

### 2. 绑定钉钉账号

```bash
curl -X POST "http://localhost:8080/api/integration/dingtalk/bind" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_jwt_token" \
  -d '{"code": "oauth_authorization_code"}'
```

响应：
```json
{
  "code": 0,
  "msg": "绑定成功",
  "data": {
    "userId": 1,
    "dingtalkUnionId": "xxx",
    "dingtalkNick": "张三"
  }
}
```

### 3. 发送工作通知

```bash
curl -X POST "http://localhost:8080/api/integration/dingtalk/message/send" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "dingtalk_user_id",
    "title": "合同审批通知",
    "content": "### 合同审批通知\n\n您有一份新合同待审批",
    "url": "https://your-domain.com/contract/123"
  }'
```

### 4. 创建审批实例

```bash
curl -X POST "http://localhost:8080/api/integration/dingtalk/approval/create" \
  -H "Content-Type: application/json" \
  -d '{
    "contractId": 123,
    "userId": "dingtalk_user_id",
    "processCode": "PROC-XXX",
    "title": "合同审批-测试合同",
    "formData": {
      "合同标题": "测试合同",
      "合同金额": "100000",
      "合同编号": "HT-2026-001"
    }
  }'
```

### 5. 查询审批状态

```bash
curl -X GET "http://localhost:8080/api/integration/dingtalk/approval/status/{approvalId}"
```

## 数据库表结构

### user_dingtalk_binding - 用户钉钉绑定表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | GrandPoem 用户ID（唯一） |
| dingtalk_unionid | VARCHAR(100) | 钉钉 unionId（唯一） |
| dingtalk_userid | VARCHAR(100) | 钉钉 userId |
| dingtalk_mobile | VARCHAR(20) | 钉钉绑定手机号 |
| access_token | VARCHAR(500) | 用户级 access_token |
| refresh_token | VARCHAR(500) | refresh_token |
| token_expires_at | DATETIME | token 过期时间 |
| is_active | TINYINT(1) | 是否激活 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### dingtalk_approval_mapping - 钉钉审批映射表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| contract_id | BIGINT | GrandPoem 合同ID |
| dingtalk_approval_id | VARCHAR(100) | 钉钉审批实例ID |
| dingtalk_process_code | VARCHAR(100) | 钉钉流程模板code |
| status | VARCHAR(50) | 审批状态 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 常见问题

### Q: access_token 频繁失效怎么办？

A: 系统已内置缓存机制，企业级 access_token 会自动缓存 2 小时（提前 5 分钟刷新）。如果仍然频繁失效，检查：
1. AppKey/AppSecret 是否正确
2. 是否有多个服务实例同时运行（token 会互相覆盖）
3. 钉钉后台是否重置了应用密钥

### Q: 消息发送失败，提示权限不足？

A: 检查以下几点：
1. 应用是否已发布上线
2. 是否申请了对应的消息权限
3. 用户是否在应用的可见范围内

### Q: 审批回调收不到？

A: 检查以下几点：
1. 回调地址是否可公网访问
2. 回调地址是否使用 HTTPS
3. 钉钉后台是否配置了正确的回调地址
4. 服务器防火墙是否放行了钉钉的 IP 段

### Q: 如何测试？

A: 开发环境建议：
1. 使用 ngrok 等工具将本地服务暴露到公网
2. 在钉钉后台配置 ngrok 的回调地址
3. 使用钉钉测试应用（不需要企业认证）
