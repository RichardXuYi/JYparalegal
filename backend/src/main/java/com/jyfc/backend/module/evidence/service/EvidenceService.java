package com.jyfc.backend.module.evidence.service;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.module.evidence.entity.EvidenceItemEntity;
import com.jyfc.backend.module.evidence.repository.EvidenceItemRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * 证据服务（V141）。list 支持按 bizType/bizId 可选过滤（二者齐备走专用查询，否则租户全量后内存过滤）；
 * create 在提供 content 时计算 sha256 固化指纹；setStatus 仅允许 UNVERIFIED/VERIFIED/REJECTED。
 */
@Service
public class EvidenceService {

    private static final Set<String> ALLOWED_STATUS = Set.of("UNVERIFIED", "VERIFIED", "REJECTED");

    private final EvidenceItemRepository repository;

    public EvidenceService(EvidenceItemRepository repository) {
        this.repository = repository;
    }

    public List<EvidenceItemEntity> list(Long tenantId, String bizType, Long bizId) {
        if (bizType != null && !bizType.isBlank() && bizId != null) {
            return repository.findAllByTenantIdAndBizTypeAndBizIdOrderByIdDesc(tenantId, bizType, bizId);
        }
        List<EvidenceItemEntity> all = repository.findAllByTenantIdOrderByIdDesc(tenantId);
        return all.stream()
                .filter(e -> bizType == null || bizType.isBlank() || bizType.equals(e.getBizType()))
                .filter(e -> bizId == null || bizId.equals(e.getBizId()))
                .toList();
    }

    public EvidenceItemEntity get(Long tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("证据不存在或跨租户"));
    }

    public EvidenceItemEntity create(Long tenantId, Long createdBy, String bizType, Long bizId, String name,
                                     String evidenceType, String source, String filePath, String note,
                                     String content) {
        if (bizType == null || bizType.isBlank()) throw new BusinessException("bizType 不能为空");
        if (name == null || name.isBlank()) throw new BusinessException("证据名称不能为空");
        EvidenceItemEntity e = new EvidenceItemEntity();
        e.setTenantId(tenantId);
        e.setCreatedBy(createdBy);
        e.setBizType(bizType);
        e.setBizId(bizId);
        e.setName(name);
        e.setEvidenceType(evidenceType);
        e.setSource(source);
        e.setFilePath(filePath);
        e.setNote(note);
        e.setStatus("UNVERIFIED");
        if (content != null) {
            e.setSha256(sha256(content));
        }
        return repository.save(e);
    }

    public EvidenceItemEntity setStatus(Long tenantId, Long id, String status) {
        if (status == null || !ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("非法证据状态: " + status);
        }
        EvidenceItemEntity e = get(tenantId, id);
        e.setStatus(status);
        return repository.save(e);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException("SHA-256 计算失败");
        }
    }
}
