package org.mytestproject.dataloader.configurations;

import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.models.EmployeeForLoadTest;
import org.mytestproject.dataloader.repositories.EmployeeRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class SpringBatchConfig {

    @Value("classpath:data.txt")
    private Resource dataFile;

    private final EmployeeRepository employeeRepository;

    public SpringBatchConfig(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Bean
    public FlatFileItemReader<EmployeeForLoadTest> reader() {
        return new FlatFileItemReaderBuilder<EmployeeForLoadTest>()
                .name("employeeReader")
                .resource(dataFile)
                .linesToSkip(1) // Skip header row
                .delimited()
                .names("id", "name", "role", "salary")
                .fieldSetMapper(fieldSet -> new EmployeeForLoadTest(
                        fieldSet.readInt("id"),
                        fieldSet.readString("name"),
                        fieldSet.readString("role"),
                        fieldSet.readLong("salary")
                ))
                .build();
    }

    @Bean
    public ItemProcessor<EmployeeForLoadTest, Employee> processor() {
        // Map DTO record directly to the database Entity (discards parsed text ID)
        return emp -> new Employee(emp.name(), emp.role(), emp.salary());
    }

    @Bean
    public Step csvFileLoadingStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager) {
        return new StepBuilder("csvFileLoadingStep", jobRepository)
                .<EmployeeForLoadTest, Employee>chunk(500)          // ✅ no transactionManager here
                .transactionManager(transactionManager)             // ✅ chained separately
                .reader(reader())
                .processor(processor())
                .writer(chunk -> {
                    employeeRepository.saveAll(chunk.getItems());
                })
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(100)
                .build();
    }

    @Bean(name = "employeeLoaderJob")
    public Job employeeLoaderJob(JobRepository jobRepository, Step csvFileLoadingStep) {
        return new JobBuilder("employeeLoaderJob", jobRepository)
                .start(csvFileLoadingStep)
                .build();
    }
}
