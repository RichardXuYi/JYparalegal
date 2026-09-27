package com.jyfc.backend.module.skill.dto;

import java.util.List;

/**
 * 上传（push）单个 skill 的请求体。{@code hash} 为客户端计算的内容指纹，服务端会重新
 * 校验；不一致则拒绝，避免半包/损坏内容落库。
 */
public record SkillPushRequest(
        String name,
        String description,
        String version,
        String hash,
        List<SkillFilePayload> files
) {}
