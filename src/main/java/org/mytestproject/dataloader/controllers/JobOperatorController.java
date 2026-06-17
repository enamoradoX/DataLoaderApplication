package org.mytestproject.dataloader.controllers;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.models.JobSummary;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController()
@RequestMapping("/api/batch")
@Slf4j
public class JobOperatorController {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job employeeLoaderJob; // Directly inject the Job bean

    // Spring injects every Job bean keyed by its bean name (e.g. "employeeLoaderJob",
    // "departmentLoaderJob"), which lets us start any job by name.
    private final Map<String, Job> jobs;

    public JobOperatorController(JobOperator jobOperator,
                              JobRepository jobRepository,
                              @Qualifier("employeeLoaderJob") Job employeeLoaderJob,
                              Map<String, Job> jobs) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.employeeLoaderJob = employeeLoaderJob;
        this.jobs = jobs;
    }

    @PostMapping("/start")
    public String startJob() throws Exception {
        // Build strongly typed JobParameters
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // Execute using the new Job-based signature
        JobExecution execution = jobOperator.start(employeeLoaderJob, jobParameters);
        return "Job started cleanly! Execution ID: " + execution.getId();
    }

    /**
     * Starts any registered job by its bean name, e.g. POST /api/batch/start/departmentLoaderJob
     * or /api/batch/start/employeeLoaderJob. A fresh "time" parameter makes each run a new
     * JobInstance so it can be re-run.
     */
    @PostMapping("/start/{jobName}")
    public String startJobByName(@PathVariable String jobName) throws Exception {
        Job job = jobs.get(jobName);
        if (job == null) {
            return "No job named '" + jobName + "'. Available jobs: " + jobs.keySet();
        }

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(job, jobParameters);
        return "Job '" + jobName + "' started. Execution ID: " + execution.getId();
    }

    @PostMapping("/stop/{executionId}")
    public String stopJob(@PathVariable Long executionId) throws Exception {
        // Fetch the target JobExecution object from the repository first
        JobExecution jobExecution = jobRepository.getJobExecution(executionId);

        if (jobExecution == null) {
            return "Cannot stop. No job execution found for ID: " + executionId;
        }

        // Pass the full execution record to the new stop signature
        boolean stopSignalSent = jobOperator.stop(jobExecution);
        return stopSignalSent ? "Stop signal sent to execution context." : "Failed to trigger stop.";
    }

    @GetMapping("/summary/{executionId}")
    public ResponseEntity<JobSummary> getJobSummary(@PathVariable Long executionId) {
        JobExecution execution = jobRepository.getJobExecution(executionId);
        if (execution == null) {
            return ResponseEntity.status(404).body(new JobSummary(executionId, "NOT_FOUND", 0, 0, 0,
                    "No job execution found for id " + executionId + "."));
        }

        // Full technical detail (incl. the exit-status stack trace) goes to the logs for
        // developers/maintainers — never to the end user. Enable DEBUG to see it on a status check;
        // an actual job failure is also error-logged by Spring Batch when it happens.
        log.debug("Job execution {} full detail: {}", executionId, execution);

        long rowsRead = 0, rowsWritten = 0, rowsSkipped = 0;
        for (StepExecution step : execution.getStepExecutions()) {
            rowsRead += step.getReadCount();
            rowsWritten += step.getWriteCount();
            rowsSkipped += step.getSkipCount();
        }

        String status = execution.getStatus().toString();
        String message = switch (status) {
            case "COMPLETED" -> String.format("Load completed: %d read, %d saved, %d skipped.",
                    rowsRead, rowsWritten, rowsSkipped);
            case "STARTING", "STARTED" -> "Load is still running…";
            case "STOPPING", "STOPPED" -> "Load was stopped before finishing.";
            case "FAILED", "ABANDONED" ->
                    "Load failed and was rolled back — no data was saved. The error has been logged for the team.";
            default -> "Load finished with status " + status + ".";
        };

        return ResponseEntity.ok(new JobSummary(executionId, status, rowsRead, rowsWritten, rowsSkipped, message));
    }
}
