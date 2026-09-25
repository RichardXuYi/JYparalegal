package com.jyfc.backend.module.signdoc.service;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.audit.entity.DataAccessLogEntity;
import com.jyfc.backend.module.audit.repository.DataAccessLogRepository;
import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import com.jyfc.backend.module.sign.repository.SignTaskRepository;
import com.jyfc.backend.module.signdoc.entity.DocFileEntity;
import com.jyfc.backend.module.signdoc.entity.SignDocEntity;
import com.jyfc.backend.module.signdoc.repository.DocFileRepository;
import com.jyfc.backend.module.signdoc.repository.SignDocRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 签署文档上传服务（文档上传/从文件建任务切片）。
 * storeUpload：base64 解码 → 落盘至数据目录（-Djy.data.dir，默认 ./data/sign-docs）→ 计算 sha256（同 SignFlowService 摘要口径）
 * → 先建 doc_file（sign_doc.doc_file_id NOT NULL 外键，V132）再挂 sign_doc → 写 UPLOAD 数据访问留痕（V134）。
 * createTaskFromFile：从上传文件直接建 DRAFT 任务（taskNo=T+uuid-hex，signMode=PARALLEL）再挂文档。
 * 授权：管理域口径，仅同租户（taskForActor 的简化：same tenant required，D12 应用层强制）。
 */
@Service
public class SignDocService {

    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final int MAX_FILES = 50;
    private static final Set<String> ALLOWED_EXT = Set.of(
            "doc", "docx", "wps", "pdf", "xls", "xlsx", "jpg", "jpeg", "bmp", "png", "rtf");

    private final SignTaskRepository taskRepository;
    private final SignDocRepository docRepository;
    private final DocFileRepository docFileRepository;
    private final DataAccessLogRepository dataAccessLogRepository;

    public SignDocService(SignTaskRepository taskRepository, SignDocRepository docRepository,
                          DocFileRepository docFileRepository, DataAccessLogRepository dataAccessLogRepository) {
        this.taskRepository = taskRepository;
        this.docRepository = docRepository;
        this.docFileRepository = docFileRepository;
        this.dataAccessLogRepository = dataAccessLogRepository;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    /** 管理域读取：仅本租户（same tenant required）。 */
    private SignTaskEntity task(Long id) {
        return taskRepository.findByIdAndTenantId(id, tenant())
                .orElseThrow(() -> new BusinessException("任务不存在或跨租户: " + id));
    }

    /** 制作台文件列表：带原始文件名与大小。 */
    public List<Map<String, Object>> listUploads(Long taskId) {
        task(taskId);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (SignDocEntity d : docRepository.findAllByTaskIdOrderByIdAsc(taskId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("sha256", d.getSha256());
            row.put("source", d.getSource());
            docFileRepository.findById(d.getDocFileId()).ifPresent(f -> {
                row.put("fileName", f.getFileName());
                row.put("sizeBytes", f.getSizeBytes());
            });
            out.add(row);
        }
        return out;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 只读守卫（bizdocs 聚合视图用）：任务不存在或跨租户即抛。 */
    public SignTaskEntity requireTaskAccessible(Long id) {
        return task(id);
    }

    /**
     * 取任务下第一份签署文档的文件名与 base64（供送签上传 e签宝）；无文档返回 null。
     * 仅用于送签时把文档内容交给 CP 上传，不做额外落盘。
     */
    public Map<String, Object> primaryDocForUpload(Long taskId) {
        task(taskId);
        return docRepository.findAllByTaskIdOrderByIdAsc(taskId).stream().findFirst().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            String name = d.getDocFileId() == null ? null
                    : docFileRepository.findById(d.getDocFileId()).map(DocFileEntity::getFileName).orElse(null);
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(Paths.get(d.getFilePath()));
            } catch (Exception e) {
                throw new BusinessException("读取签署文档失败: " + e.getMessage());
            }
            m.put("fileName", name != null ? name : "contract");
            m.put("contentBase64", Base64.getEncoder().encodeToString(bytes));
            return m;
        }).orElse(null);
    }

    @Transactional
    public SignDocEntity storeUpload(Long taskId, Long actorId, String fileName, String contentBase64) {
        SignTaskEntity t = task(taskId);
        if (fileName == null || fileName.isBlank()) throw new BusinessException("文件名不能为空");
        if (contentBase64 == null || contentBase64.isBlank()) throw new BusinessException("文件内容不能为空");
        String ext = extensionOf(fileName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 doc、docx、wps、pdf、xls、xlsx、jpg、jpeg、bmp、png、rtf");
        }
        if (docRepository.findAllByTaskIdOrderByIdAsc(t.getId()).size() >= MAX_FILES) {
            throw new BusinessException("单个任务最多 50 个文件");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("contentBase64 不是合法 base64");
        }
        if (bytes.length > MAX_BYTES) throw new BusinessException("单个文件不能超过 50MB");
        String digest = sha256(bytes);
        try {
            Path dir = Paths.get(System.getProperty("jy.data.dir", "./data/sign-docs"));
            Files.createDirectories(dir);
            String safeName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            String storageKey = t.getId() + "/" + UUID.randomUUID().toString().replace("-", "") + "-" + safeName;
            Path target = dir.resolve(storageKey);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);

            DocFileEntity f = new DocFileEntity();
            f.setStorageKey(storageKey);
            f.setFileName(fileName);
            f.setSizeBytes((long) bytes.length);
            f.setSha256(digest);
            f.setOwnerUserId(actorId);
            docFileRepository.save(f);

            SignDocEntity d = new SignDocEntity();
            d.setTaskId(t.getId());
            d.setDocFileId(f.getId());
            d.setEvidenceStatus("NONE");
            d.setSha256(digest);
            d.setFilePath(target.toAbsolutePath().toString());
            d.setSource("UPLOAD");
            d.setCreatedBy(actorId);
            docRepository.save(d);

            DataAccessLogEntity log = new DataAccessLogEntity();
            log.setActorType("USER");
            log.setActorId(actorId);
            log.setEntityType("sign_doc");
            log.setEntityId(d.getId());
            log.setAccessType("UPLOAD");
            dataAccessLogRepository.save(log);
            return d;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件落盘失败: " + e.getMessage());
        }
    }

    @Transactional
    public Long createTaskFromFile(String title, Long companyId, String fileName, String contentBase64, Long actorId) {
        Long tenantId = tenant();
        SignTaskEntity t = new SignTaskEntity();
        t.setTenantId(tenantId); // 显式注入（与 listener 双保险）
        t.setTitle(title == null || title.isBlank() ? fileName : title);
        t.setCompanyId(companyId);
        t.setTaskNo("T" + UUID.randomUUID().toString().replace("-", ""));
        t.setStatus("DRAFT");
        t.setSignMode("PARALLEL");
        t.setSource("WEB");
        t.setCreatedBy(actorId);
        SignTaskEntity saved = taskRepository.saveAndFlush(t); // 强制 insert 以取得 IDENTITY 主键，再挂文档
        storeUpload(saved.getId(), actorId, fileName, contentBase64);
        return saved.getId();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
