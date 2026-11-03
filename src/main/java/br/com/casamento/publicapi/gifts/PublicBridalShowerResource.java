package br.com.casamento.publicapi.gifts;

import br.com.casamento.auth.filter.RateLimitFilter.RateLimited;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.gift.GiftItem;
import br.com.casamento.gift.dto.*;
import br.com.casamento.gift.service.GiftService;
import br.com.casamento.gift.service.PixPayloadService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/v1/public/cha-de-panela")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
@RateLimited
public class PublicBridalShowerResource {

    @Inject
    GiftService giftService;

    @Inject
    PixPayloadService pixPayloadService;

    @GET
    @Transactional
    public Response list() {
        Event event = loadEvent();
        return Response.ok(new PublicBridalShowerResponse(
                event.coupleNames,
                giftService.listForPublicCategory(event.id, "BRIDAL_SHOWER")
        )).build();
    }

    @GET
    @Path("/gifts/{giftId}/pix")
    @Transactional
    public Response pix(@PathParam("giftId") UUID giftId) {
        Event event = loadEvent();
        GiftItem item = giftService.findVisibleItem(event.id, giftId, "BRIDAL_SHOWER");
        return Response.ok(pixPayloadService.create(event, item)).build();
    }

    @POST
    @Path("/gifts/{giftId}/contributions")
    @Transactional
    public Response reportContribution(@PathParam("giftId") UUID giftId,
                                       @Valid PixContributionRequest request) {
        Event event = loadEvent();
        GiftItem item = giftService.findVisibleItem(event.id, giftId, "BRIDAL_SHOWER");
        if (item.priceAmount == null) {
            throw AppException.badRequest("GIFT_PRICE_REQUIRED", "Este presente ainda não tem um valor PIX configurado.");
        }
        var contribution = giftService.reportPublicPixContribution(item,
                request.giverName().trim(), request.giverPhone().trim(), request.message());
        return Response.status(Response.Status.CREATED)
                .entity(PixContributionResponse.from(contribution)).build();
    }

    private Event loadEvent() {
        Event event = Event.find("ORDER BY createdAt ASC").firstResult();
        if (event == null) throw AppException.notFound("Nenhum evento cadastrado.");
        return event;
    }
}