package forge.ai;

import java.util.Random;
import java.util.function.IntSupplier;

import forge.util.MyRandom;

/**
 * Stops the AI's hypothetical combat prediction from consuming the game's RNG.
 *
 * {@code ComputerUtil.predictNextCombatsRemainingLife} runs a whole hypothetical
 * combat through {@link AiBlockController}, which rolls the random-trade and
 * save-planeswalker dice. Those draws come out of the shared game stream, so how
 * much the AI *thinks* shifts what the game later deals. A treatment that adds or
 * removes a prediction call then desynchronises the arms of a paired experiment
 * while making identical decisions, and the win-rate split measures reshuffled
 * randomness rather than the treatment.
 *
 * {@code FORGE_AI_PREDICT_RNG_ISOLATION=true} runs each prediction against a
 * private, fixed-seed Random and restores the game's afterwards, so a prediction
 * consumes nothing and costs the same regardless of board size. This is a
 * research control, not a strength patch: it is symmetric — set it for every arm
 * of an experiment, never one — and it is off by default because it does change
 * play. Under {@code Default.ai} those knobs are live (30-70), so frozen dice
 * mean a hypothetical that no longer varies between evaluations of the same
 * board. For a what-if that is arguably the more defensible reading, but it is a
 * behaviour change and is not claimed to be inert.
 *
 * Upstream's {@code SpellAbilityPicker} solves a neighbouring problem — it seeds
 * from {@code origRandom.nextLong()} so repeated simulation of one decision sees
 * the same dice — but that still consumes one draw per call, which is enough to
 * desynchronise arms that differ in how often they predict.
 *
 * {@code MyRandom}'s Random is global static, so the swap assumes one AI
 * deliberating at a time — the same assumption upstream's picker already makes,
 * and the reason its own TODO asks for a per-game instance.
 *
 * Scope is deliberately this one entry point. The other hypothetical block
 * evaluations ({@code ComputerUtilCard}, {@code DestroyAllAi}) are left alone:
 * isolating every AI-internal RNG consumer is engine surgery, and this is the
 * path with a measured experimental cost.
 */
public final class ResearchPredictionRng {
    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getenv("FORGE_AI_PREDICT_RNG_ISOLATION"));

    /** Fixed, so a prediction's dice depend on nothing but the code path — not on
     *  how many predictions preceded it, which would reintroduce the same drift. */
    private static final long SEED = 0x5CA1AB1EL;

    private ResearchPredictionRng() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static int isolate(final IntSupplier prediction) {
        if (!ENABLED) {
            return prediction.getAsInt();
        }
        final Random gameRandom = MyRandom.getRandom();
        MyRandom.setRandom(new Random(SEED));
        try {
            return prediction.getAsInt();
        } finally {
            MyRandom.setRandom(gameRandom);
        }
    }
}
