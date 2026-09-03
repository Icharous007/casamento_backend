package br.com.casamento.support;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.guest.GuestProfile;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {
    }

    public static Event persistedEvent() {
        Event event = new Event();
        event.slug = "event-" + UUID.randomUUID();
        event.title = "Casamento Teste";
        event.coupleNames = "Noivo & Noiva";
        event.eventDate = OffsetDateTime.now().plusMonths(2);
        event.status = "DRAFT";
        event.timezone = "America/Sao_Paulo";
        event.persist();
        return event;
    }

    public static Guest persistedGuest(Event event) {
        Guest guest = new Guest();
        guest.event = event;
        guest.name = "Convidado " + UUID.randomUUID();
        guest.phoneE164 = "+55" + (10000000000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 89999999999L));
        guest.status = "INVITED";
        guest.source = "IMPORTED";
        guest.persist();
        return guest;
    }

    public static GuestProfile persistedGuestProfile(Guest guest) {
        GuestProfile profile = new GuestProfile();
        profile.guest = guest;
        profile.displayName = guest.name;
        profile.acceptedTerms = true;
        profile.acceptedTermsAt = OffsetDateTime.now();
        profile.persist();
        return profile;
    }
}
