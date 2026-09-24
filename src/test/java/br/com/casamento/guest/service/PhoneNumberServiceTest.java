package br.com.casamento.guest.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberServiceTest {

    private PhoneNumberService service;

    @BeforeEach
    void setUp() {
        service = new PhoneNumberService();
    }

    @Test
    void normalizeShouldReturnNullForBlankInput() {
        assertNull(service.normalize(null));
        assertNull(service.normalize("   "));
    }

    @Test
    void normalizeShouldRemoveBrazilianCountryCode() {
        assertEquals("11 98765-4321", service.normalize("+55 11 98765-4321"));
        assertEquals("11 98765-4321", service.normalize("5511987654321"));
        assertEquals("11 98765-4321", service.normalize("+351 11 98765-4321"));
    }

    @Test
    void normalizeShouldFormatLocalNumber() {
        assertEquals("11 98765-4321", service.normalize("11987654321"));
        assertEquals("11 98765-4321", service.normalize("(11) 98765-4321"));
    }

    @Test
    void normalizeShouldRejectTooShortNumbers() {
        assertNull(service.normalize("123456"));
    }

    @Test
    void isValidPhoneShouldValidateExpectedPatterns() {
        assertTrue(service.isValidPhone("11 98765-4321"));
        assertFalse(service.isValidPhone("11987654321"));
        assertFalse(service.isValidPhone("+5511987654321"));
        assertFalse(service.isValidPhone("11 9876-4321"));
        assertFalse(service.isValidPhone(null));
    }
}
