package com.jyfc.backend.module.marketing.service.importing;

import com.jyfc.backend.module.marketing.entity.ImportJob;
import com.jyfc.backend.module.marketing.repository.ImportJobRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExcelImportService {
    private final ImportJobRepository importJobRepository;
    private final ExcelImportHandlerRegistry handlerRegistry;
    private final ImportJobRunner importJobRunner;
    private final Path baseDir;

    public ExcelImportService(ImportJobRepository importJobRepository,
                              ExcelImportHandlerRegistry handlerRegistry,
                              ImportJobRunner importJobRunner) {
        this.importJobRepository = importJobRepository;
        this.handlerRegistry = handlerRegistry;
        this.importJobRunner = importJobRunner;
        this.baseDir = Paths.get(System.getProperty("user.dir"), "uploads", "imports");
    }

    public ExcelImportHandler getHandler(String module) {
        return handlerRegistry.get(module);
    }

    public Workbook buildTemplate(String module) {
        return getHandler(module).buildTemplateWorkbook();
    }

    public Map<String, Object> handlePreview(String module, MultipartFile file, Long operatorId) throws Exception {
        if (module == null) throw new IllegalArgumentException("module must not be null");
        if (file == null) throw new IllegalArgumentException("file must not be null");
        validateFile(file);
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "import.xlsx";
        String extension = getExtension(originalName);
        String storedName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
        Path storedPath = baseDir.resolve(storedName);
        try (InputStream in = file.getInputStream(); FileOutputStream out = new FileOutputStream(storedPath.toFile())) {
            in.transferTo(out);
        }
        try (InputStream in = new FileInputStream(storedPath.toFile());
             Workbook workbook = WorkbookFactory.create(in)) {
            ExcelImportHandler handler = getHandler(module);
            List<Map<String, Object>> preview = handler.parsePreview(workbook);
            ImportJob job = new ImportJob();
            job.setModule(module);
            job.setStatus("UPLOADED");
            job.setFileName(originalName);
            job.setFilePath(storedPath.toString());
            job.setOperatorId(operatorId);
            job.setContentType(file.getContentType());
            job.setFileSize(file.getSize());
            job.setCreatedAt(LocalDateTime.now());
            job.setTotalCount(preview.size());
            job.setSuccessCount(0);
            job.setFailureCount(0);
            importJobRepository.save(job);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jobId", job.getId());
            result.put("preview", preview);
            return result;
        }
    }

    /**
     * 提交异步导入。真正的执行在 {@link ImportJobRunner}（跨 bean 调用，事务代理才会生效）。
     * <p>此前这里是 {@code @Async} 方法自调用本类的 {@code @Transactional} 方法，
     * 两层代理同时失效：既没有异步事务，也没有导入事务。</p>
     */
    @Async("importTaskExecutor")
    public void startImportAsync(Long jobId) {
        importJobRunner.run(jobId);
    }

    private void validateFile(MultipartFile file) {
        if (file == null) throw new IllegalArgumentException("File must not be null");
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过10MB");
        }
        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        if (!".xlsx".equalsIgnoreCase(extension) && !".xls".equalsIgnoreCase(extension)) {
            throw new IllegalArgumentException("仅支持.xlsx 或.xls 文件");
        }
    }

    private String getExtension(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        if (i < 0) {
            return "";
        }
        return name.substring(i).toLowerCase(Locale.ROOT);
    }
}

