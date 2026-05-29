package br.com.casamento.rsvp.dto;

import java.time.OffsetDateTime;

public record RsvpResponse(
        String guestId,
        String attendanceStatus,
        OffsetDateTime respondedAt,
        OffsetDateTime lastChangedAt
) {}
