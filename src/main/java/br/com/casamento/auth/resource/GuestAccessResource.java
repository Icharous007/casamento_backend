package br.com.casamento.auth.resource;

import br.com.casamento.auth.dto.GuestRegisterRequest;
import br.com.casamento.auth.dto.GuestResolveResponse;
import br.com.casamento.auth.filter.RateLimitFilter.RateLimited;
import br.com.casamento.auth.service.GuestTokenService;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.guest.service.PhoneNumberService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/v1/guest-access")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GuestAccessResource {

    @Inject
    GuestTokenService guestTokenService;

    @Inject
    PhoneNumberService phoneNumberService;

        @Inject
        EntityManager entityManager;

        @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "app.guest-access.allow-draft-events", defaultValue = "false")
        boolean allowDraftEvents;

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
        ensureEventAcceptsGuests(event);
        String displayName = request.displayName().strip();

        Guest guest = upsertGuest(event, phoneE164, displayName);

        if ("BLOCKED".equals(guest.status)) {
            throw AppException.badRequest("GUEST_BLOCKED", "Convidado está bloqueado.");
        }

        upsertProfile(guest.id, displayName);

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

    private void ensureEventAcceptsGuests(Event event) {
        if ("ACTIVE".equals(event.status) || (allowDraftEvents && "DRAFT".equals(event.status))) {
            return;
        }
        throw AppException.badRequest("EVENT_INACTIVE", "Evento não está disponível para novos convidados.");
    }

    private Guest upsertGuest(Event event, String phoneE164, String displayName) {
        UUID guestId = (UUID) entityManager.createNativeQuery(
                        "INSERT INTO guests (event_id, name, phone_e164, source, status) "
                                + "VALUES (?1, ?2, ?3, 'SELF_REGISTERED', 'ACTIVE') "
                                + "ON CONFLICT (event_id, phone_e164) WHERE phone_e164 IS NOT NULL "
                                + "DO UPDATE SET name = EXCLUDED.name, "
                                + "status = CASE WHEN guests.status IN ('ACTIVE', 'BLOCKED') "
                                + "THEN guests.status ELSE 'ACTIVE' END "
                                + "RETURNING id")
                .setParameter(1, event.id)
                .setParameter(2, displayName)
                .setParameter(3, phoneE164)
                .getSingleResult();
        return Guest.findById(guestId);
    }

    private void upsertProfile(UUID guestId, String displayName) {
        entityManager.createNativeQuery(
                        "INSERT INTO guest_profiles (guest_id, display_name, accepted_terms, accepted_terms_at) "
                                + "VALUES (?1, ?2, true, NOW()) "
                                + "ON CONFLICT (guest_id) DO UPDATE SET display_name = EXCLUDED.display_name, "
                                + "accepted_terms = true, accepted_terms_at = "
                                + "COALESCE(guest_profiles.accepted_terms_at, EXCLUDED.accepted_terms_at)")
                .setParameter(1, guestId)
                .setParameter(2, displayName)
                .executeUpdate();
    }
}

