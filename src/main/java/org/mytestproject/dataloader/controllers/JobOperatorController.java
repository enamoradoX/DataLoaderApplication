package org.mytestproject.dataloader.controllers;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController()
@RequestMapping("/api/batch")
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
    public String getJobSummary(@PathVariable Long executionId) {
        JobExecution jobExecution = jobRepository.getJobExecution(executionId);
        return (jobExecution != null) ? jobExecution.toString() : "Not Found";
    }
}
