package br.com.casamento.auth.dto;

import java.time.OffsetDateTime;

public record GuestResolveResponse(
        String guestId,
        String eventId,
        String displayName,
        boolean requiresProfileCompletion,
        EventSummary event
) {
    public record EventSummary(
            String title,
            String coupleNames,
            OffsetDateTime eventStartAt,
            OffsetDateTime rsvpDeadlineAt,
            String venueName,
            String venueAddress
    ) {}
}
