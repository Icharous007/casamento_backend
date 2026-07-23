package br.com.casamento.guest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGuestRequest(
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 20) String phone,
        @Size(max = 500) String notes
) {}
