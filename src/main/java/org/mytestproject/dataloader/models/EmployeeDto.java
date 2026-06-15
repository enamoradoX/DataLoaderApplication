package org.mytestproject.dataloader.models;

import jakarta.validation.constraints.*;

public record EmployeeDto(
    @NotNull(message = "ID cannot be null")
    Integer id,

    @NotBlank(message = "Employee name cannot be blank")
    @Pattern(regexp = "^[a-zA-Z\\s\\p{L}'()\\-]+$", message = "Name contains invalid characters")
    String name,

    @NotBlank(message = "Employee role cannot be blank")
    String role,

    @NotNull(message = "Salary cannot be null")
    @Min(value = 0, message = "Salary must be a non-negative number")
    @Max(value = 500000, message = "Salary exceeds maximum company allowance")
    Long salary,

    @NotBlank(message = "Email address cannot be blank")
    @Email(message = "Must be a well-formed email address")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "Strict email format check failed")
    String email,

    @NotBlank(message = "Department cannot be blank")
    String department
)
{ }
