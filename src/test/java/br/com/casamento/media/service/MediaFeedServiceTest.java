package br.com.casamento.media.service;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.auth.service.GuestTokenService;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.media.GalleryFeedSession;
import br.com.casamento.domain.media.MediaAsset;
import br.com.casamento.media.dto.MediaFeedResponse;
import br.com.casamento.media.dto.MediaItemResponse;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestTransaction
class MediaFeedServiceTest {
    @Inject
    MediaFeedService service;

    @Inject
    EntityManager entityManager;

    @Inject
    GuestTokenService tokenService;

    private Guest guest() {
        Event event = new Event();
        event.slug = "feed-test-" + UUID.randomUUID();
        event.title = "Feed test";
        event.coupleNames = "Test";
        event.eventDate = OffsetDateTime.now().plusDays(1);
        event.persist();
        Guest guest = new Guest();
        guest.event = event;
        guest.name = "Feed guest";
        guest.persist();
        return guest;
    }

    private List<MediaAsset> media(Guest guest, int count) {
        List<MediaAsset> assets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MediaAsset asset = new MediaAsset();
            asset.id = UUID.randomUUID();
            asset.event = guest.event;
            asset.guest = guest;
            asset.mediaType = i % 2 == 0 ? "PHOTO" : "VIDEO";
            asset.status = "ACTIVE";
            asset.r2Key = "feed-test/" + asset.id;
            asset.contentType = i % 2 == 0 ? "image/jpeg" : "video/mp4";
            asset.likeCount = i;
            asset.commentCount = count - i;
            asset.persist();
            assets.add(asset);
        }
        entityManager.flush();
        return assets;
    }

    private List<UUID> ids(MediaFeedResponse response) {
        return response.items().stream().map(MediaItemResponse::id).toList();
    }

    @Test
    void tenItemBatchesCoverTheSnapshotExactlyOnceDespiteEngagementChangesAndNewUploads() {
        Guest guest = guest();
        List<MediaAsset> assets = media(guest, 25);
        UUID feedId = UUID.randomUUID();
        MediaFeedResponse first = service.list(guest, feedId, "random", 0, 10);
        assertEquals(10, first.items().size());
        assertEquals(10, first.nextCursor());
        assertTrue(first.hasMore());
        assets.forEach(asset -> {
            asset.likeCount += 1000;
            asset.commentCount += 1000;
        });
        media(guest, 1);
        assertEquals(ids(first), ids(service.list(guest, feedId, "random", 0, 10)));
        MediaFeedResponse second = service.list(guest, feedId, "random", first.nextCursor(), 10);
        MediaFeedResponse third = service.list(guest, feedId, "random", second.nextCursor(), 10);
        assertEquals(10, second.items().size());
        assertEquals(5, third.items().size());
        assertFalse(third.hasMore());
        assertNull(third.nextCursor());
        List<UUID> combined = new ArrayList<>(ids(first));
        combined.addAll(ids(second));
        combined.addAll(ids(third));
        assertEquals(25, new HashSet<>(combined).size());
        assertEquals(new HashSet<>(assets.stream().map(asset -> asset.id).toList()), new HashSet<>(combined));
        entityManager.flush();
        entityManager.clear();
        assertEquals(ids(first), ids(service.list(Guest.findById(guest.id), feedId, "random", 0, 10)));
    }

    @Test
    void popularSnapshotDoesNotMoveAfterLikesChange() {
        Guest guest = guest();
        List<MediaAsset> assets = media(guest, 15);
        UUID feedId = UUID.randomUUID();
        MediaFeedResponse first = service.list(guest, feedId, "popular", 0, 10);
        assets.getFirst().likeCount = 100_000;
        assertEquals(ids(first), ids(service.list(guest, feedId, "popular", 0, 10)));
        assertEquals(assets.getLast().id, first.items().getFirst().id());
    }

    @Test
    void hiddenDeletedAndOtherEventMediaAreExcludedAndHolesAreFilled() {
        Guest guest = guest();
        media(guest, 25);
        media(guest(), 3);
        UUID feedId = UUID.randomUUID();
        service.list(guest, feedId, "random", 0, 10);
        GalleryFeedSession session = GalleryFeedSession.findById(feedId);
        MediaAsset hidden = MediaAsset.findById(session.mediaIds.get(10));
        hidden.status = "HIDDEN";
        MediaAsset deleted = MediaAsset.findById(session.mediaIds.get(11));
        deleted.status = "DELETED";
        MediaFeedResponse second = service.list(guest, feedId, "random", 10, 10);
        assertEquals(10, second.items().size());
        assertEquals(session.mediaIds.subList(12, 22), ids(second));
        assertEquals(22, second.nextCursor());
        MediaFeedResponse third = service.list(guest, feedId, "random", 22, 10);
        assertEquals(3, third.items().size());
        assertFalse(third.hasMore());
    }

    @Test
    void sessionIsBoundToGuestAndSortAndRejectsBadParameters() {
        Guest guest = guest();
        media(guest, 1);
        UUID feedId = UUID.randomUUID();
        service.list(guest, feedId, "random", 0, 10);
        Guest other = new Guest();
        other.event = guest.event;
        other.name = "Other guest";
        other.persist();
        assertEquals(401, assertThrows(AppException.class,
                () -> service.list(other, feedId, "random", 0, 10)).getStatus());
        assertEquals("INVALID_FEED", assertThrows(AppException.class,
                () -> service.list(guest, feedId, "popular", 0, 10)).getCode());
        assertThrows(AppException.class, () -> service.list(guest, feedId, "random", 2, 10));
        assertThrows(AppException.class, () -> service.list(guest, feedId, "invalid", 0, 10));
        assertThrows(AppException.class, () -> service.list(guest, feedId, "random", -1, 10));
        assertThrows(AppException.class, () -> service.list(guest, feedId, "random", 0, 24));
        assertThrows(AppException.class, () -> service.list(guest, null, "random", 0, 10));
    }

    @Test
    void expiredSessionRequiresExplicitRefreshAndCleanupKeepsRecentExpiredIds() {
        Guest guest = guest();
        UUID feedId = UUID.randomUUID();
        service.list(guest, feedId, "random", 0, 10);
        GalleryFeedSession session = GalleryFeedSession.findById(feedId);
        session.expiresAt = OffsetDateTime.now().minusMinutes(1);
        assertEquals("FEED_EXPIRED", assertThrows(AppException.class,
                () -> service.list(guest, feedId, "random", 0, 10)).getCode());
        service.cleanExpiredSessions();
        assertNotNull(GalleryFeedSession.findById(feedId));
        session.expiresAt = OffsetDateTime.now().minusDays(2);
        service.cleanExpiredSessions();
        entityManager.clear();
        assertNull(GalleryFeedSession.findById(feedId));
    }

    @Test
    void emptyAndClosedGalleryTerminateWithoutMedia() {
        Guest guest = guest();
        MediaFeedResponse empty = service.list(guest, UUID.randomUUID(), "recent", 0, 10);
        assertTrue(empty.items().isEmpty());
        assertFalse(empty.hasMore());
        assertNull(empty.nextCursor());
        media(guest, 2);
        guest.event.galleryHideAt = OffsetDateTime.now().minusSeconds(1);
        MediaFeedResponse hidden = service.list(guest, UUID.randomUUID(), "random", 0, 10);
        assertTrue(hidden.galleryHidden());
        assertTrue(hidden.items().isEmpty());
        assertFalse(hidden.hasMore());
    }

    @Test
    void httpEndpointAuthenticatesAndReturnsTenItemCursorPages() {
        record Fixture(UUID eventId, String token) {
        }
        Fixture fixture = QuarkusTransaction.requiringNew().call(() -> {
            Guest guest = guest();
            guest.status = "ACTIVE";
            media(guest, 12);
            return new Fixture(guest.event.id, tokenService.createToken(guest));
        });
        try {
            UUID feedId = UUID.randomUUID();
            var first = given()
                    .header("X-Guest-Access-Token", fixture.token())
                    .queryParam("feedId", feedId)
                    .when().get("/api/v1/media/feed")
                    .then().statusCode(200)
                    .body("items.size()", is(10))
                    .body("nextCursor", is(10))
                    .body("hasMore", is(true))
                    .body("galleryHidden", is(false))
                    .extract().jsonPath().getMap("$");
            assertEquals(Set.of("items", "nextCursor", "hasMore", "galleryHidden"), first.keySet());
            given().header("X-Guest-Access-Token", fixture.token())
                    .queryParam("feedId", feedId).queryParam("cursor", 10)
                    .when().get("/api/v1/media/feed")
                    .then().statusCode(200)
                    .body("items.size()", is(2))
                    .body("hasMore", is(false))
                    .body("nextCursor", nullValue());
            given().queryParam("feedId", feedId)
                    .when().get("/api/v1/media/feed")
                    .then().statusCode(401);
            given().header("X-Guest-Access-Token", fixture.token())
                    .queryParam("feedId", feedId).queryParam("pageSize", 24)
                    .when().get("/api/v1/media/feed")
                    .then().statusCode(400).body("code", is("INVALID_FEED"));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> Event.deleteById(fixture.eventId()));
        }
    }
}
