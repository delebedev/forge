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
    private static volatile boolean installed;

    public CountingRandom(final long seed) {
        super(seed);
        installed = true;
    }

    /**
     * Whether any counting RNG was ever installed in this JVM. Consumers must
     * treat an uninstrumented run as unknown rather than as zero draws: a
     * constant zero would read as "both arms consumed the same draws" and
     * silently certify a divergence this instrument never measured.
     */
    public static boolean instrumented() {
        return installed;
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
