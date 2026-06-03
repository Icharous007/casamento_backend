package br.com.casamento.wall.dto;

import br.com.casamento.domain.wall.WallPost;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WallPostResponse(
        UUID id,
        String postType,
        String guestName,
        String content,
        String audioUrl,
        OffsetDateTime createdAt
) {
    public static WallPostResponse from(WallPost post, String audioUrl) {
        return new WallPostResponse(
                post.id,
                post.postType,
                post.guest.name,
                post.content,
                audioUrl,
                post.createdAt
        );
    }
}
