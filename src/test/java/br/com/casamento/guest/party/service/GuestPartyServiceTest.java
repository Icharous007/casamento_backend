package br.com.casamento.guest.party.service;

import br.com.casamento.auth.entity.GuestAccessToken;
import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.rsvp.Rsvp;
import br.com.casamento.guest.party.dto.AddPartyMemberRequest;
import br.com.casamento.guest.party.dto.PartyMemberResponse;
import br.com.casamento.guest.service.PhoneNumberService;
import br.com.casamento.support.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@DisplayName("GuestPartyService Tests")
class GuestPartyServiceTest {

    @Inject
    GuestPartyService service;

    @Inject
    PhoneNumberService phoneNumberService;

    @Inject
    EntityManager entityManager;

    private Event event;
    private Guest manager;
    private Guest otherManager;

    @BeforeEach
    @Transactional
    void setUp() {
        event = TestDataFactory.persistedEvent();
        manager = TestDataFactory.persistedGuest(event);
        otherManager = TestDataFactory.persistedGuest(event);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // addPartyMember - No Phone Cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should create new managed child when no phone provided")
    @Transactional
    void shouldCreateManagedChildWithoutPhone() {
        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "João Silva",
                null,
                "CHILD",
                (short) 7
        );

        PartyMemberResponse response = service.addPartyMember(manager.id, event, request);

        assertNotNull(response.guestId());
        assertEquals("João Silva", response.name());
        assertEquals("CHILD", response.guestType());
        assertEquals((short) 7, (short) response.age());
        assertEquals("PENDING", response.rsvpStatus());
        assertFalse(response.isSelf());
        assertTrue(response.managedByMe());
        assertEquals(manager.name, response.managedByName());

        Guest created = Guest.findById(UUID.fromString(response.guestId()));
        assertNotNull(created);
        assertEquals(manager.id, created.managedByGuest.id);
        assertEquals("MANAGED", created.source);
    }

    @Test
    @DisplayName("Should create multiple children without phones")
    @Transactional
    void shouldCreateMultipleChildren() {
        AddPartyMemberRequest child1 = new AddPartyMemberRequest("João", null, "CHILD", (short) 7);
        AddPartyMemberRequest child2 = new AddPartyMemberRequest("Maria", null, "CHILD", (short) 5);

        PartyMemberResponse resp1 = service.addPartyMember(manager.id, event, child1);
        PartyMemberResponse resp2 = service.addPartyMember(manager.id, event, child2);

        assertNotEquals(resp1.guestId(), resp2.guestId());
        assertEquals("João", resp1.name());
        assertEquals("Maria", resp2.name());
    }

    @Test
    @DisplayName("Should reject empty name")
    @Transactional
    void shouldRejectEmptyName() {
        AddPartyMemberRequest request = new AddPartyMemberRequest("  ", null, "CHILD", null);

        AppException ex = assertThrows(AppException.class, () ->
                service.addPartyMember(manager.id, event, request)
        );
        assertEquals("NAME_EMPTY", ex.getCode());
    }

    @Test
    @DisplayName("Should reject invalid guest type")
    @Transactional
    void shouldRejectInvalidGuestType() {
        AddPartyMemberRequest request = new AddPartyMemberRequest("João", null, "INVALID_TYPE", null);

        AppException ex = assertThrows(AppException.class, () ->
                service.addPartyMember(manager.id, event, request)
        );
        assertEquals("INVALID_GUEST_TYPE", ex.getCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // addPartyMember - Phone Cases (5 scenarios)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Case 2a: New phone -> create managed guest")
    @Transactional
    void shouldCreateManagedAdultWithNewPhone() {
        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "Tio Fulano",
                "11987654321",
                "ADULT",
                (short) 45
        );

        PartyMemberResponse response = service.addPartyMember(manager.id, event, request);

        assertNotNull(response.guestId());
        assertEquals("Tio Fulano", response.name());
        assertEquals("+5511987654321", response.phone());
        assertEquals("ADULT", response.guestType());
        assertTrue(response.managedByMe());

        Guest created = Guest.findById(UUID.fromString(response.guestId()));
        assertEquals(manager.id, created.managedByGuest.id);
        assertEquals("MANAGED", created.source);
    }

    @Test
    @DisplayName("Case 2b: Existing phone with active token -> 409 GUEST_ALREADY_HAS_ACCESS")
    @Transactional
    void shouldBlockExistingGuestWithActiveToken() {
        String phone = "+5511987654321";
        Guest existingGuest = new Guest();
        existingGuest.event = event;
        existingGuest.name = "Tio com Acesso";
        existingGuest.phoneE164 = phone;
        existingGuest.status = "ACTIVE";
        existingGuest.source = "SELF_REGISTERED";
        existingGuest.persist();

        String tokenRaw = "test-token-" + UUID.randomUUID();
        String tokenHash = sha256Hex(tokenRaw);
        GuestAccessToken token = new GuestAccessToken();
        token.guest = existingGuest;
        token.tokenHash = tokenHash;
        token.revoked = false;
        token.persist();

        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "Tio com Acesso",
                "11987654321",
                "ADULT",
                null
        );

        AppException ex = assertThrows(AppException.class, () ->
                service.addPartyMember(manager.id, event, request)
        );
        assertEquals("GUEST_ALREADY_HAS_ACCESS", ex.getCode());
        assertTrue(ex.getMessage().contains("já acessa o evento"));
    }

    @Test
    @DisplayName("Case 2c: Existing phone without manager -> claim it")
    @Transactional
    void shouldClaimUnmanagedGuest() {
        String phone = "+5511999999999";
        Guest unmanaged = new Guest();
        unmanaged.event = event;
        unmanaged.name = "Tio Importado";
        unmanaged.phoneE164 = phone;
        unmanaged.status = "INVITED";
        unmanaged.source = "IMPORTED";
        unmanaged.managedByGuest = null;
        unmanaged.persist();

        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "Tio Importado",
                "11999999999",
                "ADULT",
                (short) 50
        );

        PartyMemberResponse response = service.addPartyMember(manager.id, event, request);

        assertEquals(unmanaged.id.toString(), response.guestId());
        assertTrue(response.managedByMe());
        assertEquals(manager.name, response.managedByName());

        Guest claimed = Guest.findById(unmanaged.id);
        assertEquals(manager.id, claimed.managedByGuest.id);
        assertEquals("ADULT", claimed.guestType);
        assertEquals((short) 50, claimed.age);
    }

    @Test
    @DisplayName("Case 2d: Managed by caller -> idempotent update")
    @Transactional
    void shouldIdempotentlyUpdateManagedByMe() {
        String phone = "+5511888888888";
        Guest managed = new Guest();
        managed.event = event;
        managed.name = "Tio Original";
        managed.phoneE164 = phone;
        managed.managedByGuest = manager;
        managed.guestType = "ADULT";
        managed.age = 40;
        managed.persist();

        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "Tio Atualizado",
                "11988888888",
                "ADULT",
                (short) 42
        );

        PartyMemberResponse response = service.addPartyMember(manager.id, event, request);

        assertEquals(managed.id.toString(), response.guestId());
        assertEquals("Tio Atualizado", response.name());
        assertEquals((short) 42, (short) response.age());

        Guest updated = Guest.findById(managed.id);
        assertEquals("Tio Atualizado", updated.name);
        assertEquals((short) 42, updated.age);
        assertEquals(manager.id, updated.managedByGuest.id);
    }

    @Test
    @DisplayName("Case 2e: Managed by other -> 409 GUEST_ALREADY_MANAGED")
    @Transactional
    void shouldBlockGuestManagedByOther() {
        String phone = "+5511777777777";
        Guest managedByOther = new Guest();
        managedByOther.event = event;
        managedByOther.name = "Tio do Outro";
        managedByOther.phoneE164 = phone;
        managedByOther.managedByGuest = otherManager;
        managedByOther.persist();

        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "Tio do Outro",
                "11977777777",
                "ADULT",
                null
        );

        AppException ex = assertThrows(AppException.class, () ->
                service.addPartyMember(manager.id, event, request)
        );
        assertEquals("GUEST_ALREADY_MANAGED", ex.getCode());
        assertTrue(ex.getMessage().contains("já está sendo gerenciada"));
    }

    @Test
    @DisplayName("Should reject invalid phone format")
    @Transactional
    void shouldRejectInvalidPhone() {
        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "João",
                "123",
                "CHILD",
                null
        );

        AppException ex = assertThrows(AppException.class, () ->
                service.addPartyMember(manager.id, event, request)
        );
        assertEquals("PHONE_INVALID", ex.getCode());
    }

    @Test
    @DisplayName("Should reject attempt to add self")
    @Transactional
    void shouldRejectAddingSelf() {
        String brazilianDigits = manager.phoneE164.substring(3);
        AddPartyMemberRequest request = new AddPartyMemberRequest(
                manager.name,
                brazilianDigits,
                "ADULT",
                null
        );

        AppException ex = assertThrows(AppException.class, () ->
                service.addPartyMember(manager.id, event, request)
        );
        assertEquals("CANNOT_ADD_SELF", ex.getCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listPartyMembers
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should list self + managed members")
    @Transactional
    void shouldListSelfAndManaged() {
        Guest child1 = new Guest();
        child1.event = event;
        child1.name = "João";
        child1.guestType = "CHILD";
        child1.age = 7;
        child1.managedByGuest = manager;
        child1.source = "MANAGED";
        child1.persist();

        Guest child2 = new Guest();
        child2.event = event;
        child2.name = "Maria";
        child2.guestType = "CHILD";
        child2.age = 5;
        child2.managedByGuest = manager;
        child2.source = "MANAGED";
        child2.persist();

        List<PartyMemberResponse> members = service.listPartyMembers(manager.id, event);

        assertEquals(3, members.size());
        assertTrue(members.stream().anyMatch(m -> m.isSelf()));
        assertTrue(members.stream().anyMatch(m -> m.name().equals("João")));
        assertTrue(members.stream().anyMatch(m -> m.name().equals("Maria")));
    }

    @Test
    @DisplayName("Should return only self for new manager")
    @Transactional
    void shouldReturnOnlySelfForNewManager() {
        List<PartyMemberResponse> members = service.listPartyMembers(manager.id, event);

        assertEquals(1, members.size());
        PartyMemberResponse self = members.get(0);
        assertTrue(self.isSelf());
        assertEquals(manager.name, self.name());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // confirmRsvp
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should confirm RSVP for managed member")
    @Transactional
    void shouldConfirmManagedMemberRsvp() {
        Guest child = new Guest();
        child.event = event;
        child.name = "João";
        child.guestType = "CHILD";
        child.managedByGuest = manager;
        child.persist();

        service.confirmRsvp(manager.id, child.id, "ATTENDING", event);

        Rsvp rsvp = Rsvp.findByGuest(child);
        assertNotNull(rsvp);
        assertEquals("ATTENDING", rsvp.response);
        assertEquals(manager.id, rsvp.confirmedByGuest.id);
    }

    @Test
    @DisplayName("Should allow self-confirmation")
    @Transactional
    void shouldAllowSelfConfirmation() {
        service.confirmRsvp(manager.id, manager.id, "DECLINED", event);

        Rsvp rsvp = Rsvp.findByGuest(manager);
        assertNotNull(rsvp);
        assertEquals("DECLINED", rsvp.response);
        assertEquals(manager.id, rsvp.confirmedByGuest.id);
    }

    @Test
    @DisplayName("Should reject confirmation if not manager")
    @Transactional
    void shouldRejectUnauthorizedConfirmation() {
        Guest child = new Guest();
        child.event = event;
        child.name = "João";
        child.managedByGuest = manager;
        child.persist();

        AppException ex = assertThrows(AppException.class, () ->
                service.confirmRsvp(otherManager.id, child.id, "ATTENDING", event)
        );
        assertTrue(ex.getMessage().contains("Acesso negado"));
    }

    @Test
    @DisplayName("Should reject confirmation after deadline")
    @Transactional
    void shouldRejectConfirmationAfterDeadline() {
        Event pastEvent = new Event();
        pastEvent.slug = "past-" + UUID.randomUUID();
        pastEvent.title = "Casamento Passado";
        pastEvent.coupleNames = "Casal";
        pastEvent.eventDate = OffsetDateTime.now().plusMonths(1);
        pastEvent.rsvpDeadlineAt = OffsetDateTime.now().minusDays(1);
        pastEvent.timezone = "America/Sao_Paulo";
        pastEvent.persist();

        Guest guestInPastEvent = new Guest();
        guestInPastEvent.event = pastEvent;
        guestInPastEvent.name = "João";
        guestInPastEvent.phoneE164 = "+5511999999999";
        guestInPastEvent.persist();

        Guest child = new Guest();
        child.event = pastEvent;
        child.name = "Filho";
        child.managedByGuest = guestInPastEvent;
        child.persist();

        AppException ex = assertThrows(AppException.class, () ->
                service.confirmRsvp(guestInPastEvent.id, child.id, "ATTENDING", pastEvent)
        );
        assertEquals("RSVP_DEADLINE_EXPIRED", ex.getCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // removePartyMember
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should remove managed member created by manager")
    @Transactional
    void shouldRemoveManagedMember() {
        Guest child = new Guest();
        child.event = event;
        child.name = "João";
        child.managedByGuest = manager;
        child.source = "MANAGED";
        child.persist();
        UUID childId = child.id;

        service.removePartyMember(manager.id, childId, event);

        Guest deleted = Guest.findById(childId);
        assertNull(deleted);
    }

    @Test
    @DisplayName("Should reject removal of self-registered member")
    @Transactional
    void shouldRejectRemovalOfSelfRegistered() {
        Guest selfReg = new Guest();
        selfReg.event = event;
        selfReg.name = "Tio";
        selfReg.phoneE164 = "+5511999999999";
        selfReg.managedByGuest = manager;
        selfReg.source = "SELF_REGISTERED";
        selfReg.persist();

        AppException ex = assertThrows(AppException.class, () ->
                service.removePartyMember(manager.id, selfReg.id, event)
        );
        assertEquals("CANNOT_REMOVE_SELF_REGISTERED", ex.getCode());
    }

    @Test
    @DisplayName("Should reject removal by non-manager")
    @Transactional
    void shouldRejectRemovalByNonManager() {
        Guest child = new Guest();
        child.event = event;
        child.name = "João";
        child.managedByGuest = manager;
        child.persist();

        AppException ex = assertThrows(AppException.class, () ->
                service.removePartyMember(otherManager.id, child.id, event)
        );
        assertTrue(ex.getMessage().contains("Acesso negado"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Authorization & Edge Cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should reject access to wrong event")
    @Transactional
    void shouldRejectWrongEvent() {
        Event otherEvent = TestDataFactory.persistedEvent();
        Guest guestInOtherEvent = TestDataFactory.persistedGuest(otherEvent);

        AddPartyMemberRequest request = new AddPartyMemberRequest("João", null, "CHILD", null);

        AppException ex = assertThrows(AppException.class, () ->
                service.addPartyMember(guestInOtherEvent.id, event, request)
        );
        assertTrue(ex.getMessage().contains("Acesso negado"));
    }

    @Test
    @DisplayName("Should reject null manager")
    @Transactional
    void shouldRejectNullManager() {
        UUID fakeId = UUID.randomUUID();
        AddPartyMemberRequest request = new AddPartyMemberRequest("João", null, "CHILD", null);

        AppException ex = assertThrows(AppException.class, () ->
                service.addPartyMember(fakeId, event, request)
        );
        assertTrue(ex.getMessage().contains("Acesso negado"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
