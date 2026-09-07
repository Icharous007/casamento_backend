package br.com.casamento.media.dto;

/**
 * Request DTO for reporting client-side media errors.
 * Strictly sanitized: no URLs, tokens, PII, or stack traces.
 */
public record ReportMediaErrorRequest(
    String flowId,
    String attemptId,
    String eventType, // validation_failed, upload_failed, intent_creation_failed, completion_failed, cors_failed, gallery_failed
    String category, // timeout, auth, network, http, browser, cors, unknown
    String mediaType, // PHOTO or VIDEO
    String contentType,
    Long fileSizeBytes,
    Integer httpStatus,
    String axiosCode,
    String errorMessage, // sanitized; must not contain URLs or tokens
    String browserDescriptor, // sanitized or truncated user agent
    String clientRoute,
    Integer durationMs,
    Boolean retryable
) {}
