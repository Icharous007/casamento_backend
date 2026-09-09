package br.com.casamento.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRsvpItemResponse(
        UUID guestId,
        String guestName,
        String phone,
        String guestType,
        Short age,
        String attendanceStatus,
        String dietaryRestrictions,
        String allergies,
        String additionalInfo,
        String confirmedByGuestName,
        OffsetDateTime respondedAt,
        OffsetDateTime lastChangedAt
) {}
