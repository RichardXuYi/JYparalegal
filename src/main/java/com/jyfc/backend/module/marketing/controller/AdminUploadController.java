package com.jyfc.backend.module.marketing.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminUploadController {
    
    @Value("${file.upload.path:./uploads}")
    private String uploadPath;
    
    // 允许的文件类型
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",  // 图片
        ".mp4", ".avi", ".mov", ".wmv", ".flv", ".mkv",   // 视频
        ".mp3", ".wav", ".ogg", ".flac",                    // 音频
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", // 文档
        ".txt", ".csv"                                       // 文本
    );
    
    // 最大文件大小 (50MB)
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    
    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> uploadFiles(
            @RequestParam(required = false, defaultValue = "misc") String type,
            @RequestParam("files") MultipartFile[] files) throws IOException {

        // 白名单校验 type 参数，防止路径穿越
        Set<String> allowedTypes = Set.of("misc", "news", "product", "avatar", "activity", "coupon");
        if (!allowedTypes.contains(type)) {
            throw new IllegalArgumentException("Invalid upload type: " + type);
        }

        // 创建上传目录
        Path uploadDir = Paths.get(uploadPath, type);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        
        List<String> fileUrls = new ArrayList<>();
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            
            // 检查文件大小
            if (file.getSize() > MAX_FILE_SIZE) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", true,
                    "message", "文件大小超出限制: " + file.getOriginalFilename()
                ));
            }
            
            // 检查文件类型
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase() 
                    : "";
                    
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", true,
                    "message", "不支持的文件类型: " + file.getOriginalFilename()
                ));
            }
            
            // 生成安全的唯一文件名
            String filename = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
            
            // 清理文件名中的特殊字符
            filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            
            // 保存文件
            Path filePath = uploadDir.resolve(filename);
            file.transferTo(java.util.Objects.requireNonNull(filePath.toFile()));
            
            // 返回访问路径
            String fileUrl = "/uploads/" + type + "/" + filename;
            fileUrls.add(fileUrl);
        }
        
        return ResponseEntity.ok(Map.of(
                "data", Map.of("urls", fileUrls),
                "message", "上传成功"
        ));
    }
}
