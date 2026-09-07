package br.com.casamento.media.dto;

import java.util.List;

/**
 * Paginated response for error listing.
 */
public record MediaErrorEventPage(
    List<MediaErrorEventResponse> items,
    long total,
    int page,
    int pageSize,
    boolean hasMore
) {}
