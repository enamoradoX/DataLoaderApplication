package org.mytestproject.dataloader.services;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.entities.SkippedRecord;
import org.mytestproject.dataloader.models.EmployeeDto;
import org.mytestproject.dataloader.models.EmployeeRecordData;
import org.mytestproject.dataloader.repositories.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
public class DataLoaderService {

    private static final Logger auditLogger = LoggerFactory.getLogger("auditLogger");

    @Value("classpath:data.txt")
    private Resource dataFile;

    private final EmployeeRepository employeeRepository;
    private final Validator validator;
    private final SkipEventPublisher skipEventPublisher;
    private final SkippedRecordService skippedRecordService;
    private final EmailNotificationService emailNotificationService;
    private final DepartmentService departmentService;

    public DataLoaderService(EmployeeRepository employeeRepository, Validator validator,
                             SkipEventPublisher skipEventPublisher, SkippedRecordService skippedRecordService,
                             EmailNotificationService emailNotificationService, DepartmentService departmentService){
        this.employeeRepository = employeeRepository;
        this.validator = validator;
        this.skipEventPublisher = skipEventPublisher;
        this.skippedRecordService = skippedRecordService;
        this.emailNotificationService = emailNotificationService;
        this.departmentService = departmentService;
    }

    public boolean loadLocalDataFile(){

        int batchSize = 500;
        List<Employee> batch = new ArrayList<>();
        int totalSaved = 0;
        int totalProcessedLines = 0;

        // Correlation id for this load run, stamped on every skip (batch jobs use the JobExecution id).
        String loadId = UUID.randomUUID().toString();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(dataFile.getInputStream(), StandardCharsets.UTF_8));
             Stream<String> lines = reader.lines()){

            java.util.Iterator<String> iterator = lines.skip(1).iterator();

            while(iterator.hasNext()){
                String line = iterator.next();
                totalProcessedLines++;
                if (line.isBlank()) continue;

                validateAndParse(line, log, loadId).ifPresent(batch::add);

                // Trigger chunk processing
                if (batch.size() >= batchSize) {
                    totalSaved += flushBatchSafely(batch, loadId);
                }
            }

            // Clean up remaining records
            if (!batch.isEmpty()) {
                totalSaved += flushBatchSafely(batch, loadId);
            }

            log.info("Data load complete. Successfully saved {}/{} lines.", totalSaved, totalProcessedLines);

            // One digest email per run for everything skipped under this loadId.
            List<SkippedRecord> skips = skippedRecordService.findByLoad(loadId);
            if (!skips.isEmpty()) {
                emailNotificationService.sendLoadDigest(loadId, skips);
            }

            return totalSaved > 0;

        } catch (Exception e) {
            log.error("Critical failure reading the data file: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Wraps database insertion in an isolated try-catch.
     * If one batch fails, the application recovers and moves to the next chunk.
     */
    private int flushBatchSafely(List<Employee> batch, String loadId) {
        try {
            employeeRepository.saveAll(batch);
            int savedCount = batch.size();
            batch.clear(); // Wipe memory immediately for GC
            return savedCount;
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            log.error("Failed to save batch of {} records. Error: {}. Skipping this chunk to maintain resiliency.",
                    batch.size(), errorMsg);

            for (Employee employee : batch) {
                auditLogger.info("PHASE: WRITE_DATABASE | RECORD ID: {} | ERROR: {}", employee.getEmployeeName(), errorMsg);
                EmployeeRecordData data = new EmployeeRecordData(
                        Objects.toString(employee.getId(), null),
                        employee.getEmployeeName(),
                        employee.getEmail(),
                        employee.getDepartment() != null ? employee.getDepartment().getName() : null,
                        employee.getRole(),
                        Objects.toString(employee.getSalary(), null));
                skipEventPublisher.publish("WRITE_DATABASE", employee.getEmployeeName(), errorMsg, data);
                skippedRecordService.record(loadId, "WRITE_DATABASE", employee.getEmployeeName(), errorMsg, data);
            }

            batch.clear(); // Still clear memory to prevent memory leaks
            return 0;
        }
    }

    /**
     * Parses each column, then runs the same EmployeeDto Jakarta Bean Validation rules
     * the Spring Batch job applies via BeanValidatingItemProcessor.
     */
    private Optional<Employee> validateAndParse(String line, Logger logger, String loadId) {
        try {
            String[] row = line.split(",");

            // Column Count Validation
            if (row.length != 6) {
                String errorMsg = String.format("Invalid column count (Expected 6, got %d)", row.length);
                logger.error("Skipping line: {}. Line: [{}]", errorMsg, line);
                auditLogger.info("PHASE: READ | RECORD ID: UNKNOWN | ERROR: {}", errorMsg);
                skipEventPublisher.publish("READ", "UNKNOWN", errorMsg);
                skippedRecordService.record(loadId, "READ", "UNKNOWN", errorMsg, null);
                return Optional.empty();
            }

            // Clean whitespaces from all inputs: id,employeeName,email,department,role,salary
            String idStr = row[0].trim();
            String name = row[1].trim();
            String email = row[2].trim();
            String department = row[3].trim();
            String role = row[4].trim();
            String salaryStr = row[5].trim();

            // ID and Salary must be numeric to even build the DTO (mirrors FlatFileItemReader read failures)
            int id;
            long salary;
            try {
                id = Integer.parseInt(idStr);
                salary = Long.parseLong(salaryStr);
            } catch (NumberFormatException e) {
                String errorMsg = String.format("Parsing error: %s", e.getMessage());
                logger.error("Skipping line: {}. Line: [{}]", errorMsg, line);
                auditLogger.info("PHASE: READ | RECORD ID: UNKNOWN | ERROR: {}", errorMsg);
                // The columns split cleanly even though a number didn't parse, so carry the raw row.
                EmployeeRecordData data = new EmployeeRecordData(idStr, name, email, department, role, salaryStr);
                skipEventPublisher.publish("READ", "UNKNOWN", errorMsg, data);
                skippedRecordService.record(loadId, "READ", "UNKNOWN", errorMsg, data);
                return Optional.empty();
            }

            // Run the same validation rules as the Spring Batch job's jsrValidator
            EmployeeDto dto = new EmployeeDto(id, name, role, salary, email, department);
            Set<ConstraintViolation<EmployeeDto>> violations = validator.validate(dto);

            if (!violations.isEmpty()) {
                EmployeeRecordData data = new EmployeeRecordData(idStr, name, email, department, role, salaryStr);
                for (ConstraintViolation<EmployeeDto> violation : violations) {
                    String errorMsg = String.format("Field '%s' %s", violation.getPropertyPath(), violation.getMessage());
                    logger.error("Skipping line [ID: {}]: {}", id, errorMsg);
                    auditLogger.info("PHASE: PROCESS_VALIDATION | RECORD ID: {} | ERROR: {}", id, errorMsg);
                    skipEventPublisher.publish("PROCESS_VALIDATION", String.valueOf(id), errorMsg, data);
                    skippedRecordService.record(loadId, "PROCESS_VALIDATION", String.valueOf(id), errorMsg, data);
                }
                return Optional.empty();
            }

            // Everything is valid! Map to entity, resolving the Department FK (find-or-create),
            // same as SpringBatchConfig's entityMapper.
            return Optional.of(new Employee(dto.name(), dto.role(), dto.salary(), dto.email(),
                    departmentService.getOrCreate(dto.department())));

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            logger.error("Skipping line: Unexpected parsing error: {}. Line: [{}]", errorMsg, line);
            auditLogger.info("PHASE: READ | RECORD ID: UNKNOWN | ERROR: {}", errorMsg);
            skipEventPublisher.publish("READ", "UNKNOWN", errorMsg);
            skippedRecordService.record(loadId, "READ", "UNKNOWN", errorMsg, null);
            return Optional.empty();
        }
    }
}
