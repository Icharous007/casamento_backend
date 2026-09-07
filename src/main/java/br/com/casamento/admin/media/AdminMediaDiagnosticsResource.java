package br.com.casamento.admin.media;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.media.MediaErrorEvent;
import br.com.casamento.media.dto.MediaErrorEventPage;
import br.com.casamento.media.dto.MediaErrorEventResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin-only endpoints for media error diagnostics.
 */
@Path("/api/v1/admin/media/diagnostics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "CERIMONIALISTA"})
public class AdminMediaDiagnosticsResource {

    private static final Logger LOG = Logger.getLogger(AdminMediaDiagnosticsResource.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Inject
    EntityManager entityManager;

    /**
     * Get error summary for an event.
     */
    @GET
    @Path("/summary")
    @Transactional
    public Response getSummary(
            @QueryParam("eventId") String eventId,
            @QueryParam("hours") @DefaultValue("24") int hours,
            @QueryParam("limit") @DefaultValue("50") int limit
    ) {
        Event event = loadEventOrDefault(eventId);

        OffsetDateTime from = OffsetDateTime.now().minusHours(Math.max(1, Math.min(720, hours)));
        OffsetDateTime to = OffsetDateTime.now();

        try {
            List<Map<String, Object>> summary = (List<Map<String, Object>>) (Object) entityManager.createQuery(
                "SELECT new map(" +
                "  e.errorCode as code, " +
                "  e.errorCategory as category, " +
                "  e.stage as stage, " +
                "  e.source as source, " +
                "  COUNT(e) as count, " +
                "  SUM(CASE WHEN e.resolvedAt IS NULL THEN 1 ELSE 0 END) as unresolvedCount, " +
                "  MAX(e.createdAt) as latestAt " +
                ") " +
                "FROM MediaErrorEvent e " +
                "WHERE e.event.id = :eventId " +
                "AND e.createdAt BETWEEN :from AND :to " +
                "GROUP BY e.errorCode, e.errorCategory, e.stage, e.source " +
                "ORDER BY unresolvedCount DESC, latestAt DESC",
                Map.class
            )
            .setParameter("eventId", event.id)
            .setParameter("from", from)
            .setParameter("to", to)
            .setMaxResults(limit)
            .getResultList();

            return Response.ok(Map.of(
                "eventId", event.id,
                "from", from,
                "to", to,
                "summary", summary
            )).build();

        } catch (Exception ex) {
            LOG.warnf(ex, "Failed to get error summary for eventId=%s", event.id);
            return Response.serverError().entity(Map.of("error", "SUMMARY_FAILED")).build();
        }
    }

    /**
     * List error events with optional filters.
     */
    @GET
    @Path("/events")
    @Transactional
    public Response listEvents(
            @QueryParam("eventId") String eventId,
            @QueryParam("source") String source,
            @QueryParam("stage") String stage,
            @QueryParam("errorCode") String errorCode,
            @QueryParam("resolved") String resolved,
            @QueryParam("hours") @DefaultValue("24") int hours,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("20") int pageSize
    ) {
        Event event = loadEventOrDefault(eventId);

        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        page = Math.max(page, 1);
        int offset = (page - 1) * pageSize;

        OffsetDateTime from = OffsetDateTime.now().minusHours(Math.max(1, Math.min(720, hours)));
        OffsetDateTime to = OffsetDateTime.now();

        try {
            // Build dynamic query
            StringBuilder jpql = new StringBuilder(
                "FROM MediaErrorEvent e WHERE e.event.id = :eventId AND e.createdAt BETWEEN :from AND :to"
            );

            if (source != null && !source.isBlank()) {
                jpql.append(" AND e.source = :source");
            }
            if (stage != null && !stage.isBlank()) {
                jpql.append(" AND e.stage = :stage");
            }
            if (errorCode != null && !errorCode.isBlank()) {
                jpql.append(" AND e.errorCode = :errorCode");
            }
            if (resolved != null && !resolved.isBlank()) {
                if ("true".equalsIgnoreCase(resolved)) {
                    jpql.append(" AND e.resolvedAt IS NOT NULL");
                } else {
                    jpql.append(" AND e.resolvedAt IS NULL");
                }
            }

            // Count total
            String countQuery = "SELECT COUNT(e) " + jpql.toString();
            jakarta.persistence.Query countQueryObj = entityManager.createQuery(countQuery);
            countQueryObj.setParameter("eventId", event.id);
            countQueryObj.setParameter("from", from);
            countQueryObj.setParameter("to", to);
            if (source != null && !source.isBlank()) {
                countQueryObj.setParameter("source", source.toUpperCase());
            }
            if (stage != null && !stage.isBlank()) {
                countQueryObj.setParameter("stage", stage.toLowerCase());
            }
            if (errorCode != null && !errorCode.isBlank()) {
                countQueryObj.setParameter("errorCode", errorCode.toUpperCase());
            }
            Long total = (Long) countQueryObj.getSingleResult();

            // Fetch page
            String selectQuery = "SELECT e " + jpql.toString() + " ORDER BY e.createdAt DESC";
            jakarta.persistence.TypedQuery<MediaErrorEvent> selectQueryObj = entityManager.createQuery(selectQuery, MediaErrorEvent.class);
            selectQueryObj.setParameter("eventId", event.id);
            selectQueryObj.setParameter("from", from);
            selectQueryObj.setParameter("to", to);
            if (source != null && !source.isBlank()) {
                selectQueryObj.setParameter("source", source.toUpperCase());
            }
            if (stage != null && !stage.isBlank()) {
                selectQueryObj.setParameter("stage", stage.toLowerCase());
            }
            if (errorCode != null && !errorCode.isBlank()) {
                selectQueryObj.setParameter("errorCode", errorCode.toUpperCase());
            }
            selectQueryObj.setFirstResult(offset);
            selectQueryObj.setMaxResults(pageSize);

            List<MediaErrorEvent> events = selectQueryObj.getResultList();

            List<MediaErrorEventResponse> items = events.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

            boolean hasMore = (offset + pageSize) < total;

            return Response.ok(new MediaErrorEventPage(items, total, page, pageSize, hasMore)).build();

        } catch (Exception ex) {
            LOG.warnf(ex, "Failed to list error events for eventId=%s", event.id);
            return Response.serverError().entity(Map.of("error", "LIST_FAILED")).build();
        }
    }

    /**
     * Mark an error event as resolved.
     */
    @PUT
    @Path("/{errorEventId}/resolve")
    @Transactional
    public Response resolveEvent(
            @PathParam("errorEventId") String errorEventId,
            ResolveErrorRequest request
    ) {
        try {
            UUID id = UUID.fromString(errorEventId);
            MediaErrorEvent event = entityManager.find(MediaErrorEvent.class, id);
            if (event == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "ERROR_EVENT_NOT_FOUND"))
                    .build();
            }

            event.resolvedAt = OffsetDateTime.now();
            event.resolutionNote = request.note;
            entityManager.merge(event);

            LOG.infof("media_error_event resolved: eventId=%s note=%s", id, request.note);

            return Response.ok(Map.of("resolved", true)).build();

        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "INVALID_ID"))
                .build();
        } catch (Exception ex) {
            LOG.warnf(ex, "Failed to resolve error event: %s", errorEventId);
            return Response.serverError().entity(Map.of("error", "RESOLVE_FAILED")).build();
        }
    }

    /**
     * Delete an error event.
     */
    @DELETE
    @Path("/{errorEventId}")
    @Transactional
    public Response deleteEvent(@PathParam("errorEventId") String errorEventId) {
        try {
            UUID id = UUID.fromString(errorEventId);
            int deleted = entityManager.createQuery(
                "DELETE FROM MediaErrorEvent e WHERE e.id = :id"
            ).setParameter("id", id).executeUpdate();

            if (deleted == 0) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "ERROR_EVENT_NOT_FOUND"))
                    .build();
            }

            LOG.infof("media_error_event deleted: eventId=%s", id);

            return Response.noContent().build();

        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "INVALID_ID"))
                .build();
        } catch (Exception ex) {
            LOG.warnf(ex, "Failed to delete error event: %s", errorEventId);
            return Response.serverError().entity(Map.of("error", "DELETE_FAILED")).build();
        }
    }

    private Event loadEventOrDefault(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            try {
                Event e = entityManager.find(Event.class, UUID.fromString(eventId));
                if (e != null) return e;
            } catch (IllegalArgumentException ex) {
                // Invalid UUID format, fall through
            }
        }

        // Default: first/latest event
        Event e = (Event) entityManager.createQuery(
            "FROM Event ORDER BY createdAt DESC"
        ).setMaxResults(1).getResultStream().findFirst().orElse(null);

        if (e == null) {
            throw AppException.notFound("No events found");
        }

        return e;
    }

    private MediaErrorEventResponse toResponse(MediaErrorEvent event) {
        return new MediaErrorEventResponse(
            event.id,
            event.source,
            event.stage,
            event.errorCode,
            event.errorCategory,
            event.errorMessage,
            event.httpStatus,
            event.axiosCode,
            event.mediaType,
            event.contentType,
            event.fileSizeBytes,
            event.durationMs,
            event.retryable,
            event.traceId,
            event.flowId,
            event.attemptId,
            event.event.id,
            event.guest != null ? event.guest.id : null,
            event.media != null ? event.media.id : null,
            event.createdAt,
            event.resolvedAt,
            event.resolutionNote
        );
    }

    public static class ResolveErrorRequest {
        public String note;
    }
}
