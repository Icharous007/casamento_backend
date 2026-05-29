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

    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:5173")
    String frontendUrl;

    @Transactional
    public GuestResponse create(Event event, CreateGuestRequest request) {
        Guest guest = new Guest();
        guest.event = event;
        guest.name = request.displayName();
        guest.status = "INVITED";
        guest.persist();

        String rawToken = tokenService.createToken(guest);

        return toResponse(guest, rawToken, null, null);
    }

    public GuestResponse toResponse(Guest guest, String rawToken, GuestProfile profile, Rsvp rsvp) {
        String qrCodeUrl = frontendUrl + "/acesso?token=" + (rawToken != null ? rawToken : "");
        String rsvpStatus = rsvp != null ? rsvp.response : "PENDING";
        String email = profile != null ? profile.email : null;

        return new GuestResponse(
                guest.id.toString(),
                guest.name,
                guest.status,
                email,
                rawToken,
                qrCodeUrl,
                rsvpStatus,
                guest.createdAt
        );
    }

    public List<Guest> list(UUID eventId, String search, int page, int pageSize) {
        if (search != null && !search.isBlank()) {
            return Guest.find(
                    "event.id = ?1 AND LOWER(name) LIKE LOWER(CONCAT('%', ?2, '%'))",
                    eventId, search
            ).page(page - 1, pageSize).list();
        }
        return Guest.find("event.id = ?1 ORDER BY name ASC", eventId)
                .page(page - 1, pageSize).list();
    }

    public long count(UUID eventId, String search) {
        if (search != null && !search.isBlank()) {
            return Guest.count(
                    "event.id = ?1 AND LOWER(name) LIKE LOWER(CONCAT('%', ?2, '%'))",
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
