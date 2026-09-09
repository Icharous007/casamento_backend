package br.com.casamento.admin.resource;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.admin.dto.AdminRsvpItemResponse;
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
import java.nio.charset.StandardCharsets;

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

        List<AdminRsvpItemResponse> items = rsvps.stream()
            .map(this::toAdminItem)
                .toList();

        long attending = rsvps.stream().filter(r -> "ATTENDING".equals(r.response)).count();
        long declined = rsvps.stream().filter(r -> "DECLINED".equals(r.response)).count();

        return Response.ok(Map.of(
                "items", items,
                "summary", Map.of("attending", attending, "declined", declined, "total", items.size())
        )).build();
    }

    @GET
    @Path("/summary")
    @Transactional
    public Response summary(@QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        List<Guest> guests = Guest.find("event.id = ?1 AND status <> 'BLOCKED' ORDER BY name", event.id).list();
        long attending = 0;
        long declined = 0;
        long pending = 0;
        long attendingAdults = 0;
        long attendingChildren = 0;
        long dietary = 0;
        long allergies = 0;
        long additional = 0;
        for (Guest guest : guests) {
            Rsvp rsvp = Rsvp.findByGuest(guest);
            if (rsvp == null) {
                pending++;
            } else if ("ATTENDING".equals(rsvp.response)) {
                attending++;
                if ("CHILD".equals(guest.guestType)) attendingChildren++; else attendingAdults++;
                if (hasText(rsvp.dietaryRestrictions)) dietary++;
                if (hasText(rsvp.allergies)) allergies++;
                if (hasText(rsvp.additionalInfo)) additional++;
            } else {
                declined++;
            }
        }
        return Response.ok(Map.of(
                "totalGuests", guests.size(), "attending", attending, "declined", declined,
                "pending", pending, "attendingAdults", attendingAdults,
                "attendingChildren", attendingChildren, "withDietaryRestrictions", dietary,
                "withAllergies", allergies, "withAdditionalInfo", additional
        )).build();
    }

    @GET
    @Path("/confirmed/export")
    @Produces("text/csv")
    @Transactional
    public Response exportConfirmed(@QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        List<Rsvp> confirmed = rsvpService.listByEvent(event).stream()
                .filter(r -> "ATTENDING".equals(r.response)).toList();
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Nome;Telefone;Tipo;Idade;Restrições alimentares;Alergias;Informações adicionais;Confirmado por;Respondido em;Atualizado em\n");
        confirmed.stream().map(this::toAdminItem).forEach(item -> csv.append(row(item)));
        return Response.ok(csv.toString().getBytes(StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=convidados-confirmados.csv")
                .build();
    }

    private AdminRsvpItemResponse toAdminItem(Rsvp rsvp) {
        return new AdminRsvpItemResponse(rsvp.guest.id, rsvp.guest.name,
                rsvp.guest.phoneE164, rsvp.guest.guestType, rsvp.guest.age, rsvp.response,
                rsvp.dietaryRestrictions, rsvp.allergies, rsvp.additionalInfo,
                rsvp.confirmedByGuest != null ? rsvp.confirmedByGuest.name : null,
                rsvp.respondedAt, rsvp.updatedAt);
    }

    private String row(AdminRsvpItemResponse item) {
        return String.join(";", csv(item.guestName()), csv(item.phone()), csv(item.guestType()),
                csv(item.age()), csv(item.dietaryRestrictions()), csv(item.allergies()),
                csv(item.additionalInfo()), csv(item.confirmedByGuestName()),
                csv(item.respondedAt()), csv(item.lastChangedAt())) + "\n";
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
