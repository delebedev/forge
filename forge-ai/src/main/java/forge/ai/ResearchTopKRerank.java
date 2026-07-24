package forge.ai;

import java.util.List;

import forge.ai.simulation.GameSimulator;
import forge.ai.simulation.GameStateEvaluator;
import forge.ai.simulation.GameStateEvaluator.Score;
import forge.ai.simulation.SimulationController;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * Bounded top-k one-ply rerank after the greedy veto scan: candidate-seat,
 * own-main-phase, empty-stack only. The greedy choice survives unless one of
 * the next few WillPlay candidates beats its simulated resolution by the
 * predeclared delta. Probing must leave the live game and every unchosen
 * SpellAbility unchanged.
 */
final class ResearchTopKRerank {
    private static final int K = clamp(Integer.parseInt(System.getenv().getOrDefault("FORGE_AI_TOPK_K", "3")), 1, 8);
    private static final int MIN_DELTA = Integer.parseInt(System.getenv().getOrDefault("FORGE_AI_TOPK_MIN_DELTA", "1"));

    private ResearchTopKRerank() {
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static int k() {
        return K;
    }

    static int minDelta() {
        return MIN_DELTA;
    }

    static boolean eligible(Game game, Player player) {
        PhaseType phase = game.getPhaseHandler().getPhase();
        return game.getPhaseHandler().isPlayerTurn(player)
                && (phase == PhaseType.MAIN1 || phase == PhaseType.MAIN2)
                && game.getStack().isEmpty();
    }

    static SpellAbility choose(Game game, Player player, SpellAbility greedyChoice, List<SpellAbility> tail, int minDelta) {
        if (greedyChoice == null || tail == null || tail.isEmpty()) {
            return greedyChoice;
        }
        try {
            GameStateEvaluator evaluator = new GameStateEvaluator();
            Score origScore = evaluator.getScoreForGameState(game, player);
            Score greedyScore = simulate(game, player, greedyChoice, origScore);

            SpellAbility bestTail = tail.get(0);
            Score bestTailScore = simulate(game, player, bestTail, origScore);
            for (int i = 1; i < tail.size(); i++) {
                SpellAbility candidate = tail.get(i);
                Score candidateScore = simulate(game, player, candidate, origScore);
                if (candidateScore.value > bestTailScore.value) {
                    bestTail = candidate;
                    bestTailScore = candidateScore;
                }
            }

            boolean overridden = Score.meetsThreshold(bestTailScore, greedyScore, minDelta);
            SpellAbility winner = overridden ? bestTail : greedyChoice;

            for (SpellAbility candidate : tail) {
                if (candidate != winner) {
                    restore(candidate);
                }
            }
            if (overridden) {
                restore(greedyChoice);
            }
            return winner;
        } catch (RuntimeException ex) {
            for (SpellAbility candidate : tail) {
                restore(candidate);
            }
            return greedyChoice;
        }
    }

    private static Score simulate(Game game, Player player, SpellAbility choice, Score origScore) {
        SimulationController controller = new ResearchOnePlySimulationController(origScore);
        GameSimulator simulator = new GameSimulator(controller, game, player, null);
        return simulator.simulateSpellAbility(choice);
    }

    // Undo probe-time mutation (targets, X paid) on a SpellAbility this seam did
    // not end up choosing, so the live game sees it as never having been touched.
    private static void restore(SpellAbility sa) {
        for (SpellAbility s = sa; s != null; s = s.getSubAbility()) {
            s.resetTargets();
        }
        sa.setXManaCostPaid(null);
    }
}
