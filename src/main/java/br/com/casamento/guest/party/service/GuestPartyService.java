package br.com.casamento.guest.party.service;

import br.com.casamento.auth.entity.GuestAccessToken;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.rsvp.Rsvp;
import br.com.casamento.guest.party.dto.AddPartyMemberRequest;
import br.com.casamento.guest.party.dto.PartyMemberResponse;
import br.com.casamento.guest.service.PhoneNumberService;
import br.com.casamento.rsvp.dto.RsvpRequest;
import br.com.casamento.rsvp.dto.RsvpResponse;
import br.com.casamento.rsvp.service.RsvpService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for party member management.
 * Handles adding dependents (children without phones) and proxy confirmations
 * (other adults by phone+name, or adults already in system but without self-access).
 */
@ApplicationScoped
public class GuestPartyService {

    @Inject
    PhoneNumberService phoneNumberService;

    @Inject
    EntityManager entityManager;

    @Inject
    RsvpService rsvpService;

    /**
     * Lists all party members for the authenticated guest:
     * - Self
     * - Guests managed by this guest (dependents/added adults)
     */
    @Transactional
    public List<PartyMemberResponse> listPartyMembers(UUID managerId, Event event) {
        Guest manager = Guest.findById(managerId);
        if (manager == null || !manager.event.id.equals(event.id)) {
            throw AppException.accessDenied("Acesso negado.");
        }

        // Self
        Rsvp selfRsvp = Rsvp.findByGuest(manager);
        List<PartyMemberResponse> members = List.of(toResponse(manager, selfRsvp, true, false, null));

        // Dependents/managed guests
        List<Guest> managed = Guest.find("managedByGuest = ?1 ORDER BY name ASC", manager).list();
        List<PartyMemberResponse> managedResponses = managed.stream()
                .map(g -> {
                    Rsvp rsvp = Rsvp.findByGuest(g);
                    return toResponse(g, rsvp, false, true, manager.name);
                })
                .toList();

        // Combine: self + managed (as mutable list to allow concat)
        var result = new java.util.ArrayList<>(members);
        result.addAll(managedResponses);
        return result;
    }

    /**
     * Adds a party member (dependent or linked adult).
     * 
     * Rules:
     * 1. No phone -> always create new managed guest (child dependent).
     * 2. With phone:
     *    a. No existing guest -> create new, managed by caller.
     *    b. Existing guest with active self-access token -> 409 GUEST_ALREADY_HAS_ACCESS (block).
     *    c. Existing guest, never managed (managed_by_guest_id null) -> "claim" it.
     *    d. Existing guest, managed by caller -> idempotent update (name).
     *    e. Existing guest, managed by other guest -> 409 GUEST_ALREADY_MANAGED.
     */
    @Transactional
    public PartyMemberResponse addPartyMember(
            UUID managerId,
            Event event,
            AddPartyMemberRequest request
    ) {
        Guest manager = Guest.findById(managerId);
        if (manager == null || !manager.event.id.equals(event.id)) {
            throw AppException.accessDenied("Acesso negado.");
        }

        // Validate guest type
        if (!request.guestType().matches("ADULT|CHILD")) {
            throw AppException.badRequest("INVALID_GUEST_TYPE", "Tipo de convidado inválido.");
        }

        String name = request.name().strip();
        if (name.isEmpty()) {
            throw AppException.badRequest("NAME_EMPTY", "Nome não pode estar vazio.");
        }

        validateAddRsvp(request);

        // Self-check: cannot add self
        if (request.phone() != null) {
            String phoneE164 = phoneNumberService.normalize(request.phone());
            if (phoneE164 != null && phoneE164.equals(manager.phoneE164)) {
                throw AppException.badRequest("CANNOT_ADD_SELF", "Não é possível adicionar a si mesmo.");
            }
        }

        // Case 1: No phone -> always create new managed child
        if (request.phone() == null || request.phone().isBlank()) {
            return createManagedGuest(manager, event, name, null, request);
        }

        // Case 2: With phone -> lookup existing
        String phoneE164 = phoneNumberService.normalize(request.phone());
        if (phoneE164 == null || !phoneNumberService.isValidE164(phoneE164)) {
            throw AppException.badRequest("PHONE_INVALID", "Número de telefone inválido.");
        }

        Guest existing = Guest.findByEventAndPhone(event.id, phoneE164);

        if (existing == null) {
            // Case 2a: Not found -> create new, managed by caller
            return createManagedGuest(manager, event, name, phoneE164, request);
        }

        // Case 2b: Existing guest with active self-access token -> block
        GuestAccessToken activeToken = GuestAccessToken
                .find("guest = ?1 AND revoked = false", existing)
                .firstResult();
        if (activeToken != null) {
            throw AppException.conflict(
                    "GUEST_ALREADY_HAS_ACCESS",
                    "Essa pessoa já acessa o evento por conta própria. Peça para ela confirmar a presença dela mesma."
            );
        }

        // Case 2c: Existing guest, never managed -> claim it
        if (existing.managedByGuest == null) {
            existing.managedByGuest = manager;
            existing.name = name;
            existing.guestType = request.guestType();
            existing.age = request.age();
            RsvpResponse response = rsvpService.upsert(existing, event, toRsvpRequest(request), manager);
            return toResponse(existing, Rsvp.findByGuest(existing), false, true, manager.name);
        }

        // Case 2d: Existing guest, managed by caller -> idempotent update
        if (existing.managedByGuest.id.equals(managerId)) {
            existing.name = name;
            existing.guestType = request.guestType();
            existing.age = request.age();
            rsvpService.upsert(existing, event, toRsvpRequest(request), manager);
            return toResponse(existing, Rsvp.findByGuest(existing), false, true, manager.name);
        }

        // Case 2e: Existing guest, managed by other -> block
        throw AppException.conflict(
                "GUEST_ALREADY_MANAGED",
                "Essa pessoa já está sendo gerenciada por outro membro da família."
        );
    }

    /**
     * Confirms RSVP on behalf of a managed guest.
     */
    @Transactional
    public RsvpResponse confirmRsvp(UUID managerId, UUID targetGuestId, RsvpRequest request, Event event) {
        // Authorize: manager must be the one managing target, or target == manager (self)
        Guest manager = Guest.findById(managerId);
        Guest target = Guest.findById(targetGuestId);

        if (manager == null || target == null
                || !manager.event.id.equals(event.id)
                || !target.event.id.equals(event.id)) {
            throw AppException.accessDenied("Acesso negado.");
        }

        if (!targetGuestId.equals(managerId) && !target.managedByGuest.id.equals(managerId)) {
            throw AppException.accessDenied("Acesso negado.");
        }

        // Authorize: check RSVP deadline
        if (event.rsvpDeadlineAt != null && java.time.OffsetDateTime.now().isAfter(event.rsvpDeadlineAt)) {
            throw AppException.rsvpDeadlineExpired();
        }

        RsvpResponse response = rsvpService.upsert(target, event, request, manager);
        if ("DECLINED".equals(request.attendanceStatus())) {
            return response;
        }
        return response;
    }

    /** Backward-compatible overload for callers that only submit attendance. */
    @Transactional
    public RsvpResponse confirmRsvp(UUID managerId, UUID targetGuestId, String response, Event event) {
        return confirmRsvp(managerId, targetGuestId,
                new RsvpRequest(response, null, null, null), event);
    }

    /**
     * Removes a party member (only if they never self-registered).
     */
    @Transactional
    public void removePartyMember(UUID managerId, UUID targetGuestId, Event event) {
        Guest manager = Guest.findById(managerId);
        Guest target = Guest.findById(targetGuestId);

        if (manager == null || target == null
                || !manager.event.id.equals(event.id)
                || !target.event.id.equals(event.id)) {
            throw AppException.accessDenied("Acesso negado.");
        }

        // Only allow removal if target is managed by caller
        if (target.managedByGuest == null || !target.managedByGuest.id.equals(managerId)) {
            throw AppException.accessDenied("Acesso negado.");
        }

        // Only allow removal if target hasn't self-registered
        if ("SELF_REGISTERED".equals(target.source)) {
            throw AppException.conflict(
                    "CANNOT_REMOVE_SELF_REGISTERED",
                    "Não é possível remover um membro que já se registrou por conta própria."
            );
        }

        target.delete();
    }

    /**
     * Asserts a guest is managed by the caller (for authorization).
     */
    public void assertManaged(UUID targetGuestId, UUID managerId) {
        Guest target = Guest.findById(targetGuestId);
        if (target == null
                || target.managedByGuest == null
                || !target.managedByGuest.id.equals(managerId)) {
            throw AppException.accessDenied("Acesso negado.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private PartyMemberResponse createManagedGuest(
            Guest manager,
            Event event,
            String name,
            String phoneE164,
            AddPartyMemberRequest request
    ) {
        Guest newGuest = new Guest();
        newGuest.event = event;
        newGuest.name = name;
        newGuest.phoneE164 = phoneE164;
        newGuest.guestType = request.guestType();
        newGuest.age = request.age();
        newGuest.managedByGuest = manager;
        newGuest.source = "MANAGED";
        newGuest.status = "ACTIVE";
        newGuest.persist();

        rsvpService.upsert(newGuest, event, toRsvpRequest(request), manager);

        return toResponse(newGuest, Rsvp.findByGuest(newGuest), false, true, manager.name);
    }

    private void validateAddRsvp(AddPartyMemberRequest request) {
        if (request.attendanceStatus() == null || !request.attendanceStatus().matches("ATTENDING|DECLINED")) {
            throw AppException.badRequest("ATTENDANCE_REQUIRED", "Informe se a pessoa irá ao evento.");
        }
        requireAnswer(request.dietaryRestrictions(), "DIETARY_REQUIRED", "Informe as restrições alimentares ou escreva Não possui.");
        requireAnswer(request.allergies(), "ALLERGIES_REQUIRED", "Informe as alergias ou escreva Não possui.");
        requireAnswer(request.additionalInfo(), "ADDITIONAL_INFO_REQUIRED", "Informe dados adicionais ou escreva Não se aplica.");
        if ("CHILD".equals(request.guestType()) && (request.age() == null || request.age() < 0 || request.age() > 120)) {
            throw AppException.badRequest("AGE_REQUIRED", "Informe uma idade válida para a criança.");
        }
    }

    private void requireAnswer(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw AppException.badRequest(code, message);
        }
    }

    private RsvpRequest toRsvpRequest(AddPartyMemberRequest request) {
        return new RsvpRequest(request.attendanceStatus(), request.dietaryRestrictions(),
                request.allergies(), request.additionalInfo());
    }

    private PartyMemberResponse toResponse(
            Guest guest,
            Rsvp rsvp,
            boolean isSelf,
            boolean managedByMe,
            String managedByName
    ) {
        String rsvpStatus = rsvp != null ? rsvp.response : "PENDING";
        boolean selfConfirmationSuggested = "CHILD".equals(guest.guestType)
            && guest.phoneE164 != null && !guest.phoneE164.isBlank();
        return new PartyMemberResponse(
                guest.id.toString(),
                guest.name,
                guest.phoneE164 != null ? guest.phoneE164 : "",
                guest.guestType,
                guest.age,
                rsvpStatus,
                rsvp != null ? rsvp.dietaryRestrictions : null,
                rsvp != null ? rsvp.allergies : null,
                rsvp != null ? rsvp.additionalInfo : null,
                selfConfirmationSuggested,
                isSelf,
                managedByMe,
                managedByName,
                guest.createdAt
        );
    }
}
