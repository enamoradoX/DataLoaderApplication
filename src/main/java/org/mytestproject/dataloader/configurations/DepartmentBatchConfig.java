package org.mytestproject.dataloader.configurations;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.mytestproject.dataloader.entities.Department;
import org.mytestproject.dataloader.exceptions.DuplicateDepartmentException;
import org.mytestproject.dataloader.listeners.DepartmentSkipListener;
import org.mytestproject.dataloader.listeners.JobPerformanceListener;
import org.mytestproject.dataloader.listeners.SkipDigestJobListener;
import org.mytestproject.dataloader.models.DepartmentDto;
import org.mytestproject.dataloader.repositories.DepartmentRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
import java.util.HashSet;
import java.util.Set;

/**
 * A standalone single-table loader for the Department lookup table — the simplest shape of a
 * Spring Batch job (one file, one entity, reader -> processor -> writer). Kept in its own
 * @Configuration so the employee job (SpringBatchConfig) stays focused, and so you can run this
 * job independently ("load one table at a time"). Nothing here runs on startup because
 * spring.batch.job.enabled=false; it's triggered on demand.
 */
@Configuration
public class DepartmentBatchConfig {

    private final DepartmentRepository departmentRepository;
    private final Validator validator;
    private final JobPerformanceListener jobPerformanceListener;
    private final DepartmentSkipListener departmentSkipListener;
    private final SkipDigestJobListener skipDigestJobListener;

    public DepartmentBatchConfig(DepartmentRepository departmentRepository, Validator validator,
                                 JobPerformanceListener jobPerformanceListener,
                                 DepartmentSkipListener departmentSkipListener,
                                 SkipDigestJobListener skipDigestJobListener) {
        this.departmentRepository = departmentRepository;
        this.validator = validator;
        this.jobPerformanceListener = jobPerformanceListener;
        this.departmentSkipListener = departmentSkipListener;
        this.skipDigestJobListener = skipDigestJobListener;
    }

    /**
     * @StepScope so the file comes from the 'inputFile' job parameter (set by the upload endpoint),
     * falling back to the bundled classpath file when absent. The file has one department name per
     * line, so the whole line IS the value: PassThroughLineMapper hands each raw line through as a String.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<String> departmentReader(
            @Value("#{jobParameters['inputFile'] ?: 'classpath:departments.txt'}") Resource inputFile) {
        return new FlatFileItemReaderBuilder<String>()
                .name("departmentReader")
                .resource(inputFile)
                .linesToSkip(1) // skip the "name" header row
                .lineMapper(new PassThroughLineMapper())
                .build();
    }

    
    /**
     * Validates each name with the same shared Validator the employee loader uses (against
     * DepartmentDto), then maps to a Department. Blank lines are filtered (return null); rows that
     * fail validation throw ConstraintViolationException so the step skips them (and the
     * DepartmentSkipListener records them). This is what stops a wrong file (e.g. customer data)
     * from loading as departments.
     */
    @Bean
    @StepScope
    public ItemProcessor<String, Department> departmentProcessor() {
        // @StepScope -> a fresh set per run (single-threaded step, so no synchronization needed).
        Set<String> seenThisRun = new HashSet<>();
        return raw -> {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            DepartmentDto dto = new DepartmentDto(raw.trim());
            Set<ConstraintViolation<DepartmentDto>> violations = validator.validate(dto);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            String name = dto.name();
            // Idempotent find-or-skip: a name already loaded — repeated in this file (seenThisRun) or
            // already in the DB (findByName) — is reported as a skip (with a reason) rather than
            // inserted again. Throwing here is a PROCESS-phase skip (no DB write attempted), so it's
            // counted in skipCount and recorded by DepartmentSkipListener, and it avoids the
            // rollback-only failure that skip-on-DataIntegrityViolation would cause with JPA.
            if (!seenThisRun.add(name)) {
                throw new DuplicateDepartmentException(
                        "Department '" + name + "' appears more than once in the file; not re-loaded.");
            }
            if (departmentRepository.findByName(name).isPresent()) {
                throw new DuplicateDepartmentException(
                        "Department '" + name + "' already exists; not re-loaded.");
            }
            return new Department(name);
        };
    }

    @Bean
    public Step departmentLoadingStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      FlatFileItemReader<String> departmentReader,
                                      ItemProcessor<String, Department> departmentProcessor) {
        return new StepBuilder("departmentLoadingStep", jobRepository)
                .<String, Department>chunk(500) // read/map/write 500 at a time, commit per chunk
                .transactionManager(transactionManager)
                .reader(departmentReader) // @StepScope proxy injected; resolves inputFile per run
                .processor(departmentProcessor) // @StepScope proxy injected; dedupes per run
                .writer(chunk -> departmentRepository.saveAll(chunk.getItems()))
                .faultTolerant()
                // Skip (and record, with a reason) rows that fail validation or are duplicates,
                // instead of failing the whole load. DataIntegrityViolation stays as a backstop.
                .skip(ConstraintViolationException.class)
                .skip(DuplicateDepartmentException.class)
                .skip(DataIntegrityViolationException.class)
                .skipLimit(1000)
                .listener(departmentSkipListener)
                .build();
    }

    @Bean(name = "departmentLoaderJob")
    public Job departmentLoaderJob(JobRepository jobRepository, Step departmentLoadingStep) {
        return new JobBuilder("departmentLoaderJob", jobRepository)
                .start(departmentLoadingStep)
                .listener(jobPerformanceListener)
                .listener(skipDigestJobListener) // one digest email of any skipped department rows
                .build();
    }
}
