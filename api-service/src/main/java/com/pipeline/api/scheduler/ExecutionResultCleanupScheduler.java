package com.pipeline.api.scheduler;

import com.pipeline.api.config.ResultsRetentionProperties;
import com.pipeline.api.repository.ExecutionResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class ExecutionResultCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExecutionResultCleanupScheduler.class);

    private final ExecutionResultRepository repository;
    private final ResultsRetentionProperties retentionProperties;

    public ExecutionResultCleanupScheduler(
            ExecutionResultRepository repository,
            ResultsRetentionProperties retentionProperties) {
        this.repository = repository;
        this.retentionProperties = retentionProperties;
    }

    @Scheduled(cron = "${codestream.results.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void purgeExpiredResults() {
        int retentionDays = Math.max(1, retentionProperties.getRetentionDays());
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = repository.deleteByExecutedAtBefore(cutoff);

        if (deleted > 0) {
            log.info("Purged {} execution result(s) older than {} days (before {})",
                    deleted, retentionDays, cutoff);
        }
    }
}
