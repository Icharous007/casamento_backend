package br.com.casamento.guest.resource;

import br.com.casamento.auth.filter.GuestTokenFilter.RequiresGuestToken;
import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.guest.GuestProfile;
import br.com.casamento.domain.rsvp.Rsvp;
import br.com.casamento.rsvp.dto.RsvpRequest;
import br.com.casamento.rsvp.dto.RsvpResponse;
import br.com.casamento.rsvp.service.RsvpService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * /api/v1/me — endpoints for the authenticated guest (self).
 */
@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiresGuestToken
public class GuestMeResource {

    @Inject
    GuestContext guestContext;

    @Inject
    RsvpService rsvpService;

    @GET
    @Transactional
    public Response getSelf() {
        // Reload as managed entities within this transaction
        Guest guest = Guest.find("SELECT g FROM Guest g JOIN FETCH g.event WHERE g.id = ?1", guestContext.getGuestId()).firstResult();
        if (guest == null) throw new WebApplicationException(401);
        GuestProfile profile = GuestProfile.findByGuest(guest);
        Event event = guest.event;

        Rsvp rsvp = Rsvp.findByGuest(guest);
        String rsvpStatus = rsvp != null ? rsvp.response : "PENDING";
        String displayName = profile != null && profile.displayName != null
                ? profile.displayName : guest.name;

        return Response.ok(Map.of(
                "guestId", guest.id.toString(),
                "displayName", displayName,
                "phone", guest.phoneE164 != null ? guest.phoneE164 : "",
                "rsvpStatus", rsvpStatus,
                "event", Map.of(
                        "title", event.title,
                        "coupleNames", event.coupleNames,
                        "eventStartAt", event.eventDate,
                        "rsvpDeadlineAt", event.rsvpDeadlineAt != null ? event.rsvpDeadlineAt : "",
                        "venueName", event.venueName != null ? event.venueName : "",
                        "venueAddress", event.venueAddress != null ? event.venueAddress : ""
                )
        )).build();
    }

    @PUT
    @Path("/rsvp")
    @Transactional
    public Response upsertRsvp(@Valid RsvpRequest request) {
        Guest guest = Guest.find("SELECT g FROM Guest g JOIN FETCH g.event WHERE g.id = ?1", guestContext.getGuestId()).firstResult();
        if (guest == null) throw new WebApplicationException(401);
        Event event = guest.event;
        RsvpResponse response = rsvpService.upsert(guest, event, request);
        return Response.ok(response).build();
    }
}
