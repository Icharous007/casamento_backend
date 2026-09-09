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
        Short age
) {}
