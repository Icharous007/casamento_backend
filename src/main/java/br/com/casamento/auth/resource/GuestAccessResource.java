package br.com.casamento.auth.resource;

import br.com.casamento.auth.dto.GuestResolveRequest;
import br.com.casamento.auth.dto.GuestResolveResponse;
import br.com.casamento.auth.entity.GuestAccessToken;
import br.com.casamento.auth.filter.RateLimitFilter.RateLimited;
import br.com.casamento.auth.service.GuestTokenService;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.guest.GuestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/guest-access")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GuestAccessResource {

    @Inject
    GuestTokenService guestTokenService;

    @POST
    @Path("/resolve")
    @RateLimited
    @Transactional
    public Response resolve(@Valid GuestResolveRequest request) {
        GuestAccessToken accessToken = guestTokenService.resolve(request.token());

        if (accessToken == null) {
            throw AppException.tokenInvalid();
        }
        if (accessToken.revoked) {
            throw AppException.tokenRevoked();
        }

        Guest guest = accessToken.guest;
        Event event = guest.event;
        GuestProfile profile = GuestProfile.findByGuest(guest);

        boolean requiresProfile = profile == null || !profile.acceptedTerms;
        String displayName = profile != null && profile.displayName != null
                ? profile.displayName
                : guest.name;

        return Response.ok(new GuestResolveResponse(
                guest.id.toString(),
                event.id.toString(),
                displayName,
                requiresProfile,
                new GuestResolveResponse.EventSummary(
                        event.title,
                        event.coupleNames,
                        event.eventDate,
                        event.rsvpDeadlineAt,
                        event.venueName,
                        event.venueAddress
                )
        )).build();
    }
}
