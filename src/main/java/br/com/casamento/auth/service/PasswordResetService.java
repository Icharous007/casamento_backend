package br.com.casamento.auth.service;

import br.com.casamento.auth.entity.InternalUser;
import br.com.casamento.auth.entity.PasswordResetToken;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

@ApplicationScoped
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final int TTL_HOURS = 1;

    private final SecureRandom secureRandom = new SecureRandom();
    private final TokenHashUtil hashUtil = new TokenHashUtil();

    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:5173")
    String frontendUrl;

    Mailer mailer;

    public PasswordResetService(Mailer mailer) {
        this.mailer = mailer;
    }

    /**
     * Generates a reset token and enqueues the email.
     * Silently no-ops if the email is not found (security: avoid user enumeration).
     */
    @Transactional
    public void requestReset(String email) {
        InternalUser user = InternalUser.findByEmail(email);
        if (user == null || !user.active) {
            return;
        }

        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = bytesToHex(bytes);
        String tokenHash = hashUtil.sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.expiresAt = OffsetDateTime.now().plusHours(TTL_HOURS);
        token.persist();

        String resetLink = frontendUrl + "/admin/reset-password?token=" + rawToken;

        mailer.send(Mail.withHtml(
                user.email,
                "Redefinição de senha — Casamento",
                "<p>Olá, " + user.name + "!</p>" +
                "<p>Clique no link abaixo para redefinir sua senha (válido por 1 hora):</p>" +
                "<p><a href='" + resetLink + "'>" + resetLink + "</a></p>" +
                "<p>Se você não solicitou a redefinição, ignore este e-mail.</p>"
        ));
    }

    /**
     * Validates token, updates password, marks token as used.
     * Returns false if token is invalid, expired, or already used.
     */
    @Transactional
    public boolean resetPassword(String rawToken, String newPasswordHash) {
        String tokenHash = hashUtil.sha256Hex(rawToken);
        PasswordResetToken token = PasswordResetToken.findByTokenHash(tokenHash);

        if (token == null || token.used || token.expiresAt.isBefore(OffsetDateTime.now())) {
            return false;
        }

        token.used = true;
        token.user.passwordHash = newPasswordHash;
        return true;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
