package br.com.casamento.guest.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberServiceTest {

    private PhoneNumberService service;

    @BeforeEach
    void setUp() {
        service = new PhoneNumberService();
        service.defaultCountryCode = "55";
    }

    @Test
    void normalizeShouldReturnNullForBlankInput() {
        assertNull(service.normalize(null));
        assertNull(service.normalize("   "));
    }

    @Test
    void normalizeShouldKeepExplicitE164() {
        assertEquals("+5511987654321", service.normalize("+5511987654321"));
        assertEquals("+441234567890", service.normalize("+44 1234 567890"));
    }

    @Test
    void normalizeShouldApplyBrazilDefaults() {
        assertEquals("+5511987654321", service.normalize("11987654321"));
        assertEquals("+5511987654321", service.normalize("(11) 98765-4321"));
        assertEquals("+5511987654321", service.normalize("5511987654321"));
    }

    @Test
    void normalizeShouldRejectTooShortNumbers() {
        assertNull(service.normalize("123456"));
    }

    @Test
    void isValidE164ShouldValidateExpectedPatterns() {
        assertTrue(service.isValidE164("+5511987654321"));
        assertTrue(service.isValidE164("+441234567890"));
        assertFalse(service.isValidE164("5511987654321"));
        assertFalse(service.isValidE164("+123"));
        assertFalse(service.isValidE164(null));
    }
}
