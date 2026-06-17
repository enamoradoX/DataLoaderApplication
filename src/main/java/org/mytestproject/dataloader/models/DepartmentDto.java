package org.mytestproject.dataloader.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Validation rules for a single department name, applied the same way EmployeeDto is — so an
 * uploaded file that isn't actually a department list (e.g. customer data) fails per-row instead
 * of loading garbage. The @Pattern excludes commas and symbols like '@', so multi-column CSV rows
 * and emails are rejected; tune the allowed characters to match your real department names.
 */
public record DepartmentDto(
        @NotBlank(message = "Department name cannot be blank")
        @Size(max = 100, message = "Department name is too long (max 100 chars)")
        @Pattern(regexp = "^[\\p{L}0-9 &'()./-]+$", message = "Department name has invalid characters")
        String name
) {}
