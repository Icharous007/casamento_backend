package br.com.casamento.gift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record CreateGiftRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        String externalUrl,
        String imageUrl,
        String priceRange,
        Integer displayOrder,
        String category,
        @DecimalMin("0.01") BigDecimal priceAmount
) {}
