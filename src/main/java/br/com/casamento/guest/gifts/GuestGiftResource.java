package br.com.casamento.guest.gifts;

import br.com.casamento.auth.filter.GuestTokenFilter.RequiresGuestToken;
import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.gift.dto.GiftItemResponse;
import br.com.casamento.gift.dto.MarkPurchasedResponse;
import br.com.casamento.gift.service.GiftService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/gifts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiresGuestToken
public class GuestGiftResource {

    @Inject
    GuestContext guestContext;

    @Inject
    GiftService giftService;

    @GET
    @Transactional
    public Response list() {
        UUID eventId = guestContext.getGuest().event.id;
        List<GiftItemResponse> items = giftService.listForGuest(eventId);
        return Response.ok(items).build();
    }

    @POST
    @Path("/{giftId}/mark-purchased")
    @Transactional
    public Response markPurchased(@PathParam("giftId") UUID giftId) {
        Guest guest = guestContext.getGuest();
        MarkPurchasedResponse response = giftService.markPurchased(giftId, guest);
        return Response.ok(response).build();
    }
}
