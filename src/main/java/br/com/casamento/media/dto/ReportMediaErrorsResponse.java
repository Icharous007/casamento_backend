package br.com.casamento.media.dto;

/**
 * Response to error report submission.
 */
public record ReportMediaErrorsResponse(
    int recorded,
    int deduplicated
) {}
