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

    static Result choose(Game game, Player player, SpellAbility greedyChoice, List<SpellAbility> tail, int minDelta) {
        if (greedyChoice == null || tail == null || tail.isEmpty()) {
            return Result.inactive(greedyChoice);
        }
        int probeFailures = 0;
        try {
            GameStateEvaluator evaluator = new GameStateEvaluator();
            Score origScore = evaluator.getScoreForGameState(game, player);
            Score greedyScore = simulate(game, player, greedyChoice, origScore);
            if (greedyScore.isSimFailure()) {
                probeFailures++;
            }

            SpellAbility bestTail = tail.get(0);
            Score bestTailScore = simulate(game, player, bestTail, origScore);
            if (bestTailScore.isSimFailure()) {
                probeFailures++;
            }
            for (int i = 1; i < tail.size(); i++) {
                SpellAbility candidate = tail.get(i);
                Score candidateScore = simulate(game, player, candidate, origScore);
                if (candidateScore.isSimFailure()) {
                    probeFailures++;
                }
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
            return Result.probed(winner, tail.size() + 1, overridden, greedyScore, bestTailScore, probeFailures);
        } catch (RuntimeException ex) {
            for (SpellAbility candidate : tail) {
                restore(candidate);
            }
            // A probe that threw is a failed probe, not a seam that never ran:
            // the comparison is unusable (NONE kinds) but the attempt is real,
            // and a diagnostic read must be able to see it.
            return Result.aborted(greedyChoice, tail.size() + 1, probeFailures + 1);
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

    /**
     * What the probe pass saw, for the decision log. {@code active} is false
     * when the seam never simulated anything — no tail, or a probe threw — so
     * a record distinguishes "did not run" from "ran and kept greedy".
     */
    static final class Result {
        final SpellAbility choice;
        final boolean active;
        final int kCollected;
        final boolean overridden;
        final Score greedyScore;
        final Score bestAltScore;
        final int probeFailures;

        private Result(SpellAbility choice, boolean active, int kCollected, boolean overridden,
                Score greedyScore, Score bestAltScore, int probeFailures) {
            this.choice = choice;
            this.active = active;
            this.kCollected = kCollected;
            this.overridden = overridden;
            this.greedyScore = greedyScore;
            this.bestAltScore = bestAltScore;
            this.probeFailures = probeFailures;
        }

        static Result inactive(SpellAbility choice) {
            return new Result(choice, false, 0, false, Score.none(), Score.none(), 0);
        }

        static Result aborted(SpellAbility choice, int kCollected, int probeFailures) {
            return new Result(choice, true, kCollected, false, Score.none(), Score.none(), probeFailures);
        }

        static Result probed(SpellAbility choice, int kCollected, boolean overridden,
                Score greedyScore, Score bestAltScore, int probeFailures) {
            return new Result(choice, true, kCollected, overridden, greedyScore, bestAltScore, probeFailures);
        }

        /**
         * Best-alternative minus greedy, defined only when both probes scored
         * finitely; zero otherwise. The exported kind fields, never this
         * number, say whether a margin exists — Score treats kind as the source
         * of truth and sentinels are never arithmetic operands.
         */
        long margin() {
            if (!greedyScore.isFinite() || !bestAltScore.isFinite()) {
                return 0L;
            }
            return Score.finiteDelta(bestAltScore, greedyScore);
        }
    }
}
