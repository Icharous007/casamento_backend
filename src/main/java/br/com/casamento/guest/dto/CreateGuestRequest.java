package br.com.casamento.guest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGuestRequest(
        @NotBlank @Size(max = 255) String displayName,
        @Email @Size(max = 255) String email,
        @Size(max = 500) String notes
) {}
