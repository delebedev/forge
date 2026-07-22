package forge.ai;

import forge.game.player.Player;

import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Batch-level evidence that an assigned research treatment reached its guarded branch. */
public final class ResearchTreatmentTelemetry {
    private static final int MAX_SEATS = 8;
    private static final Pattern AI_SEAT = Pattern.compile("^Ai\\((\\d+)\\)-");
    private static final LongAdder[] SIM_DECISIONS = counters();
    private static final LongAdder[] SIMULATIONS = counters();

    private ResearchTreatmentTelemetry() {
    }

    private static LongAdder[] counters() {
        LongAdder[] result = new LongAdder[MAX_SEATS + 1];
        for (int seat = 0; seat <= MAX_SEATS; seat++) {
            result[seat] = new LongAdder();
        }
        return result;
    }

    public static void reset() {
        for (int seat = 0; seat <= MAX_SEATS; seat++) {
            SIM_DECISIONS[seat].reset();
            SIMULATIONS[seat].reset();
        }
    }

    public static void recordSimulationDecision(final Player player, final int simulations) {
        int seat = seatFromName(player.getName());
        SIM_DECISIONS[seat].increment();
        SIMULATIONS[seat].add(simulations);
    }

    public static String summary(final AiVariant variant, final int variantSeat, final int simSeat) {
        return "Research Treatment: variant=" + variant.name().toLowerCase()
                + " variant_seat=" + variantSeat
                + " sim_seat=" + simSeat
                + " sim_decisions=" + joined(SIM_DECISIONS)
                + " simulations=" + joined(SIMULATIONS);
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
}
