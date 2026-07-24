package forge.ai;

import forge.ai.simulation.GameStateEvaluator.Score;
import forge.ai.simulation.SimulationController;

/**
 * SimulationController for strictly one-ply research probes. GameSimulator
 * consults {@link #shouldRecurse()} after resolving a spell and, when allowed,
 * spawns a full SpellAbilityPicker follow-up search on the sim game — turning
 * a single-resolution probe into a depth-bounded tree of nested game copies.
 * Research probes (ResearchTopKRerank, ResearchPolicySearch) must never pay
 * that: one cast, one resolution, one evaluation.
 */
final class ResearchOnePlySimulationController extends SimulationController {
    ResearchOnePlySimulationController(Score score) {
        super(score);
    }

    @Override
    public boolean shouldRecurse() {
        return false;
    }
}
