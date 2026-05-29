package br.com.casamento.guest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GuestProfileRequest(
        @NotBlank @Size(max = 255) String confirmedName,
        @NotBlank @Email @Size(max = 255) String confirmedEmail,
        @Size(max = 30) String confirmedPhone,
        @NotNull Boolean acceptedTerms
) {}
