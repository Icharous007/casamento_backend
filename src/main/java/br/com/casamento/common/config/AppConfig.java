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

    EmailConfig email();

    RateLimitConfig rateLimit();

    JobsConfig jobs();

    interface R2Config {
        String endpoint();
        @WithName("access-key") String accessKey();
        @WithName("secret-key") String secretKey();
        String bucket();
        String region();
    }

    interface EmailConfig {
        @WithName("daily-limit") int dailyLimit();
        @WithName("batch-size") int batchSize();
        @WithName("max-retries") int maxRetries();
    }

    interface RateLimitConfig {
        @WithName("guest-resolve") int guestResolve();
        @WithName("admin-login") int adminLogin();
        @WithName("forgot-password") int forgotPassword();
    }

    interface JobsConfig {
        String secret();
    }
}
