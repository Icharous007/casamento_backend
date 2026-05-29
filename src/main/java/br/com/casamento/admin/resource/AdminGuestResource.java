package br.com.casamento.admin.resource;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.guest.GuestProfile;
import br.com.casamento.domain.rsvp.Rsvp;
import br.com.casamento.guest.dto.CreateGuestRequest;
import br.com.casamento.guest.dto.GuestResponse;
import br.com.casamento.guest.dto.ImportResultResponse;
import br.com.casamento.guest.service.GuestImportService;
import br.com.casamento.guest.service.GuestService;
import br.com.casamento.guest.service.QrCodeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Path("/api/v1/admin/guests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "CERIMONIALISTA"})
public class AdminGuestResource {

    @Inject
    GuestService guestService;

    @Inject
    GuestImportService importService;

    @Inject
    QrCodeService qrCodeService;

    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:5173")
    String frontendUrl;

    @GET
    @Transactional
    public Response list(
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("20") int pageSize,
            @QueryParam("eventId") String eventId
    ) {
        Event event = loadEvent(eventId);

        List<Guest> guests = guestService.list(event.id, search, page, pageSize);
        long total = guestService.count(event.id, search);

        List<GuestResponse> items = guests.stream().map(g -> {
            GuestProfile profile = GuestProfile.findByGuest(g);
            Rsvp rsvp = Rsvp.findByGuest(g);
            return guestService.toResponse(g, null, profile, rsvp);
        }).toList();

        return Response.ok(Map.of(
                "items", items,
                "page", page,
                "pageSize", pageSize,
                "total", total
        )).build();
    }

    @POST
    @Transactional
    public Response create(@Valid CreateGuestRequest request, @QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        GuestResponse response = guestService.create(event, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @Path("/{guestId}")
    @Transactional
    public Response get(@PathParam("guestId") UUID guestId) {
        Guest guest = Guest.findById(guestId);
        if (guest == null) throw AppException.notFound("Convidado não encontrado.");

        GuestProfile profile = GuestProfile.findByGuest(guest);
        Rsvp rsvp = Rsvp.findByGuest(guest);

        return Response.ok(guestService.toResponse(guest, null, profile, rsvp)).build();
    }

    @DELETE
    @Path("/{guestId}")
    @Transactional
    @RolesAllowed("ADMIN")
    public Response delete(@PathParam("guestId") UUID guestId) {
        Guest guest = Guest.findById(guestId);
        if (guest == null) throw AppException.notFound("Convidado não encontrado.");
        guestService.delete(guest);
        return Response.noContent().build();
    }

    @POST
    @Path("/{guestId}/block")
    @Transactional
    @RolesAllowed("ADMIN")
    public Response block(@PathParam("guestId") UUID guestId) {
        Guest guest = Guest.findById(guestId);
        if (guest == null) throw AppException.notFound("Convidado não encontrado.");
        guestService.block(guest);
        return Response.ok(Map.of("guestId", guestId, "status", "BLOCKED")).build();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("ADMIN")
    public Response importGuests(
            @FormParam("file") FileUpload file,
            @QueryParam("eventId") String eventId
    ) {
        if (file == null) {
            throw AppException.badRequest("FILE_REQUIRED", "Arquivo é obrigatório.");
        }

        Event event = loadEvent(eventId);
        String filename = file.fileName() != null ? file.fileName().toLowerCase() : "";

        try (InputStream is = Files.newInputStream(file.filePath())) {
            ImportResultResponse result;
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                result = importService.importExcel(event, is);
            } else {
                result = importService.importCsv(event, is);
            }
            return Response.ok(result).build();
        } catch (Exception e) {
            throw AppException.badRequest("IMPORT_ERROR", "Erro ao processar arquivo: " + e.getMessage());
        }
    }

    @GET
    @Path("/{guestId}/qr-code")
    @Produces("image/png")
    @Transactional
    public Response getQrCode(@PathParam("guestId") UUID guestId) {
        Guest guest = Guest.findById(guestId);
        if (guest == null) throw AppException.notFound("Convidado não encontrado.");

        var tokens = br.com.casamento.auth.entity.GuestAccessToken.find(
                "guest = ?1 AND revoked = false", guest).list();
        if (tokens.isEmpty()) throw AppException.notFound("Token de acesso não encontrado.");

        String rawHashToken = ((br.com.casamento.auth.entity.GuestAccessToken) tokens.get(0)).tokenHash;
        String url = frontendUrl + "/acesso?token=" + rawHashToken;

        byte[] png = qrCodeService.generateQrCodePng(url);
        return Response.ok(png)
                .header("Content-Disposition", "attachment; filename=\"qr-" + guestId + ".png\"")
                .build();
    }

    @GET
    @Path("/qr-codes/export")
    @Produces("application/zip")
    @Transactional
    public Response exportQrCodes(@QueryParam("eventId") String eventId) {
        Event event = loadEvent(eventId);
        List<Guest> guests = Guest.find("event.id = ?1 ORDER BY name ASC", event.id).list();

        StreamingOutput stream = out -> {
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Guest guest : guests) {
                    var tokenOpt = br.com.casamento.auth.entity.GuestAccessToken.find(
                            "guest = ?1 AND revoked = false", guest).firstResultOptional();
                    if (tokenOpt.isEmpty()) continue;

                    String tokenHash = ((br.com.casamento.auth.entity.GuestAccessToken) tokenOpt.get()).tokenHash;
                    String url = frontendUrl + "/acesso?token=" + tokenHash;
                    byte[] png = qrCodeService.generateQrCodePng(url);

                    String safeName = guest.name.replaceAll("[^a-zA-Z0-9\\-_]", "_");
                    zip.putNextEntry(new ZipEntry("qr-" + safeName + "-" + guest.id + ".png"));
                    zip.write(png);
                    zip.closeEntry();
                }
            }
        };

        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"qrcodes.zip\"")
                .build();
    }

    private Event loadEvent(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            Event e = Event.findById(UUID.fromString(eventId));
            if (e == null) throw AppException.notFound("Evento não encontrado.");
            return e;
        }
        // Default: return first event (single-event platform)
        Event e = Event.find("ORDER BY createdAt ASC").firstResult();
        if (e == null) throw AppException.notFound("Nenhum evento cadastrado.");
        return e;
    }
}
