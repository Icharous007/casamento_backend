package br.com.casamento.guest.service;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Normalizes guest phone numbers to the Brazilian local format.
 */
@ApplicationScoped
public class PhoneNumberService {

    /**
     * Normalizes a raw phone string to the Brazilian local format
     * (e.g. 11 99999-8888). Country codes and formatting are removed.
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String digits = raw.strip().replaceAll("\\D", "");
        if (digits.length() < 11) return null;

        String localDigits = digits.substring(digits.length() - 11);
        return localDigits.substring(0, 2) + " "
                + localDigits.substring(2, 7) + "-"
                + localDigits.substring(7);
    }

    /**
     * Returns true if the string has the expected Brazilian local format.
     */
    public boolean isValidPhone(String phone) {
        return phone != null && phone.matches("\\d{2} \\d{5}-\\d{4}");
    }
}
