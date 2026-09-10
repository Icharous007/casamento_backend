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
        String displayUrl,
        String contentType,
        String caption,
        Long fileSizeBytes,
        int likeCount,
        int commentCount,
        boolean likedByMe,
        OffsetDateTime uploadedAt,
        UUID guestId,
        String guestName
) {
    public static MediaItemResponse from(MediaAsset asset, String url, String thumbUrl, String displayUrl, boolean likedByMe) {
        return new MediaItemResponse(
                asset.id,
                asset.mediaType,
                asset.status,
                url,
                thumbUrl,
                displayUrl,
                asset.contentType,
                asset.caption,
                asset.fileSizeBytes,
                asset.likeCount,
                asset.commentCount,
                likedByMe,
                asset.createdAt,
                asset.guest != null ? asset.guest.id : null,
                asset.guest != null ? asset.guest.name : null
        );
    }
}
