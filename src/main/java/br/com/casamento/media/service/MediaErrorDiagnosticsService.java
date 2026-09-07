package br.com.casamento.media.service;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.media.MediaAsset;
import br.com.casamento.domain.media.MediaErrorEvent;
import br.com.casamento.media.dto.ReportMediaErrorRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class MediaErrorDiagnosticsService {

    private static final Logger LOG = Logger.getLogger(MediaErrorDiagnosticsService.class);

    @Inject
    EntityManager entityManager;

    // Known error codes that we normalize
    private static final String CORS_BLOCKED = "CORS_BLOCKED";
    private static final String UPLOAD_LIMIT_EXCEEDED = "UPLOAD_LIMIT_EXCEEDED";
    private static final String UPLOAD_EXPIRED = "UPLOAD_EXPIRED";
    private static final String UPLOAD_VERIFICATION_FAILED = "UPLOAD_VERIFICATION_FAILED";
    private static final String INTENT_PERSIST_FAILED = "INTENT_PERSIST_FAILED";
    private static final String R2_PUT_FAILED = "R2_PUT_FAILED";
    private static final String VARIANT_DOWNLOAD_FAILED = "VARIANT_DOWNLOAD_FAILED";
    private static final String VARIANT_GENERATION_FAILED = "VARIANT_GENERATION_FAILED";
    private static final String GALLERY_QUERY_FAILED = "GALLERY_QUERY_FAILED";

    private static final String TRACE_ID_KEY = "traceId";

    // Patterns for redaction
    private static final Pattern R2_URL_PATTERN = Pattern.compile(
        "(?i)https?://[^\\s]*r2[^\\s]*(?:\\?[^\\s]*)?"
    );
    private static final Pattern AWS_SIGNATURE_PATTERN = Pattern.compile(
        "X-Amz-[A-Za-z]+=\\S+"
    );
    private static final Pattern UUID_PATH_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    );

    /**
     * Record a client-reported media error.
     * Deduplicates within a 10-minute window by flow+attempt+code.
     */
    @Transactional
    public void recordClientError(Event event, Guest guest, ReportMediaErrorRequest req) {
        String sanitizedMsg = sanitizeErrorMessage(req.errorMessage());
        String errorCode = normalizeErrorCode(req.eventType(), req.category());
        
        try {
            // Check if we already recorded this in the last 10 minutes
            long recent = (Long) entityManager.createQuery(
                "SELECT COUNT(e) FROM MediaErrorEvent e WHERE " +
                "e.event.id = :eventId AND e.guest.id = :guestId AND " +
                "e.attemptId = :attemptId AND e.errorCode = :errorCode AND " +
                "e.source = 'CLIENT' AND e.createdAt > :tenMinutesAgo"
            )
            .setParameter("eventId", event.id)
            .setParameter("guestId", guest.id)
            .setParameter("attemptId", req.attemptId())
            .setParameter("errorCode", errorCode)
            .setParameter("tenMinutesAgo", OffsetDateTime.now().minusMinutes(10))
            .getSingleResult();

            if (recent > 0) {
                LOG.debugf("media_error_event deduplicated: eventId=%s attemptId=%s code=%s",
                    event.id, req.attemptId(), errorCode);
                return;
            }

            MediaErrorEvent errorEvent = new MediaErrorEvent();
            errorEvent.id = UUID.randomUUID();
            errorEvent.event = event;
            errorEvent.guest = guest;
            errorEvent.source = "CLIENT";
            errorEvent.stage = mapEventTypeToStage(req.eventType());
            errorEvent.errorCode = errorCode;
            errorEvent.errorCategory = req.category() != null ? req.category() : "unknown";
            errorEvent.errorMessage = sanitizedMsg;
            errorEvent.httpStatus = req.httpStatus();
            errorEvent.axiosCode = req.axiosCode();
            errorEvent.mediaType = req.mediaType();
            errorEvent.contentType = req.contentType();
            errorEvent.fileSizeBytes = req.fileSizeBytes();
            errorEvent.browserDescriptor = sanitizeBrowserDescriptor(req.browserDescriptor());
            errorEvent.durationMs = req.durationMs();
            errorEvent.retryable = req.retryable() != null ? req.retryable() : false;
            errorEvent.traceId = (String) MDC.get(TRACE_ID_KEY);
            errorEvent.flowId = req.flowId();
            errorEvent.attemptId = req.attemptId();
            errorEvent.clientRoute = req.clientRoute();
            errorEvent.createdAt = OffsetDateTime.now();

            entityManager.persist(errorEvent);
            
            LOG.infof("media_error_event recorded: source=CLIENT stage=%s code=%s category=%s guestId=%s attemptId=%s",
                errorEvent.stage, errorCode, req.category(), guest.id, req.attemptId());

        } catch (Exception ex) {
            LOG.warnf(ex, "Failed to record client media error for attemptId=%s", req.attemptId());
            // Do not rethrow; error diagnostics should never break the application
        }
    }

    /**
     * Record a server-side media error.
     */
    @Transactional
    public void recordServerError(
            Event event,
            Guest guest,
            MediaAsset media,
            String stage,
            String normalizedErrorCode,
            String sanitizedMessage,
            Integer httpStatus,
            Integer durationMs,
            boolean retryable
    ) {
        try {
            MediaErrorEvent errorEvent = new MediaErrorEvent();
            errorEvent.id = UUID.randomUUID();
            errorEvent.event = event;
            errorEvent.guest = guest;
            errorEvent.media = media;
            errorEvent.source = "SERVER";
            errorEvent.stage = stage;
            errorEvent.errorCode = normalizedErrorCode;
            errorEvent.errorCategory = categorizeErrorCode(normalizedErrorCode);
            errorEvent.errorMessage = sanitizedMessage;
            errorEvent.httpStatus = httpStatus;
            errorEvent.durationMs = durationMs;
            errorEvent.retryable = retryable;
            errorEvent.traceId = (String) MDC.get(TRACE_ID_KEY);
            errorEvent.createdAt = OffsetDateTime.now();

            entityManager.persist(errorEvent);

            LOG.infof("media_error_event recorded: source=SERVER stage=%s code=%s mediaId=%s",
                stage, normalizedErrorCode, media != null ? media.id : "null");

        } catch (Exception ex) {
            LOG.warnf(ex, "Failed to record server media error for stage=%s code=%s", stage, normalizedErrorCode);
        }
    }

    /**
     * Sanitize error message: remove URLs, AWS signatures, R2 keys, and truncate.
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String sanitized = message;
        // Redact R2 URLs
        sanitized = R2_URL_PATTERN.matcher(sanitized).replaceAll("[redacted_r2_url]");
        // Redact AWS signatures
        sanitized = AWS_SIGNATURE_PATTERN.matcher(sanitized).replaceAll("[redacted_aws_sig]");
        // Redact UUID-like patterns in paths
        sanitized = UUID_PATH_PATTERN.matcher(sanitized).replaceAll("[redacted_id]");

        // Truncate to 500 chars
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 497) + "...";
        }

        return sanitized;
    }

    /**
     * Sanitize browser descriptor: truncate to 200 chars, remove version details if sensitive.
     */
    private String sanitizeBrowserDescriptor(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        // Keep first 200 chars; typically includes OS, browser, device but not personal data
        if (userAgent.length() > 200) {
            return userAgent.substring(0, 197) + "...";
        }

        return userAgent;
    }

    /**
     * Normalize error code based on event type and category.
     */
    private String normalizeErrorCode(String eventType, String category) {
        if (eventType == null) {
            return "UNKNOWN_ERROR";
        }

        return switch (eventType.toLowerCase()) {
            case "validation_failed" -> "UPLOAD_VERIFICATION_FAILED";
            case "preview_render_failed" -> "GALLERY_QUERY_FAILED";
            case "cors_failed" -> CORS_BLOCKED;
            case "upload_failed" -> {
                if ("cors".equalsIgnoreCase(category)) {
                    yield CORS_BLOCKED;
                } else if ("timeout".equalsIgnoreCase(category)) {
                    yield "UPLOAD_TIMEOUT";
                } else if ("http".equalsIgnoreCase(category)) {
                    yield R2_PUT_FAILED;
                } else {
                    yield "UPLOAD_FAILED";
                }
            }
            case "intent_creation_failed" -> INTENT_PERSIST_FAILED;
            case "completion_failed" -> "UPLOAD_COMPLETION_FAILED";
            case "gallery_failed" -> GALLERY_QUERY_FAILED;
            default -> "UNKNOWN_ERROR";
        };
    }

    /**
     * Categorize an error code into a generic category.
     */
    private String categorizeErrorCode(String errorCode) {
        if (errorCode == null) {
            return "unknown";
        }

        return switch (errorCode) {
            case CORS_BLOCKED -> "cors";
            case UPLOAD_LIMIT_EXCEEDED, "UPLOAD_TIMEOUT" -> "timeout";
            case R2_PUT_FAILED, VARIANT_DOWNLOAD_FAILED -> "network";
            case INTENT_PERSIST_FAILED, UPLOAD_VERIFICATION_FAILED, "UPLOAD_COMPLETION_FAILED" -> "http";
            case VARIANT_GENERATION_FAILED -> "unknown";
            case GALLERY_QUERY_FAILED -> "network";
            default -> "unknown";
        };
    }

    /**
     * Map event type to processing stage.
     */
    private String mapEventTypeToStage(String eventType) {
        if (eventType == null) {
            return "unknown";
        }

        return switch (eventType.toLowerCase()) {
            case "validation_failed" -> "validation";
            case "upload_failed" -> "upload";
            case "cors_failed" -> "cors";
            case "intent_creation_failed" -> "intent";
            case "completion_failed" -> "complete";
            case "preview_render_failed" -> "gallery";
            default -> "unknown";
        };
    }

    /**
     * Get aggregated error summary for a time window.
     */
    public List<Object[]> getErrorSummary(UUID eventId, OffsetDateTime from, OffsetDateTime to, int limit) {
        return entityManager.createQuery(
            "SELECT e.errorCode, e.errorCategory, e.stage, e.source, COUNT(e), " +
            "SUM(CASE WHEN e.resolvedAt IS NULL THEN 1 ELSE 0 END), MAX(e.createdAt) " +
            "FROM MediaErrorEvent e WHERE e.event.id = :eventId " +
            "AND e.createdAt BETWEEN :from AND :to " +
            "GROUP BY e.errorCode, e.errorCategory, e.stage, e.source " +
            "ORDER BY MAX(e.createdAt) DESC",
            Object[].class
        )
        .setParameter("eventId", eventId)
        .setParameter("from", from)
        .setParameter("to", to)
        .setMaxResults(limit)
        .getResultList();
    }
}
