package br.com.casamento.gift.service;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.gift.GiftItem;
import br.com.casamento.domain.gift.GiftPurchaseMark;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.gift.dto.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GiftService {

    @Inject
    EntityManager em;

    // ── Guest operations ────────────────────────────────────────────────────

    public List<GiftItemResponse> listForGuest(UUID eventId) {
        return GiftItem.listVisibleByEventAndCategory(eventId, "WEDDING")
                .stream()
                .map(GiftItemResponse::from)
                .toList();
    }

    public List<GiftItemResponse> listForPublicCategory(UUID eventId, String category) {
        return GiftItem.listVisibleByEventAndCategory(eventId, category)
                .stream().map(GiftItemResponse::from).toList();
    }

    public GiftItem findVisibleItem(UUID eventId, UUID giftId, String category) {
        GiftItem item = GiftItem.find("id = ?1 AND event.id = ?2 AND category = ?3 AND visibleToGuests = true AND status <> 'UNAVAILABLE'",
                giftId, eventId, category).firstResult();
        if (item == null) throw AppException.notFound("Presente não encontrado.");
        return item;
    }

    @Transactional
    public MarkPurchasedResponse markPurchased(UUID giftId, Guest guest) {
        GiftItem item = em.find(GiftItem.class, giftId, LockModeType.PESSIMISTIC_WRITE);
        if (item == null || !item.event.id.equals(guest.event.id)) {
            throw AppException.notFound("Presente não encontrado.");
        }
        GiftPurchaseMark mark = new GiftPurchaseMark();
        mark.giftItem = item;
        mark.guest = guest;
        mark.giverName = guest.name;
        mark.giverPhone = guest.phoneE164;
        em.persist(mark);
        em.flush();

        return new MarkPurchasedResponse(item.id, item.status, mark.purchasedAt);
    }

    @Transactional
    public GiftPurchaseMark reportPixContribution(GiftItem item, Guest guest, String giverName,
                                                  String giverPhone, String message) {
        GiftPurchaseMark mark = new GiftPurchaseMark();
        mark.giftItem = item;
        mark.guest = guest;
        mark.giverName = giverName;
        mark.giverPhone = giverPhone;
        mark.giverMessage = message;
        mark.paymentMethod = "PIX";
        mark.paymentStatus = "REPORTED";
        mark.paidAmount = item.priceAmount;
        mark.persist();
        return mark;
    }

    @Transactional
    public GiftPurchaseMark reportPublicPixContribution(GiftItem item, String giverName,
                                                         String giverPhone, String message) {
        return reportPixContribution(item, null, giverName, giverPhone, message);
    }

    // ── Admin operations ────────────────────────────────────────────────────

    public List<GiftItemAdminResponse> listForAdmin(UUID eventId, String category) {
        return GiftItem.listByEventAndCategory(eventId, category).stream().map(item -> {
            return GiftItemAdminResponse.from(item, GiftPurchaseMark.listByGiftItem(item));
        }).toList();
    }

    @Transactional
    public GiftItemAdminResponse create(Event event, CreateGiftRequest req) {
        GiftItem item = new GiftItem();
        item.event = event;
        item.title = req.title();
        item.description = req.description();
        item.externalUrl = req.externalUrl();
        item.imageUrl = req.imageUrl();
        item.priceRange = req.priceRange();
        item.priceAmount = req.priceAmount();
        item.category = normalizeCategory(req.category());
        item.displayOrder = req.displayOrder() != null ? req.displayOrder() : 0;
        item.persist();
        return GiftItemAdminResponse.from(item, List.of());
    }

    @Transactional
    public GiftItemAdminResponse update(UUID id, Event event, UpdateGiftRequest req) {
        GiftItem item = GiftItem.findById(id);
        if (item == null || !item.event.id.equals(event.id)) {
            throw AppException.notFound("Presente não encontrado.");
        }
        if (req.title() != null) item.title = req.title();
        if (req.description() != null) item.description = req.description();
        if (req.externalUrl() != null) item.externalUrl = req.externalUrl();
        if (req.imageUrl() != null) item.imageUrl = req.imageUrl();
        if (req.priceRange() != null) item.priceRange = req.priceRange();
        if (Boolean.TRUE.equals(req.clearPriceAmount())) item.priceAmount = null;
        else if (req.priceAmount() != null) item.priceAmount = req.priceAmount();
        if (req.category() != null) item.category = normalizeCategory(req.category());
        if (req.displayOrder() != null) item.displayOrder = req.displayOrder();
        if (req.visibleToGuests() != null) item.visibleToGuests = req.visibleToGuests();
        return GiftItemAdminResponse.from(item, GiftPurchaseMark.listByGiftItem(item));
    }

    @Transactional
    public void delete(UUID id, Event event) {
        GiftItem item = GiftItem.findById(id);
        if (item == null || !item.event.id.equals(event.id)) {
            throw AppException.notFound("Presente não encontrado.");
        }
        GiftPurchaseMark.listByGiftItem(item).forEach(GiftPurchaseMark::delete);
        item.delete();
    }

    @Transactional
    public GiftItemAdminResponse unmarkPurchased(UUID id, Event event) {
        GiftItem item = GiftItem.findById(id);
        if (item == null || !item.event.id.equals(event.id)) {
            throw AppException.notFound("Presente não encontrado.");
        }
        item.status = "AVAILABLE";
        List<GiftPurchaseMark> marks = GiftPurchaseMark.listByGiftItem(item);
        if (!marks.isEmpty()) marks.getFirst().delete();
        return GiftItemAdminResponse.from(item, GiftPurchaseMark.listByGiftItem(item));
    }

    private String normalizeCategory(String category) {
        String value = category == null || category.isBlank() ? "WEDDING" : category.toUpperCase();
        if (!"WEDDING".equals(value) && !"BRIDAL_SHOWER".equals(value)) {
            throw AppException.badRequest("INVALID_GIFT_CATEGORY", "Categoria de presente inválida.");
        }
        return value;
    }
}
