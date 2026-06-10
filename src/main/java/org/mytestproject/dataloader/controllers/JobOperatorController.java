package org.mytestproject.dataloader.controllers;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/api/batch")
public class JobOperatorController {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job employeeLoaderJob; // Directly inject the Job bean

    public JobOperatorController(JobOperator jobOperator,
                              JobRepository jobRepository,
                              @Qualifier("employeeLoaderJob") Job employeeLoaderJob) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.employeeLoaderJob = employeeLoaderJob;
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
    public String getJobSummary(@PathVariable Long executionId) {
        JobExecution jobExecution = jobRepository.getJobExecution(executionId);
        return (jobExecution != null) ? jobExecution.toString() : "Not Found";
    }
}
