package com.jyfc.backend.module.marketing.service.importing;

import com.jyfc.backend.module.marketing.entity.ImportJob;
import com.jyfc.backend.module.marketing.repository.ImportJobRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/** 真正执行导入。单独成 bean，异步调用时事务才会生效。 */
@Service
public class ImportJobRunner {

    private final ImportJobRepository jobs;
    private final ExcelImportHandlerRegistry handlers;

    public ImportJobRunner(ImportJobRepository jobs, ExcelImportHandlerRegistry handlers) {
        this.jobs = jobs;
        this.handlers = handlers;
    }

    @Transactional
    public void run(Long jobId) {
        ImportJob job = jobs.findById(jobId).orElse(null);
        if (job == null) return;
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        try (InputStream in = Files.newInputStream(Path.of(job.getFilePath()));
             Workbook workbook = WorkbookFactory.create(in)) {
            ExcelImportResult result = handlers.get(job.getModule()).importWorkbook(workbook, job.getOperatorId());
            job.setTotalCount(result.getTotalCount());
            job.setSuccessCount(result.getSuccessCount());
            job.setFailureCount(result.getFailureCount());
            String summary = result.getErrors().stream().limit(8).collect(Collectors.joining("；"));
            if (summary.length() > 1000) summary = summary.substring(0, 1000);
            job.setErrorSummary(summary.isBlank() ? null : summary);
            job.setStatus(result.getFailureCount() > 0 && result.getSuccessCount() == 0 ? "FAILED" : "DONE");
        } catch (Exception ex) {
            job.setStatus("FAILED");
            String message = ex.getMessage() == null ? "导入失败" : ex.getMessage();
            job.setErrorSummary(message.length() > 1000 ? message.substring(0, 1000) : message);
        }
        job.setFinishedAt(LocalDateTime.now());
        jobs.save(job);
    }
}
