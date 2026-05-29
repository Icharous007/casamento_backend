package br.com.casamento.admin.resource;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.rsvp.Rsvp;
import br.com.casamento.rsvp.dto.RsvpRequest;
import br.com.casamento.rsvp.dto.RsvpResponse;
import br.com.casamento.rsvp.service.RsvpService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/admin/rsvps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "CERIMONIALISTA"})
public class AdminRsvpResource {

    @Inject
    RsvpService rsvpService;

    @GET
    @Transactional
    public Response list(@QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        List<Rsvp> rsvps = rsvpService.listByEvent(event);

        List<RsvpResponse> items = rsvps.stream()
                .map(r -> new RsvpResponse(
                        r.guest.id.toString(),
                        r.response,
                        r.respondedAt,
                        r.updatedAt
                ))
                .toList();

        long attending = rsvps.stream().filter(r -> "ATTENDING".equals(r.response)).count();
        long declined = rsvps.stream().filter(r -> "DECLINED".equals(r.response)).count();

        return Response.ok(Map.of(
                "items", items,
                "summary", Map.of("attending", attending, "declined", declined, "total", items.size())
        )).build();
    }

    @PUT
    @Path("/{guestId}")
    @Transactional
    public Response override(@PathParam("guestId") UUID guestId, @Valid RsvpRequest request) {
        Guest guest = Guest.findById(guestId);
        if (guest == null) throw AppException.notFound("Convidado não encontrado.");

        RsvpResponse response = rsvpService.upsert(guest, guest.event, request);
        return Response.ok(response).build();
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
