package br.com.casamento.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService();

    @Test
    void hashShouldProduceBcryptHashAndValidateOriginalPassword() {
        String hash = service.hash("Casamento2027!");

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2"));
        assertTrue(service.matches("Casamento2027!", hash));
    }

    @Test
    void hashShouldUseSaltAndGenerateDifferentHashesForSamePassword() {
        String hashA = service.hash("same-password");
        String hashB = service.hash("same-password");

        assertNotEquals(hashA, hashB);
        assertTrue(service.matches("same-password", hashA));
        assertTrue(service.matches("same-password", hashB));
    }

    @Test
    void matchesShouldReturnFalseForWrongPassword() {
        String hash = service.hash("correct-password");

        assertFalse(service.matches("wrong-password", hash));
    }
}
