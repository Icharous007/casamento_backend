package br.com.casamento.media.service;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.media.MediaAsset;
import br.com.casamento.domain.media.MediaComment;
import br.com.casamento.domain.media.MediaLike;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.media.dto.AddCommentRequest;
import br.com.casamento.media.dto.MediaCommentResponse;
import br.com.casamento.media.dto.MediaItemResponse;
import br.com.casamento.storage.R2StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class MediaService {

    private static final long MAX_PHOTO_BYTES = 10 * 1024 * 1024L;  // 10 MB
    private static final long MAX_VIDEO_BYTES = 50 * 1024 * 1024L;  // 50 MB
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of(
            "image/jpeg", "image/png", "image/heic", "image/heif");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/webm");

    @Inject
    R2StorageService r2;

    // ── Upload ──────────────────────────────────────────────────────────────

    @Transactional
    public MediaItemResponse upload(Guest guest, String filename, String contentType,
                                    long fileSize, InputStream data) {
        String mediaType = detectMediaType(contentType, fileSize);

        UUID mediaId = UUID.randomUUID();
        String ext = extensionFor(contentType);
        String r2Key = buildKey(guest.event.id, guest.id, mediaId, mediaType, ext);

        r2.upload(r2Key, data, fileSize, contentType);

        MediaAsset asset = new MediaAsset();
        asset.event = guest.event;
        asset.guest = guest;
        asset.mediaType = mediaType;
        asset.r2Key = r2Key;
        asset.originalFilename = filename;
        asset.contentType = contentType;
        asset.fileSizeBytes = fileSize;
        asset.status = "ACTIVE";
        asset.id = mediaId;
        asset.persist();

        String url = r2.publicUrl(r2Key);
        return MediaItemResponse.from(asset, url, null, false);
    }

    // ── Gallery (guest) ─────────────────────────────────────────────────────

    public Map<String, Object> listGallery(Event event, Guest guest, String sort,
                                           int page, int pageSize) {
        if (event.galleryHideAt != null && OffsetDateTime.now().isAfter(event.galleryHideAt)) {
            return Map.of("items", List.of(), "galleryHidden", true, "total", 0);
        }

        String orderBy = "top".equals(sort)
                ? "likeCount DESC, createdAt DESC"
                : "createdAt DESC";

        List<MediaAsset> assets = MediaAsset.find(
                "event.id = ?1 AND status = 'ACTIVE' ORDER BY " + orderBy,
                event.id
        ).page(page - 1, pageSize).list();

        long total = MediaAsset.count("event.id = ?1 AND status = 'ACTIVE'", event.id);

        List<MediaItemResponse> items = assets.stream().map(a -> {
            boolean liked = MediaLike.existsByMediaAndGuest(a, guest);
            String url = r2.publicUrl(a.r2Key);
            String thumb = a.r2ThumbKey != null ? r2.publicUrl(a.r2ThumbKey) : null;
            return MediaItemResponse.from(a, url, thumb, liked);
        }).toList();

        return Map.of("items", items, "galleryHidden", false,
                "page", page, "pageSize", pageSize, "total", total);
    }

    // ── Likes ────────────────────────────────────────────────────────────────

    @Transactional
    public void addLike(UUID mediaId, Guest guest) {
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null || !asset.event.id.equals(guest.event.id)) {
            throw AppException.notFound("Mídia não encontrada.");
        }
        if (MediaLike.existsByMediaAndGuest(asset, guest)) {
            throw AppException.conflict("LIKE_ALREADY_EXISTS", "Você já curtiu esta mídia.");
        }
        MediaLike like = new MediaLike();
        like.media = asset;
        like.guest = guest;
        like.persist();
        asset.likeCount++;
    }

    @Transactional
    public void removeLike(UUID mediaId, Guest guest) {
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null || !asset.event.id.equals(guest.event.id)) {
            throw AppException.notFound("Mídia não encontrada.");
        }
        MediaLike like = MediaLike.findByMediaAndGuest(asset, guest);
        if (like == null) {
            throw AppException.notFound("Curtida não encontrada.");
        }
        like.delete();
        if (asset.likeCount > 0) asset.likeCount--;
    }

    // ── Comments ─────────────────────────────────────────────────────────────

    @Transactional
    public MediaCommentResponse addComment(UUID mediaId, Guest guest, AddCommentRequest req) {
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null || !asset.event.id.equals(guest.event.id)) {
            throw AppException.notFound("Mídia não encontrada.");
        }
        MediaComment comment = new MediaComment();
        comment.media = asset;
        comment.guest = guest;
        comment.content = req.content();
        comment.persist();
        asset.commentCount++;
        return MediaCommentResponse.from(comment);
    }

    public List<MediaCommentResponse> listComments(UUID mediaId, UUID eventId) {
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null || !asset.event.id.equals(eventId)) {
            throw AppException.notFound("Mídia não encontrada.");
        }
        return MediaComment.find(
                "media.id = ?1 AND status = 'ACTIVE' ORDER BY createdAt ASC", mediaId
        ).<MediaComment>list().stream().map(MediaCommentResponse::from).toList();
    }

    // ── Admin operations ─────────────────────────────────────────────────────

    @Transactional
    public void removeComment(UUID commentId) {
        MediaComment comment = MediaComment.findById(commentId);
        if (comment == null) throw AppException.notFound("Comentário não encontrado.");
        comment.status = "REMOVED";
        // decrement comment_count
        MediaAsset asset = comment.media;
        if (asset.commentCount > 0) asset.commentCount--;
    }

    @Transactional
    public void hideMedia(UUID mediaId) {
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null) throw AppException.notFound("Mídia não encontrada.");
        asset.status = "HIDDEN";
    }

    @Transactional
    public void deleteMedia(UUID mediaId) {
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null) throw AppException.notFound("Mídia não encontrada.");
        // soft-delete
        asset.status = "DELETED";
    }

    public List<MediaItemResponse> listAllForAdmin(UUID eventId, int page, int pageSize) {
        List<MediaAsset> assets = MediaAsset.find(
                "event.id = ?1 AND status <> 'DELETED' ORDER BY createdAt DESC", eventId
        ).page(page - 1, pageSize).list();

        return assets.stream().map(a -> {
            String url = r2.publicUrl(a.r2Key);
            String thumb = a.r2ThumbKey != null ? r2.publicUrl(a.r2ThumbKey) : null;
            return MediaItemResponse.from(a, url, thumb, false);
        }).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String detectMediaType(String contentType, long fileSize) {
        if (ALLOWED_PHOTO_TYPES.contains(contentType)) {
            if (fileSize > MAX_PHOTO_BYTES) {
                throw AppException.badRequest("FILE_TOO_LARGE", "Fotos devem ter no máximo 10 MB.");
            }
            return "PHOTO";
        }
        if (ALLOWED_VIDEO_TYPES.contains(contentType)) {
            if (fileSize > MAX_VIDEO_BYTES) {
                throw AppException.badRequest("FILE_TOO_LARGE", "Vídeos devem ter no máximo 50 MB.");
            }
            return "VIDEO";
        }
        throw AppException.badRequest("INVALID_MEDIA_TYPE",
                "Formato não suportado. Fotos: JPEG, PNG, HEIC. Vídeos: MP4, MOV, WebM.");
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/heic", "image/heif" -> ".heic";
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            case "video/webm" -> ".webm";
            default -> "";
        };
    }

    private String buildKey(UUID eventId, UUID guestId, UUID mediaId, String mediaType, String ext) {
        String folder = "PHOTO".equals(mediaType) ? "photos" : "videos";
        return "media/" + eventId + "/" + folder + "/" + guestId + "/" + mediaId + ext;
    }
}
