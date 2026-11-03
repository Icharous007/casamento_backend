package br.com.casamento.guest.gifts;

import br.com.casamento.auth.filter.GuestTokenFilter.RequiresGuestToken;
import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.gift.dto.GiftItemResponse;
import br.com.casamento.gift.dto.MarkPurchasedResponse;
import br.com.casamento.gift.dto.PixContributionRequest;
import br.com.casamento.gift.dto.PixContributionResponse;
import br.com.casamento.gift.dto.PixPaymentResponse;
import br.com.casamento.gift.service.GiftService;
import br.com.casamento.gift.service.PixPayloadService;
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

    @Inject
    PixPayloadService pixPayloadService;

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

    @GET
    @Path("/{giftId}/pix")
    @Transactional
    public Response pix(@PathParam("giftId") UUID giftId) {
        Guest guest = guestContext.getGuest();
        var item = giftService.findVisibleItem(guest.event.id, giftId, "WEDDING");
        PixPaymentResponse response = pixPayloadService.create(guest.event, item);
        return Response.ok(response).build();
    }

    @POST
    @Path("/{giftId}/pix-contributions")
    @Transactional
    public Response reportPixContribution(@PathParam("giftId") UUID giftId,
                                          @jakarta.validation.Valid PixContributionRequest request) {
        Guest guest = guestContext.getGuest();
        var item = giftService.findVisibleItem(guest.event.id, giftId, "WEDDING");
        if (item.priceAmount == null) {
            throw br.com.casamento.common.exception.AppException.badRequest(
                    "GIFT_PRICE_REQUIRED", "Este presente ainda não tem um valor PIX configurado.");
        }
        var contribution = giftService.reportPixContribution(item, guest,
                request.giverName().trim(), request.giverPhone().trim(), request.message());
        return Response.status(Response.Status.CREATED)
                .entity(PixContributionResponse.from(contribution)).build();
    }
}
