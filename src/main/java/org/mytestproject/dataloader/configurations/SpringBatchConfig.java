package org.mytestproject.dataloader.configurations;

import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.listeners.EmployeeSkipListener;
import org.mytestproject.dataloader.listeners.JobPerformanceListener;
import org.mytestproject.dataloader.models.EmployeeDto;
import org.mytestproject.dataloader.repositories.EmployeeRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.batch.infrastructure.item.validator.BeanValidatingItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.util.List;

@Configuration
public class SpringBatchConfig {

    @Value("classpath:data.txt")
    private Resource dataFile;

    private final EmployeeRepository employeeRepository;

    private final EmployeeSkipListener employeeSkipListener; // Inject the listener

    private final JobPerformanceListener jobPerformanceListener;

    public SpringBatchConfig(EmployeeRepository employeeRepository, EmployeeSkipListener employeeSkipListener, JobPerformanceListener jobPerformanceListener) {
        this.employeeRepository = employeeRepository;
        this.employeeSkipListener = employeeSkipListener;
        this.jobPerformanceListener = jobPerformanceListener;
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
    public BeanValidatingItemProcessor<EmployeeDto> jsrValidator() throws Exception {
        BeanValidatingItemProcessor<EmployeeDto> processor = new BeanValidatingItemProcessor<>();
        // CRITICAL: Set to true so bad rows throw a ValidationException and trigger a Skip,
        // instead of throwing a critical error that terminates the whole job.
        processor.setFilter(false);
        processor.afterPropertiesSet();
        return processor;
    }

    // 2. The Clean Mapping Bean (Only runs if validation passes)
    @Bean
    public ItemProcessor<EmployeeDto, Employee> entityMapper() {
        return emp -> new Employee(emp.name(), emp.role(), emp.salary());
    }

    // 3. The Composite Pipeline Bean (Combines Validation + Mapping)
    @Bean
    public CompositeItemProcessor<EmployeeDto, Employee> processor(
            BeanValidatingItemProcessor<EmployeeDto> jsrValidator,
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
                .delimited().delimiter("|")
                .names("id", "name", "role", "salary")
                .fieldSetMapper(fieldSet -> new EmployeeDto(
                        fieldSet.readInt("id"),
                        fieldSet.readString("name"),
                        fieldSet.readString("role"),
                        fieldSet.readLong("salary")
                ))
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
