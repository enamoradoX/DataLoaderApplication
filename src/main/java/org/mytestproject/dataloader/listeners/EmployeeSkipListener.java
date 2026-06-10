package org.mytestproject.dataloader.listeners;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.models.EmployeeDto;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmployeeSkipListener implements SkipListener<EmployeeDto, Employee> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("Skipped during READ: Formatting/Parsing failure -> {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(EmployeeDto item, Throwable t) {
        if (t.getCause() instanceof ConstraintViolationException violationException) {
            // Extracts the exact error message defined in your DTO annotations
            violationException.getConstraintViolations().forEach(violation ->
                    log.warn("Skipped Row [ID: {}]: Field '{}' failed validation. Error: {}",
                            item.id(), violation.getPropertyPath(), violation.getMessage())
            );
        } else {
            log.warn("Skipped during PROCESS [ID: {}]: Reason -> {}", item.id(), t.getMessage());
        }
    }

    @Override
    public void onSkipInWrite(Employee item, Throwable t) {
        log.error("Skipped during WRITE [Name: {}]: Database Constraint Failure -> {}",
                item.getEmployeeName(), t.getMessage());
    }
}
