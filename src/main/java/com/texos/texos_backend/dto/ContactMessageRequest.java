package com.texos.texos_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContactMessageRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(max = 150)
    private String subject;

    @NotBlank
    @Pattern(regexp = "^(Sales & Business Enquiries|Restaurant Onboarding|Technical Support|Subscription & Billing|Payment Related|General Enquiries)$")
    private String category;

    @NotBlank
    @Size(max = 5000)
    private String message;
}
