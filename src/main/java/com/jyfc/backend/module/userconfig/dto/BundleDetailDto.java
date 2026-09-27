package com.jyfc.backend.module.userconfig.dto;

import java.util.List;

/**
 * bundle 完整内容（下载 pull 用），含全部文件。
 */
public record BundleDetailDto(
        String kind,
        String slug,
        String hash,
        String clientType,
        String updatedAt,
        List<BundleFilePayload> files
) {}
