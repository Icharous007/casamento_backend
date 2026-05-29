package br.com.casamento.auth.service;

import br.com.casamento.auth.entity.InternalUser;
import br.com.casamento.auth.entity.RefreshToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

@ApplicationScoped
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int TTL_DAYS = 30;

    private final SecureRandom secureRandom = new SecureRandom();
    private final TokenHashUtil hashUtil = new TokenHashUtil();

    /**
     * Creates a new refresh token for a user. Returns the raw (unhashed) token.
     */
    @Transactional
    public String createToken(InternalUser user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = bytesToHex(bytes);
        String tokenHash = hashUtil.sha256Hex(rawToken);

        RefreshToken token = new RefreshToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.expiresAt = OffsetDateTime.now().plusDays(TTL_DAYS);
        token.persist();

        return rawToken;
    }

    /**
     * Validates the raw token, revokes it and returns the associated user (detached).
     * Returns null if the token is invalid or expired.
     */
    @Transactional
    public InternalUser consumeToken(String rawToken) {
        String tokenHash = hashUtil.sha256Hex(rawToken);
        RefreshToken token = RefreshToken
                .find("FROM RefreshToken t JOIN FETCH t.user WHERE t.tokenHash = ?1", tokenHash)
                .firstResult();

        if (token == null || token.revoked || token.expiresAt.isBefore(OffsetDateTime.now())) {
            return null;
        }

        token.revoked = true;
        InternalUser user = token.user;
        // Access all needed fields while session is open to avoid lazy init issues
        boolean active = user.active;
        String id = user.id.toString();
        String name = user.name;
        String email = user.email;
        String role = user.role;
        return active ? user : null;
    }

    @Transactional
    public void revokeByHash(String rawToken) {
        String tokenHash = hashUtil.sha256Hex(rawToken);
        RefreshToken token = RefreshToken.findByTokenHash(tokenHash);
        if (token != null) {
            token.revoked = true;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
