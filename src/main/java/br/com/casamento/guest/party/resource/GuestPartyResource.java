package br.com.casamento.guest.party.resource;

import br.com.casamento.auth.filter.GuestTokenFilter.RequiresGuestToken;
import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.guest.party.dto.AddPartyMemberRequest;
import br.com.casamento.guest.party.dto.PartyMemberResponse;
import br.com.casamento.guest.party.service.GuestPartyService;
import br.com.casamento.rsvp.dto.RsvpRequest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for managing party members (family/dependents).
 * Allows an authenticated guest to confirm RSVP on behalf of others.
 */
@Path("/api/v1/me/party")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiresGuestToken
public class GuestPartyResource {

    @Inject
    GuestContext guestContext;

    @Inject
    GuestPartyService partyService;

    /**
     * GET /api/v1/me/party
     * Lists all party members (self + dependents/managed guests) with RSVP status.
     */
    @GET
    @Transactional
    public Response listPartyMembers() {
        var eventId = guestContext.getEventId();
        var guestId = guestContext.getGuestId();

        br.com.casamento.domain.event.Event event = Event.findById(eventId);
        if (event == null) throw new WebApplicationException(401);

        List<PartyMemberResponse> members = partyService.listPartyMembers(guestId, event);
        return Response.ok(members).build();
    }

    /**
     * POST /api/v1/me/party
     * Adds a new party member (dependent or linked adult).
     */
    @POST
    @Transactional
    public Response addPartyMember(@Valid AddPartyMemberRequest request) {
        var eventId = guestContext.getEventId();
        var guestId = guestContext.getGuestId();

        Event event = Event.findById(eventId);
        if (event == null) throw new WebApplicationException(401);

        PartyMemberResponse response = partyService.addPartyMember(guestId, event, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /**
     * PUT /api/v1/me/party/{guestId}/rsvp
     * Confirms RSVP (attendance/decline) for a specific party member.
     */
    @PUT
    @Path("/{guestId}/rsvp")
    @Transactional
    public Response confirmPartyMemberRsvp(
            @PathParam("guestId") UUID targetGuestId,
            @Valid RsvpRequest request
    ) {
        var eventId = guestContext.getEventId();
        var callerId = guestContext.getGuestId();

        Event event = Event.findById(eventId);
        if (event == null) throw new WebApplicationException(401);

        return Response.ok(partyService.confirmRsvp(callerId, targetGuestId, request, event)).build();
    }

    /**
     * DELETE /api/v1/me/party/{guestId}
     * Removes a party member (only if they haven't self-registered).
     */
    @DELETE
    @Path("/{guestId}")
    @Transactional
    public Response removePartyMember(@PathParam("guestId") UUID targetGuestId) {
        var eventId = guestContext.getEventId();
        var guestId = guestContext.getGuestId();

        Event event = Event.findById(eventId);
        if (event == null) throw new WebApplicationException(401);

        partyService.removePartyMember(guestId, targetGuestId, event);
        return Response.noContent().build();
    }
}
