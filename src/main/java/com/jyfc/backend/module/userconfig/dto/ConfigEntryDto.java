package com.jyfc.backend.module.userconfig.dto;

/**
 * 用户配置 KV 条目（读写共用）。{@code contentJson} 为 JSON 文本，服务端不解析语义。
 */
public record ConfigEntryDto(
        String namespace,
        String key,
        String contentJson,
        String hash,
        long version,
        String clientType,
        String updatedAt
) {}
