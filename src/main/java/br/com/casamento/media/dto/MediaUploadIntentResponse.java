package br.com.casamento.media.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MediaUploadIntentResponse(
        UUID mediaId,
        String status,
        String uploadUrl,
        OffsetDateTime uploadExpiresAt
) {
}