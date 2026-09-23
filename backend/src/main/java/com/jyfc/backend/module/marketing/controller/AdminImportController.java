package com.jyfc.backend.module.marketing.controller;

import com.jyfc.backend.module.marketing.entity.ImportJob;
import com.jyfc.backend.module.marketing.repository.ImportJobRepository;
import com.jyfc.backend.module.marketing.service.importing.ExcelImportService;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@RestController
@RequestMapping("/api/admin/import")
public class AdminImportController {
    private final ExcelImportService excelImportService;
    private final ImportJobRepository importJobRepository;

    public AdminImportController(ExcelImportService excelImportService, ImportJobRepository importJobRepository) {
        this.excelImportService = excelImportService;
        this.importJobRepository = importJobRepository;
    }

    @GetMapping("/template/{module}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_EMPLOYEE')")
    public ResponseEntity<InputStreamResource> downloadTemplate(@PathVariable String module) throws Exception {
        Workbook workbook = excelImportService.buildTemplate(module);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (workbook) {
            workbook.write(out);
        }
        String filename = module + "-template.xlsx";
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        InputStreamResource resource = new InputStreamResource(in);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return ResponseEntity.ok().headers(headers).contentLength(out.size()).contentType(MediaType.APPLICATION_OCTET_STREAM).body(resource);
    }

    @PostMapping("/preview/{module}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_EMPLOYEE')")
    public ResponseEntity<?> preview(@PathVariable String module, @RequestParam("file") MultipartFile file, @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        try {
            Map<String, Object> data = excelImportService.handlePreview(module, file, operatorId);
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "预览失败", "detail", ex.getMessage()));
        }
    }

    @PostMapping("/start/{jobId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_EMPLOYEE')")
    public ResponseEntity<?> start(@PathVariable Long jobId) {
        Optional<ImportJob> jobOpt = importJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ImportJob job = jobOpt.get();
        if (!"UPLOADED".equals(job.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "当前状态不允许开始导入"));
        }
        excelImportService.startImportAsync(jobId);
        return ResponseEntity.ok(Map.of("jobId", jobId));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_EMPLOYEE')")
    public ResponseEntity<?> jobDetail(@PathVariable Long jobId) {
        Optional<ImportJob> jobOpt = importJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ImportJob job = jobOpt.get();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Map<String, Object> body = new HashMap<>();
        body.put("id", job.getId());
        body.put("module", job.getModule());
        body.put("status", job.getStatus());
        body.put("fileName", job.getFileName());
        body.put("totalCount", job.getTotalCount());
        body.put("successCount", job.getSuccessCount());
        body.put("failureCount", job.getFailureCount());
        body.put("errorSummary", job.getErrorSummary());
        body.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().format(fmt) : null);
        body.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().format(fmt) : null);
        body.put("finishedAt", job.getFinishedAt() != null ? job.getFinishedAt().format(fmt) : null);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_EMPLOYEE')")
    public ResponseEntity<?> listJobs(
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        int pageNum = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(size, 100));
        PageRequest pageable = PageRequest.of(pageNum - 1, pageSize);
        Page<ImportJob> p;
        if (module != null && !module.isBlank()) {
            p = importJobRepository.findByModuleOrderByCreatedAtDesc(module, pageable);
        } else {
            p = importJobRepository.findAll(pageable);
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> rows = p.getContent().stream().map(job -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", job.getId());
            m.put("module", job.getModule());
            m.put("status", job.getStatus());
            m.put("fileName", job.getFileName());
            m.put("totalCount", job.getTotalCount());
            m.put("successCount", job.getSuccessCount());
            m.put("failureCount", job.getFailureCount());
            m.put("errorSummary", job.getErrorSummary());
            m.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().format(fmt) : null);
            m.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().format(fmt) : null);
            m.put("finishedAt", job.getFinishedAt() != null ? job.getFinishedAt().format(fmt) : null);
            return m;
        }).toList();
        Map<String, Object> body = new HashMap<>();
        body.put("data", rows);
        body.put("total", p.getTotalElements());
        body.put("page", pageNum);
        body.put("size", pageSize);
        return ResponseEntity.ok(body);
    }
}
