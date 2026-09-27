package com.jyfc.backend.module.userconfig.dto;

import java.util.List;

/**
 * 上传（push）单个 bundle 的请求体。{@code hash} 为客户端计算的内容指纹（算法与
 * skill 同步一致），服务端会重新校验；不一致则拒绝，避免半包/损坏内容落库。
 */
public record BundlePushRequest(
        String hash,
        String clientType,
        List<BundleFilePayload> files
) {}
