package forge.ai;

import forge.game.player.Player;

import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Batch-level evidence that an assigned research treatment reached its guarded branch. */
public final class ResearchTreatmentTelemetry {
    private static final int MAX_SEATS = 8;
    private static final Pattern AI_SEAT = Pattern.compile("^Ai\\((\\d+)\\)-");
    private static final LongAdder[] SIM_DECISIONS = counters();
    private static final LongAdder[] WORK_DECISIONS = counters();
    private static final LongAdder[] SIMULATIONS = counters();
    private static final LongAccumulator[] MAX_SIMULATIONS = maxima();

    private ResearchTreatmentTelemetry() {
    }

    private static LongAdder[] counters() {
        LongAdder[] result = new LongAdder[MAX_SEATS + 1];
        for (int seat = 0; seat <= MAX_SEATS; seat++) {
            result[seat] = new LongAdder();
        }
        return result;
    }

    private static LongAccumulator[] maxima() {
        LongAccumulator[] result = new LongAccumulator[MAX_SEATS + 1];
        for (int seat = 0; seat <= MAX_SEATS; seat++) {
            result[seat] = new LongAccumulator(Long::max, 0);
        }
        return result;
    }

    public static void reset() {
        for (int seat = 0; seat <= MAX_SEATS; seat++) {
            SIM_DECISIONS[seat].reset();
            WORK_DECISIONS[seat].reset();
            SIMULATIONS[seat].reset();
            MAX_SIMULATIONS[seat].reset();
        }
    }

    public static void recordSimulationDecision(final Player player, final int simulations) {
        int seat = seatFromName(player.getName());
        SIM_DECISIONS[seat].increment();
        if (simulations > 0) {
            WORK_DECISIONS[seat].increment();
        }
        SIMULATIONS[seat].add(simulations);
        MAX_SIMULATIONS[seat].accumulate(simulations);
    }

    public static String summary(final AiVariant variant, final int variantSeat, final int simSeat) {
        return "Research Treatment: variant=" + variant.name().toLowerCase()
                + " variant_seat=" + variantSeat
                + " sim_seat=" + simSeat
                + " sim_decisions=" + joined(SIM_DECISIONS)
                + " work_decisions=" + joined(WORK_DECISIONS)
                + " simulations=" + joined(SIMULATIONS)
                + " max_simulations=" + joined(MAX_SIMULATIONS);
    }

    private static int seatFromName(final String name) {
        Matcher matcher = AI_SEAT.matcher(name);
        if (!matcher.find()) {
            return 0;
        }
        int seat = Integer.parseInt(matcher.group(1));
        return seat <= MAX_SEATS ? seat : 0;
    }

    private static String joined(final LongAdder[] counters) {
        StringBuilder result = new StringBuilder();
        for (int seat = 1; seat <= MAX_SEATS; seat++) {
            if (seat > 1) {
                result.append(',');
            }
            result.append(counters[seat].sum());
        }
        return result.toString();
    }

    private static String joined(final LongAccumulator[] counters) {
        StringBuilder result = new StringBuilder();
        for (int seat = 1; seat <= MAX_SEATS; seat++) {
            if (seat > 1) {
                result.append(',');
            }
            result.append(counters[seat].get());
        }
        return result.toString();
    }
}
