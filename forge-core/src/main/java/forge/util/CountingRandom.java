package forge.util;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Seeded RNG that counts the draws it serves.
 *
 * Every java.util.Random accessor funnels through next(int), so overriding it
 * counts every draw regardless of which method a caller used.
 *
 * The count exists to separate two indistinguishable explanations for a
 * behavior difference between two arms of the same seeded game: the arms
 * decided differently, or one of them consumed a different number of random
 * draws and displaced the whole downstream stream. A perturbation that changes
 * the draw count cannot support a sensitivity claim on divergence alone.
 */
public final class CountingRandom extends Random {
    private static final long serialVersionUID = 1L;
    private static final AtomicLong DRAWS = new AtomicLong();

    public CountingRandom(final long seed) {
        super(seed);
    }

    @Override
    protected int next(final int bits) {
        DRAWS.incrementAndGet();
        return super.next(bits);
    }

    public static long draws() {
        return DRAWS.get();
    }

    public static void resetDraws() {
        DRAWS.set(0);
    }
}
