package br.com.casamento.admin.wall;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.wall.service.WallService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@Path("/api/v1/admin/wall")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "CERIMONIALISTA"})
public class AdminWallResource {

    @Inject
    WallService wallService;

    @GET
    @Transactional
    public Response list(
            @QueryParam("eventId") String eventId,
            @QueryParam("status") String status,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("50") int pageSize
    ) {
        Event event = loadEvent(eventId);
        Map<String, Object> result = wallService.listForAdmin(event.id, status, page, pageSize);
        return Response.ok(result).build();
    }

    @GET
    @Path("/summary")
    @Transactional
    public Response summary(@QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        return Response.ok(wallService.summary(event.id)).build();
    }

    @DELETE
    @Path("/{postId}")
    @Transactional
    public Response remove(@PathParam("postId") UUID postId) {
        wallService.removePost(postId);
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
