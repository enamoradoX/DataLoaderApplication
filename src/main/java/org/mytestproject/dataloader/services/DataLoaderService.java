package org.mytestproject.dataloader.services;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.repositories.EmployeeRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Slf4j
public class DataLoaderService {

    @Value("classpath:data.txt")
    private Resource dataFile;

    private final EmployeeRepository employeeRepository;

    public DataLoaderService(EmployeeRepository employeeRepository){
        this.employeeRepository = employeeRepository;
    }

    public boolean loadLocalDataFile(){

        int batchSize = 500;
        List<Employee> batch = new ArrayList<>();
        int totalSaved = 0;
        int totalProcessedLines = 0;


        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(dataFile.getInputStream(), StandardCharsets.UTF_8));
             Stream<String> lines = reader.lines()){

            java.util.Iterator<String> iterator = lines.skip(1).iterator();

            while(iterator.hasNext()){
                String line = iterator.next();
                totalProcessedLines++;
                if (line.isBlank()) continue;

                validateAndParse(line, log).ifPresent(batch::add);

                // Trigger chunk processing
                if (batch.size() >= batchSize) {
                    totalSaved += flushBatchSafely(batch);
                }
            }

            // Clean up remaining records
            if (!batch.isEmpty()) {
                totalSaved += flushBatchSafely(batch);
            }

            log.info("Data load complete. Successfully saved {}/{} lines.", totalSaved, totalProcessedLines);
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
    private int flushBatchSafely(List<Employee> batch) {
        try {
            employeeRepository.saveAll(batch);
            int savedCount = batch.size();
            batch.clear(); // Wipe memory immediately for GC
            return savedCount;
        } catch (Exception e) {
            log.error("Failed to save batch of {} records. Error: {}. Skipping this chunk to maintain resiliency.",
                    batch.size(), e.getMessage());
            batch.clear(); // Still clear memory to prevent memory leaks
            return 0;
        }
    }

    /**
     * Helper method to validate every column and handle parsing errors safely.
     */
    private Optional<Employee> validateAndParse(String line, Logger logger) {
        try {
            String[] row = line.split(",");

            // Column Count Validation
            if (row.length != 4) {
                logger.error("Skipping line: Invalid column count (Expected 4, got {}). Line: [{}]", row.length, line);
                return Optional.empty();
            }

            // Clean whitespaces from all inputs
            String idStr = row[0].trim();
            String name = row[1].trim();
            String role = row[2].trim();
            String salaryStr = row[3].trim();

            // Column 1 Validation: ID must be a number
            int id;
            try {
                id = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                logger.error("Skipping line: ID [{}] is not a valid integer. Line: [{}]", idStr, line);
                return Optional.empty();
            }

            // Column 2 Validation: Name cannot be empty
            if (name.isEmpty()) {
                logger.error("Skipping line: Name column is missing or blank. Line: [{}]", line);
                return Optional.empty();
            }

            // Column 3 Validation: Role cannot be empty
            if (role.isEmpty()) {
                logger.error("Skipping line: Role column is missing or blank. Line: [{}]", line);
                return Optional.empty();
            }

            // Column 4 Validation: Salary must be a number and non-negative
            long salary;
            try {
                salary = Long.parseLong(salaryStr);
                if (salary < 0) {
                    logger.error("Skipping line: Salary [{}] cannot be negative. Line: [{}]", salaryStr, line);
                    return Optional.empty();
                }
            } catch (NumberFormatException e) {
                logger.error("Skipping line: Salary [{}] is not a valid number. Line: [{}]", salaryStr, line);
                return Optional.empty();
            }

            // Everything is valid! Return the record object packed inside an Optional
            return Optional.of(new Employee(name, role, salary));

        } catch (Exception e) {
            logger.error("Skipping line: Unexpected parsing error: {}. Line: [{}]", e.getMessage(), line);
            return Optional.empty();
        }
    }
}
