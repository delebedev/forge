package forge.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import forge.game.player.Player;

/**
 * How often each AI personality property is actually read, per seat.
 *
 * A knob that is never read cannot matter at any budget, and a knob read
 * constantly can still be neutral — activation is necessary evidence, never
 * sufficient. Its use is to tell an undetectable effect apart from an absent
 * one: a whole-pool average divides a within-cell effect by the share of cells
 * where the path fires, so a real effect confined to a few cells reads as
 * noise. Counts come from the seat's own reads, so a baseline arm reports the
 * activation of a knob it is not using.
 */
public final class ResearchActivation {

    private static final Map<String, Map<AiProps, AtomicLong>> COUNTS = new ConcurrentHashMap<>();

    private ResearchActivation() {
    }

    static void record(final Player player, final AiProps prop) {
        if (player == null) {
            return;
        }
        COUNTS.computeIfAbsent(player.getName(), k -> new EnumMap<>(AiProps.class))
                .computeIfAbsent(prop, k -> new AtomicLong())
                .incrementAndGet();
    }

    public static void reset() {
        COUNTS.clear();
    }

    /** "seat=<name> PROP:count,PROP:count" per seat, stable order, or "" when nothing was read. */
    public static String summary() {
        final List<String> seats = new ArrayList<>(COUNTS.keySet());
        seats.sort(String::compareTo);
        final StringBuilder sb = new StringBuilder();
        for (final String seat : seats) {
            final Map<AiProps, AtomicLong> props = COUNTS.get(seat);
            if (props == null || props.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("seat=").append(seat).append(' ');
            boolean first = true;
            for (final Map.Entry<AiProps, AtomicLong> entry : props.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(entry.getKey().name()).append(':').append(entry.getValue().get());
                first = false;
            }
        }
        return sb.toString();
    }
}
