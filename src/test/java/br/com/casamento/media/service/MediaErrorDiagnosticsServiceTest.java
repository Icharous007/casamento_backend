package br.com.casamento.media.service;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.media.MediaErrorEvent;
import br.com.casamento.media.dto.ReportMediaErrorRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class MediaErrorDiagnosticsServiceTest {

    @Inject
    EntityManager entityManager;

    @Inject
    MediaErrorDiagnosticsService service;

    private Event testEvent;
    private Guest testGuest;

    @BeforeEach
    @Transactional
    void setup() {
        // Create test event and guest
        testEvent = new Event();
        testEvent.slug = "test-event-" + UUID.randomUUID();
        testEvent.title = "Test Event";
        testEvent.coupleNames = "Test Couple";
        testEvent.eventDate = OffsetDateTime.now().plusDays(1);
        testEvent.createdAt = OffsetDateTime.now();
        entityManager.persist(testEvent);

        testGuest = new Guest();
        testGuest.event = testEvent;
        testGuest.name = "Test Guest";
        testGuest.createdAt = OffsetDateTime.now();
        entityManager.persist(testGuest);
    }

    @Test
    @Transactional
    void testRecordClientError() {
        ReportMediaErrorRequest request = new ReportMediaErrorRequest(
            "flow-123",
            "attempt-1",
            "upload_failed",
            "cors",
            "PHOTO",
            "image/jpeg",
            1024L,
            403,
            null,
            "CORS error on PUT request",
            "Mozilla/5.0 (Linux)",
            "/media/capture",
            1234,
            true
        );

        service.recordClientError(testEvent, testGuest, request);

        MediaErrorEvent recorded = (MediaErrorEvent) entityManager.createQuery(
            "FROM MediaErrorEvent WHERE flowId = :flowId"
        ).setParameter("flowId", "flow-123")
         .getResultStream().findFirst().orElse(null);

        assertNotNull(recorded);
        assertEquals("CLIENT", recorded.source);
        assertEquals("upload", recorded.stage);
        assertEquals("CORS_BLOCKED", recorded.errorCode);
        assertEquals("cors", recorded.errorCategory);
        assertEquals(403, recorded.httpStatus);
        assertEquals("PHOTO", recorded.mediaType);
    }

    @Test
    @Transactional
    void testErrorMessageSanitization() {
        ReportMediaErrorRequest request = new ReportMediaErrorRequest(
            "flow-124",
            "attempt-1",
            "upload_failed",
            "network",
            "VIDEO",
            "video/mp4",
            5242880L,
            null,
            "ERR_NETWORK",
            "Failed to upload to https://abc123.r2.cloudflarestorage.com/path/12345678-1234-1234-1234-123456789012?X-Amz-Signature=sig123",
            "Mozilla/5.0 (iPhone)",
            "/media/gallery",
            5000,
            false
        );

        service.recordClientError(testEvent, testGuest, request);

        MediaErrorEvent recorded = (MediaErrorEvent) entityManager.createQuery(
            "FROM MediaErrorEvent WHERE flowId = :flowId"
        ).setParameter("flowId", "flow-124")
         .getResultStream().findFirst().orElse(null);

        assertNotNull(recorded);
        // Message should be sanitized: URLs and UUIDs redacted
        assertNotNull(recorded.errorMessage);
        assertFalse(recorded.errorMessage.contains("r2.cloudflarestorage.com"));
        assertFalse(recorded.errorMessage.contains("12345678-1234-1234-1234-123456789012"));
        assertFalse(recorded.errorMessage.contains("X-Amz-Signature"));
    }

    @Test
    @Transactional
    void testBrowserDescriptorTruncation() {
        String longUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59 " +
                               "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59";

        ReportMediaErrorRequest request = new ReportMediaErrorRequest(
            "flow-125",
            "attempt-1",
            "validation_failed",
            "browser",
            "PHOTO",
            "image/png",
            2048L,
            null,
            null,
            "File format not supported",
            longUserAgent,
            "/media/capture",
            100,
            false
        );

        service.recordClientError(testEvent, testGuest, request);

        MediaErrorEvent recorded = (MediaErrorEvent) entityManager.createQuery(
            "FROM MediaErrorEvent WHERE flowId = :flowId"
        ).setParameter("flowId", "flow-125")
         .getResultStream().findFirst().orElse(null);

        assertNotNull(recorded);
        // Browser descriptor should be truncated to 200 chars
        assertTrue(recorded.browserDescriptor.length() <= 200);
    }

    @Test
    @Transactional
    void testDeduplicationWithinWindow() {
        ReportMediaErrorRequest request = new ReportMediaErrorRequest(
            "flow-126",
            "attempt-2",
            "cors_failed",
            "cors",
            "PHOTO",
            "image/jpeg",
            1024L,
            0,
            null,
            "CORS blocked",
            "Mozilla/5.0",
            "/media/upload",
            500,
            true
        );

        // Record first error
        service.recordClientError(testEvent, testGuest, request);

        long countAfterFirst = (Long) entityManager.createQuery(
            "SELECT COUNT(e) FROM MediaErrorEvent e WHERE flowId = :flowId"
        ).setParameter("flowId", "flow-126").getSingleResult();
        assertEquals(1, countAfterFirst);

        // Record same error again (should be deduplicated)
        service.recordClientError(testEvent, testGuest, request);

        long countAfterSecond = (Long) entityManager.createQuery(
            "SELECT COUNT(e) FROM MediaErrorEvent e WHERE flowId = :flowId"
        ).setParameter("flowId", "flow-126").getSingleResult();
        assertEquals(1, countAfterSecond, "Same error within 10 minutes should be deduplicated");
    }

    @Test
    @Transactional
    void testErrorCodeNormalization() {
        String[] eventTypes = {"validation_failed", "upload_failed", "cors_failed", "intent_creation_failed", "completion_failed", "preview_render_failed"};
        String[] expectedCodes = {"UPLOAD_VERIFICATION_FAILED", "UPLOAD_FAILED", "CORS_BLOCKED", "INTENT_PERSIST_FAILED", "UPLOAD_COMPLETION_FAILED", "GALLERY_QUERY_FAILED"};

        for (int i = 0; i < eventTypes.length; i++) {
            String flowId = "flow-norm-" + i;
            ReportMediaErrorRequest request = new ReportMediaErrorRequest(
                flowId,
                "attempt-" + i,
                eventTypes[i],
                "unknown",
                "PHOTO",
                "image/jpeg",
                512L,
                null,
                null,
                "Test error",
                "Mozilla/5.0",
                "/test",
                100,
                false
            );

            service.recordClientError(testEvent, testGuest, request);

            MediaErrorEvent recorded = (MediaErrorEvent) entityManager.createQuery(
                "FROM MediaErrorEvent WHERE flowId = :flowId"
            ).setParameter("flowId", flowId)
             .getResultStream().findFirst().orElse(null);

            assertNotNull(recorded, "Should have recorded error for " + eventTypes[i]);
            assertEquals(expectedCodes[i], recorded.errorCode, "Error code should be normalized for " + eventTypes[i]);
        }
    }
}
