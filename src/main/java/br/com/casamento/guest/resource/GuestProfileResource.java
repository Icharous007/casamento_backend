package br.com.casamento.guest.resource;

import br.com.casamento.auth.filter.GuestTokenFilter.RequiresGuestToken;
import br.com.casamento.auth.security.GuestContext;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.guest.GuestProfile;
import br.com.casamento.guest.dto.GuestProfileRequest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.Map;

@Path("/api/v1/guest-profile")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiresGuestToken
public class GuestProfileResource {

    @Inject
    GuestContext guestContext;

    @POST
    @Transactional
    public Response completeProfile(@Valid GuestProfileRequest request) {
        if (!Boolean.TRUE.equals(request.acceptedTerms())) {
            throw AppException.badRequest("TERMS_NOT_ACCEPTED", "Aceite os termos para continuar.");
        }

        // Reload as managed entity within this transaction
        Guest guest = Guest.findById(guestContext.getGuestId());
        if (guest == null) throw AppException.notFound("Convidado não encontrado.");

        GuestProfile existingProfile = GuestProfile.findByGuest(guest);
        if (existingProfile != null && existingProfile.acceptedTerms) {
            throw AppException.conflict("GUEST_PROFILE_ALREADY_COMPLETED",
                    "Perfil já foi completado anteriormente.");
        }

        GuestProfile profile = existingProfile;
        if (profile == null) {
            profile = new GuestProfile();
            profile.guest = guest;
        }

        profile.displayName = request.confirmedName();
        profile.email = request.confirmedEmail();
        profile.phone = request.confirmedPhone();
        profile.acceptedTerms = true;
        profile.acceptedTermsAt = OffsetDateTime.now();

        if (profile.id == null) {
            profile.persist();
        }

        guest.status = "ACTIVE";

        return Response.ok(Map.of(
                "guestId", guest.id.toString(),
                "confirmedEmail", profile.email,
                "profileCompleted", true
        )).build();
    }
}
