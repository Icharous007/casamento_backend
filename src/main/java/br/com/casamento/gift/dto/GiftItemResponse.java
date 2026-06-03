package br.com.casamento.gift.dto;

import br.com.casamento.domain.gift.GiftItem;

import java.util.UUID;

public record GiftItemResponse(
        UUID id,
        String title,
        String description,
        String externalUrl,
        String imageUrl,
        String priceRange,
        int displayOrder,
        String status
) {
    public static GiftItemResponse from(GiftItem item) {
        return new GiftItemResponse(
                item.id,
                item.title,
                item.description,
                item.externalUrl,
                item.imageUrl,
                item.priceRange,
                item.displayOrder,
                item.status
        );
    }
}
