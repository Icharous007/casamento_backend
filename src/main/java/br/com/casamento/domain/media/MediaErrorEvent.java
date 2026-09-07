package br.com.casamento.domain.media;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;

@Entity
@Table(name = "media_error_events")
public class MediaErrorEvent extends PanacheEntityBase {

    @Id
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    public Guest guest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    public MediaAsset media;

    @Column(nullable = false, length = 20)
    public String source; // CLIENT or SERVER

    @Column(nullable = false, length = 50)
    public String stage; // validation, upload, intent, cors, complete, variant, gallery

    @Column(name = "error_code", nullable = false, length = 50)
    public String errorCode; // CORS_BLOCKED, UPLOAD_LIMIT_EXCEEDED, etc.

    @Column(name = "error_category", nullable = false, length = 30)
    public String errorCategory; // timeout, auth, network, http, browser, cors, unknown

    @Column(name = "error_message", length = 500)
    public String errorMessage; // sanitized; no URLs, tokens, or keys

    @Column(name = "http_status")
    public Integer httpStatus;

    @Column(name = "axios_code", length = 50)
    public String axiosCode;

    @Column(name = "media_type", length = 10)
    public String mediaType; // PHOTO or VIDEO

    @Column(name = "content_type", length = 100)
    public String contentType;

    @Column(name = "file_size_bytes")
    public Long fileSizeBytes;

    @Column(name = "browser_descriptor", length = 200)
    public String browserDescriptor; // sanitized user agent

    @Column(name = "duration_ms")
    public Integer durationMs;

    @Column
    public Boolean retryable = true;

    @Column(name = "trace_id", length = 100)
    public String traceId;

    @Column(name = "flow_id", length = 100)
    public String flowId;

    @Column(name = "attempt_id", length = 100)
    public String attemptId;

    @Column(name = "client_route", length = 200)
    public String clientRoute;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    public OffsetDateTime resolvedAt;

    @Column(name = "resolution_note", length = 500)
    public String resolutionNote;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
