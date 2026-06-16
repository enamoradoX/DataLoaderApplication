package org.mytestproject.dataloader.configurations;

import org.mytestproject.dataloader.entities.Department;
import org.mytestproject.dataloader.repositories.DepartmentRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.PassThroughLineMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A standalone single-table loader for the Department lookup table — the simplest shape of a
 * Spring Batch job (one file, one entity, reader -> processor -> writer). Kept in its own
 * @Configuration so the employee job (SpringBatchConfig) stays focused, and so you can run this
 * job independently ("load one table at a time"). Nothing here runs on startup because
 * spring.batch.job.enabled=false; it's triggered on demand.
 */
@Configuration
public class DepartmentBatchConfig {

    @Value("classpath:departments.txt")
    private Resource departmentsFile;

    private final DepartmentRepository departmentRepository;

    public DepartmentBatchConfig(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * The file has one department name per line, so the whole line IS the value:
     * PassThroughLineMapper hands each raw line to the processor as a String.
     */
    @Bean
    public FlatFileItemReader<String> departmentReader() {
        return new FlatFileItemReaderBuilder<String>()
                .name("departmentReader")
                .resource(departmentsFile)
                .linesToSkip(1) // skip the "name" header row
                .lineMapper(new PassThroughLineMapper())
                .build();
    }

    /**
     * Turns a raw name into a Department, trimming whitespace. Returning null filters the item
     * out of the chunk (Spring Batch treats a null processor result as "skip this item"), which
     * we use to drop blank lines.
     */
    @Bean
    public ItemProcessor<String, Department> departmentProcessor() {
        return name -> (name == null || name.isBlank()) ? null : new Department(name.trim());
    }

    @Bean
    public Step departmentLoadingStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager) {
        return new StepBuilder("departmentLoadingStep", jobRepository)
                .<String, Department>chunk(500) // read/map/write 500 at a time, commit per chunk
                .transactionManager(transactionManager)
                .reader(departmentReader())
                .processor(departmentProcessor())
                .writer(chunk -> departmentRepository.saveAll(chunk.getItems()))
                .faultTolerant()
                // Department.name is unique, so a name already in the DB (or duplicated in the
                // file) throws on save. Skip those instead of failing the whole load.
                .skip(DataIntegrityViolationException.class)
                .skipLimit(1000)
                .build();
    }

    @Bean(name = "departmentLoaderJob")
    public Job departmentLoaderJob(JobRepository jobRepository, Step departmentLoadingStep) {
        return new JobBuilder("departmentLoaderJob", jobRepository)
                .start(departmentLoadingStep)
                .build();
    }
}
