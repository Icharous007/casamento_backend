package br.com.casamento.guest.media;

import br.com.casamento.auth.filter.GuestTokenFilter.RequiresGuestToken;
import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.media.dto.AddCommentRequest;
import br.com.casamento.media.dto.MediaCommentResponse;
import br.com.casamento.media.dto.MediaItemResponse;
import br.com.casamento.media.service.MediaService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/media")
@Produces(MediaType.APPLICATION_JSON)
@RequiresGuestToken
public class GuestMediaResource {

    @Inject
    GuestContext guestContext;

    @Inject
    MediaService mediaService;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response upload(
            @FormParam("file") FileUpload file
    ) throws IOException {
        if (file == null) {
            throw br.com.casamento.common.exception.AppException
                    .badRequest("FILE_REQUIRED", "Arquivo é obrigatório.");
        }
        Guest guest = guestContext.getGuest();
        long size = Files.size(file.filePath());
        String contentType = file.contentType() != null
                ? file.contentType()
                : "application/octet-stream";
        try (InputStream is = Files.newInputStream(file.filePath())) {
            MediaItemResponse response = mediaService.upload(
                    guest, file.fileName(), contentType, size, is);
            return Response.status(Response.Status.CREATED).entity(response).build();
        }
    }

    @GET
    @Transactional
    public Response gallery(
            @QueryParam("sort") @DefaultValue("recent") String sort,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("20") int pageSize
    ) {
        Guest guest = guestContext.getGuest();
        Map<String, Object> result = mediaService.listGallery(
                guest.event, guest, sort, page, pageSize);
        return Response.ok(result).build();
    }

    @POST
    @Path("/{mediaId}/like")
    @Transactional
    public Response addLike(@PathParam("mediaId") UUID mediaId) {
        Guest guest = guestContext.getGuest();
        mediaService.addLike(mediaId, guest);
        return Response.ok(Map.of("mediaId", mediaId, "liked", true)).build();
    }

    @DELETE
    @Path("/{mediaId}/like")
    @Transactional
    public Response removeLike(@PathParam("mediaId") UUID mediaId) {
        Guest guest = guestContext.getGuest();
        mediaService.removeLike(mediaId, guest);
        return Response.ok(Map.of("mediaId", mediaId, "liked", false)).build();
    }

    @POST
    @Path("/{mediaId}/comments")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response addComment(@PathParam("mediaId") UUID mediaId,
                               @Valid AddCommentRequest request) {
        Guest guest = guestContext.getGuest();
        MediaCommentResponse response = mediaService.addComment(mediaId, guest, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @Path("/{mediaId}/comments")
    @Transactional
    public Response listComments(@PathParam("mediaId") UUID mediaId) {
        UUID eventId = guestContext.getGuest().event.id;
        List<MediaCommentResponse> comments = mediaService.listComments(mediaId, eventId);
        return Response.ok(comments).build();
    }
}
