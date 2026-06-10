package org.mytestproject.dataloader.models;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmployeeDto(
    @NotNull(message = "ID cannot be null")
    Integer id,

    @NotBlank(message = "Employee name cannot be blank")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    String name,

    @NotBlank(message = "Employee role cannot be blank")
    String role,

    @NotNull(message = "Salary cannot be null")
    @Min(value = 0, message = "Salary must be a non-negative number")
    Long salary) 
{ }
