package br.com.casamento.auth.security;

import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.guest.GuestProfile;
import jakarta.enterprise.context.RequestScoped;

import java.util.UUID;

/**
 * Request-scoped bean that holds the authenticated guest identity.
 * Populated by GuestTokenFilter on successful token validation.
 */
@RequestScoped
public class GuestContext {

    private Guest guest;
    private GuestProfile profile;

    public boolean isAuthenticated() {
        return guest != null;
    }

    public Guest getGuest() {
        return guest;
    }

    public GuestProfile getProfile() {
        return profile;
    }

    public UUID getGuestId() {
        return guest != null ? guest.id : null;
    }

    public UUID getEventId() {
        return guest != null ? guest.event.id : null;
    }

    public void populate(Guest guest, GuestProfile profile) {
        this.guest = guest;
        this.profile = profile;
    }
}
