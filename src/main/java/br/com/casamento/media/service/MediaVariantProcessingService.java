package br.com.casamento.media.service;

import br.com.casamento.domain.media.MediaAsset;
import br.com.casamento.domain.media.MediaVariantJob;
import br.com.casamento.storage.R2StorageService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MediaVariantProcessingService {

    private static final Logger LOG = Logger.getLogger(MediaVariantProcessingService.class);
    private static final int MAX_ATTEMPTS = 3;

    @Inject
    EntityManager entityManager;

    @Inject
    R2StorageService r2;

    @Inject
    MediaVariantService variantService;

    @Inject
    MediaErrorDiagnosticsService diagnosticsService;

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void processNext() {
        JobPayload payload = QuarkusTransaction.requiringNew().call(this::claimNext);
        if (payload == null) {
            return;
        }

        Path downloaded = null;
        try {
            downloaded = Files.createTempFile("media-variant-", extensionFor(payload.contentType()));
            r2.downloadTo(payload.r2Key(), downloaded);
            Optional<MediaVariantService.Variants> variants = "PHOTO".equals(payload.mediaType())
                    ? variantService.generatePhotoVariants(downloaded, payload.contentType())
                    : variantService.generateVideoPoster(downloaded);
            if (variants.isEmpty()) {
                throw new IllegalStateException("Variant generation returned no result");
            }

            String thumbKey = buildVariantKey(payload, "thumbs");
            String displayKey = buildVariantKey(payload, "display");
            MediaVariantService.Variants generated = variants.get();
            r2.upload(thumbKey, new ByteArrayInputStream(generated.thumbnail()), generated.thumbnail().length, "image/jpeg");
            r2.upload(displayKey, new ByteArrayInputStream(generated.display()), generated.display().length, "image/jpeg");
            QuarkusTransaction.requiringNew().run(() -> complete(payload.mediaId(), thumbKey, displayKey));
        } catch (Exception exception) {
            LOG.warnf(exception, "Variant processing failed for media %s", payload.mediaId());
            String error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            QuarkusTransaction.requiringNew().run(() -> fail(payload.mediaId(), error));
        } finally {
            if (downloaded != null) {
                try {
                    Files.deleteIfExists(downloaded);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    private JobPayload claimNext() {
        OffsetDateTime now = OffsetDateTime.now();
        MediaVariantJob job = entityManager.createQuery(
                        "SELECT j FROM MediaVariantJob j JOIN FETCH j.media m JOIN FETCH m.guest "
                                + "WHERE (j.status = 'PENDING' AND j.availableAt <= :now) "
                                + "OR (j.status = 'PROCESSING' AND j.leaseExpiresAt <= :now) "
                                + "ORDER BY j.availableAt ASC", MediaVariantJob.class)
                .setParameter("now", now)
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (job == null) {
            return null;
        }
        job.status = "PROCESSING";
        job.attemptCount++;
        job.leaseExpiresAt = now.plusMinutes(5);
        MediaAsset media = job.media;
        return new JobPayload(media.id, media.event.id, media.guest.id, media.mediaType, media.contentType, media.r2Key);
    }

    private void complete(UUID mediaId, String thumbKey, String displayKey) {
        MediaVariantJob job = entityManager.find(MediaVariantJob.class, mediaId, LockModeType.PESSIMISTIC_WRITE);
        if (job == null || !"PROCESSING".equals(job.status)) {
            return;
        }
        job.media.r2ThumbKey = thumbKey;
        job.media.r2DisplayKey = displayKey;
        job.status = "COMPLETED";
        job.leaseExpiresAt = null;
        job.lastError = null;
    }

    private void fail(UUID mediaId, String error) {
        MediaVariantJob job = entityManager.find(MediaVariantJob.class, mediaId, LockModeType.PESSIMISTIC_WRITE);
        if (job == null || !"PROCESSING".equals(job.status)) {
            return;
        }
        job.lastError = error.length() > 500 ? error.substring(0, 500) : error;
        job.leaseExpiresAt = null;
        if (job.attemptCount >= MAX_ATTEMPTS) {
            job.status = "FAILED";
            return;
        }
        job.status = "PENDING";
        job.availableAt = OffsetDateTime.now().plusMinutes(job.attemptCount);
    }

    private String buildVariantKey(JobPayload payload, String variantFolder) {
        return "media/" + payload.eventId() + "/" + variantFolder + "/" + payload.guestId()
                + "/" + payload.mediaId() + ".jpg";
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/heic", "image/heif" -> ".heic";
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            default -> ".bin";
        };
    }

    private record JobPayload(UUID mediaId, UUID eventId, UUID guestId, String mediaType,
                              String contentType, String r2Key) {
    }
}