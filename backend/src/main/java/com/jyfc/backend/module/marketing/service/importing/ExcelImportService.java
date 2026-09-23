package com.jyfc.backend.module.marketing.service.importing;

import com.jyfc.backend.module.marketing.entity.ImportJob;
import com.jyfc.backend.module.marketing.repository.ImportJobRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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
    private final Map<String, ExcelImportHandler> handlers = new HashMap<>();
    private final Path baseDir;

    public ExcelImportService(ImportJobRepository importJobRepository, ObjectProvider<ExcelImportHandler> handlerProvider) {
        this.importJobRepository = importJobRepository;
        handlerProvider.forEach(h -> handlers.put(h.getModule(), h));
        this.baseDir = Paths.get(System.getProperty("user.dir"), "uploads", "imports");
    }

    public ExcelImportHandler getHandler(String module) {
        ExcelImportHandler handler = handlers.get(module);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported module: " + module);
        }
        return handler;
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

    @Async("importTaskExecutor")
    public void startImportAsync(Long jobId) {
        runImport(jobId);
    }

    @Transactional
    public void runImport(Long jobId) {
        if (jobId == null) return;
        Optional<ImportJob> jobOpt = importJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return;
        }
        ImportJob job = jobOpt.get();
        if (!Objects.equals(job.getStatus(), "UPLOADED")) {
            return;
        }
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        importJobRepository.save(job);
        File file = new File(job.getFilePath());
        if (!file.exists()) {
            job.setStatus("FAILED");
            job.setErrorSummary("源文件不存在");
            job.setFinishedAt(LocalDateTime.now());
            importJobRepository.save(job);
            return;
        }
        try (InputStream in = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(in)) {
            ExcelImportHandler handler = getHandler(job.getModule());
            ExcelImportResult result = handler.importWorkbook(workbook, job.getOperatorId());
            job.setStatus("SUCCESS");
            job.setTotalCount(result.getTotalCount());
            job.setSuccessCount(result.getSuccessCount());
            job.setFailureCount(result.getFailureCount());
            String summary = String.join(" | ", result.getErrors().size() > 20 ? result.getErrors().subList(0, 20) : result.getErrors());
            job.setErrorSummary(summary);
            job.setFinishedAt(LocalDateTime.now());
            importJobRepository.save(job);
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorSummary(e.getMessage());
            job.setFinishedAt(LocalDateTime.now());
            importJobRepository.save(job);
        }
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

