package br.com.casamento.gift.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MarkPurchasedResponse(UUID giftId, String status, OffsetDateTime markedAt) {}
