package br.com.casamento.media.dto;

import br.com.casamento.domain.media.MediaAsset;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MediaItemResponse(
        UUID id,
        String mediaType,
        String status,
        String url,
        String thumbnailUrl,
        String contentType,
        Long fileSizeBytes,
        int likeCount,
        int commentCount,
        boolean likedByMe,
        OffsetDateTime uploadedAt
) {
    public static MediaItemResponse from(MediaAsset asset, String url, String thumbUrl, boolean likedByMe) {
        return new MediaItemResponse(
                asset.id,
                asset.mediaType,
                asset.status,
                url,
                thumbUrl,
                asset.contentType,
                asset.fileSizeBytes,
                asset.likeCount,
                asset.commentCount,
                likedByMe,
                asset.createdAt
        );
    }
}
