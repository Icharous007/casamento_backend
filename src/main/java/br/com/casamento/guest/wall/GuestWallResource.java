package br.com.casamento.guest.wall;

import br.com.casamento.auth.filter.GuestTokenFilter.RequiresGuestToken;
import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.wall.dto.CreateTextPostRequest;
import br.com.casamento.wall.dto.WallPostResponse;
import br.com.casamento.wall.service.WallService;
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
import java.util.Map;

@Path("/api/v1/wall")
@Produces(MediaType.APPLICATION_JSON)
@RequiresGuestToken
public class GuestWallResource {

    @Inject
    GuestContext guestContext;

    @Inject
    WallService wallService;

    @GET
    @Transactional
    public Response list(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("20") int pageSize
    ) {
        Guest guest = guestContext.getGuest();
        Map<String, Object> result = wallService.list(guest.event.id, page, pageSize);
        return Response.ok(result).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createText(@Valid CreateTextPostRequest request) {
        Guest guest = guestContext.getGuest();
        WallPostResponse response = wallService.createTextPost(guest, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/audio")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response createAudio(
            @FormParam("file") FileUpload file,
            @FormParam("durationSec") int durationSec
    ) throws IOException {
        if (file == null) {
            throw AppException.badRequest("FILE_REQUIRED", "Arquivo de áudio é obrigatório.");
        }
        Guest guest = guestContext.getGuest();
        long size = Files.size(file.filePath());
        String contentType = file.contentType() != null ? file.contentType() : "audio/mpeg";
        try (InputStream is = Files.newInputStream(file.filePath())) {
            WallPostResponse response = wallService.createAudioPost(
                    guest, durationSec, is, size, contentType);
            return Response.status(Response.Status.CREATED).entity(response).build();
        }
    }
}
