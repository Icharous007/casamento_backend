package br.com.casamento.storage;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
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

    public R2StorageService(
            @ConfigProperty(name = "app.r2.endpoint") String endpoint,
            @ConfigProperty(name = "app.r2.access-key") String accessKey,
            @ConfigProperty(name = "app.r2.secret-key") String secretKey,
            @ConfigProperty(name = "app.r2.bucket") String bucket,
            @ConfigProperty(name = "app.r2.region", defaultValue = "auto") String region
    ) {
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true) // required for MinIO / R2
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
        // In production, R2 public bucket URL or CDN domain should be injected.
        // For now, return the S3-path URL so the frontend can construct download links.
        return s3.utilities().getUrl(b -> b.bucket(bucket).key(key)).toString();
    }
}
