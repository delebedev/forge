package forge.ai;

/**
 * CreatureEvaluator with per-term multipliers applied to each stock
 * contribution. With the identity table this is arithmetically identical to
 * stock: round(value * 1.0) == value for every int.
 */
final class ResearchWeightedCreatureEvaluator extends CreatureEvaluator {
    private final ResearchCreatureWeights weights;

    ResearchWeightedCreatureEvaluator(ResearchCreatureWeights weights) {
        this.weights = weights;
    }

    @Override
    protected int addValue(int value, String text) {
        return (int) Math.round(value * weights.multiplier(text));
    }
}
