package br.com.casamento.gift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGiftRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        String externalUrl,
        String imageUrl,
        String priceRange,
        Integer displayOrder
) {}
