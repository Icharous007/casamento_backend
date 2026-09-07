package br.com.casamento.media.dto;

import java.util.List;

/**
 * Request to report a batch of client errors.
 */
public record ReportMediaErrorsRequest(
    List<ReportMediaErrorRequest> errors
) {}
