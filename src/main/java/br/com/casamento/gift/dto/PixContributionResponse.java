package br.com.casamento.gift.dto;

import br.com.casamento.domain.gift.GiftPurchaseMark;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PixContributionResponse(UUID id, String paymentStatus, OffsetDateTime reportedAt) {
    public static PixContributionResponse from(GiftPurchaseMark mark) {
        return new PixContributionResponse(mark.id, mark.paymentStatus, mark.purchasedAt);
    }
}