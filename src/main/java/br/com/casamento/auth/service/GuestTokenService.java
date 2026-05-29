package br.com.casamento.auth.service;

import br.com.casamento.auth.entity.GuestAccessToken;
import br.com.casamento.domain.guest.Guest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;

@ApplicationScoped
public class GuestTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final TokenHashUtil hashUtil = new TokenHashUtil();

    /**
     * Generates a new access token for a guest and persists its hash.
     * Returns the raw (unhashed) token to be sent to the guest.
     */
    @Transactional
    public String createToken(Guest guest) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = bytesToHex(bytes);

        GuestAccessToken token = new GuestAccessToken();
        token.guest = guest;
        token.tokenHash = hashUtil.sha256Hex(rawToken);
        token.persist();

        return rawToken;
    }

    /**
     * Resolves a raw guest access token to the corresponding GuestAccessToken entity,
     * with guest and event eagerly loaded.
     * Returns null if not found.
     */
    public GuestAccessToken resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        String tokenHash = hashUtil.sha256Hex(rawToken);
        return GuestAccessToken
                .find("FROM GuestAccessToken t JOIN FETCH t.guest g JOIN FETCH g.event WHERE t.tokenHash = ?1", tokenHash)
                .firstResult();
    }

    /**
     * Resolves token and loads profile in a single transaction.
     * Returns null[] if token not found; result[0]=GuestAccessToken, result[1]=GuestProfile (may be null).
     */
    @jakarta.transaction.Transactional
    public Object[] resolveWithProfile(String rawToken) {
        GuestAccessToken token = resolve(rawToken);
        if (token == null) return null;
        br.com.casamento.domain.guest.GuestProfile profile =
                br.com.casamento.domain.guest.GuestProfile.findByGuest(token.guest);
        return new Object[]{token, profile};
    }

    @Transactional
    public void revokeByGuest(Guest guest) {
        GuestAccessToken.update("revoked = true WHERE guest = ?1", guest);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
