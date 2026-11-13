package br.com.casamento.media.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaFeedRankingTest {
    private final OffsetDateTime now = OffsetDateTime.now();

    private MediaFeedService.Candidate candidate(long id, int likes, int comments, OffsetDateTime createdAt) {
        return new MediaFeedService.Candidate(new UUID(0, id), likes, comments, createdAt);
    }

    @Test
    void randomOrderIsRepeatableWithoutDuplicatesAndVariesWithSeed() {
        List<MediaFeedService.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            candidates.add(candidate(i, i, 50 - i, now));
        }
        List<UUID> first = MediaFeedService.orderedIds(candidates, "random", new Random(42));
        assertEquals(first, MediaFeedService.orderedIds(candidates, "random", new Random(42)));
        assertNotEquals(first, MediaFeedService.orderedIds(candidates, "random", new Random(43)));
        assertEquals(50, first.stream().distinct().count());
        assertTrue(first.contains(candidates.getFirst().id()));
    }

    @Test
    void commentsAndLikesIncreaseSelectionProbabilityButZeroEngagementStillAppears() {
        List<MediaFeedService.Candidate> candidates = List.of(
                candidate(1, 0, 0, now),
                candidate(2, 20, 0, now),
                candidate(3, 0, 20, now));
        Map<UUID, Integer> wins = new HashMap<>();
        Random random = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            UUID first = MediaFeedService.orderedIds(candidates, "random", random).getFirst();
            wins.merge(first, 1, Integer::sum);
        }
        assertTrue(wins.get(candidates.get(2).id()) > wins.get(candidates.get(1).id()));
        assertTrue(wins.get(candidates.get(1).id()) > 3 * wins.get(candidates.get(0).id()));
        assertTrue(wins.get(candidates.get(0).id()) > 500);
    }

    @Test
    void logarithmicWeightsDoNotMakeHighlyPopularMediaWinEveryTime() {
        List<MediaFeedService.Candidate> candidates = List.of(
                candidate(1, 0, 0, now), candidate(2, Integer.MAX_VALUE, Integer.MAX_VALUE, now));
        Random random = new Random(123);
        long zeroWins = 0;
        for (int i = 0; i < 10_000; i++) {
            if (MediaFeedService.orderedIds(candidates, "random", random).getFirst().equals(candidates.getFirst().id())) {
                zeroWins++;
            }
        }
        assertTrue(zeroWins > 50);
    }

    @Test
    void recentAndPopularKeepTheirExistingSemanticsWithStableTieBreaks() {
        var old = candidate(3, 20, 0, now.minusDays(1));
        var newer = candidate(2, 0, 100, now);
        var tied = candidate(1, 0, 0, now);
        List<MediaFeedService.Candidate> candidates = List.of(old, newer, tied);
        assertEquals(List.of(tied.id(), newer.id(), old.id()),
                MediaFeedService.orderedIds(candidates, "recent", new Random(1)));
        assertEquals(List.of(old.id(), tied.id(), newer.id()),
                MediaFeedService.orderedIds(candidates, "popular", new Random(1)));
        assertTrue(MediaFeedService.orderedIds(List.of(), "random", new Random()).isEmpty());
    }
}
