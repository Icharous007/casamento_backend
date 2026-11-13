package br.com.casamento.media.service;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.media.GalleryFeedSession;
import br.com.casamento.domain.media.MediaAsset;
import br.com.casamento.media.dto.MediaFeedResponse;
import br.com.casamento.media.dto.MediaItemResponse;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

@ApplicationScoped
public class MediaFeedService {
    @Inject
    EntityManager entityManager;

    @Inject
    MediaService mediaService;

    record Candidate(UUID id, int likes, int comments, OffsetDateTime createdAt) {
    }

    @Transactional
    public MediaFeedResponse list(Guest guest, UUID feedId, String sort, int cursor, int pageSize) {
        if (feedId == null || !Set.of("recent", "popular", "random").contains(sort)
                || cursor < 0 || pageSize < 1 || pageSize > 10) {
            throw AppException.badRequest("INVALID_FEED", "Parâmetros da galeria inválidos.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (guest.event.galleryHideAt != null && !now.isBefore(guest.event.galleryHideAt)) {
            return new MediaFeedResponse(List.of(), null, false, true);
        }

        GalleryFeedSession session = GalleryFeedSession.findById(feedId);
        if (session == null && cursor == 0) {
            // Serialize first-page retries (including cancelled browser requests) for this guest.
            entityManager.find(Guest.class, guest.id, LockModeType.PESSIMISTIC_WRITE);
            session = GalleryFeedSession.findById(feedId);
            if (session == null) {
                session = createSession(guest, feedId, sort, now);
            }
        }
        if (session == null || !session.expiresAt.isAfter(now)) {
            throw new AppException("FEED_EXPIRED", "A galeria expirou. Atualize para continuar.", 410);
        }
        if (!session.guestId.equals(guest.id) || !session.eventId.equals(guest.event.id)) {
            throw AppException.accessDenied();
        }
        if (!session.sort.equals(sort) || cursor > session.mediaIds.size()) {
            throw AppException.badRequest("INVALID_FEED", "Cursor da galeria inválido.");
        }

        List<MediaItemResponse> items = new ArrayList<>();
        int position = cursor;
        // Fill holes left by moderation without returning hidden/deleted media or skipping active IDs.
        while (items.size() < pageSize && position < session.mediaIds.size()) {
            int end = Math.min(position + pageSize - items.size(), session.mediaIds.size());
            List<UUID> ids = session.mediaIds.subList(position, end);
            List<MediaAsset> assets = MediaAsset.find(
                    "SELECT a FROM MediaAsset a LEFT JOIN FETCH a.guest "
                            + "WHERE a.event.id = ?1 AND a.status = 'ACTIVE' AND a.id IN ?2",
                    guest.event.id, ids).list();
            Set<UUID> likedIds = mediaService.likedMediaIds(assets, guest);
            Map<UUID, MediaAsset> byId = new HashMap<>();
            assets.forEach(asset -> byId.put(asset.id, asset));
            for (UUID id : ids) {
                MediaAsset asset = byId.get(id);
                if (asset != null) {
                    items.add(mediaService.toResponse(asset, likedIds.contains(id)));
                }
            }
            position = end;
        }
        boolean hasMore = position < session.mediaIds.size();
        return new MediaFeedResponse(items, hasMore ? position : null, hasMore, false);
    }

    private GalleryFeedSession createSession(Guest guest, UUID feedId, String sort, OffsetDateTime now) {
        List<Candidate> candidates = entityManager.createQuery(
                "SELECT a.id, a.likeCount, a.commentCount, a.createdAt FROM MediaAsset a "
                        + "WHERE a.event.id = :eventId AND a.status = 'ACTIVE'", Tuple.class)
                .setParameter("eventId", guest.event.id)
                .getResultList().stream()
                .map(row -> new Candidate(row.get(0, UUID.class), row.get(1, Integer.class),
                        row.get(2, Integer.class), row.get(3, OffsetDateTime.class)))
                .toList();
        GalleryFeedSession session = new GalleryFeedSession();
        session.id = feedId;
        session.eventId = guest.event.id;
        session.guestId = guest.id;
        session.sort = sort;
        session.mediaIds = orderedIds(candidates, sort, RandomGenerator.getDefault());
        session.expiresAt = now.plusHours(2);
        session.persist();
        return session;
    }

    static List<UUID> orderedIds(List<Candidate> candidates, String sort, RandomGenerator random) {
        Comparator<Candidate> recent = Comparator.comparing(Candidate::createdAt).reversed()
                .thenComparing(Candidate::id);
        Comparator<Candidate> comparator;
        if ("random".equals(sort)) {
            Map<UUID, Double> keys = new HashMap<>();
            candidates.forEach(candidate -> {
                double weight = 1 + Math.log1p(candidate.likes()) + 2 * Math.log1p(candidate.comments());
                keys.put(candidate.id(), -Math.log(1 - random.nextDouble()) / weight);
            });
            comparator = Comparator.comparingDouble((Candidate candidate) -> keys.get(candidate.id()))
                    .thenComparing(Candidate::id);
        } else if ("popular".equals(sort)) {
            comparator = Comparator.comparingInt(Candidate::likes).reversed().thenComparing(recent);
        } else {
            comparator = recent;
        }
        return candidates.stream().sorted(comparator).map(Candidate::id).toList();
    }

    @Scheduled(every = "1h", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void cleanExpiredSessions() {
        GalleryFeedSession.delete("expiresAt < ?1", OffsetDateTime.now().minusDays(1));
    }
}
