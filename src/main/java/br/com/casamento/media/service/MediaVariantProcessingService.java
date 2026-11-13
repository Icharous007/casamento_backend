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
            QuarkusTransaction.requiringNew().run(() -> complete(payload, thumbKey, displayKey));
        } catch (Exception | LinkageError exception) {
            LOG.warnf(exception, "Variant processing failed for media %s", payload.mediaId());
            String error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            QuarkusTransaction.requiringNew().run(() -> fail(payload, error));
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

    JobPayload claimNext() {
        OffsetDateTime now = OffsetDateTime.now();
        int exhausted = entityManager.createQuery(
                        "UPDATE MediaVariantJob j SET j.status = 'FAILED', j.leaseExpiresAt = NULL, "
                                + "j.lastError = COALESCE(j.lastError, 'Attempt limit reached after worker lease expired'), "
                                + "j.updatedAt = :now "
                                + "WHERE j.attemptCount >= :maxAttempts AND "
                                + "(j.status = 'PENDING' OR (j.status = 'PROCESSING' AND j.leaseExpiresAt <= :now))")
                .setParameter("now", now)
                .setParameter("maxAttempts", MAX_ATTEMPTS)
                .executeUpdate();
        if (exhausted > 0) {
            LOG.warnf("Marked %d variant jobs FAILED after exhausting attempt limit", exhausted);
        }
        MediaVariantJob job = entityManager.createQuery(
                        "SELECT j FROM MediaVariantJob j JOIN FETCH j.media m JOIN FETCH m.guest "
                                + "WHERE j.attemptCount < :maxAttempts AND "
                                + "((j.status = 'PENDING' AND j.availableAt <= :now) "
                                + "OR (j.status = 'PROCESSING' AND j.leaseExpiresAt <= :now)) "
                                + "ORDER BY j.availableAt ASC", MediaVariantJob.class)
                .setParameter("now", now)
                .setParameter("maxAttempts", MAX_ATTEMPTS)
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
        return new JobPayload(media.id, media.event.id, media.guest.id, media.mediaType, media.contentType,
                media.r2Key, job.attemptCount);
    }

    void complete(JobPayload payload, String thumbKey, String displayKey) {
        MediaVariantJob job = entityManager.find(MediaVariantJob.class, payload.mediaId(), LockModeType.PESSIMISTIC_WRITE);
        if (!ownsAttempt(job, payload)) {
            return;
        }
        job.media.r2ThumbKey = thumbKey;
        job.media.r2DisplayKey = displayKey;
        job.status = "COMPLETED";
        job.leaseExpiresAt = null;
        job.lastError = null;
        LOG.infof("Media variants completed mediaId=%s attempt=%d", payload.mediaId(), payload.attempt());
    }

    void fail(JobPayload payload, String error) {
        MediaVariantJob job = entityManager.find(MediaVariantJob.class, payload.mediaId(), LockModeType.PESSIMISTIC_WRITE);
        if (!ownsAttempt(job, payload)) {
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

    private boolean ownsAttempt(MediaVariantJob job, JobPayload payload) {
        return job != null && "PROCESSING".equals(job.status) && job.attemptCount == payload.attempt();
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

    record JobPayload(UUID mediaId, UUID eventId, UUID guestId, String mediaType,
                      String contentType, String r2Key, int attempt) {
    }
}