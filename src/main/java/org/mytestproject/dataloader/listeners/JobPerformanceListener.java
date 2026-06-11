package org.mytestproject.dataloader.listeners;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class JobPerformanceListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("▶️ Batch Job '{}' initialized and started.", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("▶️ Batch Job '{}' finished with status: [{}]. Extracting performance metrics...",
                jobExecution.getJobInstance().getJobName(), jobExecution.getStatus());

        int totalRead = 0;
        int totalWritten = 0;
        int totalReadSkips = 0;
        int totalProcessSkips = 0;
        int totalWriteSkips = 0;

        // Aggregate performance metrics across all executed steps
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            totalRead += stepExecution.getReadCount();
            totalWritten += stepExecution.getWriteCount();
            totalReadSkips += stepExecution.getReadSkipCount();
            totalProcessSkips += stepExecution.getProcessSkipCount();
            totalWriteSkips += stepExecution.getWriteSkipCount();
        }

        // Calculate exact execution duration
        long durationMillis = 0;
        if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
            durationMillis = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
        }

        // Print the performance summary report
        log.info("""
                
                ========================================================================
                                      BATCH JOB PERFORMANCE SUMMARY
                ========================================================================
                Job Name          : {}
                Job Status        : {}
                Exit Status       : {}
                Duration          : {} ms
                ------------------------------------------------------------------------
                Total Rows Read   : {}
                Successfully Saved: {}
                ------------------------------------------------------------------------
                SKIPPED RECORD METRICS (Logged to skipped_records.log):
                  - Failed to Parse (Read Skips)   : {}
                  - Failed Validation (Process Skips): {}
                  - Database Constraints (Write Skips): {}
                ========================================================================
                """,
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus(),
                jobExecution.getExitStatus().getExitCode(),
                durationMillis,
                totalRead,
                totalWritten,
                totalReadSkips,
                totalProcessSkips,
                totalWriteSkips
        );
    }
}
