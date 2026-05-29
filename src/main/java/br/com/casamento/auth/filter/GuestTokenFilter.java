package br.com.casamento.auth.filter;

import br.com.casamento.auth.entity.GuestAccessToken;
import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.auth.service.GuestTokenService;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.guest.GuestProfile;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JAX-RS filter that reads X-Guest-Access-Token header and populates GuestContext.
 * Applied only to endpoints annotated with @RequiresGuestToken.
 */
@Provider
@GuestTokenFilter.RequiresGuestToken
@Priority(200)
public class GuestTokenFilter implements ContainerRequestFilter {

    static final String HEADER = "X-Guest-Access-Token";

    @Inject
    GuestTokenService guestTokenService;

    @Inject
    GuestContext guestContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String rawToken = requestContext.getHeaderString(HEADER);
        if (rawToken == null || rawToken.isBlank()) {
            throw AppException.tokenInvalid();
        }

        Object[] resolved = guestTokenService.resolveWithProfile(rawToken);

        if (resolved == null) {
            throw AppException.tokenInvalid();
        }

        GuestAccessToken accessToken = (GuestAccessToken) resolved[0];
        if (accessToken.revoked) {
            throw AppException.tokenRevoked();
        }

        Guest guest = accessToken.guest;
        GuestProfile profile = (GuestProfile) resolved[1];
        guestContext.populate(guest, profile);
    }

    /**
     * Annotation to mark JAX-RS resource methods/classes that require guest authentication.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @jakarta.ws.rs.NameBinding
    public @interface RequiresGuestToken {
    }
}
