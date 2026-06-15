package org.mytestproject.dataloader.configurations;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.listeners.EmployeeSkipListener;
import org.mytestproject.dataloader.listeners.JobPerformanceListener;
import org.mytestproject.dataloader.models.EmployeeDto;
import org.mytestproject.dataloader.repositories.EmployeeRepository;
import org.mytestproject.dataloader.services.DepartmentService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Configuration
public class SpringBatchConfig {

    @Value("classpath:data.txt")
    private Resource dataFile;

    private final EmployeeRepository employeeRepository;

    private final EmployeeSkipListener employeeSkipListener; // Inject the listener

    private final JobPerformanceListener jobPerformanceListener;

    private final Validator validator;

    private final DepartmentService departmentService;

    public SpringBatchConfig(EmployeeRepository employeeRepository, EmployeeSkipListener employeeSkipListener,
                              JobPerformanceListener jobPerformanceListener, Validator validator,
                              DepartmentService departmentService) {
        this.employeeRepository = employeeRepository;
        this.employeeSkipListener = employeeSkipListener;
        this.jobPerformanceListener = jobPerformanceListener;
        this.validator = validator;
        this.departmentService = departmentService;
    }

    @Bean
    public DataSourceInitializer batchSchemaInitializer(DataSource dataSource, ResourceLoader resourceLoader) {
        ResourceDatabasePopulator databasePopulator = new ResourceDatabasePopulator();

        // Explicitly pulls the official, bundled H2 database DDL script from the Spring Batch core library jar
        databasePopulator.addScript(resourceLoader.getResource(
                "classpath:org/springframework/batch/core/schema-h2.sql"));

        databasePopulator.setContinueOnError(true); // Prevents startup failure if tables happen to already exist

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(databasePopulator);
        return initializer;
    }

    // 1. The Validation Engine Bean
    @Bean
    public ItemProcessor<EmployeeDto, EmployeeDto> jsrValidator() {
        return dto -> {
            Set<ConstraintViolation<EmployeeDto>> violations = validator.validate(dto);
            if (!violations.isEmpty()) {
                // Throwing here lets the step's fault-tolerant skip handling and
                // EmployeeSkipListener.onSkipInProcess take over, mirroring DataLoaderService.
                throw new ConstraintViolationException(violations);
            }
            return dto;
        };
    }

    // 2. The Clean Mapping Bean (Only runs if validation passes). Resolves the Department FK
    //    via find-or-create so JPA writes department_id when the Employee is saved.
    @Bean
    public ItemProcessor<EmployeeDto, Employee> entityMapper() {
        return dto -> new Employee(dto.name(), dto.role(), dto.salary(), dto.email(),
                departmentService.getOrCreate(dto.department()));
    }

    // 3. The Composite Pipeline Bean (Combines Validation + Mapping)
    @Bean
    public CompositeItemProcessor<EmployeeDto, Employee> processor(
            ItemProcessor<EmployeeDto, EmployeeDto> jsrValidator,
            ItemProcessor<EmployeeDto, Employee> entityMapper) {

        CompositeItemProcessor<EmployeeDto, Employee> compositeProcessor = new CompositeItemProcessor<>();
        compositeProcessor.setDelegates(List.of(jsrValidator, entityMapper));
        return compositeProcessor;
    }

    @Bean
    public FlatFileItemReader<EmployeeDto> reader() {
        return new FlatFileItemReaderBuilder<EmployeeDto>()
                .name("employeeReader")
                .resource(dataFile)
                .linesToSkip(1) // Skip header row
                .delimited()
                .names("id","employeeName","email","department","role","salary")
                .fieldSetMapper(fieldSet -> new EmployeeDto(
                        fieldSet.readInt("id"),
                        fieldSet.readString("employeeName"),
                        fieldSet.readString("role"),
                        fieldSet.readLong("salary"),
                        fieldSet.readString("email"),
                        fieldSet.readString("department")
                ))
                .build();
    }

    /**
     * Spring Batch 6 / Spring Framework 7 fold retry + backoff into a single RetryPolicy.
     * Retry only TRANSIENT db failures (deadlock, lock timeout, dropped connection), with
     * exponential backoff: wait 1s, then 2s, then 4s (capped at 10s) between attempts.
     * Validation/parse failures are deterministic, so they are excluded and fall straight
     * through to skip.
     */
    @Bean
    public RetryPolicy writeRetryPolicy() {
        return RetryPolicy.builder()
                .maxRetries(3) // up to 3 retries after the first attempt before the item is skipped
                .delay(Duration.ofSeconds(1))
                .multiplier(2.0)        // 1s -> 2s -> 4s ...
                .maxDelay(Duration.ofSeconds(10)) // never wait more than 10s
                .includes(List.of(TransientDataAccessException.class))
                .build();
    }

    @Bean
    public Step csvFileLoadingStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager) throws Exception { // 1. Inject the writer bean here

        return new StepBuilder("csvFileLoadingStep", jobRepository)
                .<EmployeeDto, Employee>chunk(500)
                .transactionManager(transactionManager)
                .reader(reader())
                .processor(processor(jsrValidator(), entityMapper()))
                .writer(chunk -> {
                    employeeRepository.saveAll(chunk.getItems());
                })
                .faultTolerant()
                .retryPolicy(writeRetryPolicy()) // transient-only retry + backoff (see writeRetryPolicy)
                .skip(Exception.class)
                .skipLimit(100)
                .listener(employeeSkipListener)
                .build();
    }


    @Bean(name = "employeeLoaderJob")
    public Job employeeLoaderJob(JobRepository jobRepository, Step csvFileLoadingStep) {
        return new JobBuilder("employeeLoaderJob", jobRepository)
                .start(csvFileLoadingStep)
                .listener(jobPerformanceListener)
                .build();
    }
}
