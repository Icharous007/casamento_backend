package br.com.casamento.auth.service;

import br.com.casamento.auth.entity.InternalUser;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@ApplicationScoped
public class JwtService {

    private static final Duration JWT_TTL = Duration.ofHours(1);

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    /**
     * Generates a signed JWT for an admin/cerimonialista user.
     * Uses the private key configured in application.properties.
     */
    public String generateToken(InternalUser user) {
        Instant now = Instant.now();
        return Jwt.issuer(issuer)
                .subject(user.id.toString())
                .upn(user.email)
                .groups(Set.of(user.role))
                .claim("name", user.name)
                .issuedAt(now)
                .expiresAt(now.plus(JWT_TTL))
                .sign();
    }
}
