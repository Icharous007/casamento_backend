package br.com.casamento.guest.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to add a party member (dependent/proxy).
 * Either a child (name only, no phone) or an adult (name + phone).
 */
public record AddPartyMemberRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 20) String phone,
        @NotBlank String guestType, // ADULT | CHILD
        Short age,
        String attendanceStatus,
        String dietaryRestrictions,
        String allergies,
        String additionalInfo
) {
        /** Compatibility constructor for existing service tests and internal callers. */
        public AddPartyMemberRequest(String name, String phone, String guestType, Short age) {
                this(name, phone, guestType, age, "DECLINED", "Não possui", "Não possui", "Não se aplica");
        }
}
