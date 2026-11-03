package br.com.casamento.admin.gifts;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.gift.dto.PixSettingsRequest;
import br.com.casamento.gift.dto.PixSettingsResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/v1/admin/gifts/pix-settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "CERIMONIALISTA"})
public class AdminPixSettingsResource {

    @GET
    @Transactional
    public Response get(@QueryParam("eventId") String eventId) {
        return Response.ok(PixSettingsResponse.from(loadEvent(eventId))).build();
    }

    @PUT
    @Transactional
    public Response update(@Valid PixSettingsRequest request, @QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        event.pixKey = request.pixKey().trim();
        event.pixKeyType = request.pixKeyType().toUpperCase();
        event.pixMerchantName = request.merchantName() == null || request.merchantName().isBlank()
                ? null : request.merchantName().trim();
        event.pixMerchantCity = request.merchantCity().trim();
        return Response.ok(PixSettingsResponse.from(event)).build();
    }

    private Event loadEvent(String eventId) {
        Event event;
        if (eventId != null && !eventId.isBlank()) {
            event = Event.findById(UUID.fromString(eventId));
        } else {
            event = Event.find("ORDER BY createdAt ASC").firstResult();
        }
        if (event == null) throw AppException.notFound("Evento não encontrado.");
        return event;
    }
}