package org.mytestproject.dataloader.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DatabaseLoaderRunner {

    private final DataLoaderService customIteratorLoader;
    private final JobOperator jobOperator;
    private final Job employeeLoaderJob;

    @Value("${app.loader.strategy:iterator}")
    private String loaderStrategy;

    public DatabaseLoaderRunner(DataLoaderService customIteratorLoader,
                                JobOperator jobOperator,
                                Job employeeLoaderJob) {
        this.customIteratorLoader = customIteratorLoader;
        this.jobOperator = jobOperator;
        this.employeeLoaderJob = employeeLoaderJob;
    }

//    @Override
//    public void run(String... args) throws Exception {
//        if ("batch".equalsIgnoreCase(loaderStrategy)) {
//            log.info("Starting data load using strategy: SPRING BATCH");
//            JobParameters params = new JobParametersBuilder()
//                    .addLong("time", System.currentTimeMillis()) // Ensures unique job instances each run
//                    .toJobParameters();
//            jobOperator.start(employeeLoaderJob, params);
//        } else {
//            log.info("Starting data load using strategy: CUSTOM ITERATOR");
//            customIteratorLoader.loadLocalDataFile();
//        }
//    }
}
