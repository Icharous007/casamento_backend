package br.com.casamento.media.service;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.media.MediaAsset;
import br.com.casamento.domain.media.MediaVariantJob;
import br.com.casamento.storage.R2StorageService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestProfile(MediaVariantProcessingServiceTest.Profile.class)
class MediaVariantProcessingServiceTest {
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.scheduler.enabled", "false");
        }
    }

    @Inject
    MediaVariantProcessingService service;

    @Inject
    EntityManager entityManager;

    @InjectMock
    MediaVariantService variants;

    @InjectMock
    R2StorageService storage;

    private MediaVariantJob job(int attempts, String status) {
        Event event = new Event();
        event.slug = "variant-test-" + UUID.randomUUID();
        event.title = "Variant test";
        event.coupleNames = "Test";
        event.eventDate = OffsetDateTime.now().plusDays(1);
        event.persist();
        Guest guest = new Guest();
        guest.event = event;
        guest.name = "Test";
        guest.persist();
        MediaAsset media = new MediaAsset();
        media.id = UUID.randomUUID();
        media.event = event;
        media.guest = guest;
        media.mediaType = "PHOTO";
        media.contentType = "image/heic";
        media.r2Key = "test/" + media.id;
        media.status = "ACTIVE";
        media.persist();
        MediaVariantJob job = new MediaVariantJob();
        job.media = media;
        job.attemptCount = attempts;
        job.status = status;
        job.availableAt = OffsetDateTime.now().minusMinutes(10);
        job.leaseExpiresAt = "PROCESSING".equals(status) ? OffsetDateTime.now().minusMinutes(1) : null;
        job.persist();
        entityManager.flush();
        return job;
    }

    @Test
    @TestTransaction
    void expiredLeaseCannotBypassAttemptLimit() {
        MediaVariantJob job = job(9677, "PROCESSING");
        assertNull(service.claimNext());
        entityManager.clear();
        MediaVariantJob failed = MediaVariantJob.findById(job.mediaId);
        assertEquals("FAILED", failed.status);
        assertEquals(9677, failed.attemptCount);
        assertNull(failed.leaseExpiresAt);
        assertNotNull(failed.lastError);
        assertNull(service.claimNext());
    }

    @Test
    @TestTransaction
    void pendingAtLimitIsFailedWithoutCallingConverter() {
        MediaVariantJob job = job(3, "PENDING");
        assertNull(service.claimNext());
        entityManager.clear();
        assertEquals("FAILED", ((MediaVariantJob) MediaVariantJob.findById(job.mediaId)).status);
    }

    @Test
    @TestTransaction
    void recoveredLeaseGetsNewAttemptAndRejectsLateCompletion() {
        MediaVariantJob job = job(1, "PROCESSING");
        var payload = service.claimNext();
        assertNotNull(payload);
        assertEquals(2, payload.attempt());
        var old = new MediaVariantProcessingService.JobPayload(payload.mediaId(), payload.eventId(),
                payload.guestId(), payload.mediaType(), payload.contentType(), payload.r2Key(), 1);
        service.complete(old, "old-thumb", "old-display");
        service.fail(old, "late error");
        assertEquals("PROCESSING", job.status);
        assertNull(job.media.r2ThumbKey);
        service.complete(payload, "thumb", "display");
        assertEquals("COMPLETED", job.status);
        assertEquals("thumb", job.media.r2ThumbKey);
        assertEquals("display", job.media.r2DisplayKey);
        assertNull(job.leaseExpiresAt);
    }

    @Test
    @TestTransaction
    void failureBacksOffAndStopsAtThreeAttempts() {
        MediaVariantJob job = job(0, "PENDING");
        var first = service.claimNext();
        service.fail(first, "failure");
        assertEquals("PENDING", job.status);
        assertEquals(1, job.attemptCount);
        assertTrue(job.availableAt.isAfter(OffsetDateTime.now().plusSeconds(50)));
        assertNull(service.claimNext());
        job.availableAt = OffsetDateTime.now().minusMinutes(1);
        var second = service.claimNext();
        service.fail(second, "failure");
        assertEquals(2, job.attemptCount);
        assertTrue(job.availableAt.isAfter(OffsetDateTime.now().plusSeconds(110)));
        job.availableAt = OffsetDateTime.now().minusMinutes(1);
        var third = service.claimNext();
        service.fail(third, "x".repeat(600));
        assertEquals("FAILED", job.status);
        assertEquals(3, job.attemptCount);
        assertEquals(500, job.lastError.length());
        assertNull(service.claimNext());
    }

    @Test
    void nativeLinkageErrorIsRecordedAndLeaseIsReleased() {
        UUID mediaId = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> job(0, "PENDING").mediaId);
        try {
            when(variants.generatePhotoVariants(any(Path.class), eq("image/heic")))
                    .thenThrow(new UnsatisfiedLinkError("missing native library"));
            service.processNext();
            io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                MediaVariantJob job = MediaVariantJob.findById(mediaId);
                assertEquals("PENDING", job.status);
                assertEquals(1, job.attemptCount);
                assertNull(job.leaseExpiresAt);
                assertTrue(job.lastError.contains("UnsatisfiedLinkError"));
                assertTrue(job.lastError.contains("missing native library"));
            });
        } finally {
            io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                MediaVariantJob job = MediaVariantJob.findById(mediaId);
                UUID eventId = job.media.event.id;
                entityManager.clear();
                entityManager.createNativeQuery("DELETE FROM events WHERE id = :id")
                        .setParameter("id", eventId).executeUpdate();
            });
            reset(variants, storage);
        }
    }
}
