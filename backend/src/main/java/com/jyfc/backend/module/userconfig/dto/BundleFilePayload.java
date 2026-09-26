package com.jyfc.backend.module.userconfig.dto;

/**
 * bundle 内的单个文件。{@code path} 为相对 bundle 根目录的正斜杠路径；{@code contentBase64}
 * 为文件原始字节的 Base64 编码（对文本/二进制均无损）。
 */
public record BundleFilePayload(
        String path,
        String contentBase64
) {}
