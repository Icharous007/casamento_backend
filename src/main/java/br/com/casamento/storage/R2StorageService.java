package br.com.casamento.storage;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Thin wrapper around AWS SDK v2 that targets Cloudflare R2 (S3-compatible endpoint).
 * In development, the same client works against MinIO.
 */
@ApplicationScoped
public class R2StorageService {

    private static final Logger LOG = Logger.getLogger(R2StorageService.class);
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;
    private final String mediaDeliveryBaseUrl;
    private final String mediaDeliverySigningKey;
    private final Duration uploadUrlTtl;
    private final Duration deliveryUrlTtl;

    public record PresignedUpload(String url, Instant expiresAt) {
    }

    public record StoredObjectMetadata(long contentLength, String contentType) {
    }

    public R2StorageService(
            @ConfigProperty(name = "app.r2.endpoint") String endpoint,
            @ConfigProperty(name = "app.r2.access-key") String accessKey,
            @ConfigProperty(name = "app.r2.secret-key") String secretKey,
            @ConfigProperty(name = "app.r2.bucket") String bucket,
                @ConfigProperty(name = "app.r2.region", defaultValue = "auto") String region,
                @ConfigProperty(name = "app.r2.public-base-url", defaultValue = "") String publicBaseUrl,
                @ConfigProperty(name = "app.r2.api-call-timeout", defaultValue = "PT30S") Duration apiCallTimeout,
                @ConfigProperty(name = "app.r2.api-attempt-timeout", defaultValue = "PT10S") Duration apiAttemptTimeout,
                @ConfigProperty(name = "app.r2.connection-timeout", defaultValue = "PT5S") Duration connectionTimeout,
                @ConfigProperty(name = "app.r2.socket-timeout", defaultValue = "PT20S") Duration socketTimeout,
                @ConfigProperty(name = "app.r2.upload-url-ttl", defaultValue = "PT10M") Duration uploadUrlTtl,
                @ConfigProperty(name = "app.media.delivery-base-url") Optional<String> mediaDeliveryBaseUrl,
                @ConfigProperty(name = "app.media.delivery-signing-key") Optional<String> mediaDeliverySigningKey,
                    @ConfigProperty(name = "app.media.delivery-url-ttl", defaultValue = "PT5M") Duration deliveryUrlTtl,
                    @ConfigProperty(name = "app.media.require-private-delivery", defaultValue = "false") boolean requirePrivateDelivery
    ) {
        this.bucket = bucket;
            this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
            this.mediaDeliveryBaseUrl = normalizeBaseUrl(mediaDeliveryBaseUrl.orElse(null));
            this.mediaDeliverySigningKey = mediaDeliverySigningKey.orElse("");
            this.uploadUrlTtl = uploadUrlTtl;
            this.deliveryUrlTtl = deliveryUrlTtl;
            if (requirePrivateDelivery && (this.mediaDeliveryBaseUrl == null || this.mediaDeliverySigningKey.isBlank()
                || this.publicBaseUrl != null)) {
                throw new IllegalStateException("Private media delivery requires MEDIA_DELIVERY_BASE_URL and "
                    + "MEDIA_DELIVERY_SIGNING_KEY with R2_PUBLIC_BASE_URL unset.");
            }
            S3Configuration configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();
            ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(apiCallTimeout)
                .apiCallAttemptTimeout(apiAttemptTimeout)
                .build();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(configuration)
                .overrideConfiguration(overrideConfiguration)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                    .connectionTimeout(connectionTimeout)
                    .socketTimeout(socketTimeout))
                .build();
            this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(configuration)
                .build();
    }

    /**
     * Upload bytes to R2 and return the storage key.
     */
    public void upload(String key, InputStream data, long contentLength, String contentType) {
        long startedAt = System.nanoTime();
        try {
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build(),
                RequestBody.fromInputStream(data, contentLength)
            );
            LOG.infof("storage operation=upload contentType=%s bytes=%d durationMs=%d outcome=success",
                contentType, contentLength, elapsedMs(startedAt));
        } catch (RuntimeException exception) {
            LOG.errorf(exception, "storage operation=upload contentType=%s bytes=%d durationMs=%d outcome=failure errorType=%s",
                contentType, contentLength, elapsedMs(startedAt), exception.getClass().getSimpleName());
            throw exception;
        }
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

    public PresignedUpload presignUpload(String key, String contentType) {
        PresignedPutObjectRequest request = presigner.presignPutObject(builder -> builder
                .signatureDuration(uploadUrlTtl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build()));
        return new PresignedUpload(request.url().toString(), Instant.now().plus(uploadUrlTtl));
    }

    public Instant uploadUrlExpiresAt() {
        return Instant.now().plus(uploadUrlTtl);
    }

    public StoredObjectMetadata head(String key) {
        try {
            HeadObjectResponse response = s3.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return new StoredObjectMetadata(response.contentLength(), normalizeContentType(response.contentType()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return null;
            }
            throw exception;
        }
    }

    public byte[] readPrefix(String key, int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        try (ResponseInputStream<GetObjectResponse> response = s3.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key)
                        .range("bytes=0-" + (maxBytes - 1)).build())) {
            return response.readNBytes(maxBytes);
        } catch (java.io.IOException exception) {
            throw new RuntimeException("Failed to read object prefix " + key, exception);
        }
    }

    public void downloadTo(String key, Path destination) {
        try (ResponseInputStream<GetObjectResponse> response = s3.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            Files.copy(response, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException exception) {
            throw new RuntimeException("Failed to download object " + key, exception);
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /**
     * Build a public URL for an object. Works when R2 bucket has public access enabled
     * or when using a custom domain. Falls back to pre-signed URL pattern if needed.
     */
    public String publicUrl(String key) {
        if (mediaDeliveryBaseUrl != null && !mediaDeliverySigningKey.isBlank()) {
            long expiresAt = Instant.now().plus(deliveryUrlTtl).getEpochSecond();
            String signature = signDeliveryKey(key, expiresAt);
            return mediaDeliveryBaseUrl + "/" + encodeKey(key)
                    + "?expires=" + expiresAt + "&signature=" + signature;
        }
        if (publicBaseUrl != null) {
            return publicBaseUrl + "/" + key;
        }

        return s3.utilities().getUrl(b -> b.bucket(bucket).key(key)).toString();
    }

    @PreDestroy
    void close() {
        presigner.close();
        s3.close();
    }

    private String normalizeBaseUrl(String configuredValue) {
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

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType).trim().toLowerCase();
    }

    private String signDeliveryKey(String key, long expiresAt) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(mediaDeliverySigningKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return HexFormat.of().formatHex(mac.doFinal((key + "\n" + expiresAt).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign media delivery URL", exception);
        }
    }

    private String encodeKey(String key) {
        String[] segments = key.split("/");
        for (int index = 0; index < segments.length; index++) {
            segments[index] = URLEncoder.encode(segments[index], StandardCharsets.UTF_8).replace("+", "%20");
        }
        return String.join("/", segments);
    }
}
