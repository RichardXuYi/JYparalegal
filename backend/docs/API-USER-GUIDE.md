# 企业智能合同与财务管理系统 - 完整使用指南

## 📋 项目概述

本项目是一个基于Java Spring Boot的企业级智能合同与财务管理系统，集成了阿里云通义千问（Qwen）和DeepSeek大模型，实现了合同管理、发票识别、财务对账等核心功能。

### 🎯 核心功能

1. **合同管理** - 合同上传、智能解析、条款拆分、风险审核
2. **发票管理** - OCR识别、发票解析、与合同金额对齐校验
3. **付款管理** - 付款计划生成、付款记录、逾期追踪
4. **对账管理** - 自动对账、差异分析、AI报告生成
5. **知识库** - 风险规则库、合同模板库、条款知识库

---

## 🚀 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis（可选，用于缓存）

### 2. 配置API Key

在项目根目录创建 `.env` 文件：

```bash
# AI Service API Keys
DASHSCOPE_API_KEY=sk-your-dashscope-api-key
DEEPSEEK_API_KEY=sk-your-deepseek-api-key

# Milvus Vector Store（可选）
MILVUS_ENABLED=false
MILVUS_HOST=localhost
MILVUS_PORT=19530

# File Upload
UPLOAD_DIR=./uploads
```

### 3. 启动应用

```bash
cd backend
mvn spring-boot:run
```

应用将在 http://localhost:8080 启动。

---

## 📚 API接口文档

### 一、合同管理模块

#### 1.1 合同整理（上传+解析+入库）

```bash
POST /api/contracts/organize
Content-Type: multipart/form-data

参数：
  - file: 合同文件（PDF/DOCX/TXT）

响应示例：
{
  "success": true,
  "duplicate": false,
  "message": "整理完成，档案 ID=1，知识库 +15 条",
  "contractId": 1,
  "contractTitle": "采购合同-2024001",
  "clausesAdded": 15
}
```

#### 1.2 合同列表查询

```bash
GET /api/contracts
Query参数：
  - contractNumber: 合同编号（模糊匹配）
  - contractType: 合同类型
  - status: 合同状态
  - partyAName: 甲方名称（模糊匹配）
  - page: 页码（默认0）
  - size: 每页数量（默认10）
```

#### 1.3 获取合同详情

```bash
GET /api/contracts/{id}

响应：包含合同信息和所有条款
```

#### 1.4 获取合同内容（调档）

```bash
GET /api/contracts/{id}/content

响应：
{
  "title": "采购合同",
  "contractNumber": "PO-2024-001",
  "originalText": "...",
  "textLength": 12580
}
```

#### 1.5 合同AI审核

```bash
POST /api/contracts/audit/{id}

响应：
{
  "contractId": 1,
  "auditStatus": "completed",
  "riskLevel": "medium",
  "clauseRisks": [
    {
      "clauseNumber": "第七条",
      "clauseTitle": "违约责任",
      "clauseType": "penalty",
      "risks": ["违约金比例可能过高"],
      "riskLevel": "high"
    }
  ],
  "report": "## 审核报告\n\n..."
}
```

#### 1.6 相似条款检索

```bash
POST /api/contracts/search/similar
Content-Type: application/json

{
  "query": "付款方式及违约责任",
  "topK": 5
}

响应：
{
  "query": "付款方式及违约责任",
  "results": [
    {
      "id": 123,
      "text": "第七条 付款方式...",
      "score": 0.85,
      "metadata": {
        "contractId": 1,
        "clauseType": "payment"
      }
    }
  ],
  "count": 5
}
```

#### 1.7 合同统计

```bash
GET /api/contracts/stats

响应：
{
  "totalContracts": 150,
  "activeContracts": 120,
  "pendingAudit": 30
}
```

---

### 二、发票管理模块

#### 2.1 发票上传与处理

```bash
POST /api/finance/invoices/upload
Content-Type: multipart/form-data

参数：
  - file: 发票图片或PDF（必需）
  - contractId: 关联合同ID（可选）

响应：
{
  "success": true,
  "message": "发票处理完成",
  "invoiceId": 1,
  "invoiceNumber": "2024001234",
  "validationStatus": "passed",
  "validationResult": {
    "valid": true,
    "message": "校验通过",
    "errors": [],
    "warnings": []
  }
}
```

#### 2.2 发票列表查询

```bash
GET /api/finance/invoices
Query参数：
  - contractId: 合同ID
  - validationStatus: 校验状态（pending/passed/error）
  - page: 页码
  - size: 每页数量
```

#### 2.3 发票校验

```bash
POST /api/finance/invoices/{id}/validate

响应：
{
  "valid": false,
  "message": "存在错误",
  "errors": ["发票金额超过合同金额，超出：5000.00元"],
  "warnings": ["发票税率与合同税率不符"]
}
```

---

### 三、付款管理模块

#### 3.1 创建付款计划

```bash
POST /api/finance/payments
Content-Type: application/json

{
  "contractId": 1,
  "paymentType": "预付款",
  "paymentDate": "2024-03-15",
  "plannedAmount": 30000.00,
  "paymentMethod": "银行转账",
  "remarks": "首付款30%"
}

响应：创建的付款记录对象
```

#### 3.2 记录实际付款

```bash
PUT /api/finance/payments/{id}/record
Content-Type: application/x-www-form-urlencoded

参数：
  - actualAmount: 实际付款金额
  - paidBy: 经办人
  - paymentDate: 付款日期

响应：更新后的付款记录对象
```

#### 3.3 获取付款计划

```bash
GET /api/finance/payments/plan/{contractId}

响应：
{
  "contractId": 1,
  "contractNumber": "PO-2024-001",
  "totalAmount": 100000.00,
  "currency": "CNY",
  "schedules": [
    {
      "stage": "预付款",
      "percentage": 30,
      "amount": 30000.00,
      "condition": "合同签订后3工作日"
    },
    {
      "stage": "进度款",
      "percentage": 50,
      "amount": 50000.00,
      "condition": "交付验收合格后"
    },
    {
      "stage": "尾款",
      "percentage": 20,
      "amount": 20000.00,
      "condition": "质保期满后"
    }
  ],
  "totalSchedules": 3,
  "totalPlannedAmount": 100000.00
}
```

#### 3.4 获取逾期付款

```bash
GET /api/finance/payments/overdue

响应：
{
  "count": 2,
  "payments": [...]
}
```

---

### 四、对账管理模块

#### 4.1 执行对账

```bash
POST /api/finance/reconciliation/{contractId}
Query参数：
  - startDate: 对账周期开始（可选）
  - endDate: 对账周期结束（可选）

响应：
{
  "id": 1,
  "contractId": 1,
  "reconciliationDate": "2024-03-20",
  "periodStart": "2024-01-01",
  "periodEnd": "2024-03-20",
  "contractAmount": 100000.00,
  "invoicedAmount": 85000.00,
  "paidAmount": 60000.00,
  "pendingAmount": 40000.00,
  "invoiceCount": 3,
  "paymentCount": 2,
  "discrepancyAmount": 25000.00,
  "discrepancyDetails": {...},
  "reportContent": "## 对账报告\n\n..."
}
```

#### 4.2 对账历史

```bash
GET /api/finance/reconciliation/history/{contractId}

响应：对账记录列表
```

#### 4.3 对账仪表盘

```bash
GET /api/finance/dashboard
Query参数：
  - startDate: 统计开始日期
  - endDate: 统计结束日期

响应：
{
  "totalInvoiced": 1500000.00,
  "totalPaid": 1200000.00,
  "pendingAmount": 300000.00,
  "invoiceCount": 45,
  "paymentCount": 38,
  "passedInvoiceCount": 42,
  "errorInvoiceCount": 3
}
```

---

### 五、知识库管理

#### 5.1 获取风险规则

```bash
GET /api/knowledge/rules
Query参数：
  - severity: 严重程度（high/medium/low）
  - ruleType: 规则类型
  - page: 页码
  - size: 每页数量
```

#### 5.2 创建风险规则

```bash
POST /api/knowledge/rules
Content-Type: application/json

{
  "name": "自定义风险规则",
  "type": "detection",
  "severity": "high",
  "checkLogic": "当检测到关键词时触发",
  "triggerConditions": {
    "keywords": ["违约金", "每日"],
    "threshold": 0.2
  }
}
```

#### 5.3 初始化默认规则

```bash
POST /api/knowledge/rules/init

响应：{"message": "默认规则初始化完成"}
```

#### 5.4 条款检索

```bash
GET /api/knowledge/clauses/search
Query参数：
  - keyword: 关键词（模糊匹配）
  - clauseType: 条款类型
  - limit: 返回数量（默认10）

响应：
{
  "count": 5,
  "clauses": [...]
}
```

#### 5.5 创建合同模板

```bash
POST /api/knowledge/templates
Content-Type: application/json

{
  "name": "标准采购合同模板",
  "contractType": "purchase",
  "description": "适用于一般采购业务",
  "requiredClauses": ["合同标的", "质量标准", "付款方式", "违约责任"],
  "optionalClauses": ["知识产权", "保密条款"],
  "introTemplate": "根据《中华人民共和国民法典》及相关法律法规，甲乙双方经协商一致，订立本合同。",
  "closingTemplate": "本合同一式两份，甲乙双方各执一份，自双方签字盖章之日起生效。"
}
```

#### 5.6 知识库统计

```bash
GET /api/knowledge/stats

响应：
{
  "totalClauses": 500,
  "standardClauses": 120,
  "totalTemplates": 15,
  "activeRules": 8,
  "clauseTypeDistribution": {
    "payment": 85,
    "penalty": 62,
    "dispute": 45,
    "force_majeure": 38
  }
}
```

---

## 🗄️ 数据库表结构

### 核心表

| 表名 | 说明 |
|------|------|
| `contracts` | 合同主表 |
| `invoices` | 发票表 |
| `payments` | 付款记录表 |
| `reconciliation_records` | 对账记录表 |
| `contract_clauses` | 合同条款库 |
| `contract_templates` | 合同模板库 |
| `risk_rules` | 风险规则库 |

### 详细字段说明

参见 `V7__contract_management.sql` 文件。

---

## 🔧 配置说明

### application.yml 关键配置

```yaml
spring:
  ai:
    alibaba:
      dashscope:
        api-key: ${DASHSCOPE_API_KEY}
        chat:
          options:
            model: qwen-plus
            temperature: 0.7
            max-tokens: 4096
        embedding:
          options:
            model: text-embedding-v3
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-chat

vectorstore:
  milvus:
    enabled: false
    host: localhost
    port: 19530
    collection-name: contract_clauses
    dimension: 1536

document:
  parser:
    pdf:
      max-file-size: 10485760
      ocr-enabled: true
    ocr:
      provider: qwen-vl
      confidence-threshold: 0.8
```

---

## 📊 使用流程示例

### 场景：采购合同全流程管理

#### Step 1: 上传并整理合同

```bash
curl -X POST http://localhost:8080/api/contracts/organize \
  -F "file=@采购合同.pdf"
```

#### Step 2: AI审核合同

```bash
curl -X POST http://localhost:8080/api/contracts/audit/1
```

#### Step 3: 上传发票

```bash
curl -X POST http://localhost:8080/api/finance/invoices/upload \
  -F "file=@发票.jpg" \
  -F "contractId=1"
```

#### Step 4: 创建付款计划

```bash
curl -X POST http://localhost:8080/api/finance/payments \
  -H "Content-Type: application/json" \
  -d '{
    "contractId": 1,
    "paymentType": "预付款",
    "paymentDate": "2024-03-15",
    "plannedAmount": 30000.00
  }'
```

#### Step 5: 执行对账

```bash
curl -X POST "http://localhost:8080/api/finance/reconciliation/1?startDate=2024-01-01&endDate=2024-03-31"
```

---

## 🔐 安全注意事项

1. **API Key安全**
   - 不要将API Key提交到代码仓库
   - 使用环境变量或`.env`文件管理
   - 生产环境使用密钥管理服务

2. **文件上传**
   - 限制文件大小（默认10MB）
   - 验证文件类型（PDF/DOCX/TXT）
   - 扫描恶意文件

3. **数据保护**
   - 合同内容存储MD5/SHA256哈希
   - 敏感字段加密存储
   - 定期备份数据库

---

## 🐛 常见问题

### Q1: 发票OCR识别失败？

A: 检查以下几点：
- 图片清晰度是否足够
- API Key是否正确配置
- 网络是否能访问阿里云

### Q2: 向量检索返回空结果？

A: 确认：
- Milvus服务是否启动
- 是否已索引条款数据
- collection名称是否正确

### Q3: 合同审核报告生成慢？

A: 可能原因：
- LLM API响应延迟
- 合同文本过长
- 网络问题

---

## 📞 技术支持

如有问题，请提交Issue或联系开发团队。

---

**祝使用愉快！** 🚀
