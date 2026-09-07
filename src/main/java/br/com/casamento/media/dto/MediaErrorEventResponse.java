package br.com.casamento.media.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for listing media error events.
 */
public record MediaErrorEventResponse(
    UUID id,
    String source,
    String stage,
    String errorCode,
    String errorCategory,
    String errorMessage,
    Integer httpStatus,
    String axiosCode,
    String mediaType,
    String contentType,
    Long fileSizeBytes,
    Integer durationMs,
    Boolean retryable,
    String traceId,
    String flowId,
    String attemptId,
    UUID eventId,
    UUID guestId,
    UUID mediaId,
    OffsetDateTime createdAt,
    OffsetDateTime resolvedAt,
    String resolutionNote
) {}
