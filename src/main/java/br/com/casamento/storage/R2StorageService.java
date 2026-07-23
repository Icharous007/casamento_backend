package br.com.casamento.storage;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;

/**
 * Thin wrapper around AWS SDK v2 that targets Cloudflare R2 (S3-compatible endpoint).
 * In development, the same client works against MinIO.
 */
@ApplicationScoped
public class R2StorageService {

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public R2StorageService(
            @ConfigProperty(name = "app.r2.endpoint") String endpoint,
            @ConfigProperty(name = "app.r2.access-key") String accessKey,
            @ConfigProperty(name = "app.r2.secret-key") String secretKey,
            @ConfigProperty(name = "app.r2.bucket") String bucket,
        @ConfigProperty(name = "app.r2.region", defaultValue = "auto") String region,
        @ConfigProperty(name = "app.r2.public-base-url", defaultValue = "") String publicBaseUrl
    ) {
        this.bucket = bucket;
    this.publicBaseUrl = normalizePublicBaseUrl(publicBaseUrl);
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(false)
            .build())
                .build();
    }

    /**
     * Upload bytes to R2 and return the storage key.
     */
    public void upload(String key, InputStream data, long contentLength, String contentType) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(data, contentLength)
        );
    }

    /**
     * Delete an object from R2. Silently ignores NoSuchKeyException.
     */
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException ignored) {
            // already gone — that's fine
        }
    }

    /**
     * Build a public URL for an object. Works when R2 bucket has public access enabled
     * or when using a custom domain. Falls back to pre-signed URL pattern if needed.
     */
    public String publicUrl(String key) {
        if (publicBaseUrl != null) {
            return publicBaseUrl + "/" + key;
        }

        return s3.utilities().getUrl(b -> b.bucket(bucket).key(key)).toString();
    }

    private String normalizePublicBaseUrl(String configuredValue) {
        if (configuredValue == null) {
            return null;
        }

        String trimmed = configuredValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }
}
