package br.com.casamento.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    public String code;
    public String message;
    public Map<String, Object> details;
    public String traceId;
    public Instant timestamp;

    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message, String traceId) {
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String code, String message, Map<String, Object> details, String traceId) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }
}
