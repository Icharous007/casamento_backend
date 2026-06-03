package br.com.casamento.admin.media;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.media.dto.MediaItemResponse;
import br.com.casamento.media.service.MediaService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/admin/media")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "CERIMONIALISTA"})
public class AdminMediaResource {

    @Inject
    MediaService mediaService;

    @GET
    @Transactional
    public Response list(
            @QueryParam("eventId") String eventId,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("20") int pageSize
    ) {
        Event event = loadEvent(eventId);
        List<MediaItemResponse> items = mediaService.listAllForAdmin(event.id, page, pageSize);
        return Response.ok(items).build();
    }

    @PUT
    @Path("/{mediaId}/hide")
    @Transactional
    public Response hide(@PathParam("mediaId") UUID mediaId) {
        mediaService.hideMedia(mediaId);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{mediaId}")
    @Transactional
    @RolesAllowed("ADMIN")
    public Response delete(@PathParam("mediaId") UUID mediaId) {
        mediaService.deleteMedia(mediaId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/comments/{commentId}")
    @Transactional
    public Response removeComment(@PathParam("commentId") UUID commentId) {
        mediaService.removeComment(commentId);
        return Response.noContent().build();
    }

    private Event loadEvent(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            Event e = Event.findById(UUID.fromString(eventId));
            if (e == null) throw AppException.notFound("Evento não encontrado.");
            return e;
        }
        Event e = Event.find("ORDER BY createdAt ASC").firstResult();
        if (e == null) throw AppException.notFound("Nenhum evento cadastrado.");
        return e;
    }
}
