package com.jyfc.backend.module.userconfig.dto;

/**
 * bundle 元数据摘要（列表用，不含文件内容）。
 */
public record BundleSummaryDto(
        String kind,
        String slug,
        String hash,
        long sizeBytes,
        int fileCount,
        String clientType,
        String updatedAt
) {}
