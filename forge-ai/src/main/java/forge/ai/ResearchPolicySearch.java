package forge.ai;

import forge.ai.simulation.GameSimulator;
import forge.ai.simulation.GameStateEvaluator;
import forge.ai.simulation.GameStateEvaluator.Score;
import forge.ai.simulation.SimulationController;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

final class ResearchPolicySearch {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getenv().getOrDefault("FORGE_AI_SEARCH", "false"));
    private static final int MIN_DELTA = Integer.parseInt(System.getenv().getOrDefault("FORGE_AI_SEARCH_MIN_DELTA", "0"));

    private ResearchPolicySearch() {
    }

    static boolean enabled() {
        return ENABLED;
    }

    static SpellAbility choose(Game game, Player player, SpellAbility neuralChoice, SpellAbility fallbackChoice) {
        if (!ENABLED || neuralChoice == null) {
            return neuralChoice;
        }
        try {
            GameStateEvaluator evaluator = new GameStateEvaluator();
            Score origScore = evaluator.getScoreForGameState(game, player);
            Score neuralScore = simulate(game, player, neuralChoice, origScore);
            Score fallbackScore = fallbackChoice == null ? origScore : simulate(game, player, fallbackChoice, origScore);
            return Score.meetsThreshold(neuralScore, fallbackScore, MIN_DELTA) ? neuralChoice : fallbackChoice;
        } catch (RuntimeException ex) {
            return fallbackChoice;
        }
    }

    private static Score simulate(Game game, Player player, SpellAbility choice, Score origScore) {
        SimulationController controller = new SimulationController(origScore);
        GameSimulator simulator = new GameSimulator(controller, game, player, null);
        return simulator.simulateSpellAbility(choice);
    }
}
