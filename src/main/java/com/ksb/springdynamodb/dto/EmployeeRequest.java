package com.ksb.springdynamodb.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record EmployeeRequest(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,
        @NotBlank(message = "Department is required") String department,
        @NotBlank(message = "Title is required") String title,
        @NotNull(message = "Salary is required") @PositiveOrZero(message = "Salary must be zero or positive") BigDecimal salary
) {
}
