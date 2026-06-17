package org.mytestproject.dataloader.listeners;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Department;
import org.mytestproject.dataloader.entities.SkipTargetType;
import org.mytestproject.dataloader.models.EmployeeRecordData;
import org.mytestproject.dataloader.services.SkippedRecordService;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.stereotype.Component;

/**
 * Captures skipped department rows into the same SkippedRecord store as employees (tagged
 * DEPARTMENT), so they flow through the end-of-run digest and the /skips review page.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DepartmentSkipListener implements SkipListener<String, Department> {

    private final SkippedRecordService skippedRecordService;

    @Override
    public void onSkipInRead(Throwable t) {
        String errorMsg = t.getMessage();
        log.error("Department skipped during READ: {}", errorMsg);
        skippedRecordService.record(currentLoadId(), SkipTargetType.DEPARTMENT, "READ", "UNKNOWN", errorMsg, null);
    }

    @Override
    public void onSkipInProcess(String item, Throwable t) {
        String value = clip(item);
        String errorMsg = describe(t);
        log.error("Department skipped during PROCESS [{}]: {}", value, errorMsg);
        skippedRecordService.record(currentLoadId(), SkipTargetType.DEPARTMENT, "PROCESS_VALIDATION",
                value, errorMsg, departmentData(value));
    }

    @Override
    public void onSkipInWrite(Department item, Throwable t) {
        String value = (item != null) ? clip(item.getName()) : "UNKNOWN";
        String errorMsg = t.getMessage();
        log.error("Department skipped during WRITE [{}]: {}", value, errorMsg);
        skippedRecordService.record(currentLoadId(), SkipTargetType.DEPARTMENT, "WRITE_DATABASE",
                value, errorMsg, departmentData(value));
    }

    private String currentLoadId() {
        StepContext context = StepSynchronizationManager.getContext();
        return (context != null) ? String.valueOf(context.getStepExecution().getJobExecutionId()) : null;
    }

    /** Only the department column is meaningful for a department row. */
    private static EmployeeRecordData departmentData(String name) {
        return new EmployeeRecordData(null, null, null, name, null, null);
    }

    private static String describe(Throwable t) {
        ConstraintViolationException cve = switch (t) {
            case ConstraintViolationException c -> c;
            case Throwable other when other.getCause() instanceof ConstraintViolationException c -> c;
            default -> null;
        };
        if (cve != null) {
            return cve.getConstraintViolations().stream()
                    .map(v -> "Field '" + v.getPropertyPath() + "' " + v.getMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Validation failed");
        }
        return t.getMessage();
    }

    /** Trim and cap length so an oversized garbage line fits the varchar(255) columns. */
    private static String clip(String value) {
        if (value == null) {
            return "UNKNOWN";
        }
        String v = value.trim();
        return v.length() > 250 ? v.substring(0, 250) : v;
    }
}
