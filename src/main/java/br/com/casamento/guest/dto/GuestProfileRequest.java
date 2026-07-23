package br.com.casamento.guest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Guest completes their profile on first access.
 * Phone is already known from the OTP login; confirmedName is the display name.
 * No email is collected for guests.
 */
public record GuestProfileRequest(
        @NotBlank @Size(max = 255) String confirmedName,
        @NotNull Boolean acceptedTerms
) {}
