package br.com.casamento.media.dto;

import br.com.casamento.domain.media.MediaComment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MediaCommentResponse(
        UUID id,
        UUID guestId,
        String guestName,
        String content,
        OffsetDateTime createdAt
) {
    public static MediaCommentResponse from(MediaComment comment) {
        return new MediaCommentResponse(
                comment.id,
                comment.guest.id,
                comment.guest.name,
                comment.content,
                comment.createdAt
        );
    }
}
