package br.com.casamento.gift.dto;

import br.com.casamento.domain.gift.GiftItem;

import java.util.UUID;
import java.math.BigDecimal;

public record GiftItemResponse(
        UUID id,
        String title,
        String description,
        String externalUrl,
        String imageUrl,
        String priceRange,
        BigDecimal priceAmount,
        String category,
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
                item.priceAmount,
                item.category,
                item.displayOrder,
                item.status
        );
    }
}
