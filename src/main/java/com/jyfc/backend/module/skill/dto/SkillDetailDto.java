package com.jyfc.backend.module.skill.dto;

import java.util.List;

/**
 * skill 完整内容（下载 pull 用），含全部文件。
 */
public record SkillDetailDto(
        String slug,
        String name,
        String description,
        String version,
        String hash,
        List<SkillFilePayload> files
) {}
