package br.com.casamento.admin.gifts;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.gift.dto.*;
import br.com.casamento.gift.service.GiftService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/admin/gifts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "CERIMONIALISTA"})
public class AdminGiftResource {

    @Inject
    GiftService giftService;

    @GET
    @Transactional
    public Response list(@QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        List<GiftItemAdminResponse> items = giftService.listForAdmin(event.id);
        return Response.ok(items).build();
    }

    @POST
    @Transactional
    public Response create(@Valid CreateGiftRequest request, @QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        GiftItemAdminResponse created = giftService.create(event, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") UUID id, @Valid UpdateGiftRequest request,
                           @QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        GiftItemAdminResponse updated = giftService.update(id, event, request);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed("ADMIN")
    public Response delete(@PathParam("id") UUID id, @QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        giftService.delete(id, event);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/unmark-purchased")
    @Transactional
    @RolesAllowed("ADMIN")
    public Response unmarkPurchased(@PathParam("id") UUID id, @QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        GiftItemAdminResponse result = giftService.unmarkPurchased(id, event);
        return Response.ok(result).build();
    }

    private Event loadEvent(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            Event e = Event.findById(UUID.fromString(eventId));
            if (e == null) throw AppException.notFound("Evento não encontrado.");
            return e;
        }
        Event e = Event.find("ORDER BY createdAt ASC").firstResult();
        if (e == null) throw AppException.notFound("Nenhum evento cadastrado.");
        return e;
    }
}
