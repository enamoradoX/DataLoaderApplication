package org.mytestproject.dataloader.services;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.models.EmployeeDto;
import org.mytestproject.dataloader.models.EmployeeRecordData;
import org.mytestproject.dataloader.models.ReprocessResult;
import org.mytestproject.dataloader.repositories.EmployeeRepository;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Re-runs a single corrected record through the SAME validation rules the loaders use
 * (the shared Validator against EmployeeDto), then saves it if it now passes. This is the
 * backend for the reprocess-a-skipped-record flow.
 */
@Service
@Slf4j
public class ReprocessService {

    private final EmployeeRepository employeeRepository;
    private final Validator validator;

    public ReprocessService(EmployeeRepository employeeRepository, Validator validator) {
        this.employeeRepository = employeeRepository;
        this.validator = validator;
    }

    public ReprocessResult reprocess(EmployeeRecordData data) {
        List<String> errors = new ArrayList<>();

        // The numeric columns must parse before we can build the typed DTO. These mirror the
        // READ-phase checks in the loaders.
        Integer id = null;
        Long salary = null;
        try {
            id = Integer.parseInt(safeTrim(data.id()));
        } catch (NumberFormatException e) {
            errors.add(String.format("Field 'id' [%s] is not a valid integer", data.id()));
        }
        try {
            salary = Long.parseLong(safeTrim(data.salary()));
        } catch (NumberFormatException e) {
            errors.add(String.format("Field 'salary' [%s] is not a valid number", data.salary()));
        }

        if (!errors.isEmpty()) {
            return ReprocessResult.rejected(errors);
        }

        // Same Jakarta Bean Validation rules the Spring Batch job and legacy loader apply.
        EmployeeDto dto = new EmployeeDto(id, safeTrim(data.name()), safeTrim(data.role()), salary, safeTrim(data.email()));
        Set<ConstraintViolation<EmployeeDto>> violations = validator.validate(dto);

        if (!violations.isEmpty()) {
            for (ConstraintViolation<EmployeeDto> violation : violations) {
                errors.add(String.format("Field '%s' %s", violation.getPropertyPath(), violation.getMessage()));
            }
            return ReprocessResult.rejected(errors);
        }

        Employee saved = employeeRepository.save(new Employee(dto.name(), dto.role(), dto.salary(), dto.email()));
        log.info("Reprocessed record saved as employee id {}", saved.getId());
        return ReprocessResult.saved(saved.getId());
    }

    private static String safeTrim(String value) {
        return (value == null) ? null : value.trim();
    }
}
