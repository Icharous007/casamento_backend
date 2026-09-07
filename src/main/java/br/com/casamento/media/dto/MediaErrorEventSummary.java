package br.com.casamento.media.dto;

import java.time.OffsetDateTime;

/**
 * Summary DTO for admin diagnostics dashboard.
 */
public record MediaErrorEventSummary(
    String errorCode,
    String errorCategory,
    String stage,
    String source,
    long count,
    long unresolvedCount,
    OffsetDateTime latestAt,
    Integer[] recentHttpStatuses // up to 5 most recent distinct statuses
) {}
