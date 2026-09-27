package com.jyfc.backend.module.userconfig.dto;

/**
 * 覆盖写单个配置条目的请求体。{@code hash} 为客户端计算的 contentJson SHA-256，服务端
 * 会重新校验；不一致则拒绝，避免半包/损坏内容落库。
 */
public record ConfigPutRequest(
        String contentJson,
        String hash,
        String clientType
) {}
