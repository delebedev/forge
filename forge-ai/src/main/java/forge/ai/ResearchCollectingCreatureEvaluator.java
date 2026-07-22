package forge.ai;

import forge.game.card.Card;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stock CreatureEvaluator that additionally records each addValue
 * contribution keyed by normalized term. Arithmetic is untouched, so
 * BASE + sum(terms) + residual == stock score holds by construction:
 * the residual absorbs everything not routed through addValue
 * (AIEvaluationModifier) and the reset labels clear prior terms because a
 * reset discards the accumulated value.
 *
 * Not thread-safe; construct one per evaluation pass.
 */
final class ResearchCollectingCreatureEvaluator extends CreatureEvaluator {
    static final int BASE = 80;
    static final String BASE_KEY = "_base";
    static final String RESIDUAL_KEY = "_residual";

    /** Labels where CreatureEvaluator assigns (value = ...) instead of accumulating. */
    private static final Set<String> RESET_LABELS = Set.of("detained", "useless", "tapped-useless");

    private final Map<String, Long> terms = new LinkedHashMap<>();

    @Override
    protected int addValue(int value, String text) {
        String key = ResearchCreatureWeights.termKey(text);
        if (RESET_LABELS.contains(key)) {
            terms.clear();
        }
        if (value != 0) {
            terms.merge(key, (long) value, Long::sum);
        }
        return value;
    }

    /**
     * Evaluate one card, returning its per-term feature contributions
     * (including {@code _base} and {@code _residual}) whose sum equals the
     * stock score exactly.
     */
    Map<String, Long> featuresFor(Card c) {
        terms.clear();
        long score = evaluateCreature(c);
        Map<String, Long> features = new LinkedHashMap<>();
        features.put(BASE_KEY, (long) BASE);
        long sum = BASE;
        for (Map.Entry<String, Long> e : terms.entrySet()) {
            features.put(e.getKey(), e.getValue());
            sum += e.getValue();
        }
        long residual = score - sum;
        if (residual != 0) {
            features.put(RESIDUAL_KEY, residual);
        }
        return features;
    }
}
