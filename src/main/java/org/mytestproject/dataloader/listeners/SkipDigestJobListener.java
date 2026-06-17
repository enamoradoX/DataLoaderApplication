package org.mytestproject.dataloader.listeners;

import org.mytestproject.dataloader.entities.SkippedRecord;
import org.mytestproject.dataloader.services.EmailNotificationService;
import org.mytestproject.dataloader.services.SkippedRecordService;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * At the end of any job, sends one digest email for everything skipped under that run (loadId =
 * JobExecution id). Shared by the employee and department jobs so both report skips the same way.
 */
@Component
public class SkipDigestJobListener implements JobExecutionListener {

    private final SkippedRecordService skippedRecordService;
    private final EmailNotificationService emailNotificationService;

    public SkipDigestJobListener(SkippedRecordService skippedRecordService,
                                 EmailNotificationService emailNotificationService) {
        this.skippedRecordService = skippedRecordService;
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String loadId = String.valueOf(jobExecution.getId());
        List<SkippedRecord> skips = skippedRecordService.findByLoad(loadId);
        if (!skips.isEmpty()) {
            emailNotificationService.sendLoadDigest(loadId, skips);
        }
    }
}
