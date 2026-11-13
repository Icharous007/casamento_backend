package br.com.casamento.media.dto;

import java.util.List;

public record MediaFeedResponse(
        List<MediaItemResponse> items,
        Integer nextCursor,
        boolean hasMore,
        boolean galleryHidden
) {
}
