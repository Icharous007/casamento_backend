package br.com.casamento.rsvp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RsvpRequest(
        @NotBlank @Pattern(regexp = "ATTENDING|DECLINED") String attendanceStatus,
        String dietaryRestrictions,
        String allergies,
        String additionalInfo
) {}
