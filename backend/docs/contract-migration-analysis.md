# 合同管理功能移植架构分析方案

## 一、项目现状分析

### 1.1 contract-system (Python/FastAPI)
**核心组件：**
- `parser.py` - 文档解析引擎（1158行）
  - PDF解析：PyMuPDF（主）+ pypdf（fallback）
  - DOC/DOCX解析：python-docx
  - 图片OCR：Qwen VL模型
  - 条款拆分：正则匹配（第X条、X.X等）
  - 元数据提取：正则 + LLM增强
  - 中文分词：jieba

- `llm.py` - LLM调用封装
  - Qwen模型统一调用
  - 异步支持
  - 并发处理

- `embedding.py` - 向量化和检索
  - SentenceTransformer文本嵌入（768维）
  - Qdrant本地向量库（SQLite持久化）
  - 批量检索优化

- `audit.py` - 合同审核
  - 条款拆分
  - 风险规则匹配
  - LLM综合评估

- `generation.py` - 合同生成
  - 模板匹配
  - 变量提取和填充
  - LLM润色（可选）

**技术栈优势：**
- Python生态：PyMuPDF、pdfplumber、pytesseract等库成熟
- OCR能力：支持图片扫描件识别
- 向量库：Qdrant本地模式，零Docker部署
- LLM集成：Qwen VL统一模型（文本+视觉）

### 1.2 J-Y-Financial-Consulting (Java/Spring Boot)
**现有能力：**
- 多模型AI支持（OpenAI、DeepSeek、Qwen、SiliconFlow）
- 数据库：MySQL + Redis
- 数据库迁移：Flyway
- 文件处理：Apache POI（Excel）
- 已有Spring Security + JWT认证

**不足：**
- PDF解析能力有限（需要引入库）
- 没有向量库支持
- 没有专门的OCR服务
- 文档解析生态不如Python丰富

---

## 二、方案对比分析

### 2.1 方案A：纯Java实现

**思路：** 完全在Java中实现所有功能

**优势：**
- 单一技术栈，维护成本低
- 与现有项目无缝集成
- 代码风格统一

**劣势：**
- PDF解析库不如Python成熟（Apache PDFBox功能有限）
- OCR集成复杂（需要调用外部服务或本地Tesseract）
- 向量库需要自己实现或引入ML库
- 开发周期长，需要重新实现Python中的复杂逻辑

**技术挑战：**
1. **PDF解析**：需要支持文字PDF和扫描件PDF
2. **OCR识别**：需要集成OCR服务（Tesseract、百度OCR等）
3. **向量库**：需要引入embedding模型或调用外部服务
4. **条款拆分**：需要复杂的正则和NLP处理

**预估工期：** 4-6周（仅基础功能）

---

### 2.2 方案B：Python微服务架构

**思路：** 将文档处理、OCR、向量库等功能封装为Python微服务，Java项目通过HTTP API调用

**架构图：**
```
┌─────────────────────────────────────────────────────┐
│              J-Y-Financial-Consulting               │
│                  (Java Spring Boot)                │
├─────────────────────────────────────────────────────┤
│  Contract Module  │  Marketing Module  │  AI Module │
├─────────────────────────────────────────────────────┤
│              Business Logic & API Layer             │
└──────────────┬──────────────────────────────────────┘
               │ HTTP API (REST)
               ▼
┌─────────────────────────────────────────────────────┐
│            Document Processing Service               │
│              (Python FastAPI)                       │
├─────────────────────────────────────────────────────┤
│  Parser Service  │  OCR Service  │  Embedding Svc  │
│  - PDF解析      │  - 图片OCR    │  - 向量存储      │
│  - DOCX解析    │  - PDF OCR    │  - 语义检索      │
│  - 条款拆分    │  - 表格识别   │  - 批量处理      │
└─────────────────────────────────────────────────────┘
```

**优势：**
- 充分利用Python成熟的文档处理生态
- 微服务独立部署和扩展
- OCR和向量库能力强大
- 可以复用contract-system的现有代码
- 开发周期短（1-2周）

**劣势：**
- 增加了系统复杂度（两个服务）
- 需要维护服务间通信
- 部署和运维成本增加
- 技术栈不统一

**预估工期：** 1-2周（集成开发）

---

### 2.3 方案C：混合架构（推荐）

**思路：** 将通用功能组件化，Java和Python各司其职

**设计原则：**
1. **AI能力统一封装**：所有LLM调用通过统一的AI服务组件
2. **文档处理组件化**：PDF解析、OCR等作为独立服务
3. **业务逻辑在Java**：合同审核、生成、档案管理等业务逻辑保留在Java
4. **向量库独立**：嵌入向量库作为独立组件（可选用Qdrant）

**架构设计：**
```
┌──────────────────────────────────────────────────────────┐
│                   统一功能组件层                           │
├──────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │  AI服务     │  │  文档处理   │  │  向量库     │     │
│  │  组件       │  │  服务       │  │  组件       │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
│        │                │                │              │
│        ▼                ▼                ▼              │
│  ┌───────────┐    ┌───────────┐    ┌───────────┐       │
│  │  Qwen     │    │  PDF解析  │    │  Qdrant   │       │
│  │  DeepSeek │    │  OCR      │    │  Milvus   │       │
│  │  OpenAI   │    │  DOCX解析 │    │  Pinecone │       │
│  └───────────┘    └───────────┘    └───────────┘       │
└──────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────┐
│                    业务模块层                             │
├──────────────────────────────────────────────────────────┤
│  合同管理模块  │  营销模块  │  AI对话模块  │  新闻模块  │
├──────────────────────────────────────────────────────────┤
│  • 合同整理   │  • 活动    │  • 多模型    │  • 文章   │
│  • 合同审核   │  • 优惠券  │  • 对话历史  │  • 产品   │
│  • 合同生成   │  • 导入    │  • Token统计 │  • 订单   │
│  • 知识库     │            │              │           │
│  • 调档检索   │            │              │           │
└──────────────────────────────────────────────────────────┘
```

**核心组件设计：**

#### 1. AI服务组件（ai-service）
```java
@Service
public class AIServiceManager {
    // 统一的AI调用入口
    public String chat(String prompt, String systemPrompt);

    // 多模型支持
    public String chatWithModel(String model, String prompt);

    // 批量处理
    public List<String> batchChat(List<String> prompts);
}
```

#### 2. 文档处理服务（document-service）
```python
# Python FastAPI服务
@app.post("/parse/pdf")
def parse_pdf(file: UploadFile):
    # PDF文字提取 + OCR
    pass

@app.post("/parse/docx")
def parse_docx(file: UploadFile):
    # DOCX解析
    pass

@app.post("/ocr/image")
def ocr_image(file: UploadFile):
    # 图片OCR
    pass
```

#### 3. 向量库组件（vector-store）
```java
@Service
public class VectorStoreService {
    public void addDocuments(List<String> texts, List<Map<String, Object>> metadatas);
    public List<SearchResult> search(String query, int topK);
    public void deleteByIds(List<String> ids);
}
```

**优势：**
- ✓ 最佳技术选型（各技术栈发挥所长）
- ✓ 组件可复用（其他模块也能调用）
- ✓ 架构清晰，易于维护和扩展
- ✓ 开发周期适中（2-3周）

**劣势：**
- 需要维护多个组件
- 技术栈不完全统一

**预估工期：** 2-3周（集成开发）

---

## 三、重构建议

### 3.1 是否需要重构？

**结论：建议采用渐进式重构，而非完全重构**

**理由：**
1. 现有Java项目架构良好（模块化设计）
2. 已有AI服务管理组件
3. 只需添加新模块，无需改动核心
4. contract-system的Python代码可以复用

### 3.2 重构策略

**Phase 1：基础架构搭建（1周）**
- [ ] 引入Apache PDFBox（文字PDF解析）
- [ ] 集成OCR服务（优先使用阿里云OCR或腾讯OCR）
- [ ] 集成向量库（Qdrant或Milvus）
- [ ] 完善AI服务组件（支持批量调用）

**Phase 2：合同管理模块开发（1周）**
- [ ] 实现合同整理功能
- [ ] 实现合同审核功能
- [ ] 实现合同生成功能
- [ ] 实现知识库管理
- [ ] 实现调档检索

**Phase 3：优化和扩展（1周）**
- [ ] 添加缓存机制
- [ ] 性能优化
- [ ] 错误处理完善
- [ ] 监控和日志

### 3.3 具体实施建议

#### 1. PDF解析方案选择

**推荐方案：Apache PDFBox + OCR服务**

```java
@Service
public class PDFParserService {

    public String extractText(byte[] pdfBytes) {
        // 1. 尝试Apache PDFBox提取文字
        String text = pdfBoxExtractor.extract(pdfBytes);

        // 2. 如果文字少于50字，可能是扫描件，调用OCR
        if (text.length() < 50) {
            return ocrService.processPDF(pdfBytes);
        }

        return text;
    }
}
```

#### 2. OCR服务选择

**推荐方案：阿里云OCR或腾讯OCR**

```java
@Service
public class OCRService {

    public String processImage(byte[] imageBytes) {
        // 调用阿里云OCR
        return aliYunOCR.recognize(imageBytes);
    }

    public String processPDF(byte[] pdfBytes) {
        // 1. PDF转图片
        // 2. 逐页OCR
        return ocrService.batchRecognize(images);
    }
}
```

#### 3. 向量库选择

**推荐方案：Qdrant（轻量级本地向量库）**

- 零Docker本地模式可用
- 支持Python和Java客户端
- 性能优秀
- 易于部署

```java
@Service
public class VectorStoreService {

    public void initialize() {
        // 初始化Qdrant客户端
        // 连接到现有Qdrant实例或创建本地实例
    }

    public void addDocument(String text, Map<String, Object> metadata) {
        // 1. 文本嵌入
        List<Float> embedding = embeddingService.encode(text);

        // 2. 存储到向量库
        qdrantClient.upsert(collectionName, embedding, metadata);
    }

    public List<SearchResult> search(String query, int topK) {
        // 1. 查询嵌入
        List<Float> queryEmbedding = embeddingService.encode(query);

        // 2. 向量检索
        return qdrantClient.search(collectionName, queryEmbedding, topK);
    }
}
```

#### 4. LLM集成

**推荐方案：复用现有的QwenProvider**

```java
@Service
public class ContractAuditService {

    public String auditWithLLM(String contractText) {
        // 复用现有的AI服务
        String prompt = buildAuditPrompt(contractText);
        return aiServiceManager.chat(prompt);
    }
}
```

---

## 四、技术选型总结

| 功能模块 | 推荐技术 | 原因 |
|---------|---------|------|
| PDF解析 | Apache PDFBox + OCR | 文字PDF直接解析，扫描件OCR |
| DOCX解析 | Apache POI | 已在项目中使用 |
| OCR识别 | 阿里云OCR | 准确率高，支持多种格式 |
| 向量库 | Qdrant | 轻量级，支持本地模式 |
| 文本嵌入 | 阿里云Text Embedding | 托管服务，无需部署 |
| LLM调用 | 复用QwenProvider | 统一管理，已集成 |

---

## 五、风险评估

### 5.1 技术风险
- **PDF解析质量**：Apache PDFBox对复杂PDF支持可能不如PyMuPDF
  - **缓解**：结合OCR服务兜底
- **OCR准确性**：云服务OCR可能有误差
  - **缓解**：选择主流服务商，定期评估质量

### 5.2 性能风险
- **OCR耗时**：大批量扫描件处理可能慢
  - **缓解**：异步处理 + 队列
- **向量检索**：大批量数据可能慢
  - **缓解**：合理分片 + 缓存

### 5.3 运维风险
- **多服务依赖**：OCR服务、向量化服务故障影响业务
  - **缓解**：降级策略 + 监控告警

---

## 六、实施优先级

### P0（必须）
1. ✅ 数据库表结构（已完成）
2. ✅ 基础实体类（已完成）
3. ⬜ 合同整理功能
4. ⬜ 合同审核功能

### P1（重要）
5. ⬜ 合同生成功能
6. ⬜ 知识库管理
7. ⬜ 调档检索

### P2（优化）
8. ⬜ 性能优化
9. ⬜ 缓存机制
10. ⬜ 监控告警

---

## 七、总结

**推荐采用方案C（混合架构）：**

1. **保持Java主项目架构稳定**
   - 业务逻辑在Java
   - API接口在Java
   - 数据库操作在Java

2. **引入必要的Java库**
   - Apache PDFBox（PDF解析）
   - Qdrant客户端（向量库）

3. **集成云服务**
   - 阿里云OCR（图片/扫描件识别）
   - 阿里云Text Embedding（文本向量化）

4. **复用现有AI服务**
   - 复用QwenProvider进行LLM调用

**预期收益：**
- ✓ 开发周期短（2-3周）
- ✓ 技术风险可控
- ✓ 架构清晰，易于维护
- ✓ 组件可复用
- ✓ 性能满足需求

---

## 下一步行动

1. **确认方案**：请确认采用方案C
2. **技术选型确认**：确认OCR服务商和向量库方案
3. **开始实施**：按Phase顺序开发
4. **定期评审**：每周评审进度和风险
