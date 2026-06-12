package org.mytestproject.dataloader.listeners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.models.EmployeeDto;
import org.mytestproject.dataloader.services.SkipEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor // Automatically injects the SkipEventPublisher via constructor
public class EmployeeSkipListener implements SkipListener<EmployeeDto, Employee> {

    private static final Logger auditLogger = LoggerFactory.getLogger("auditLogger");
    private final SkipEventPublisher skipEventPublisher;

    @Override
    public void onSkipInRead(Throwable t) {
        String errorMsg = t.getMessage();
        log.error("Skipped during READ: Formatting/Parsing failure -> {}", errorMsg);
        auditLogger.info("PHASE: READ | RECORD ID: UNKNOWN | ERROR: {}", errorMsg);

        skipEventPublisher.publish("READ", "UNKNOWN", errorMsg);
    }

    @Override
    public void onSkipInProcess(EmployeeDto item, Throwable t) {
        String recordId = (item != null) ? String.valueOf(item.id()) : "UNKNOWN";

        // jsrValidator throws ConstraintViolationException directly; fall back to the cause
        // in case the step ever wraps it.
        ConstraintViolationException violationException = switch (t) {
            case ConstraintViolationException cve -> cve;
            case Throwable other when other.getCause() instanceof ConstraintViolationException cve -> cve;
            default -> null;
        };

        if (violationException != null) {
            violationException.getConstraintViolations().forEach(violation -> {
                String errorMsg = String.format("Field '%s' %s", violation.getPropertyPath(), violation.getMessage());

                log.error("Skipped Row [ID: {}]: {}", recordId, errorMsg);
                auditLogger.info("PHASE: PROCESS_VALIDATION | RECORD ID: {} | ERROR: {}", recordId, errorMsg);

                skipEventPublisher.publish("PROCESS_VALIDATION", recordId, errorMsg);
            });
        } else {
            String errorMsg = t.getMessage();
            log.error("Skipped during PROCESS [ID: {}]: Reason -> {}", recordId, errorMsg);
            auditLogger.info("PHASE: PROCESS | RECORD ID: {} | ERROR: {}", recordId, errorMsg);

            skipEventPublisher.publish("PROCESS", recordId, errorMsg);
        }
    }

    @Override
    public void onSkipInWrite(Employee item, Throwable t) {
        String name = (item != null) ? item.getEmployeeName() : "UNKNOWN";
        String errorMsg = t.getMessage();

        log.error("Skipped during WRITE [Name: {}]: Database Constraint Failure -> {}", name, errorMsg);
        auditLogger.info("PHASE: WRITE_DATABASE | RECORD ID: {} | ERROR: {}", name, errorMsg);

        skipEventPublisher.publish("WRITE_DATABASE", name, errorMsg);
    }
}
