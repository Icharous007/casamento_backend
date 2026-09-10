package br.com.casamento.media.service;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.common.filter.TraceIdFilter;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.media.MediaAsset;
import br.com.casamento.domain.media.MediaComment;
import br.com.casamento.domain.media.MediaLike;
import br.com.casamento.domain.media.MediaUploadIntent;
import br.com.casamento.domain.media.MediaVariantJob;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.media.dto.AddCommentRequest;
import br.com.casamento.media.dto.CreateMediaUploadIntentRequest;
import br.com.casamento.media.dto.MediaCommentResponse;
import br.com.casamento.media.dto.MediaItemResponse;
import br.com.casamento.media.dto.MediaUploadIntentResponse;
import br.com.casamento.storage.R2StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private static final long MAX_VIDEO_BYTES = 200 * 1024 * 1024L;
        private static final String QUICKTIME_CONTENT_TYPE = "video/quicktime";
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of(
            "image/jpeg", "image/png", "image/heic", "image/heif");
        private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", QUICKTIME_CONTENT_TYPE);
    private static final int MAX_PAGE_SIZE = 24;

    @Inject
    R2StorageService r2;

    @Inject
    MediaVariantService variantService;

    @Inject
    MediaErrorDiagnosticsService diagnosticsService;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "app.media.max-pending-uploads-per-guest", defaultValue = "3")
    int maxPendingUploadsPerGuest;

    public record DirectUploadVerification(UUID mediaId, String r2Key, boolean completed) {
    }

    // ── Upload ──────────────────────────────────────────────────────────────

    @Transactional
    public MediaItemResponse upload(Guest guest, String filename, String contentType,
                                    long fileSize, Path filePath, String caption) throws IOException {
        ensureGalleryWritable(guest.event.id);
        String normalizedCaption = normalizeCaption(caption);
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
        asset.caption = normalizedCaption;
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

        String url = resolveServedUrl(asset);
        String thumb = asset.r2ThumbKey != null ? r2.publicUrl(asset.r2ThumbKey) : null;
        String display = asset.r2DisplayKey != null ? r2.publicUrl(asset.r2DisplayKey) : null;
        logUpload("upload.success", guest, mediaId, contentType, fileSize,
                "persist", elapsedMs(startedAt));
        return MediaItemResponse.from(asset, url, thumb, display, false);
    }

    private String resolveServedUrl(MediaAsset asset) {
        if ("VIDEO".equals(asset.mediaType) && asset.r2CompressedKey != null) {
            return r2.publicUrl(asset.r2CompressedKey);
        }
        return r2.publicUrl(asset.r2Key);
    }

    private MediaItemResponse toResponse(MediaAsset asset, boolean likedByMe) {
        String thumb = asset.r2ThumbKey != null ? r2.publicUrl(asset.r2ThumbKey) : null;
        String display = asset.r2DisplayKey != null ? r2.publicUrl(asset.r2DisplayKey) : null;
        return MediaItemResponse.from(asset, resolveServedUrl(asset), thumb, display, likedByMe);
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

    // ── Direct upload (browser -> R2) ──────────────────────────────────────

    @Transactional
    public MediaUploadIntentResponse createDirectUploadIntent(Guest authenticatedGuest,
                                                               CreateMediaUploadIntentRequest request,
                                                               String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        lockIdempotencyKey(authenticatedGuest.id, idempotencyKey);
        Event event = Event.findById(authenticatedGuest.event.id);
        ensureGalleryWritable(event.id);

        String contentType = normalizeContentType(request.contentType());
        long fileSize = request.fileSizeBytes();
        String normalizedCaption = normalizeCaption(request.caption());
        String mediaType = detectMediaType(contentType, fileSize);
        Guest guest = Guest.findById(authenticatedGuest.id);
        MediaUploadIntent existing = MediaUploadIntent.findByGuestAndIdempotencyKey(guest.id, idempotencyKey);
        if (existing != null) {
            if (existing.expectedFileSize != fileSize || !existing.expectedContentType.equals(contentType)
                    || !java.util.Objects.equals(existing.media.caption, normalizedCaption)) {
                throw AppException.conflict("IDEMPOTENCY_KEY_REUSED", "A chave de envio já pertence a outro arquivo.");
            }
            if ("COMPLETED".equals(existing.status)) {
                return new MediaUploadIntentResponse(existing.id, existing.status, null, existing.expiresAt);
            }
            existing.status = "PENDING";
            existing.failureCode = null;
            return presignIntent(existing);
        }

        long pending = expireAbandonedIntents(guest.id);
        if (pending >= maxPendingUploadsPerGuest) {
            throw AppException.conflict("UPLOAD_LIMIT_EXCEEDED", "Conclua ou cancele os envios pendentes antes de iniciar outro.");
        }

        UUID mediaId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.id = mediaId;
        asset.event = event;
        asset.guest = guest;
        asset.mediaType = mediaType;
        asset.status = "PROCESSING";
        asset.r2Key = buildKey(event.id, guest.id, mediaId, mediaType, extensionFor(contentType));
        asset.originalFilename = request.filename().strip();
        asset.contentType = contentType;
        asset.caption = normalizedCaption;
        asset.fileSizeBytes = fileSize;
        asset.persist();

        MediaUploadIntent intent = new MediaUploadIntent();
        intent.id = mediaId;
        intent.media = asset;
        intent.guest = guest;
        intent.idempotencyKey = idempotencyKey;
        intent.expectedContentType = contentType;
        intent.expectedFileSize = fileSize;
        intent.expiresAt = OffsetDateTime.ofInstant(r2.uploadUrlExpiresAt(), ZoneOffset.UTC);
        intent.persist();
        return presignIntent(intent);
    }

    @Transactional
    public DirectUploadVerification loadDirectUploadVerification(Guest guest, UUID mediaId) {
        MediaUploadIntent intent = MediaUploadIntent.find(
                "SELECT i FROM MediaUploadIntent i JOIN FETCH i.media WHERE i.id = ?1 AND i.guest.id = ?2",
                mediaId, guest.id).firstResult();
        if (intent == null) {
            throw AppException.notFound("Envio de mídia não encontrado.");
        }
        if ("COMPLETED".equals(intent.status)) {
            return new DirectUploadVerification(mediaId, intent.media.r2Key, true);
        }
        if (!"PENDING".equals(intent.status) || OffsetDateTime.now().isAfter(intent.expiresAt)) {
            if ("PENDING".equals(intent.status)) {
                intent.status = "EXPIRED";
            }
            throw AppException.conflict("UPLOAD_EXPIRED", "O link de envio expirou. Inicie o envio novamente.");
        }
        return new DirectUploadVerification(mediaId, intent.media.r2Key, false);
    }

    @Transactional
    public MediaItemResponse publishDirectUpload(Guest guest, UUID mediaId,
                                                  R2StorageService.StoredObjectMetadata metadata,
                                                  byte[] header) {
        ensureGalleryWritable(guest.event.id);
        MediaUploadIntent intent = entityManager.find(MediaUploadIntent.class, mediaId, LockModeType.PESSIMISTIC_WRITE);
        if (intent == null || !intent.guest.id.equals(guest.id)) {
            throw AppException.notFound("Envio de mídia não encontrado.");
        }
        MediaAsset asset = MediaAsset.find(
                "SELECT a FROM MediaAsset a LEFT JOIN FETCH a.guest WHERE a.id = ?1", mediaId).firstResult();
        if (asset == null) {
            throw AppException.notFound("Mídia não encontrada.");
        }
        if ("COMPLETED".equals(intent.status)) {
            return toResponse(asset, false);
        }
        if (!"PENDING".equals(intent.status) || OffsetDateTime.now().isAfter(intent.expiresAt)) {
            intent.status = "EXPIRED";
            throw AppException.conflict("UPLOAD_EXPIRED", "O link de envio expirou. Inicie o envio novamente.");
        }
        if (metadata == null || metadata.contentLength() != intent.expectedFileSize
                || !intent.expectedContentType.equals(metadata.contentType())
                || !hasExpectedSignature(intent.expectedContentType, header)) {
            intent.status = "FAILED";
            intent.failureCode = "OBJECT_VERIFICATION_FAILED";
            throw AppException.badRequest("UPLOAD_VERIFICATION_FAILED", "Não foi possível validar o arquivo enviado.");
        }

        asset.status = "ACTIVE";
        intent.status = "COMPLETED";
        intent.completedAt = OffsetDateTime.now();
        MediaVariantJob job = new MediaVariantJob();
        job.media = asset;
        job.availableAt = OffsetDateTime.now();
        job.persist();
        return toResponse(asset, false);
    }

    private MediaUploadIntentResponse presignIntent(MediaUploadIntent intent) {
        R2StorageService.PresignedUpload upload = r2.presignUpload(intent.media.r2Key, intent.expectedContentType);
        intent.expiresAt = OffsetDateTime.ofInstant(upload.expiresAt(), ZoneOffset.UTC);
        return new MediaUploadIntentResponse(intent.id, intent.status, upload.url(), intent.expiresAt);
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw AppException.badRequest("IDEMPOTENCY_KEY_INVALID", "Chave de envio inválida.");
        }
    }

    private long expireAbandonedIntents(UUID guestId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<MediaUploadIntent> pendingIntents = MediaUploadIntent.find(
                "SELECT i FROM MediaUploadIntent i JOIN FETCH i.media "
                        + "WHERE i.guest.id = ?1 AND i.status = 'PENDING'", guestId).list();
        for (MediaUploadIntent pendingIntent : pendingIntents) {
            if (now.isAfter(pendingIntent.expiresAt) || r2.head(pendingIntent.media.r2Key) == null) {
                pendingIntent.status = "EXPIRED";
            }
        }
        entityManager.flush();
        return pendingIntents.stream()
                .filter(intent -> "PENDING".equals(intent.status) && intent.expiresAt.isAfter(now))
                .count();
    }

    private void lockIdempotencyKey(UUID guestId, String idempotencyKey) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, guestId + ":" + idempotencyKey)
                .getSingleResult();
    }

    private String normalizeContentType(String contentType) {
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType).trim().toLowerCase();
    }

    private String normalizeCaption(String caption) {
        if (caption == null) {
            return null;
        }
        String normalized = caption.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > 500) {
            throw AppException.badRequest("CAPTION_TOO_LONG", "A legenda deve ter no máximo 500 caracteres.");
        }
        return normalized;
    }

    private boolean hasExpectedSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/jpeg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "image/png" -> header.length >= 8
                    && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                    && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A;
            case "image/heic", "image/heif", "video/mp4", QUICKTIME_CONTENT_TYPE -> header.length >= 12
                    && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
            default -> false;
        };
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
        String orderBy = popular ? "a.likeCount DESC, a.createdAt DESC" : "a.createdAt DESC";

        List<MediaAsset> assets = MediaAsset.find(
            "SELECT a FROM MediaAsset a LEFT JOIN FETCH a.guest "
                + "WHERE a.event.id = ?1 AND a.status = 'ACTIVE' ORDER BY " + orderBy,
                event.id
        ).page(safePage - 1, safePageSize).list();

        long total = MediaAsset.count("event.id = ?1 AND status = 'ACTIVE'", event.id);

        Set<UUID> likedIds = likedMediaIds(assets, guest);

        List<MediaItemResponse> items = assets.stream().map(a -> {
            boolean liked = likedIds.contains(a.id);
            String url = resolveServedUrl(a);
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
        ensureGalleryWritable(guest.event.id);
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null || !asset.event.id.equals(guest.event.id)) {
            throw AppException.notFound("Mídia não encontrada.");
        }
        int inserted = entityManager.createNativeQuery(
                        "INSERT INTO media_likes (id, media_id, guest_id, created_at) "
                                + "VALUES (gen_random_uuid(), ?1, ?2, NOW()) "
                                + "ON CONFLICT (media_id, guest_id) DO NOTHING")
                .setParameter(1, mediaId)
                .setParameter(2, guest.id)
                .executeUpdate();
        if (inserted == 0) {
            throw AppException.conflict("LIKE_ALREADY_EXISTS", "Você já curtiu esta mídia.");
        }
        MediaAsset.update("likeCount = likeCount + 1 WHERE id = ?1", mediaId);
    }

    @Transactional
    public void removeLike(UUID mediaId, Guest guest) {
        ensureGalleryWritable(guest.event.id);
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null || !asset.event.id.equals(guest.event.id)) {
            throw AppException.notFound("Mídia não encontrada.");
        }
        MediaLike like = MediaLike.findByMediaAndGuest(asset, guest);
        if (like == null) {
            throw AppException.notFound("Curtida não encontrada.");
        }
        like.delete();
        MediaAsset.update("likeCount = CASE WHEN likeCount > 0 THEN likeCount - 1 ELSE 0 END WHERE id = ?1", mediaId);
    }

    // ── Comments ─────────────────────────────────────────────────────────────

    @Transactional
    public MediaCommentResponse addComment(UUID mediaId, Guest guest, AddCommentRequest req) {
        ensureGalleryWritable(guest.event.id);
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null || !asset.event.id.equals(guest.event.id)) {
            throw AppException.notFound("Mídia não encontrada.");
        }
        MediaComment comment = new MediaComment();
        comment.media = asset;
        comment.guest = guest;
        comment.content = req.content();
        comment.persist();
        MediaAsset.update("commentCount = commentCount + 1 WHERE id = ?1", mediaId);
        return MediaCommentResponse.from(comment);
    }

    public List<MediaCommentResponse> listComments(UUID mediaId, UUID eventId) {
        MediaAsset asset = MediaAsset.findById(mediaId);
        if (asset == null || !asset.event.id.equals(eventId)) {
            throw AppException.notFound("Mídia não encontrada.");
        }
        return MediaComment.find(
            "SELECT c FROM MediaComment c JOIN FETCH c.guest "
                + "WHERE c.media.id = ?1 AND c.status = 'ACTIVE' ORDER BY c.createdAt ASC", mediaId
        ).<MediaComment>list().stream().map(MediaCommentResponse::from).toList();
    }

    // ── Admin operations ─────────────────────────────────────────────────────

    @Transactional
    public void removeComment(UUID commentId) {
        MediaComment comment = MediaComment.findById(commentId);
        if (comment == null) throw AppException.notFound("Comentário não encontrado.");
        MediaAsset asset = comment.media;
        int removed = MediaComment.update("status = 'REMOVED' WHERE id = ?1 AND status = 'ACTIVE'", commentId);
        if (removed > 0) {
            MediaAsset.update(
                "commentCount = CASE WHEN commentCount > 0 THEN commentCount - 1 ELSE 0 END WHERE id = ?1",
                asset.id);
        }
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
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        List<MediaAsset> assets = MediaAsset.find(
            "SELECT a FROM MediaAsset a LEFT JOIN FETCH a.guest "
                + "WHERE a.event.id = ?1 AND a.status <> 'DELETED' ORDER BY a.createdAt DESC", eventId
        ).page(safePage - 1, safePageSize).list();

        return assets.stream().map(a -> {
            String url = resolveServedUrl(a);
            String thumb = a.r2ThumbKey != null ? r2.publicUrl(a.r2ThumbKey) : null;
            String display = a.r2DisplayKey != null ? r2.publicUrl(a.r2DisplayKey) : null;
            return MediaItemResponse.from(a, url, thumb, display, false);
        }).toList();
    }

    /**
     * Queues thumb/display regeneration for previously-uploaded assets that predate
     * variant generation. The scheduler does the R2 and image work outside this transaction.
     */
    @Transactional
    public int backfillVariants(UUID eventId, int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 50);
        List<MediaAsset> assets = MediaAsset.find(
                "event.id = ?1 AND status = 'ACTIVE' AND r2ThumbKey IS NULL ORDER BY createdAt ASC",
                eventId
        ).page(0, safeLimit).list();

        int queued = 0;
        for (MediaAsset asset : assets) {
            MediaVariantJob job = MediaVariantJob.findById(asset.id);
            if (job == null) {
                job = new MediaVariantJob();
                job.media = asset;
                job.availableAt = OffsetDateTime.now();
                job.persist();
                queued++;
            } else if ("FAILED".equals(job.status)) {
                job.status = "PENDING";
                job.attemptCount = 0;
                job.availableAt = OffsetDateTime.now();
                job.lastError = null;
                queued++;
            }
        }
        return queued;
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
                throw AppException.badRequest("FILE_TOO_LARGE", "Vídeos devem ter no máximo 200 MB.");
            }
            return "VIDEO";
        }
        throw AppException.badRequest("INVALID_MEDIA_TYPE",
            "Formato não suportado. Fotos: JPEG, PNG, HEIC. Vídeos: MP4 ou MOV.");
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/heic", "image/heif" -> ".heic";
            case "video/mp4" -> ".mp4";
            case QUICKTIME_CONTENT_TYPE -> ".mov";
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

    private void ensureGalleryWritable(UUID eventId) {
        Event event = Event.findById(eventId);
        if (event == null || (event.galleryHideAt != null && OffsetDateTime.now().isAfter(event.galleryHideAt))) {
            throw AppException.conflict("GALLERY_CLOSED", "A galeria não está mais disponível para alterações.");
        }
    }
}
