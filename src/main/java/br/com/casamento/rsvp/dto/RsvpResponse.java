package br.com.casamento.rsvp.dto;

import java.time.OffsetDateTime;

public record RsvpResponse(
        String guestId,
        String attendanceStatus,
        String dietaryRestrictions,
        String allergies,
        String additionalInfo,
        OffsetDateTime respondedAt,
        OffsetDateTime lastChangedAt
) {}
