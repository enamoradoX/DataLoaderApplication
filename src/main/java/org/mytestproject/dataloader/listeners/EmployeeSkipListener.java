package org.mytestproject.dataloader.listeners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.models.EmployeeDto;
import org.mytestproject.dataloader.models.EmployeeRecordData;
import org.mytestproject.dataloader.services.SkipEventPublisher;
import org.mytestproject.dataloader.services.SkippedRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor // Injects SkipEventPublisher + SkippedRecordService via constructor
public class EmployeeSkipListener implements SkipListener<EmployeeDto, Employee> {

    private static final Logger auditLogger = LoggerFactory.getLogger("auditLogger");
    private final SkipEventPublisher skipEventPublisher;
    private final SkippedRecordService skippedRecordService;

    /**
     * Correlation id for the current run = the JobExecution id, read from the live step context.
     * (A SkipListener's callbacks don't receive the StepExecution, and passing this object to
     * .listener(...) doesn't reliably register a @BeforeStep hook, so we pull it from the
     * thread-bound StepContext instead.)
     */
    private String currentLoadId() {
        StepContext context = StepSynchronizationManager.getContext();
        return (context != null) ? String.valueOf(context.getStepExecution().getJobExecutionId()) : null;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        String errorMsg = t.getMessage();
        log.error("Skipped during READ: Formatting/Parsing failure -> {}", errorMsg);
        auditLogger.info("PHASE: READ | RECORD ID: UNKNOWN | ERROR: {}", errorMsg);

        // No item is available on a read failure, so there is no row payload to carry.
        skipEventPublisher.publish("READ", "UNKNOWN", errorMsg);
        skippedRecordService.record(currentLoadId(), "READ", "UNKNOWN", errorMsg, null);
    }

    @Override
    public void onSkipInProcess(EmployeeDto item, Throwable t) {
        String recordId = (item != null) ? String.valueOf(item.id()) : "UNKNOWN";
        EmployeeRecordData data = toRecordData(item);
        String loadId = currentLoadId();

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

                skipEventPublisher.publish("PROCESS_VALIDATION", recordId, errorMsg, data);
                skippedRecordService.record(loadId, "PROCESS_VALIDATION", recordId, errorMsg, data);
            });
        } else {
            String errorMsg = t.getMessage();
            log.error("Skipped during PROCESS [ID: {}]: Reason -> {}", recordId, errorMsg);
            auditLogger.info("PHASE: PROCESS | RECORD ID: {} | ERROR: {}", recordId, errorMsg);

            skipEventPublisher.publish("PROCESS", recordId, errorMsg, data);
            skippedRecordService.record(loadId, "PROCESS", recordId, errorMsg, data);
        }
    }

    @Override
    public void onSkipInWrite(Employee item, Throwable t) {
        String name = (item != null) ? item.getEmployeeName() : "UNKNOWN";
        String errorMsg = t.getMessage();

        log.error("Skipped during WRITE [Name: {}]: Database Constraint Failure -> {}", name, errorMsg);
        auditLogger.info("PHASE: WRITE_DATABASE | RECORD ID: {} | ERROR: {}", name, errorMsg);

        EmployeeRecordData data = toRecordData(item);
        skipEventPublisher.publish("WRITE_DATABASE", name, errorMsg, data);
        skippedRecordService.record(currentLoadId(), "WRITE_DATABASE", name, errorMsg, data);
    }

    private EmployeeRecordData toRecordData(EmployeeDto item) {
        if (item == null) {
            return null;
        }
        return new EmployeeRecordData(
                Objects.toString(item.id(), null),
                item.name(),
                item.email(),
                item.role(),
                Objects.toString(item.salary(), null));
    }

    private EmployeeRecordData toRecordData(Employee item) {
        if (item == null) {
            return null;
        }
        return new EmployeeRecordData(
                Objects.toString(item.getId(), null),
                item.getEmployeeName(),
                item.getEmail(),
                item.getRole(),
                Objects.toString(item.getSalary(), null));
    }
}
