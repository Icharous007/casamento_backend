package br.com.casamento.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenHashUtilTest {

    private final TokenHashUtil util = new TokenHashUtil();

    @Test
    void sha256HexShouldBeDeterministic() {
        String a = util.sha256Hex("abc123");
        String b = util.sha256Hex("abc123");

        assertEquals(a, b);
    }

    @Test
    void sha256HexShouldReturn64HexChars() {
        String hash = util.sha256Hex("token-value");

        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void sha256HexShouldHandleArbitraryInput() {
        String hash = util.sha256Hex("token_with_symbols_123");

        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}
