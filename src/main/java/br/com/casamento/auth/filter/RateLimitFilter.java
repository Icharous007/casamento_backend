package br.com.casamento.auth.filter;

import br.com.casamento.common.dto.ErrorResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Simple in-memory rate limiter for sensitive endpoints.
 * Applied via @RateLimited annotation with a named limit key.
 * Resets counters every minute.
 */
@Provider
@RateLimitFilter.RateLimited
@Priority(100)
public class RateLimitFilter implements ContainerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;

    /** clientKey → [count, windowStart] */
    private Cache<String, long[]> counters;

    @ConfigProperty(name = "app.rate-limit.guest-resolve", defaultValue = "10")
    int guestResolveLimit;

    @ConfigProperty(name = "app.rate-limit.admin-login", defaultValue = "10")
    int adminLoginLimit;

    @ConfigProperty(name = "app.rate-limit.max-tracked-clients", defaultValue = "10000")
    long maxTrackedClients;

    @PostConstruct
    void initializeCache() {
        counters = Caffeine.newBuilder()
                .maximumSize(maxTrackedClients)
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .build();
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        int maxRequests = resolveLimit(path);
        String clientKey = clientIp(ctx) + "|" + path;

        long now = Instant.now().toEpochMilli();
        long[] entry = counters.asMap().compute(clientKey, (k, v) -> {
            if (v == null || now - v[1] >= WINDOW_MILLIS) {
                return new long[]{1, now};
            }
            v[0]++;
            return v;
        });

        if (entry[0] > maxRequests) {
            ctx.abortWith(Response.status(429)
                    .entity(new ErrorResponse("RATE_LIMIT_EXCEEDED",
                            "Muitas requisições. Tente novamente em breve.", null, null))
                    .build());
        }
    }

    private int resolveLimit(String path) {
        if (path.contains("guest-access/register")) return guestResolveLimit;
        if (path.contains("admin/auth/login")) return adminLoginLimit;
        return 60;
    }

    private String clientIp(ContainerRequestContext ctx) {
        String realIp = ctx.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return "unknown";
    }

    /**
     * Annotation to activate rate limiting on a resource method/class.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @jakarta.ws.rs.NameBinding
    public @interface RateLimited {
    }
}
