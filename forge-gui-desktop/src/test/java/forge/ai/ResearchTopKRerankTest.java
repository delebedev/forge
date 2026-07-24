package forge.ai;

import java.util.ArrayList;
import java.util.List;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.google.common.collect.Lists;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Bounded top-k one-ply rerank after the greedy veto scan: candidate-seat,
 * own-main-phase, empty-stack only. Later WillPlay candidates are compared by
 * one simulated resolution each; the greedy choice survives unless an
 * alternative beats it by the predeclared delta. Probing must leave the live
 * game and every unchosen SpellAbility unchanged.
 */
public class ResearchTopKRerankTest extends AITest {

    private Game createGame(AiVariant p1Variant) {
        List<RegisteredPlayer> players = Lists.newArrayList();
        Deck d1 = new Deck();
        players.add(new RegisteredPlayer(d1).setPlayer(new LobbyPlayerAi("p2", null)));
        LobbyPlayerAi p1Lobby = new LobbyPlayerAi("p1", null);
        p1Lobby.setAiVariant(p1Variant);
        players.add(new RegisteredPlayer(d1).setPlayer(p1Lobby));
        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, players, "Test");
        Game game = new Game(players, rules, match);
        Player p = game.getPlayers().get(1);
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getPhaseHandler().onStackResolved();
        return game;
    }

    private void stockLibraries(Game game) {
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 10; i++) {
                addCardToZone("Plains", player, ZoneType.Library);
            }
        }
    }

    /** Board: greedy sort prefers Divination (3 cmc); Shock is lethal only when opponent is at 2. */
    private static final class LethalBoard {
        final Game game;
        final Player p;
        final Player opponent;
        final SpellAbility divinationSa;
        final SpellAbility shockSa;

        LethalBoard(ResearchTopKRerankTest test, AiVariant variant, int opponentLife) {
            game = test.createGame(variant);
            p = game.getPlayers().get(1);
            opponent = game.getPlayers().get(0);
            test.stockLibraries(game);
            test.addCard("Island", p);
            test.addCard("Island", p);
            test.addCard("Mountain", p);
            Card divination = test.addCardToZone("Divination", p, ZoneType.Hand);
            Card shock = test.addCardToZone("Shock", p, ZoneType.Hand);
            opponent.setLife(opponentLife, null);
            // MAIN2: DrawAi defers card draw in MAIN1 (WaitForMain2), which would
            // hand the greedy scan Shock outright and make the override vacuous.
            game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
            game.getAction().checkStateEffects(true);
            divinationSa = divination.getFirstSpellAbility();
            shockSa = shock.getFirstSpellAbility();
        }
    }

    // --- seam-level: ResearchTopKRerank.choose ---

    @Test
    public void emptyTailReturnsGreedyIdentity() {
        LethalBoard b = new LethalBoard(this, AiVariant.CANDIDATE, 2);
        b.divinationSa.setActivatingPlayer(b.p);
        SpellAbility chosen = ResearchTopKRerank.choose(b.game, b.p, b.divinationSa, new ArrayList<>(), 1);
        AssertJUnit.assertSame(b.divinationSa, chosen);
    }

    @Test
    public void overridesGreedyWhenAlternativeSimulatesLethal() {
        LethalBoard b = new LethalBoard(this, AiVariant.CANDIDATE, 2);
        b.divinationSa.setActivatingPlayer(b.p);
        b.shockSa.setActivatingPlayer(b.p);
        b.shockSa.getTargets().add(b.opponent);
        SpellAbility chosen = ResearchTopKRerank.choose(b.game, b.p, b.divinationSa,
                Lists.newArrayList(b.shockSa), 1);
        AssertJUnit.assertSame(b.shockSa, chosen);
        // Live game untouched by probing.
        AssertJUnit.assertEquals(2, b.opponent.getLife());
        AssertJUnit.assertTrue(b.game.getStack().isEmpty());
        AssertJUnit.assertEquals(ZoneType.Hand, b.shockSa.getHostCard().getZone().getZoneType());
    }

    @Test
    public void keepsGreedyBelowDeltaAndRestoresUnchosenTargets() {
        LethalBoard b = new LethalBoard(this, AiVariant.CANDIDATE, 20);
        b.divinationSa.setActivatingPlayer(b.p);
        b.shockSa.setActivatingPlayer(b.p);
        b.shockSa.getTargets().add(b.opponent);
        SpellAbility chosen = ResearchTopKRerank.choose(b.game, b.p, b.divinationSa,
                Lists.newArrayList(b.shockSa), 1_000_000);
        AssertJUnit.assertSame(b.divinationSa, chosen);
        // The probed-but-unchosen alternative is restored to pristine state.
        AssertJUnit.assertEquals(0, b.shockSa.getTargets().size());
    }

    @Test
    public void restoresOverriddenGreedyTargets() {
        Game game = createGame(AiVariant.CANDIDATE);
        Player p = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        stockLibraries(game);
        addCard("Swamp", p);
        addCard("Swamp", p);
        addCard("Mountain", p);
        Card murder = addCardToZone("Murder", p, ZoneType.Hand);
        Card shock = addCardToZone("Shock", p, ZoneType.Hand);
        Card bear = addCard("Runeclaw Bear", opponent);
        opponent.setLife(2, null);
        game.getAction().checkStateEffects(true);

        SpellAbility murderSa = murder.getFirstSpellAbility();
        SpellAbility shockSa = shock.getFirstSpellAbility();
        murderSa.setActivatingPlayer(p);
        shockSa.setActivatingPlayer(p);
        murderSa.getTargets().add(bear);
        shockSa.getTargets().add(opponent);

        SpellAbility chosen = ResearchTopKRerank.choose(game, p, murderSa,
                Lists.newArrayList(shockSa), 1);
        AssertJUnit.assertSame(shockSa, chosen);
        // Greedy incumbent lost the rerank; its probe-time targets must not leak.
        AssertJUnit.assertEquals(0, murderSa.getTargets().size());
        AssertJUnit.assertTrue(bear.isInPlay());
    }

    @Test
    public void simulationFailureOnAlternativeKeepsGreedy() {
        LethalBoard b = new LethalBoard(this, AiVariant.CANDIDATE, 2);
        b.divinationSa.setActivatingPlayer(b.p);
        // A spell whose host card is in no zone cannot be found in the sim
        // copy: the probe must classify it as failure, never as a loss, and
        // never let it displace the greedy choice.
        Card ghost = createCard("Shock", b.p);
        SpellAbility ghostSa = ghost.getFirstSpellAbility();
        ghostSa.setActivatingPlayer(b.p);
        SpellAbility chosen = ResearchTopKRerank.choose(b.game, b.p, b.divinationSa,
                Lists.newArrayList(ghostSa), 1);
        AssertJUnit.assertSame(b.divinationSa, chosen);
    }

    @Test
    public void probeControllerNeverRecurses() {
        // One-ply means one ply: a probe controller at depth 0 with no budget
        // pressure must still refuse GameSimulator's follow-up-search recursion
        // (GameSimulator spawns a full SpellAbilityPicker tree when the
        // controller allows it — the T1 OOM canary, cascade 36dbe170b602).
        AssertJUnit.assertFalse(
                new ResearchOnePlySimulationController(new forge.ai.simulation.GameStateEvaluator.Score(0))
                        .shouldRecurse());
    }

    // --- eligibility gate ---

    @Test
    public void eligibleOnlyOnOwnMainPhaseWithEmptyStack() {
        LethalBoard b = new LethalBoard(this, AiVariant.CANDIDATE, 20);
        AssertJUnit.assertTrue(ResearchTopKRerank.eligible(b.game, b.p));
        // Opponent's turn: not eligible for p.
        b.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, b.opponent);
        AssertJUnit.assertFalse(ResearchTopKRerank.eligible(b.game, b.p));
        // Own non-main phase: not eligible.
        b.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, b.p);
        AssertJUnit.assertFalse(ResearchTopKRerank.eligible(b.game, b.p));
        b.game.getPhaseHandler().devModeSet(PhaseType.MAIN2, b.p);
        AssertJUnit.assertTrue(ResearchTopKRerank.eligible(b.game, b.p));
    }

    // --- integration through AiController (candidate defaults: k=3, delta=1) ---

    @Test
    public void candidateSeatOverridesGreedyEndToEnd() {
        LethalBoard b = new LethalBoard(this, AiVariant.CANDIDATE, 2);
        AiController ai = ((PlayerControllerAi) b.p.getController()).getAi();
        List<SpellAbility> chosen = ai.chooseSpellAbilityToPlay();
        AssertJUnit.assertNotNull(chosen);
        AssertJUnit.assertEquals(1, chosen.size());
        AssertJUnit.assertEquals("Shock", chosen.get(0).getHostCard().getName());
    }

    @Test
    public void baselineSeatKeepsGreedyEndToEnd() {
        LethalBoard b = new LethalBoard(this, AiVariant.BASELINE, 2);
        AiController ai = ((PlayerControllerAi) b.p.getController()).getAi();
        List<SpellAbility> chosen = ai.chooseSpellAbilityToPlay();
        AssertJUnit.assertNotNull(chosen);
        AssertJUnit.assertEquals(1, chosen.size());
        AssertJUnit.assertEquals("Divination", chosen.get(0).getHostCard().getName());
    }
}
