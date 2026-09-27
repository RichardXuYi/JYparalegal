package com.jyfc.backend.module.skill.dto;

/**
 * skill 元数据摘要（列表用，不含文件内容）。
 */
public record SkillSummaryDto(
        String slug,
        String name,
        String description,
        String version,
        String hash,
        long sizeBytes,
        int fileCount,
        String updatedAt
) {}
