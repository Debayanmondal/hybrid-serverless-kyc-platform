package com.bank.kyc.onboarding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplicantRegistrationRequest {

    @NotBlank(message = "firstName is required")
    private String firstName;

    @NotBlank(message = "lastName is required")
    private String lastName;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    private String email;

    private String phone;
    private String dateOfBirth;
    private String nationality;
}
