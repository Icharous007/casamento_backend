package br.com.casamento.gift.dto;

import jakarta.validation.constraints.Size;

public record UpdateGiftRequest(
        @Size(max = 255) String title,
        String description,
        String externalUrl,
        String imageUrl,
        String priceRange,
        Integer displayOrder,
        Boolean visibleToGuests
) {}
