# GrandPoem 企业微信集成文档

## 目录

1. [概述](#概述)
2. [企业微信管理后台配置](#企业微信管理后台配置)
3. [配置文件说明](#配置文件说明)
4. [接口清单](#接口清单)
5. [OAuth 登录流程](#oauth-登录流程)
6. [通讯录同步](#通讯录同步)
7. [消息推送](#消息推送)
8. [审批对接](#审批对接)
9. [JS-SDK 集成](#js-sdk-集成)
10. [事件回调验证](#事件回调验证)
11. [频率限制](#频率限制)
12. [常见问题](#常见问题)

---

## 概述

GrandPoem 合同管理平台通过企业微信 API 实现以下功能：

| 模块 | 说明 | 依赖 Secret |
|------|------|-------------|
| OAuth 登录 | 用户通过企业微信扫码/点击应用登录 | 应用 Secret |
| 通讯录同步 | 自动同步企业组织架构和成员 | 通讯录同步 Secret |
| 应用消息 | 推送合同审批、通知到企业微信 | 应用 Secret |
| OA 审批 | 对接企业微信审批流 | 审批 Secret |
| JS-SDK | 网页调用企业微信原生能力（拍照、定位等） | 应用 Secret |

**企业微信与其他平台的主要差异：**

- 通讯录管理使用独立的"通讯录同步"接口，需单独配置 Secret
- 应用消息需要 agentId，消息推送有频率限制
- 审批 API 需要先在企业微信后台创建审批模板，通过 templateId 发起
- 需要配置"可信域名"和 OAuth 回调域

---

## 企业微信管理后台配置

### 1. 获取 CorpID

1. 登录 [企业微信管理后台](https://work.weixin.qq.com/wework_admin/frame)
2. 进入 **我的企业 → 企业信息**
3. 复制 **企业ID（CorpID）**

### 2. 创建自建应用

1. 进入 **应用管理 → 自建 → 创建应用**
2. 填写应用名称（如 "GrandPoem 合同管理"）
3. 上传应用 Logo
4. 创建完成后，记录 **AgentId** 和 **Secret**

### 3. 配置可信域名

1. 进入 **应用管理 → 自建应用 → 设置可信域名**
2. 输入 GrandPoem 业务的域名（如 `contract.grandpoem.ai`）
3. 按照提示下载验证文件并放置到服务器

### 4. 配置 OAuth 回调域

1. 进入 **应用管理 → 自建应用 → 企业微信授权登录**
2. 点击 **设置回调域名**
3. 输入与可信域名相同的域名

### 5. 获取通讯录同步 Secret

1. 进入 **管理工具 → 通讯录同步**
2. 点击 **开启 API 接口同步**
3. 记录 **Secret**
4. 设置通讯录同步范围为"全部成员"（按需）

### 6. 获取审批 Secret

1. 进入 **应用管理 → 审批 → 前往设置 → 权限管理**
2. 开启 **API 调用权限**
3. 记录 **Secret**
4. 在 **模板管理** 中创建合同审批模板，记录 templateId

### 7. 配置回调 URL

1. 进入 **应用管理 → 自建应用 → 接收消息 → 设置 API 接收**
2. URL 填写：`https://your-domain.com/api/integration/wecom/callback`
3. Token 和 EncodingAESKey 自行生成并保存
4. 会触发 URL 验证（GET 请求），验证通过后生效

---

## 配置文件说明

在 `application.yml` 中添加以下配置：

```yaml
integration:
  wecom:
    enabled: true                     # 是否启用企业微信集成
    corp-id: ww1234567890abcdef       # 企业 CorpID
    corp-secret: xxxxxxxxxxxxxxxx     # 应用 Secret
    agent-id: "1000001"               # 应用 AgentID
    contact-secret: xxxxxxxxxxxxxx    # 通讯录同步 Secret
    approval-secret: xxxxxxxxxxxxxx   # 审批 Secret
    redirect-uri: https://contract.grandpoem.ai/oauth/callback  # OAuth 回调
    base-url: https://qyapi.weixin.qq.com/cgi-bin           # API 基础地址
    auth-base-url: https://open.weixin.qq.com/connect/oauth2 # OAuth 基础地址
```

**注意：**
- `corp-secret`（应用 Secret）、`contact-secret`（通讯录 Secret）、`approval-secret`（审批 Secret）
  是三个不同的密钥，分别来自企业微信后台的不同位置
- `agentId` 仅在自建应用时使用，若只用通讯录同步则不需要
- `redirect-uri` 需要是已配置的**可信域名**下的地址

---

## 接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/integration/wecom/login-url` | 生成企业微信登录 URL |
| POST | `/api/integration/wecom/login` | code 换登录态 |
| GET | `/api/integration/wecom/userinfo` | 获取绑定用户信息 |
| POST | `/api/integration/wecom/bind` | 绑定企业微信账号到系统用户 |
| POST | `/api/integration/wecom/contact/sync` | 手动触发通讯录同步 |
| POST | `/api/integration/wecom/message/send` | 发送应用消息 |
| POST | `/api/integration/wecom/approval/create` | 创建审批 |
| GET | `/api/integration/wecom/js-sdk-config` | 获取 JS-SDK 配置 |
| GET | `/api/integration/wecom/callback` | 回调 URL 验证 |
| POST | `/api/integration/wecom/callback` | 事件回调接收 |

### 1. 生成登录 URL

```
GET /api/integration/wecom/login-url?state=xxx&scope=base
```

- `state`：可选，用于防 CSRF
- `scope`：可选，`base`（静默授权）或 `userinfo`（手动授权）

### 2. 企业微信登录

```
POST /api/integration/wecom/login
Content-Type: application/json

{
  "code": "xxxxxx"
}
```

### 3. 发送消息

```
POST /api/integration/wecom/message/send
Content-Type: application/json

{
  "userIds": ["zhangsan", "lisi"],
  "type": "textcard",
  "title": "合同审批通知",
  "content": "合同《XXXX》待审批",
  "url": "https://contract.grandpoem.ai/approval/123"
}
```

支持的消息类型：`text`、`markdown`、`textcard`、`news`

### 4. 创建审批

```
POST /api/integration/wecom/approval/create
Content-Type: application/json

{
  "contractId": 123,
  "templateId": "3Tk1f8D2xqz9m5R",  // 企业微信审批模板 ID
  "applicant": "zhangsan",
  "summary": "合同《XXXX》审批申请",
  "formData": [
    {
      "control": "Text",
      "id": "Text-xxxxxxxx",
      "title": "合同名称",
      "value": ["XXXX合同"]
    }
  ]
}
```

---

## OAuth 登录流程

```
┌──────────┐        ┌──────────┐        ┌──────────────┐
│  前端页面  │        │ 后端服务  │        │ 企业微信服务器 │
└────┬─────┘        └────┬─────┘        └──────┬───────┘
     │                    │                     │
     │  1. GET /login-url  │                     │
     │◄──── loginUrl ─────│                     │
     │                    │                     │
     │  2. 跳转到 loginUrl  │                     │
     │──────────────────────────────────────────►│
     │                    │                     │
     │  3. 企业微信授权页    │                     │
     │    用户确认登录      │                     │
     │                    │                     │
     │  4. 回调 redirectUri  │                     │
     │◄──── code + state ─│                     │
     │                    │                     │
     │  5. POST /login     │                     │
     │────── code ────────►│                     │
     │                    │  6. 获取 access_token  │
     │                    │──────────────────────►│
     │                    │◄──── token ──────────│
     │                    │  7. code 换 UserID    │
     │                    │──────────────────────►│
     │                    │◄──── userid ────────│
     │◄── userInfo ──────│                     │
     │                    │                     │
```

**关键点：**
- `scope=base`：静默授权，只能获取 UserID 和 DeviceID
- `scope=userinfo`：需要用户手动确认，可额外获取 user_ticket
- 获取 user_ticket 后可通过 `getUserDetail` 获取姓名、头像、手机等详细信息
- 企业微信 UserID 是**企业内唯一**，不同企业间的 UserID 可能重复

---

## 通讯录同步

### 同步策略

| 任务 | 调度时间 | 说明 |
|------|----------|------|
| 全量同步 | 每天 02:00 | 递归同步所有部门和成员 |
| 增量同步 | 每小时 | 同步根部门下的成员变更 |

### 同步流程

1. 调用 `/department/list` 获取所有部门（递归）
2. 保存部门映射到 `wecom_department_mapping` 表
3. 遍历每个部门，调用 `/user/list` 获取成员
4. 更新 `user_wecom_binding` 表中的成员信息

### 手动触发

```
POST /api/integration/wecom/contact/sync
```

### 注意事项

- 通讯录同步使用独立的 **contactSecret**（在管理后台 → 管理工具 → 通讯录同步 获取）
- 全量同步时不会删除本地已存在的成员（企业微信没有提供成员删除事件）
- 建议结合回调通知（`change_contact` 事件）实现实时同步
- 频率限制：获取成员列表 1000 次/分钟，获取部门列表 100 次/分钟

---

## 消息推送

### 支持的消息类型

| 类型 | 方法 | 适用场景 |
|------|------|----------|
| text | `sendTextMessage` | 简单通知 |
| markdown | `sendMarkdownMessage` | 带格式的公告 |
| textcard | `sendTextCardMessage` | **审批通知（推荐）** |
| news | `sendNewsMessage` | 图文混排消息 |

### 频率限制

| 限制项 | 上限 |
|--------|------|
| 单个应用对同一成员同类型消息 | 200 次/分钟 |
| 单个应用对同一成员所有消息 | 600 次/分钟 |
| 单个应用对同一部门所有消息 | 200 次/分钟 |

### 建议

- 审批通知推荐使用 `textcard` 类型，视觉效果更好
- 高频通知考虑消息队列异步发送
- 发送失败时检查 `invaliduser` 和 `invalidparty` 字段

---

## 审批对接

### 审批模板

企业微信的审批模板需要先在管理后台创建：

1. 进入 **应用管理 → 审批 → 模板管理**
2. 创建合同审批模板
3. 添加需要的控件（文本、金额、附件、日期等）
4. 记录模板 ID（`template_id`）

### 发起审批

后端通过 `createApproval` 方法发起审批：

```
POST /api/integration/wecom/approval/create
```

审批发起后，企业微信会返回 `sp_no`（审批编号），与合同关联保存在 `wecom_approval_mapping` 表中。

### 审批状态

| 状态码 | 含义 |
|--------|------|
| 1 | 审批中 |
| 2 | 已通过 |
| 3 | 已驳回 |
| 4 | 已撤销 |
| 6 | 通过中（部分审批节点已通过） |

### 回调事件

当审批状态变更时，企业微信会向回调 URL 推送 `approval_change` 事件。
需要配置回调 URL 才能接收。

---

## JS-SDK 集成

### 前端使用步骤

```javascript
// 1. 获取配置
fetch('/api/integration/wecom/js-sdk-config?url=' + encodeURIComponent(location.href))
  .then(res => res.json())
  .then(res => {
    // 2. 注入配置
    wx.config({
      beta: true,
      debug: false,
      appId: res.data.corpid,
      timestamp: res.data.timestamp,
      nonceStr: res.data.nonceStr,
      signature: res.data.signature,
      jsApiList: [
        'chooseImage',
        'getLocation',
        'scanQRCode',
        'startRecord',
        'openEnterpriseChat'
      ]
    });

    // 3. 初始化
    wx.ready(function() {
      console.log('JS-SDK ready');
    });
  });
```

### 可用 API（需在 jsApiList 中声明）

- `chooseImage` - 拍照/选择图片
- `getLocation` - 获取地理位置
- `scanQRCode` - 扫码
- `startRecord` / `stopRecord` - 录音
- `openEnterpriseChat` - 打开会话
- `shareAppMessage` - 分享到会话

### 注意事项

- 必须配置**可信域名**（JS接口安全域名），否则 JS-SDK 无法初始化
- 签名时使用的 URL 必须与当前页面完全一致（包括参数和 hash）
- 企业微信 JS-SDK 的 `appId` 是 `corpid`，不是自建应用的 `agentId`

---

## 事件回调验证

### URL 验证流程

企业微信配置回调 URL 时，会用 GET 请求验证：

```
GET /api/integration/wecom/callback?
    msg_signature=xxx&
    timestamp=xxx&
    nonce=xxx&
    echostr=xxx
```

当前实现直接返回 `echostr` 通过验证。正式环境需要实现消息加解密。

### 消息加解密（TODO）

需要实现企业微信的 [消息加解密方案](https://developer.work.weixin.qq.com/document/path/90971)：

1. 解析 XML 格式的密文
2. 使用 EncodingAESKey 解密
3. 解析解密后的 JSON/XML
4. 处理事件（审批变更、成员变更等）

### 支持的事件类型

| 事件 | 说明 |
|------|------|
| `approval_change` | 审批状态变更 |
| `change_contact` | 通讯录变更 |
| `click` | 菜单点击 |
| `enter_agent` | 进入应用 |

---

## 频率限制

### access_token

| 限制项 | 上限 |
|--------|------|
| 每日获取次数 | 2000 次 |
| 缓存时间 | 7200 秒 |

**建议：** TokenService 已实现了 Redis 缓存，缓存过期前 300 秒刷新，
正常情况下每天只调用十几次，远低于上限。

### 通讯录 API

| 接口 | 限制 |
|------|------|
| 获取部门列表 | 100 次/分钟 |
| 获取部门成员 | 1000 次/分钟 |
| 创建/更新/删除成员 | 100 次/分钟 |
| 全量覆盖 | 10 次/分钟 |

### 消息推送 API

| 限制项 | 上限 |
|--------|------|
| 同成员同类型 | 200 次/分钟 |
| 同成员所有类型 | 600 次/分钟 |
| 同部门 | 200 次/分钟 |

### 审批 API

| 接口 | 限制 |
|------|------|
| 发起审批 | 60 次/分钟 |
| 获取审批详情 | 100 次/分钟 |

---

## 应用配置示例（application.yml）

```yaml
integration:
  wecom:
    enabled: true
    corp-id: ww1111111111111111
    corp-secret: xxxxxxxxxxxxxxxxxxxxxxxxxxx
    agent-id: "1000001"
    contact-secret: xxxxxxxxxxxxxxxxxxxxxxxxxxx
    approval-secret: xxxxxxxxxxxxxxxxxxxxxxxxxxx
    redirect-uri: https://contract-test.grandpoem.ai/wecom/callback
    # 以下使用默认值即可
    # base-url: https://qyapi.weixin.qq.com/cgi-bin
    # auth-base-url: https://open.weixin.qq.com/connect/oauth2
```

---

## 常见问题

### Q: 为什么 access_token 获取失败？

A: 检查以下配置：
- `corpId` 是否正确（管理后台 → 我的企业 → 企业信息）
- 对应的 Secret 是否匹配（应用/通讯录/审批是三个不同的 Secret）
- 是否有 IP 白名单限制（管理后台 → 我的企业 → 安全设置 → IP 白名单）

### Q: 为什么登录时报 redirect_uri 不匹配？

A: 确认：
- 回调域名已配置（应用管理 → 自建应用 → 企业微信授权登录 → 设置回调域名）
- `redirect_uri` 使用的是可信域名
- URL 编码正确

### Q: 为什么通讯录同步速度很慢？

A: 通讯录同步是递归请求，大企业可能有几百个部门。建议：
- 同步任务放在凌晨执行
- 首次同步可以通过管理后台导出 CSV 再导入
- 日常用增量同步（每小时）和回调通知

### Q: 为什么消息发送失败（45009 错误）？

A: 触发了频率限制。解决方案：
- 减少发送频率
- 使用消息队列削峰
- 将全员通知改为按部门分批发送

### Q: 为什么 JS-SDK 初始化失败？

A: 常见原因：
- 可信域名未配置或配置错误
- 签名 URL 与当前页面 URL 不一致（包括 HTTP/HTTPS、参数、hash）
- 页面未在企业微信客户端内打开
