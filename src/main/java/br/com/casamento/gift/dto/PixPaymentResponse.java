package br.com.casamento.gift.dto;

import java.math.BigDecimal;

public record PixPaymentResponse(
        String title,
        BigDecimal amount,
        String pixKey,
        String pixKeyType,
        String receiverName,
        String receiverCity,
        String payload,
        String qrCodeBase64
) {}