package com.jyfc.backend.module.marketing.service.importing;

import com.jyfc.backend.module.marketing.entity.ImportJob;
import com.jyfc.backend.module.marketing.repository.ImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 异步导入任务的编排：状态流转在此，数据落库交给 {@link ImportTxExecutor}。
 *
 * <p>本类<b>刻意不加</b> {@code @Transactional}：状态标记必须独立于导入事务落库，
 * 否则导入回滚会把 FAILED 一并抹掉，任务永远停在 RUNNING。</p>
 */
@Service
public class ImportJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportJobRunner.class);

    private final ImportJobRepository importJobRepository;
    private final ExcelImportHandlerRegistry handlerRegistry;
    private final ImportTxExecutor txExecutor;

    public ImportJobRunner(ImportJobRepository importJobRepository,
                           ExcelImportHandlerRegistry handlerRegistry,
                           ImportTxExecutor txExecutor) {
        this.importJobRepository = importJobRepository;
        this.handlerRegistry = handlerRegistry;
        this.txExecutor = txExecutor;
    }

    public void run(Long jobId) {
        if (jobId == null) {
            return;
        }
        Optional<ImportJob> jobOpt = importJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("导入任务不存在: jobId={}", jobId);
            return;
        }
        ImportJob job = jobOpt.get();
        if (!Objects.equals(job.getStatus(), "UPLOADED")) {
            log.info("导入任务非 UPLOADED 状态，跳过: jobId={} status={}", jobId, job.getStatus());
            return;
        }

        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        importJobRepository.save(job);
        log.info("导入任务开始: jobId={} module={} operator={}", jobId, job.getModule(), job.getOperatorId());

        File file = new File(job.getFilePath());
        if (!file.exists()) {
            markFailed(job, "源文件不存在");
            return;
        }

        try {
            ExcelImportResult result = txExecutor.importFile(
                    handlerRegistry.get(job.getModule()), file, job.getOperatorId());
            job.setStatus("SUCCESS");
            job.setTotalCount(result.getTotalCount());
            job.setSuccessCount(result.getSuccessCount());
            job.setFailureCount(result.getFailureCount());
            job.setErrorSummary(String.join(" | ",
                    result.getErrors().size() > 20 ? result.getErrors().subList(0, 20) : result.getErrors()));
            job.setFinishedAt(LocalDateTime.now());
            importJobRepository.save(job);
            log.info("导入任务成功: jobId={} total={} success={} failure={}",
                    jobId, result.getTotalCount(), result.getSuccessCount(), result.getFailureCount());
        } catch (Exception e) {
            log.error("导入任务失败: jobId={} module={}", jobId, job.getModule(), e);
            markFailed(job, e.getMessage());
        }
    }

    private void markFailed(ImportJob job, String reason) {
        job.setStatus("FAILED");
        job.setErrorSummary(reason);
        job.setFinishedAt(LocalDateTime.now());
        importJobRepository.save(job);
        log.warn("导入任务标记为失败: jobId={} reason={}", job.getId(), reason);
    }
}
