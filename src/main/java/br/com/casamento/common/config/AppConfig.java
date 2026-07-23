package br.com.casamento.common.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    @WithName("base-url")
    String baseUrl();

    @WithName("frontend-url")
    String frontendUrl();

    String timezone();

    @WithName("confirmation-phrase")
    String confirmationPhrase();

    R2Config r2();

    RateLimitConfig rateLimit();

    JobsConfig jobs();

    interface R2Config {
        String endpoint();
        @WithName("access-key") String accessKey();
        @WithName("secret-key") String secretKey();
        String bucket();
        String region();
    }

    interface RateLimitConfig {
        @WithName("guest-resolve") int guestResolve();
        @WithName("admin-login") int adminLogin();
    }

    interface JobsConfig {
        String secret();
    }
}
