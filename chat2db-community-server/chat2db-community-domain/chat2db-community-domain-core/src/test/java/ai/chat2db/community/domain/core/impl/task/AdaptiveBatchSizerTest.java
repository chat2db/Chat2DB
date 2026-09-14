package ai.chat2db.community.domain.core.impl.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tuning behaviour of the fast-mode batch-size observer: the size starts at the configured
 * baseline, grows while the measured throughput keeps improving, shrinks when it regresses, never
 * falls below the contract floor of 100 rows, and is not capped on the way up.
 */
class AdaptiveBatchSizerTest {

    private static final long MILLI = 1_000_000L;

    @Test
    void floorsInitialValueAtTheContractMinimum() {
        assertEquals(100, new AdaptiveBatchSizer(1).batchSize());
        assertEquals(20_000, new AdaptiveBatchSizer(20_000).batchSize());
        assertEquals(500_000, new AdaptiveBatchSizer(500_000).batchSize(),
                "the baseline may be large: there is no upper bound on the configured start");
    }

    @Test
    void growsWhileThroughputImproves() {
        AdaptiveBatchSizer sizer = new AdaptiveBatchSizer(1_000);
        long nanos = 10 * MILLI;
        sizer.record(1_000, nanos); // first observation only establishes the reference
        assertEquals(1_000, sizer.batchSize());
        for (int round = 0; round < 4; round++) {
            nanos = Math.max(1L, nanos / 2); // half the time for the same rows: twice the throughput
            sizer.record(1_000, nanos);
        }
        assertEquals(16_000, sizer.batchSize(), "improving throughput doubles the batch each window");
    }

    @Test
    void shrinksWhenThroughputRegressesAndStopsAtTheFloor() {
        AdaptiveBatchSizer sizer = new AdaptiveBatchSizer(8_000);
        long nanos = 5 * MILLI;
        sizer.record(8_000, nanos); // reference
        for (int round = 0; round < 12; round++) {
            nanos = nanos * 4; // four times slower: throughput clearly regresses
            sizer.record(sizer.batchSize(), nanos);
        }
        assertEquals(100, sizer.batchSize(), "a slow target drives the batch down to the floor");
    }

    @Test
    void hasNoUpperBoundOnGrowth() {
        AdaptiveBatchSizer sizer = new AdaptiveBatchSizer(1_000);
        long nanos = 10 * MILLI;
        sizer.record(1_000, nanos);
        for (int round = 0; round < 12; round++) {
            nanos = Math.max(1L, nanos / 2);
            sizer.record(1_000, nanos);
        }
        assertTrue(sizer.batchSize() > 1_000_000,
                "growth is unbounded by contract, the throughput feedback is the only ceiling");
    }

    @Test
    void keepsSizeWhenThroughputIsFlat() {
        AdaptiveBatchSizer sizer = new AdaptiveBatchSizer(5_000);
        sizer.record(5_000, 5 * MILLI);
        sizer.record(5_000, 5 * MILLI);
        sizer.record(5_000, 5 * MILLI);
        assertEquals(5_000, sizer.batchSize(), "no improvement and no regression keeps the size");
    }

    @Test
    void ignoresInvalidObservations() {
        AdaptiveBatchSizer sizer = new AdaptiveBatchSizer(500);
        sizer.record(0, MILLI);
        sizer.record(500, 0L);
        sizer.record(-1, MILLI);
        assertEquals(500, sizer.batchSize());
    }

}
