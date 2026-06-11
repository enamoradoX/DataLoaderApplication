package org.mytestproject.dataloader.listeners;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.models.EmployeeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // Explicitly import LoggerFactory
import org.springframework.batch.core.listener.SkipListener;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Component;

@Component
@Slf4j // This still gives you standard console logs via 'log'
public class EmployeeSkipListener implements SkipListener<EmployeeDto, Employee> {

    // Target the isolated appender channel defined in logback-spring.xml
    private static final Logger auditLogger = LoggerFactory.getLogger("auditLogger");

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("Skipped during READ: Formatting/Parsing failure -> {}", t.getMessage());

        // This writes exclusively to logs/skipped_records.log
        auditLogger.info("PHASE: READ | RECORD ID: UNKNOWN | ERROR: {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(EmployeeDto item, Throwable t) {
        String recordId = (item != null) ? String.valueOf(item.id()) : "UNKNOWN";

        if (t.getCause() instanceof ConstraintViolationException violationException) {
            violationException.getConstraintViolations().forEach(violation -> {
                String errorMsg = String.format("Field '%s' %s", violation.getPropertyPath(), violation.getMessage());

                log.warn("Skipped Row [ID: {}]: {}", recordId, errorMsg);
                auditLogger.info("PHASE: PROCESS_VALIDATION | RECORD ID: {} | ERROR: {}", recordId, errorMsg);
            });
        } else {
            log.warn("Skipped during PROCESS [ID: {}]: Reason -> {}", recordId, t.getMessage());
            auditLogger.info("PHASE: PROCESS | RECORD ID: {} | ERROR: {}", recordId, t.getMessage());
        }
    }

    @Override
    public void onSkipInWrite(Employee item, Throwable t) {
        String name = (item != null) ? item.getEmployeeName() : "UNKNOWN";

        log.error("Skipped during WRITE [Name: {}]: Database Constraint Failure -> {}", name, t.getMessage());
        auditLogger.info("PHASE: WRITE_DATABASE | RECORD ID: {} | ERROR: {}", name, t.getMessage());
    }
}
