package br.com.casamento.support;

import br.com.casamento.auth.service.GuestTokenService;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class GuestAuthTestSupport {

    @Inject
    GuestTokenService guestTokenService;

    public GuestTokenData createAuthenticatedGuest() {
        Event event = TestDataFactory.persistedEvent();
        Guest guest = TestDataFactory.persistedGuest(event);
        String rawToken = guestTokenService.createToken(guest);
        return new GuestTokenData(guest, rawToken);
    }

    public record GuestTokenData(Guest guest, String rawToken) {
        public String headerName() {
            return "X-Guest-Access-Token";
        }
    }
}
