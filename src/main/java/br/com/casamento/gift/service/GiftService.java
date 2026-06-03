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
        return GiftItem.listVisibleByEvent(eventId)
                .stream()
                .map(GiftItemResponse::from)
                .toList();
    }

    @Transactional
    public MarkPurchasedResponse markPurchased(UUID giftId, Guest guest) {
        GiftItem item = em.find(GiftItem.class, giftId, LockModeType.PESSIMISTIC_WRITE);
        if (item == null || !item.event.id.equals(guest.event.id)) {
            throw AppException.notFound("Presente não encontrado.");
        }
        if (!"AVAILABLE".equals(item.status)) {
            throw AppException.giftAlreadyPurchased();
        }
        item.status = "PURCHASED";

        GiftPurchaseMark mark = new GiftPurchaseMark();
        mark.giftItem = item;
        mark.guest = guest;
        em.persist(mark);
        em.flush();

        return new MarkPurchasedResponse(item.id, "PURCHASED", mark.purchasedAt);
    }

    // ── Admin operations ────────────────────────────────────────────────────

    public List<GiftItemAdminResponse> listForAdmin(UUID eventId) {
        return GiftItem.listByEvent(eventId).stream().map(item -> {
            GiftPurchaseMark mark = GiftPurchaseMark.findByGiftItem(item);
            return GiftItemAdminResponse.from(item, mark);
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
        item.displayOrder = req.displayOrder() != null ? req.displayOrder() : 0;
        item.persist();
        return GiftItemAdminResponse.from(item, null);
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
        if (req.displayOrder() != null) item.displayOrder = req.displayOrder();
        if (req.visibleToGuests() != null) item.visibleToGuests = req.visibleToGuests();
        GiftPurchaseMark mark = GiftPurchaseMark.findByGiftItem(item);
        return GiftItemAdminResponse.from(item, mark);
    }

    @Transactional
    public void delete(UUID id, Event event) {
        GiftItem item = GiftItem.findById(id);
        if (item == null || !item.event.id.equals(event.id)) {
            throw AppException.notFound("Presente não encontrado.");
        }
        GiftPurchaseMark mark = GiftPurchaseMark.findByGiftItem(item);
        if (mark != null) mark.delete();
        item.delete();
    }

    @Transactional
    public GiftItemAdminResponse unmarkPurchased(UUID id, Event event) {
        GiftItem item = GiftItem.findById(id);
        if (item == null || !item.event.id.equals(event.id)) {
            throw AppException.notFound("Presente não encontrado.");
        }
        item.status = "AVAILABLE";
        GiftPurchaseMark mark = GiftPurchaseMark.findByGiftItem(item);
        if (mark != null) mark.delete();
        return GiftItemAdminResponse.from(item, null);
    }
}
