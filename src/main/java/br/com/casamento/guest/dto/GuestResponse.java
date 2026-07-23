package br.com.casamento.guest.dto;

import java.time.OffsetDateTime;

public record GuestResponse(
        String id,
        String name,
        String status,
        String phone,
        String source,
        String accessToken,
        String qrCodeUrl,
        String rsvpStatus,
        OffsetDateTime createdAt
) {}
