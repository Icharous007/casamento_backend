package br.com.casamento.guest.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Normalizes guest phone numbers to E.164 format.
 * Defaults to Brazil (+55) when no country code is provided.
 */
@ApplicationScoped
public class PhoneNumberService {

    @ConfigProperty(name = "app.phone.default-country-code", defaultValue = "55")
    String defaultCountryCode;

    /**
     * Normalizes a raw phone string to E.164 (e.g. +5511999998888).
     * Accepts digits, spaces, hyphens, parentheses, and a leading '+'.
     * Returns null if the input is null/blank or results in a number that is
     * clearly invalid (fewer than 7 digits after stripping formatting).
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // Strip all formatting characters except leading +
        String stripped = raw.strip();
        boolean hasPlus = stripped.startsWith("+");
        String digits = stripped.replaceAll("[^0-9]", "");

        if (digits.length() < 7) return null;

        if (hasPlus) {
            // Already has a country calling code
            return "+" + digits;
        }

        // Brazil: 11-digit mobile (2-digit area + 9-digit number) without country code
        if (digits.length() == 11) {
            return "+" + defaultCountryCode + digits;
        }

        // Brazil: 13-digit number already has 55 prefix (user typed it without +)
        if (digits.startsWith("55") && digits.length() == 13) {
            return "+" + digits;
        }

        // Any other case: just prepend default country code
        return "+" + defaultCountryCode + digits;
    }

    /**
     * Returns true if the string looks like a valid E.164 number
     * (+<countryCode><number>, 8–15 digits total).
     */
    public boolean isValidE164(String phone) {
        if (phone == null || !phone.startsWith("+")) return false;
        String digits = phone.substring(1);
        return digits.matches("\\d{7,14}");
    }
}
