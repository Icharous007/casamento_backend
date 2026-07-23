package br.com.casamento.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Guest hits the event QR code / save-the-date link, enters phone + display
 * name, and opts in. The backend normalizes the phone, finds or creates the
 * guest, and immediately issues an access token — no OTP/verification step.
 */
public record GuestRegisterRequest(
        @NotBlank @Size(max = 100) String eventSlug,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 255) String displayName,
        @NotNull Boolean acceptedTerms
) {}
