package br.com.casamento.guest.media;

import br.com.casamento.auth.filter.GuestTokenFilter.RequiresGuestToken;
import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.media.dto.AddCommentRequest;
import br.com.casamento.media.dto.CreateMediaUploadIntentRequest;
import br.com.casamento.media.dto.MediaCommentResponse;
import br.com.casamento.media.dto.MediaItemResponse;
import br.com.casamento.media.dto.MediaUploadIntentResponse;
import br.com.casamento.media.service.MediaService;
import br.com.casamento.storage.R2StorageService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import br.com.casamento.common.filter.TraceIdFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/media")
@Produces(MediaType.APPLICATION_JSON)
@RequiresGuestToken
public class GuestMediaResource {

    private static final Logger LOG = Logger.getLogger(GuestMediaResource.class);

    @Inject
    GuestContext guestContext;

    @Inject
    MediaService mediaService;

    @Inject
    R2StorageService r2;

    @POST
    @Path("/upload-intents")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createUploadIntent(@Valid CreateMediaUploadIntentRequest request,
                                       @HeaderParam("Idempotency-Key") String idempotencyKey) {
        MediaUploadIntentResponse response = mediaService.createDirectUploadIntent(
                guestContext.getGuest(), request, idempotencyKey);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/upload-intents/{mediaId}/complete")
    public Response completeUpload(@PathParam("mediaId") UUID mediaId) {
        Guest guest = guestContext.getGuest();
        MediaService.DirectUploadVerification verification = mediaService.loadDirectUploadVerification(guest, mediaId);
        R2StorageService.StoredObjectMetadata metadata = r2.head(verification.r2Key());
        byte[] header = metadata == null || verification.completed() ? new byte[0] : r2.readPrefix(verification.r2Key(), 32);
        MediaItemResponse response = mediaService.publishDirectUpload(guest, mediaId, metadata, header);
        return Response.ok(response).build();
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response upload(
            @FormParam("file") FileUpload file,
            @FormParam("caption") String caption
    ) throws IOException {
        if (file == null) {
            LOG.infof("media_upload event=upload.failure stage=request errorCode=FILE_REQUIRED traceId=%s outcome=failure",
                    traceId());
            throw br.com.casamento.common.exception.AppException
                    .badRequest("FILE_REQUIRED", "Arquivo é obrigatório.");
        }
        Guest guest = guestContext.getGuest();
        long size;
        try {
            size = Files.size(file.filePath());
        } catch (IOException exception) {
            LOG.errorf(exception, "media_upload event=upload.failure stage=request errorCode=FILE_READ_ERROR traceId=%s outcome=failure",
                    traceId());
            throw exception;
        }
        String contentType = file.contentType() != null
                ? file.contentType()
                : "application/octet-stream";
        MediaItemResponse response = mediaService.upload(
            guest, file.fileName(), contentType, size, file.filePath(), caption);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    private String traceId() {
        Object value = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return value != null ? value.toString() : "none";
    }

    @GET
    @Transactional
    public Response gallery(
            @QueryParam("sort") @DefaultValue("recent") String sort,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("12") int pageSize
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
