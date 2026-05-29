package br.com.casamento.guest.dto;

import java.util.List;

public record ImportResultResponse(
        int imported,
        int skipped,
        int total,
        List<ImportError> errors
) {
    public record ImportError(int row, String reason) {}
}
