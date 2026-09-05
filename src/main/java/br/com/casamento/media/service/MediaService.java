package br.com.casamento.media.service;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.common.filter.TraceIdFilter;
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
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class MediaService {

    private static final Logger LOG = Logger.getLogger(MediaService.class);

    private static final long MAX_PHOTO_BYTES = 10 * 1024 * 1024L;  // 10 MB
    private static final long MAX_VIDEO_BYTES = 50 * 1024 * 1024L;  // 50 MB
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of(
            "image/jpeg", "image/png", "image/heic", "image/heif");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/webm");
    private static final int MAX_PAGE_SIZE = 24;

    @Inject
    R2StorageService r2;

    @Inject
    MediaVariantService variantService;

    // ── Upload ──────────────────────────────────────────────────────────────

    @Transactional
    public MediaItemResponse upload(Guest guest, String filename, String contentType,
                                    long fileSize, Path filePath) throws IOException {
        long startedAt = System.nanoTime();
        UUID mediaId = UUID.randomUUID();
        logUpload("upload.start", guest, mediaId, contentType, fileSize, null, null);

        String mediaType;
        try {
            mediaType = detectMediaType(contentType, fileSize);
        } catch (AppException exception) {
            logUploadFailure("validation", guest, mediaId, contentType, fileSize, exception.getCode(), startedAt);
            throw exception;
        }

        String ext = extensionFor(contentType);
        String r2Key = buildKey(guest.event.id, guest.id, mediaId, mediaType, ext);

        try (InputStream data = Files.newInputStream(filePath)) {
            long storageStartedAt = System.nanoTime();
            try {
                r2.upload(r2Key, data, fileSize, contentType);
                logUpload("upload.original.success", guest, mediaId, contentType, fileSize,
                        "storage", elapsedMs(storageStartedAt));
            } catch (RuntimeException exception) {
                logUploadFailure("storage", guest, mediaId, contentType, fileSize,
                        "STORAGE_ERROR", startedAt);
                throw exception;
            }
        }

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

        Optional<MediaVariantService.Variants> variants = "PHOTO".equals(mediaType)
                ? variantService.generatePhotoVariants(filePath, contentType)
                : variantService.generateVideoPoster(filePath);
        if (variants.isPresent()) {
            long variantStartedAt = System.nanoTime();
            try {
                uploadVariants(asset, variants.get());
                logUpload("upload.variants.success", guest, mediaId, contentType, fileSize,
                        "variants", elapsedMs(variantStartedAt));
            } catch (RuntimeException exception) {
                logUploadFailure("variant-storage", guest, mediaId, contentType, fileSize,
                        "VARIANT_STORAGE_ERROR", startedAt);
                throw exception;
            }
        } else {
            logUpload("upload.variants.degraded", guest, mediaId, contentType, fileSize,
                    "variants", elapsedMs(startedAt));
        }

        asset.persist();

        String url = r2.publicUrl(r2Key);
        String thumb = asset.r2ThumbKey != null ? r2.publicUrl(asset.r2ThumbKey) : null;
        String display = asset.r2DisplayKey != null ? r2.publicUrl(asset.r2DisplayKey) : null;
        logUpload("upload.success", guest, mediaId, contentType, fileSize,
                "persist", elapsedMs(startedAt));
        return MediaItemResponse.from(asset, url, thumb, display, false);
    }

    private void logUpload(String event, Guest guest, UUID mediaId, String contentType,
                           long fileSize, String stage, Long durationMs) {
        String outcome = "started";
        if (event.endsWith("success")) {
            outcome = "success";
        } else if (event.endsWith("degraded")) {
            outcome = "degraded";
        }
        LOG.infof("media_upload event=%s traceId=%s eventId=%s guestId=%s mediaId=%s contentType=%s fileSizeBytes=%d stage=%s durationMs=%s outcome=%s",
                event, traceId(), guest.event.id, guest.id, mediaId, contentType, fileSize,
            stage, durationMs, outcome);
    }

    private void logUploadFailure(String stage, Guest guest, UUID mediaId, String contentType,
                                  long fileSize, String errorCode, long startedAt) {
        LOG.errorf("media_upload event=upload.failure traceId=%s eventId=%s guestId=%s mediaId=%s contentType=%s fileSizeBytes=%d stage=%s durationMs=%d errorCode=%s outcome=failure",
                traceId(), guest.event.id, guest.id, mediaId, contentType, fileSize, stage,
                elapsedMs(startedAt), errorCode);
    }

    private String traceId() {
        Object value = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return value != null ? value.toString() : "none";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private void uploadVariants(MediaAsset asset, MediaVariantService.Variants v) {
        UUID folderId = asset.guest != null ? asset.guest.id : asset.id;
        String thumbKey = buildVariantKey(asset.event.id, folderId, asset.id, "thumbs");
        String displayKey = buildVariantKey(asset.event.id, folderId, asset.id, "display");
        r2.upload(thumbKey, new ByteArrayInputStream(v.thumbnail()), v.thumbnail().length, "image/jpeg");
        r2.upload(displayKey, new ByteArrayInputStream(v.display()), v.display().length, "image/jpeg");
        asset.r2ThumbKey = thumbKey;
        asset.r2DisplayKey = displayKey;
    }

    // ── Gallery (guest) ─────────────────────────────────────────────────────

    public Map<String, Object> listGallery(Event event, Guest guest, String sort,
                                           int page, int pageSize) {
        if (event.galleryHideAt != null && OffsetDateTime.now().isAfter(event.galleryHideAt)) {
            return Map.of("items", List.of(), "galleryHidden", true, "total", 0,
                    "page", Math.max(1, page), "pageSize", pageSize, "hasMore", false);
        }

        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        boolean popular = "popular".equals(sort) || "top".equals(sort);
        String orderBy = popular ? "likeCount DESC, createdAt DESC" : "createdAt DESC";

        List<MediaAsset> assets = MediaAsset.find(
                "event.id = ?1 AND status = 'ACTIVE' ORDER BY " + orderBy,
                event.id
        ).page(safePage - 1, safePageSize).list();

        long total = MediaAsset.count("event.id = ?1 AND status = 'ACTIVE'", event.id);

        Set<UUID> likedIds = likedMediaIds(assets, guest);

        List<MediaItemResponse> items = assets.stream().map(a -> {
            boolean liked = likedIds.contains(a.id);
            String url = r2.publicUrl(a.r2Key);
            String thumb = a.r2ThumbKey != null ? r2.publicUrl(a.r2ThumbKey) : null;
            String display = a.r2DisplayKey != null ? r2.publicUrl(a.r2DisplayKey) : null;
            return MediaItemResponse.from(a, url, thumb, display, liked);
        }).toList();

        boolean hasMore = (long) safePage * safePageSize < total;

        return Map.of("items", items, "galleryHidden", false,
                "page", safePage, "pageSize", safePageSize, "total", total, "hasMore", hasMore);
    }

    private Set<UUID> likedMediaIds(List<MediaAsset> assets, Guest guest) {
        if (assets.isEmpty()) {
            return Set.of();
        }
        List<UUID> assetIds = assets.stream().map(a -> a.id).toList();
        return new HashSet<>(MediaLike.find("media.id IN ?1 AND guest = ?2", assetIds, guest)
                .<MediaLike>list().stream().map(l -> l.media.id).collect(Collectors.toSet()));
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
            String display = a.r2DisplayKey != null ? r2.publicUrl(a.r2DisplayKey) : null;
            return MediaItemResponse.from(a, url, thumb, display, false);
        }).toList();
    }

    /**
     * Regenerates thumb/display variants for previously-uploaded assets that predate
     * variant generation (r2ThumbKey still null). Downloads the original from R2,
     * runs it through the same pipeline used at upload time, and re-uploads variants.
     */
    @Transactional
    public int backfillVariants(UUID eventId, int limit) {
        List<MediaAsset> assets = MediaAsset.find(
                "event.id = ?1 AND status = 'ACTIVE' AND r2ThumbKey IS NULL ORDER BY createdAt ASC",
                eventId
        ).page(0, limit).list();

        int processed = 0;
        for (MediaAsset asset : assets) {
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("backfill-", extensionFor(asset.contentType));
                Files.write(tempFile, r2.download(asset.r2Key));

                Optional<MediaVariantService.Variants> variants = "PHOTO".equals(asset.mediaType)
                        ? variantService.generatePhotoVariants(tempFile, asset.contentType)
                        : variantService.generateVideoPoster(tempFile);

                if (variants.isPresent()) {
                    uploadVariants(asset, variants.get());
                    processed++;
                }
            } catch (Exception e) {
                LOG.warnf(e, "Backfill failed for media %s", asset.id);
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                        // best effort cleanup
                    }
                }
            }
        }
        return processed;
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

    private String buildVariantKey(UUID eventId, UUID guestId, UUID mediaId, String variantFolder) {
        return "media/" + eventId + "/" + variantFolder + "/" + guestId + "/" + mediaId + ".jpg";
    }
}
