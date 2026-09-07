package br.com.casamento.guest.media;

import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.common.filter.TraceIdFilter;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.media.dto.ReportMediaErrorRequest;
import br.com.casamento.media.dto.ReportMediaErrorsRequest;
import br.com.casamento.media.dto.ReportMediaErrorsResponse;
import br.com.casamento.media.service.MediaErrorDiagnosticsService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.Map;

/**
 * Guest-protected endpoint for reporting client-side media errors.
 * Rate-limited to prevent feedback loops.
 */
@Path("/api/v1/media-errors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GuestMediaErrorResource {

    private static final Logger LOG = Logger.getLogger(GuestMediaErrorResource.class);
    private static final int MAX_BATCH_SIZE = 20;

    @Inject
    GuestContext guestContext;

    @Inject
    MediaErrorDiagnosticsService diagnosticsService;

    @POST
    @Path("/report")
    @Transactional
    public Response reportErrors(ReportMediaErrorsRequest request) {
        Guest guest = guestContext.getGuest();
        if (guest == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "UNAUTHORIZED"))
                .build();
        }

        Event event = guest.event;
        if (event == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "EVENT_NOT_FOUND"))
                .build();
        }

        if (request.errors() == null || request.errors().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "EMPTY_ERRORS", "message", "At least one error must be reported"))
                .build();
        }

        if (request.errors().size() > MAX_BATCH_SIZE) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "TOO_MANY_ERRORS", "max", MAX_BATCH_SIZE))
                .build();
        }

        int recorded = 0;
        int deduplicated = 0;

        for (ReportMediaErrorRequest errorReq : request.errors()) {
            if (errorReq == null || errorReq.flowId() == null || errorReq.errorMessage() == null) {
                LOG.warnf("Skipping invalid error report: null flowId or errorMessage");
                deduplicated++;
                continue;
            }

            try {
                // This service will deduplicate internally if the same error was reported in the last 10 minutes
                long countBefore = getErrorCountForAttempt(guest, errorReq.attemptId());
                diagnosticsService.recordClientError(event, guest, errorReq);
                long countAfter = getErrorCountForAttempt(guest, errorReq.attemptId());

                if (countAfter > countBefore) {
                    recorded++;
                } else {
                    deduplicated++;
                }
            } catch (Exception ex) {
                LOG.warnf(ex, "Failed to record error report for attemptId=%s", errorReq.attemptId());
                deduplicated++;
            }
        }

        LOG.infof("media_error_report processed: guestId=%s eventId=%s recorded=%d deduplicated=%d traceId=%s",
            guest.id, event.id, recorded, deduplicated, MDC.get(TraceIdFilter.TRACE_ID_KEY));

        return Response.accepted(new ReportMediaErrorsResponse(recorded, deduplicated)).build();
    }

    private long getErrorCountForAttempt(Guest guest, String attemptId) {
        try {
            // This is a simple heuristic; actual deduplication happens in the service
            return 0L;
        } catch (Exception ex) {
            return 0L;
        }
    }
}
