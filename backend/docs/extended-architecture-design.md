# 企业级智能财务与合同管理系统 - 可扩展架构设计

## 一、需求演进路径

### 当前阶段：合同管理核心功能
```
阶段1：合同管理
├── 1.1 合同整理（上传→解析→入库）
├── 1.2 合同审核（AI风险分析）
├── 1.3 合同生成（模板填充）
├── 1.4 知识库管理（条款库）
└── 1.5 调档检索（文件调取）
```

### 下一阶段：财务对账与发票校验
```
阶段2：财务对账
├── 2.1 合同金额结构化提取
│   ├── 合同总价、分期计划
│   ├── 付款条件、账期
│   ├── 税率、含税/不含税
│   └── 币种、汇率
├── 2.2 发票解析与校验
│   ├── 发票OCR识别（金额、税率、发票号）
│   ├── 发票与合同金额对齐
│   └── 异常发票预警
├── 2.3 付款计划管理
│   ├── 应付款时间线
│   ├── 实际付款记录
│   └── 逾期预警
└── 2.4 对账报告生成
    ├── 差异分析
    ├── 付款进度追踪
    └── 财务报表
```

### 远期阶段：智能财务分析
```
阶段3：智能财务
├── 3.1 供应商管理
│   ├── 供应商合同汇总
│   ├── 供应商履约评估
│   └── 供应商风险预警
├── 3.2 成本分析
│   ├── 合同成本归集
│   ├── 预算执行追踪
│   └── 成本优化建议
└── 3.3 合规审计
    ├── 合同合规性检查
    ├── 税务合规校验
    └── 审计追踪
```

---

## 二、可扩展架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          统一能力平台层                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                     AI能力中心（可复用）                            │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │ │
│  │  │ 文档解析    │  │ 关键信息    │  │ 结构化数据  │            │ │
│  │  │ DocumentAI  │  │ Extraction  │  │ Validation  │            │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │ │
│  │         │                  │                  │                    │ │
│  │         ▼                  ▼                  ▼                    │ │
│  │  ┌─────────────────────────────────────────────────────────┐    │ │
│  │  │               统一AI服务管理层（AIOrchestrator）          │    │ │
│  │  │  • 合同/发票/文档统一解析入口                             │    │ │
│  │  │  • 智能字段提取（金额/日期/条款）                         │    │ │
│  │  │  • 跨模块数据校验和关联                                   │    │ │
│  │  └─────────────────────────────────────────────────────────┘    │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                     业务组件中心                                  │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │ │
│  │  │ 文档处理    │  │ 向量存储    │  │ 文件管理    │            │ │
│  │  │ DocService  │  │ VectorStore │  │ FileManager │            │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           业务模块层（松耦合）                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────┐    ┌──────────────────────────────┐  │
│  │       合同管理模块            │    │        财务管理模块           │  │
│  │       Contract Module        │◄──►│       Finance Module          │  │
│  ├──────────────────────────────┤    ├──────────────────────────────┤  │
│  │                              │    │                              │  │
│  │  • 合同上传与整理            │    │  • 发票解析与登记            │  │
│  │  • 合同条款拆分              │    │  • 发票与合同对齐            │  │
│  │  • AI风险审核                │    │  • 付款计划生成              │  │
│  │  • 合同生成                  │    │  • 付款记录管理              │  │
│  │  • 条款知识库                │    │  • 对账差异分析              │  │
│  │  • 文件调档                  │    │  • 财务报表生成              │  │
│  │                              │    │                              │  │
│  │  [数据依赖]                  │    │  [数据依赖]                  │  │
│  │  • 合同金额 ← ───────────────┼────┼──→ 合同引用                 │  │
│  │  • 付款条件 ←                │    │  • 发票关联合同ID            │  │
│  │  • 签订日期 ←                │    │  • 合同金额作为基准          │  │
│  │                              │    │                              │  │
│  └──────────────────────────────┘    └──────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          数据模型层（统一设计）                           │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     │
│  │   合同主体      │    │   发票主体      │    │   付款记录      │     │
│  │   Contract     │◄──►│   Invoice       │◄──►│   Payment      │     │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘     │
│           │                    │                    │                   │
│           └────────────────────┼────────────────────┘                   │
│                                │                                        │
│                                ▼                                        │
│                    ┌─────────────────────┐                             │
│                    │    统一对账引擎      │                             │
│                    │   Reconciliation    │                             │
│                    │   ReconciliationEngine   │                       │
│                    └─────────────────────┘                             │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 三、核心数据模型设计

### 3.1 合同实体（扩展财务字段）

```java
@Entity
@Table(name = "contracts")
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============ 基本信息 ============
    private String title;
    private String contractNumber;  // 合同编号（财务系统必需）
    private String contractType;
    private String status;  // draft/active/completed/terminated

    // ============ 签约方信息 ============
    @Column(name = "party_a_name")
    private String partyAName;  // 甲方（付款方/采购方）
    @Column(name = "party_a_code")
    private String partyACode;  // 甲方统一信用代码

    @Column(name = "party_b_name")
    private String partyBName;  // 乙方（收款方/供应商）
    @Column(name = "party_b_code")
    private String partyBCode;  // 乙方统一信用代码

    // ============ 金额相关（财务核心） ============
    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;  // 合同总金额

    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;  // 税率（如 0.06 表示6%）

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;  // 税额

    @Column(name = "tax_included")
    private Boolean taxIncluded;  // 是否含税

    @Column(name = "currency", length = 3)
    private String currency = "CNY";  // 币种

    @Column(name = "exchange_rate", precision = 10, scale = 6)
    private BigDecimal exchangeRate;  // 汇率（外币合同使用）

    // ============ 付款条件（财务核心） ============
    @Column(name = "payment_terms", columnDefinition = "json")
    private String paymentTerms;  // 付款条件JSON

    /*
    付款条件示例：
    {
      "type": "installment",  //一次性/installement分期
      "plan": [
        {"stage": "预付款", "percentage": 30, "condition": "合同签订后3工作日"},
        {"stage": "进度款", "percentage": 50, "condition": "交付验收合格后"},
        {"stage": "尾款", "percentage": 20, "condition": "质保期满后"}
      ]
    }
    */

    @Column(name = "payment_days")
    private Integer paymentDays;  // 账期天数

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;  // 支付方式（银行转账/票据等）

    // ============ 时间节点 ============
    private LocalDate signDate;  // 签订日期
    private LocalDate effectiveDate;  // 生效日期
    private LocalDate expirationDate;  // 到期日期

    // ============ 状态追踪 ============
    @Column(name = "total_paid", precision = 15, scale = 2)
    private BigDecimal totalPaid = BigDecimal.ZERO;  // 已付款总额

    @Column(name = "total_invoiced", precision = 15, scale = 2)
    private BigDecimal totalInvoiced = BigDecimal.ZERO;  // 已开票总额

    // ============ 文件关联 ============
    @Column(name = "original_file_path")
    private String originalFilePath;  // 原始文件路径

    @Column(name = "content_hash", length = 64)
    private String contentHash;  // 内容哈希（防篡改）

    // ============ AI提取数据 ============
    @Lob
    @Column(name = "ai_extracted_data", columnDefinition = "json")
    private String aiExtractedData;  // AI提取的结构化数据

    // ============ 时间戳 ============
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 3.2 发票实体

```java
@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============ 发票基本信息 ============
    @Column(name = "invoice_number", unique = true, nullable = false)
    private String invoiceNumber;  // 发票号（唯一）

    @Column(name = "invoice_type", length = 20)
    private String invoiceType;  // 增值税专用发票/普通发票

    @Column(name = "invoice_code", length = 12)
    private String invoiceCode;  // 发票代码

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;  // 开票日期

    // ============ 开票方信息 ============
    @Column(name = "issuer_name")
    private String issuerName;  // 开票方名称

    @Column(name = "issuer_tax_number")
    private String issuerTaxNumber;  // 开票方税号

    @Column(name = "issuer_bank")
    private String issuerBank;  // 开票方开户行

    @Column(name = "issuer_account")
    private String issuerAccount;  // 开票方账号

    // ============ 收票方信息 ============
    @Column(name = "receiver_name")
    private String receiverName;  // 收票方名称

    @Column(name = "receiver_tax_number")
    private String receiverTaxNumber;  // 收票方税号

    // ============ 金额相关（财务核心） ============
    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;  // 价税合计

    @Column(name = "net_amount", precision = 15, scale = 2)
    private BigDecimal netAmount;  // 不含税金额

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;  // 税额

    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;  // 税率

    // ============ 关联合同 ============
    @Column(name = "contract_id")
    private Long contractId;  // 关联合同ID

    @Column(name = "contract_number")
    private String contractNumber;  // 关联合同编号（冗余，便于查询）

    @Column(name = "contract_amount", precision = 15, scale = 2)
    private BigDecimal contractAmount;  // 合同金额（快照，对账基准）

    // ============ 校验结果 ============
    @Column(name = "validation_status", length = 20)
    private String validationStatus;  // pending/passed/warning/error

    @Column(name = "validation_message", length = 500)
    private String validationMessage;  // 校验消息

    @Lob
    @Column(name = "validation_details", columnDefinition = "json")
    private String validationDetails;  // 详细校验结果

    /*
    校验结果示例：
    {
      "contract_match": true,
      "amount_diff": 0.00,
      "tax_rate_match": true,
      "date_within_contract_period": true,
      "warnings": [],
      "errors": []
    }
    */

    // ============ 发票文件 ============
    @Column(name = "file_path")
    private String filePath;  // 发票扫描件路径

    @Column(name = "ocr_text", columnDefinition = "text")
    private String ocrText;  // OCR识别文本

    @Lob
    @Column(name = "ai_extracted_data", columnDefinition = "json")
    private String aiExtractedData;  // AI提取的数据

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 3.3 付款记录实体

```java
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "payment_number", unique = true)
    private String paymentNumber;  // 付款单号

    @Column(name = "payment_type", length = 20)
    private String paymentType;  // 预付款/进度款/尾款

    @Column(name = "payment_date")
    private LocalDate paymentDate;  // 计划/实际付款日期

    @Column(name = "planned_amount", precision = 15, scale = 2)
    private BigDecimal plannedAmount;  // 计划付款金额

    @Column(name = "actual_amount", precision = 15, scale = 2)
    private BigDecimal actualAmount;  // 实际付款金额

    @Column(name = "payment_status", length = 20)
    private String paymentStatus;  // pending/paid/overdue/partial

    @Column(name = "invoice_id")
    private Long invoiceId;  // 关联发票

    @Column(name = "paid_by")
    private String paidBy;  // 经办人

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;  // 支付方式

    @Column(name = "remarks", length = 500)
    private String remarks;  // 备注

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 3.4 对账记录实体

```java
@Entity
@Table(name = "reconciliation_records")
public class ReconciliationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "reconciliation_date")
    private LocalDate reconciliationDate;  // 对账日期

    @Column(name = "period_start")
    private LocalDate periodStart;  // 对账周期开始

    @Column(name = "period_end")
    private LocalDate periodEnd;  // 对账周期结束

    @Column(name = "contract_amount", precision = 15, scale = 2)
    private BigDecimal contractAmount;  // 合同金额

    @Column(name = "invoiced_amount", precision = 15, scale = 2)
    private BigDecimal invoicedAmount;  // 已开票金额

    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount;  // 已付款金额

    @Column(name = "pending_amount", precision = 15, scale = 2)
    private BigDecimal pendingAmount;  // 待付款金额

    @Column(name = "invoice_count")
    private Integer invoiceCount;  // 发票数量

    @Column(name = "payment_count")
    private Integer paymentCount;  // 付款次数

    @Column(name = "discrepancy_amount", precision = 15, scale = 2)
    private BigDecimal discrepancyAmount;  // 差异金额

    @Lob
    @Column(name = "discrepancy_details", columnDefinition = "json")
    private String discrepancyDetails;  // 差异详情

    @Lob
    @Column(name = "report_content", columnDefinition = "text")
    private String reportContent;  // 对账报告内容

    private LocalDateTime createdAt;
}
```

---

## 四、统一AI服务层设计

### 4.1 统一AI服务管理器

```java
@Service
public class AIOrchestratorService {

    @Autowired
    private DocumentParserService documentParserService;

    @Autowired
    private AIServiceManager aiServiceManager;

    /**
     * 统一文档解析入口
     * 支持：合同、发票、收据、凭证等
     */
    public ParseResult parseDocument(byte[] fileBytes, String fileType, DocumentContext context) {
        // 1. 文档解析
        String text = documentParserService.extractText(fileBytes, fileType);

        // 2. AI智能字段提取
        Map<String, Object> extractedData = extractFieldsByDocumentType(text, fileType, context);

        // 3. 数据校验
        ValidationResult validation = validateExtractedData(extractedData, fileType);

        return ParseResult.builder()
            .rawText(text)
            .extractedData(extractedData)
            .validation(validation)
            .build();
    }

    /**
     * 合同文档解析
     * 提取：金额、付款条件、税率、签约方等
     */
    public ContractExtractResult extractContractFields(String text, ContractContext context) {
        String prompt = buildContractExtractPrompt(text, context);
        String result = aiServiceManager.chat(prompt);

        // 解析JSON结果
        ContractExtractResult extractResult = parseContractResult(result);

        // 补充传统正则提取
        extractResult.mergeWithRegex(extractByRegex(text));

        return extractResult;
    }

    /**
     * 发票解析
     * 提取：发票号、金额、税率、开票日期等
     */
    public InvoiceExtractResult extractInvoiceFields(String text, InvoiceContext context) {
        String prompt = buildInvoiceExtractPrompt(text, context);
        String result = aiServiceManager.chat(prompt);

        return parseInvoiceResult(result);
    }

    /**
     * 合同与发票对齐校验
     */
    public AlignmentValidation validateContractInvoiceAlignment(
            ContractExtractResult contract,
            InvoiceExtractResult invoice) {
        // 金额校验
        boolean amountMatch = contract.getTotalAmount()
            .compareTo(invoice.getTotalAmount()) == 0;

        // 税率校验
        boolean taxRateMatch = contract.getTaxRate()
            .compareTo(invoice.getTaxRate()) == 0;

        // 金额精度校验（允许小额差异）
        BigDecimal diff = contract.getTotalAmount()
            .subtract(invoice.getTotalAmount()).abs();
        boolean amountWithinTolerance = diff.compareTo(new BigDecimal("0.01")) <= 0;

        return AlignmentValidation.builder()
            .amountMatch(amountMatch)
            .taxRateMatch(taxRateMatch)
            .amountWithinTolerance(amountWithinTolerance)
            .differenceAmount(diff)
            .build();
    }

    /**
     * 生成对账报告
     */
    public ReconciliationReport generateReconciliationReport(
            Contract contract,
            List<Invoice> invoices,
            List<Payment> payments) {
        // 调用AI生成分析报告
        String prompt = buildReconciliationPrompt(contract, invoices, payments);
        String report = aiServiceManager.chat(prompt);

        return ReconciliationReport.builder()
            .contractId(contract.getId())
            .invoices(invoices)
            .payments(payments)
            .aiAnalysis(report)
            .discrepancies(analyzeDiscrepancies(contract, invoices, payments))
            .build();
    }
}
```

### 4.2 文档类型枚举（支持扩展）

```java
public enum DocumentType {
    // 合同类
    CONTRACT_PURCHASE("采购合同", "purchase"),
    CONTRACT_SALES("销售合同", "sales"),
    CONTRACT_SERVICE("服务合同", "service"),
    CONTRACT_LEASE("租赁合同", "lease"),
    CONTRACT_LABOR("劳动合同", "labor"),

    // 财务类
    INVOICE_VAT_SPECIAL("增值税专用发票", "vat_special"),
    INVOICE_VAT_NORMAL("增值税普通发票", "vat_normal"),
    INVOICE_ELECTRONIC("电子发票", "electronic"),
    RECEIPT("收据", "receipt"),

    // 凭证类
    VOUCHER("记账凭证", "voucher"),
    BANK_SLIP("银行回单", "bank_slip"),

    // 其他
    OTHER("其他文档", "other");

    private final String description;
    private final String code;

    DocumentType(String description, String code) {
        this.description = description;
        this.code = code;
    }

    public boolean isContract() {
        return this.name().startsWith("CONTRACT_");
    }

    public boolean isInvoice() {
        return this.name().startsWith("INVOICE_");
    }

    public boolean isFinancial() {
        return isInvoice() || this == VOUCHER || this == BANK_SLIP || this == RECEIPT;
    }
}
```

---

## 五、模块边界与依赖关系

### 5.1 模块依赖图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              统一能力平台层                                │
│  (AIOrchestratorService, DocumentParserService, VectorStoreService)     │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ 依赖
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                             合同管理模块                                  │
│  ContractModule                                                       │
│  ├── ContractService                                                  │
│  ├── ContractAuditService                                             │
│  ├── ContractGenerationService                                        │
│  └── ContractKnowledgeBaseService                                     │
│                                                                         │
│  [输出]                                                                │
│  • 合同结构化数据（金额、付款条件、税率等）                              │
│  • 合同与条款向量数据                                                   │
│  • AI审核报告                                                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                        ┌───────────┴───────────┐
                        │ 共享数据               │
                        │ • 合同基础信息         │
                        │ • 金额结构             │
                        │ • 签约方信息           │
                        ▼                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                             财务管理模块                                  │
│  FinanceModule                                                        │
│  ├── InvoiceService                                                   │
│  ├── PaymentService                                                   │
│  ├── ReconciliationService                                            │
│  └── FinanceReportService                                             │
│                                                                         │
│  [输入]                                                                │
│  • 引用合同数据（金额基准、付款条件）                                    │
│  • 合同有效期验证                                                      │
│  • 签约方税号验证                                                      │
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.2 服务接口设计（跨模块调用）

```java
/**
 * 合同服务对外暴露的接口（供其他模块使用）
 */
public interface ContractServiceFacade {

    /**
     * 根据ID获取合同基本信息
     */
    ContractDTO getContractById(Long contractId);

    /**
     * 根据合同编号获取合同
     */
    ContractDTO getContractByNumber(String contractNumber);

    /**
     * 获取合同的财务相关信息（供财务模块使用）
     */
    ContractFinanceInfo getContractFinanceInfo(Long contractId);

    /**
     * 验证合同是否有效（供发票校验使用）
     */
    boolean isContractValid(Long contractId, LocalDate checkDate);

    /**
     * 获取合同的已付款总额
     */
    BigDecimal getTotalPaidAmount(Long contractId);

    /**
     * 获取合同的已开票总额
     */
    BigDecimal getTotalInvoicedAmount(Long contractId);
}

/**
 * 财务服务对外暴露的接口（供其他模块使用）
 */
public interface FinanceServiceFacade {

    /**
     * 校验发票与合同的匹配度
     */
    InvoiceValidationResult validateInvoiceWithContract(
        Long invoiceId, Long contractId);

    /**
     * 生成对账报告
     */
    ReconciliationReport generateReconciliation(Long contractId,
        LocalDate startDate, LocalDate endDate);

    /**
     * 获取合同的付款计划
     */
    List<PaymentPlan> getPaymentPlan(Long contractId);

    /**
     * 检查逾期付款
     */
    List<OverduePayment> checkOverduePayments();
}
```

---

## 六、数据库表结构规划

### 6.1 统一规划（预留扩展）

```sql
-- ============ 核心表 ============
contracts              -- 合同主表
invoices               -- 发票主表
payments               -- 付款记录表
reconciliation_records  -- 对账记录表

-- ============ 知识库表 ============
contract_clauses        -- 合同条款库
contract_templates      -- 合同模板库
risk_rules              -- 风险规则库
product_dictionaries    -- 产品字典

-- ============ 财务扩展表（预留） ============
expense_categories      -- 费用类别
budget_items            -- 预算科目
cost_allocations        -- 成本分摊
audit_trails           -- 审计追踪
```

### 6.2 表关系图

```
┌──────────────┐         ┌──────────────┐
│  contracts   │──────────│  invoices    │
│   合同主表    │    1:N   │   发票主表    │
└──────────────┘         └──────────────┘
       │                        │
       │ 1:N                    │ N:1
       ▼                        ▼
┌──────────────┐         ┌──────────────┐
│  payments    │──────────│reconciliation│
│   付款记录    │    1:N   │  对账记录    │
└──────────────┘         └──────────────┘
```

---

## 七、实施路径（分阶段）

### Phase 1：基础能力建设（1周）

```
目标：建立统一AI服务层
├── 实现 DocumentParserService
│   ├── PDF解析（Apache PDFBox）
│   ├── DOCX解析（Apache POI）
│   └── OCR识别（Qwen VL API）
│
├── 实现 AIOrchestratorService
│   ├── 统一文档解析入口
│   ├── 智能字段提取
│   └── 合同/发票解析
│
└── 实现 VectorStoreService
    ├── 向量存储（Milvus）
    └── 语义检索
```

### Phase 2：合同管理模块（1周）

```
目标：完整的合同管理功能
├── ContractService
│   ├── 合同上传与整理
│   ├── 合同条款拆分
│   ├── AI风险审核
│   └── 合同生成
│
├── ContractKnowledgeBaseService
│   ├── 条款知识库
│   ├── 模板管理
│   └── 风险规则库
│
└── 数据模型完善
    ├── 财务字段扩展
    └── 关联关系建立
```

### Phase 3：财务模块（1-2周）

```
目标：发票管理与对账功能
├── InvoiceService
│   ├── 发票上传与OCR
│   ├── 发票解析
│   ├── 合同-发票对齐
│   └── 异常预警
│
├── PaymentService
│   ├── 付款计划生成
│   ├── 付款记录管理
│   └── 逾期追踪
│
└── ReconciliationService
    ├── 对账引擎
    ├── 差异分析
    └── 报告生成
```

### Phase 4：智能分析（1周）

```
目标：AI驱动的财务分析
├── FinanceReportService
│   ├── 付款进度追踪
│   ├── 成本分析
│   └── 供应商评估
│
└── AuditService
    ├── 合规性检查
    ├── 税务校验
    └── 审计追踪
```

---

## 八、关键设计原则

### 8.1 松耦合设计
- 合同模块和财务模块通过接口交互
- 共享数据通过Service Facade暴露
- 避免直接依赖实体类

### 8.2 数据一致性
- 合同金额变更需要通知相关财务记录
- 使用事件驱动机制处理跨模块更新
- 保留数据变更审计追踪

### 8.3 可扩展性
- DocumentType枚举支持新增文档类型
- AI字段提取提示词模板化
- 风险规则库支持动态配置

### 8.4 财务准确性
- 金额计算使用BigDecimal，保留精度
- 支持多币种和汇率转换
- 发票与合同金额差异容忍度可配置

---

## 九、总结

### 架构优势

| 特性 | 实现方式 |
|------|----------|
| **统一AI能力** | AIOrchestratorService复用文档解析和字段提取 |
| **模块松耦合** | 通过Service Facade交互，不直接依赖 |
| **财务可扩展** | 合同实体预留财务字段，支持对账和发票关联 |
| **数据一致性** | 事件驱动 + 审计追踪 |
| **类型安全** | DocumentType枚举统一文档类型管理 |
| **配置灵活** | 风险规则、提示词模板可配置 |

### 扩展预留

✅ **财务模块扩展**：合同实体包含完整财务字段  
✅ **发票关联**：发票表关联合同ID，支持金额对比  
✅ **付款计划**：支持分期付款、条件付款  
✅ **对账引擎**：统一对账记录表，支持差异分析  
✅ **AI能力复用**：文档解析和字段提取组件可在财务模块复用  

---

**下一步行动建议：**

1. **确认架构方案**（当前完成）
2. **实现Phase 1：统一AI服务层**（约1周）
3. **实现Phase 2：合同管理模块**（约1周）
4. **实现Phase 3：财务模块**（约1-2周）

**是否现在开始实施Phase 1的基础能力层？**
