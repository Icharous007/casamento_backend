package br.com.casamento.auth.resource;

import br.com.casamento.auth.dto.GuestRegisterRequest;
import br.com.casamento.auth.dto.GuestResolveResponse;
import br.com.casamento.auth.filter.RateLimitFilter.RateLimited;
import br.com.casamento.auth.service.GuestTokenService;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.guest.GuestProfile;
import br.com.casamento.guest.service.PhoneNumberService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;

@Path("/api/v1/guest-access")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GuestAccessResource {

    @Inject
    GuestTokenService guestTokenService;

    @Inject
    PhoneNumberService phoneNumberService;

    // -------------------------------------------------------------------------
    // Guest scans the event QR/save-the-date link, enters phone + name, and is
    // immediately granted an access token — no OTP/verification step.
    // -------------------------------------------------------------------------

    @POST
    @Path("/register")
    @RateLimited
    @Transactional
    public Response register(@Valid GuestRegisterRequest request) {
        if (!Boolean.TRUE.equals(request.acceptedTerms())) {
            throw AppException.badRequest("TERMS_NOT_ACCEPTED", "Aceite os termos para continuar.");
        }

        String phoneE164 = phoneNumberService.normalize(request.phone());
        if (phoneE164 == null || !phoneNumberService.isValidE164(phoneE164)) {
            throw AppException.badRequest("PHONE_INVALID", "Número de telefone inválido.");
        }

        Event event = loadEventBySlug(request.eventSlug());
        String displayName = request.displayName().strip();

        // Same phone within the event = same guest. Update the name if it changed.
        Guest guest = Guest.findByEventAndPhone(event.id, phoneE164);
        if (guest == null) {
            guest = new Guest();
            guest.event = event;
            guest.name = displayName;
            guest.phoneE164 = phoneE164;
            guest.source = "SELF_REGISTERED";
            guest.status = "ACTIVE";
            guest.persist();
        } else {
            if (!displayName.equals(guest.name)) {
                guest.name = displayName;
            }
            if (!"ACTIVE".equals(guest.status) && !"BLOCKED".equals(guest.status)) {
                guest.status = "ACTIVE";
            }
        }

        if ("BLOCKED".equals(guest.status)) {
            throw AppException.badRequest("GUEST_BLOCKED", "Convidado está bloqueado.");
        }

        GuestProfile profile = GuestProfile.findByGuest(guest);
        if (profile == null) {
            profile = new GuestProfile();
            profile.guest = guest;
            profile.displayName = displayName;
            profile.acceptedTerms = true;
            profile.acceptedTermsAt = OffsetDateTime.now();
            profile.persist();
        } else if (!profile.acceptedTerms) {
            profile.acceptedTerms = true;
            profile.acceptedTermsAt = OffsetDateTime.now();
        }

        String accessToken = guestTokenService.createToken(guest);

        return Response.ok(new GuestResolveResponse(
                guest.id.toString(),
                event.id.toString(),
                displayName,
                false,
                accessToken,
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

    private Event loadEventBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw AppException.notFound("Evento não encontrado.");
        }
        Event event = Event.find("slug", slug).firstResult();
        if (event == null) throw AppException.notFound("Evento não encontrado.");
        return event;
    }
}

