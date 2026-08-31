package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.IdentityHashMap;
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
    public void decisionLoggerEmitsStableSchemaSixDiagnostics() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card first = addCardToZone("Sleight of Hand", p, ZoneType.Graveyard);
        Card second = addCardToZone("Sleight of Hand", p, ZoneType.Graveyard);
        Card together = addCardToZone("Together as One", p, ZoneType.Hand);
        SpellAbility candidate = together.getSpellAbilities().get(0);

        ResearchDecisionLogger.CardRef firstRef = ResearchDecisionLogger.cardRef(first);
        ResearchDecisionLogger.CardRef secondRef = ResearchDecisionLogger.cardRef(second);
        Assert.assertEquals(firstRef.sameNameOrdinal(), 0);
        Assert.assertEquals(secondRef.sameNameOrdinal(), 1);
        Assert.assertEquals(secondRef.zone(), "Graveyard");

        ResearchDecisionLogger.AbilityAiDecision nested =
                new ResearchDecisionLogger.AbilityAiDecision(AiPlayDecision.TargetingFailed, 0, false);
        ResearchDecisionLogger.CandidateDiagnostics diagnostics =
                new ResearchDecisionLogger.CandidateDiagnostics(
                        List.of(new ResearchDecisionLogger.ManaPaymentAttempt(
                                "Draw", true, 2, List.of("W", "U"),
                                List.of(new ResearchDecisionLogger.ManaPaymentStep(
                                        firstRef, "GENERIC", List.of("U"))))),
                        List.of(new ResearchDecisionLogger.ReplayOption(
                                secondRef, 0, "Dig", "{U}", "{U}", "nested_veto",
                                AiPlayDecision.CantPlayAi, nested, null)),
                        secondRef);
        Map<SpellAbility, ResearchDecisionLogger.CandidateEvaluation> evaluations = new IdentityHashMap<>();
        evaluations.put(candidate, new ResearchDecisionLogger.CandidateEvaluation(
                AiPlayDecision.CantPlayAi, nested, diagnostics));

        String json = ResearchDecisionLogger.buildPriorityDecisionJson(
                game, p, List.of(candidate), null,
                false, 0, 0, false, 0, 0, evaluations);

        Assert.assertTrue(json.contains("\"schema\":\"priority_decision_v6\""));
        Assert.assertTrue(json.contains("\"action_schema\":\"priority_action_v4\""));
        Assert.assertTrue(json.contains("\"colors_paid\":[\"W\",\"U\"]"));
        Assert.assertTrue(json.contains("\"same_name_ordinal\":1"));
        Assert.assertTrue(json.contains("\"disposition\":\"nested_veto\""));
        Assert.assertTrue(json.contains("\"cost_payable\":null"));
        Assert.assertTrue(json.contains("\"selected_replay_target\":{\"owner\":\""
                + p.getName() + "\",\"zone\":\"Graveyard\",\"name\":\"Sleight of Hand\",\"same_name_ordinal\":1}"));
    }
}
