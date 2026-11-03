package br.com.casamento.gift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PixContributionRequest(
        @NotBlank @Size(max = 255) String giverName,
        @NotBlank @Size(max = 20) String giverPhone,
        @Size(max = 500) String message
) {}