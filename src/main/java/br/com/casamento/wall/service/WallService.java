package br.com.casamento.wall.service;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.wall.WallPost;
import br.com.casamento.storage.R2StorageService;
import br.com.casamento.wall.dto.CreateTextPostRequest;
import br.com.casamento.wall.dto.WallPostResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class WallService {

    private static final int MAX_DURATION_SECONDS = 60;

    @Inject
    R2StorageService r2;

    // ── Guest operations ────────────────────────────────────────────────────

    @Transactional
    public WallPostResponse createTextPost(Guest guest, CreateTextPostRequest req) {
        WallPost post = new WallPost();
        post.event = guest.event;
        post.guest = guest;
        post.postType = "TEXT";
        post.content = req.content();
        post.persist();
        return WallPostResponse.from(post, null);
    }

    @Transactional
    public WallPostResponse createAudioPost(Guest guest, int durationSec,
                                            InputStream audioData, long fileSize,
                                            String contentType) {
        if (durationSec > MAX_DURATION_SECONDS) {
            throw AppException.badRequest("AUDIO_TOO_LONG",
                    "Áudio deve ter no máximo 60 segundos.");
        }
        UUID postId = UUID.randomUUID();
        String r2Key = "wall/" + guest.event.id + "/" + postId + ".audio";
        r2.upload(r2Key, audioData, fileSize, contentType != null ? contentType : "audio/mpeg");

        WallPost post = new WallPost();
        post.id = postId;
        post.event = guest.event;
        post.guest = guest;
        post.postType = "AUDIO";
        post.r2AudioKey = r2Key;
        post.persist();

        String audioUrl = r2.publicUrl(r2Key);
        return WallPostResponse.from(post, audioUrl);
    }

    public Map<String, Object> list(UUID eventId, int page, int pageSize) {
        List<WallPost> posts = WallPost.find(
                "event.id = ?1 AND status = 'ACTIVE' ORDER BY createdAt DESC", eventId
        ).page(page - 1, pageSize).list();

        long total = WallPost.count("event.id = ?1 AND status = 'ACTIVE'", eventId);

        List<WallPostResponse> items = posts.stream().map(p -> {
            String audioUrl = p.r2AudioKey != null ? r2.publicUrl(p.r2AudioKey) : null;
            return WallPostResponse.from(p, audioUrl);
        }).toList();

        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total);
    }

    // ── Admin operations ────────────────────────────────────────────────────

    @Transactional
    public void removePost(UUID postId) {
        WallPost post = WallPost.findById(postId);
        if (post == null) throw AppException.notFound("Post não encontrado.");
        post.status = "REMOVED";
    }

    public Map<String, Object> listForAdmin(UUID eventId, String status, int page, int pageSize) {
        String query = status != null && !status.isBlank()
                ? "event.id = ?1 AND status = '" + sanitizeStatus(status) + "' ORDER BY createdAt DESC"
                : "event.id = ?1 ORDER BY createdAt DESC";

        List<WallPost> posts = WallPost.find(query, eventId).page(page - 1, pageSize).list();

        String countQuery = status != null && !status.isBlank()
                ? "event.id = ?1 AND status = '" + sanitizeStatus(status) + "'"
                : "event.id = ?1";
        long total = WallPost.count(countQuery, eventId);

        List<WallPostResponse> items = posts.stream().map(p -> {
            String audioUrl = p.r2AudioKey != null ? r2.publicUrl(p.r2AudioKey) : null;
            return WallPostResponse.from(p, audioUrl);
        }).toList();

        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total);
    }

    public Map<String, Object> summary(UUID eventId) {
        long totalActive = WallPost.count("event.id = ?1 AND status = 'ACTIVE'", eventId);
        long totalRemoved = WallPost.count("event.id = ?1 AND status = 'REMOVED'", eventId);
        long textPosts = WallPost.count("event.id = ?1 AND postType = 'TEXT' AND status = 'ACTIVE'", eventId);
        long audioPosts = WallPost.count("event.id = ?1 AND postType = 'AUDIO' AND status = 'ACTIVE'", eventId);
        return Map.of(
                "totalActive", totalActive,
                "totalRemoved", totalRemoved,
                "textPosts", textPosts,
                "audioPosts", audioPosts
        );
    }

    private String sanitizeStatus(String status) {
        return switch (status.toUpperCase()) {
            case "ACTIVE" -> "ACTIVE";
            case "REMOVED" -> "REMOVED";
            default -> throw AppException.badRequest("INVALID_STATUS", "Status inválido: " + status);
        };
    }
}
