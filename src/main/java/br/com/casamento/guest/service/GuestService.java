package br.com.casamento.guest.service;

import br.com.casamento.auth.service.GuestTokenService;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.guest.GuestProfile;
import br.com.casamento.domain.rsvp.Rsvp;
import br.com.casamento.guest.dto.CreateGuestRequest;
import br.com.casamento.guest.dto.GuestResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GuestService {

    @Inject
    GuestTokenService tokenService;

    @Inject
    PhoneNumberService phoneNumberService;

    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:5173")
    String frontendUrl;

    @Transactional
    public GuestResponse create(Event event, CreateGuestRequest request) {
        String phoneE164 = phoneNumberService.normalize(request.phone());

        // Enforce one phone per event
        if (phoneE164 != null && Guest.findByEventAndPhone(event.id, phoneE164) != null) {
            throw br.com.casamento.common.exception.AppException.badRequest(
                    "PHONE_DUPLICATE", "Já existe um convidado com este telefone neste evento.");
        }

        Guest guest = new Guest();
        guest.event = event;
        guest.name = request.displayName();
        guest.phoneE164 = phoneE164;
        guest.source = "IMPORTED";
        guest.status = "INVITED";
        guest.persist();

        return toResponse(guest, null, null, null);
    }

    public GuestResponse toResponse(Guest guest, String rawToken, GuestProfile profile, Rsvp rsvp) {
        String eventQrUrl = frontendUrl + "/save-the-date?event="
                + (guest.event != null ? guest.event.slug : "");
        String rsvpStatus = rsvp != null ? rsvp.response : "PENDING";

        return new GuestResponse(
                guest.id.toString(),
                guest.name,
                guest.status,
                guest.phoneE164,
                guest.source,
                rawToken,
                eventQrUrl,
                rsvpStatus,
                guest.createdAt
        );
    }

    public List<Guest> list(UUID eventId, String search, int page, int pageSize) {
        if (search != null && !search.isBlank()) {
            return Guest.find(
                    "event.id = ?1 AND (LOWER(name) LIKE LOWER(CONCAT('%', ?2, '%')) OR phoneE164 LIKE CONCAT('%', ?2, '%'))",
                    eventId, search
            ).page(page - 1, pageSize).list();
        }
        return Guest.find("event.id = ?1 ORDER BY name ASC", eventId)
                .page(page - 1, pageSize).list();
    }

    public long count(UUID eventId, String search) {
        if (search != null && !search.isBlank()) {
            return Guest.count(
                    "event.id = ?1 AND (LOWER(name) LIKE LOWER(CONCAT('%', ?2, '%')) OR phoneE164 LIKE CONCAT('%', ?2, '%'))",
                    eventId, search
            );
        }
        return Guest.count("event.id = ?1", eventId);
    }

    @Transactional
    public void block(Guest guest) {
        guest.status = "BLOCKED";
    }

    @Transactional
    public void delete(Guest guest) {
        guest.delete();
    }
}
