package forge.ai;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-feature refinement of the {@link AiVariant#CANDIDATE} seat.
 *
 * {@code FORGE_AI_CANDIDATE_FEATURES} is a comma-separated subset of
 * {@link #KNOWN}; the candidate seat evaluates only the listed features.
 * Unset or blank means every feature is active (a plain candidate seat).
 * Unknown names fail fast — a typo must never silently run a different
 * experiment. Baseline seats never consult this class.
 */
public final class ResearchCandidateFeatures {
    public static final String CAST_TRIGGER_MAIN1 = "cast-trigger-main1";
    public static final String MANDATORY_ETB = "mandatory-etb";

    private static final Set<String> KNOWN = new LinkedHashSet<>(Arrays.asList(
            CAST_TRIGGER_MAIN1, MANDATORY_ETB));

    /** null = all features enabled. */
    private static final Set<String> ENABLED = parse(System.getenv("FORGE_AI_CANDIDATE_FEATURES"));

    private ResearchCandidateFeatures() {
    }

    static Set<String> parse(final String env) {
        if (env == null || env.trim().isEmpty()) {
            return null;
        }
        final Set<String> selected = new LinkedHashSet<>();
        for (final String raw : env.split(",")) {
            final String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!KNOWN.contains(name)) {
                throw new IllegalArgumentException(
                        "Unknown FORGE_AI_CANDIDATE_FEATURES entry: " + name + " (known: " + KNOWN + ")");
            }
            selected.add(name);
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException(
                    "FORGE_AI_CANDIDATE_FEATURES is set but selects no feature: " + env);
        }
        return Collections.unmodifiableSet(selected);
    }

    public static boolean isEnabled(final String feature) {
        if (!KNOWN.contains(feature)) {
            throw new IllegalArgumentException("Unknown candidate feature: " + feature);
        }
        return ENABLED == null || ENABLED.contains(feature);
    }
}
