package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.ai.simulation.GameStateEvaluator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class ResearchEvalWeightsAiTest extends AITest {

    private static final List<String> FIXTURES = List.of(
            "Grizzly Bears",      // vanilla
            "Serra Angel",        // flying + vigilance
            "Wall of Omens",      // defender
            "Llanowar Elves",     // mana ability
            "Vampire Nighthawk"); // flying + deathtouch + lifelink

    @Test
    public void identityTableMatchesStockOnRealCards() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        CreatureEvaluator stock = new CreatureEvaluator();
        ResearchWeightedCreatureEvaluator identity =
                new ResearchWeightedCreatureEvaluator(ResearchCreatureWeights.parse(List.of(), "x"));
        for (String name : FIXTURES) {
            Card c = addCard(name, p);
            Assert.assertEquals(identity.evaluateCreature(c), stock.evaluateCreature(c), name);
        }
    }

    @Test
    public void powerMultiplierShiftsScoreByExactlyThePowerTerm() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        CreatureEvaluator stock = new CreatureEvaluator();
        ResearchWeightedCreatureEvaluator doubledPower =
                new ResearchWeightedCreatureEvaluator(ResearchCreatureWeights.parse(List.of("power = 2"), "x"));

        Card bears = addCard("Grizzly Bears", p);
        // Only the plain power term (power * 15) scales for a keywordless creature.
        Assert.assertEquals(doubledPower.evaluateCreature(bears),
                stock.evaluateCreature(bears) + bears.getNetCombatDamage() * 15);
    }

    @Test
    public void collectedFeaturesSumToStockScore() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        CreatureEvaluator stock = new CreatureEvaluator();
        ResearchCollectingCreatureEvaluator collector = new ResearchCollectingCreatureEvaluator();
        for (String name : FIXTURES) {
            Card c = addCard(name, p);
            Map<String, Long> features = collector.featuresFor(c);
            long sum = features.values().stream().mapToLong(Long::longValue).sum();
            Assert.assertEquals(sum, stock.evaluateCreature(c), name + " features: " + features);
        }
    }

    @Test
    public void contextRoutesTheStaticFunnel() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bears = addCard("Grizzly Bears", p);
        int stockScore = ComputerUtilCard.evaluateCreature(bears);

        ResearchCreatureWeights.setContext(ResearchCreatureWeights.parse(List.of("power = 2"), "x"));
        try {
            Assert.assertEquals(ComputerUtilCard.evaluateCreature(bears),
                    stockScore + bears.getNetCombatDamage() * 15);
        } finally {
            ResearchCreatureWeights.setContext(null);
        }
        Assert.assertEquals(ComputerUtilCard.evaluateCreature(bears), stockScore);
    }

    @Test
    public void lobbyPlayerRoutesWeightsIntoOnlyItsController() {
        Game game = initAndCreateGame();
        LobbyPlayerAi weighted = (LobbyPlayerAi) game.getPlayers().get(0).getLobbyPlayer();
        weighted.setEvalWeights(ResearchCreatureWeights.parse(List.of("power = 2"), "x"));
        // Controllers are created per game; rebuild them from the lobby players.
        PlayerControllerAi weightedController = new PlayerControllerAi(game, game.getPlayers().get(0), weighted);
        PlayerControllerAi stockController = new PlayerControllerAi(game, game.getPlayers().get(1),
                game.getPlayers().get(1).getLobbyPlayer());

        Assert.assertNotNull(weightedController.getAi().getEvalWeights());
        Assert.assertNull(stockController.getAi().getEvalWeights());
    }

    @Test
    public void fittedWeightsChangeSimulationFutureRankingWhileIdentityIsInert() {
        ResearchCreatureWeights fitted = ResearchCreatureWeights.parse(List.of(
                "cmc = 0.0", "flying = 3.4841", "non-token = 0.0",
                "power = 1.035", "toughness = 2.5316", "vigilance = 3.0927"), "evalfit-v1");
        ResearchCreatureWeights identity = ResearchCreatureWeights.parse(List.of(), "identity");

        int stockAngel = simulationScore("Serra Angel", null);
        int stockBrontodon = simulationScore("Ancient Brontodon", null);
        Assert.assertTrue(stockBrontodon > stockAngel);
        Assert.assertEquals(simulationScore("Serra Angel", identity), stockAngel);
        Assert.assertEquals(simulationScore("Ancient Brontodon", identity), stockBrontodon);
        Assert.assertTrue(simulationScore("Serra Angel", fitted) > simulationScore("Ancient Brontodon", fitted));
    }

    private int simulationScore(String cardName, ResearchCreatureWeights weights) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        moveToMain2(game, ai);
        addCard(cardName, ai);
        ResearchCreatureWeights.setContext(weights);
        try {
            return new GameStateEvaluator().getScoreForGameState(game, ai).value;
        } finally {
            ResearchCreatureWeights.setContext(null);
        }
    }
}
