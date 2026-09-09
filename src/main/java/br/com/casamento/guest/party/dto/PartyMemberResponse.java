package br.com.casamento.guest.party.dto;

import java.time.OffsetDateTime;

/**
 * Response representing a party member (self + dependents/managed guests).
 */
public record PartyMemberResponse(
        String guestId,
        String name,
        String phone,
        String guestType,
        Short age,
        String rsvpStatus,
        String dietaryRestrictions,
        String allergies,
        String additionalInfo,
        boolean selfConfirmationSuggested,
        boolean isSelf,
        boolean managedByMe,
        String managedByName,
        OffsetDateTime createdAt
) {}
