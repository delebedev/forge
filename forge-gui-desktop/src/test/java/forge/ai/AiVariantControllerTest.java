package forge.ai;

import com.google.common.collect.Lists;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.ai.simulation.GameCopier;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class AiVariantControllerTest extends AITest {
    private Game createVariantGame() {
        final LobbyPlayerAi candidate = new LobbyPlayerAi("candidate", null);
        candidate.setAiVariant(AiVariant.CANDIDATE);
        final LobbyPlayerAi baseline = new LobbyPlayerAi("baseline", null);
        final Deck deck = new Deck();
        final List<RegisteredPlayer> players = Lists.newArrayList(
                new RegisteredPlayer(deck).setPlayer(candidate),
                new RegisteredPlayer(deck).setPlayer(baseline));
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(players, rules, new Match(rules, players, "Variant test"));
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        game.getPhaseHandler().onStackResolved();
        return game;
    }

    @Test
    public void routesVariantIntoOnlyTheSelectedController() {
        final Game game = createVariantGame();

        Assert.assertEquals(((PlayerControllerAi) game.getPlayers().get(0).getController()).getAiVariant(),
                AiVariant.CANDIDATE);
        Assert.assertEquals(((PlayerControllerAi) game.getPlayers().get(1).getController()).getAiVariant(),
                AiVariant.BASELINE);
    }

    @Test
    public void gameCopyPreservesEachControllerVariant() {
        final Game copy = new GameCopier(createVariantGame()).makeCopy();

        Assert.assertEquals(((PlayerControllerAi) copy.getPlayers().get(0).getController()).getAiVariant(),
                AiVariant.CANDIDATE);
        Assert.assertEquals(((PlayerControllerAi) copy.getPlayers().get(1).getController()).getAiVariant(),
                AiVariant.BASELINE);
    }

    @Test
    public void mindSlaveControllerUsesTheMastersVariant() {
        final Game game = createVariantGame();
        final Player candidate = game.getPlayers().get(0);
        final Player baseline = game.getPlayers().get(1);
        final long timestamp = game.getNextTimestamp();

        baseline.addController(timestamp, candidate);
        try {
            Assert.assertEquals(((PlayerControllerAi) baseline.getController()).getAiVariant(),
                    AiVariant.CANDIDATE);
        } finally {
            baseline.removeController(timestamp, false);
        }
        Assert.assertEquals(((PlayerControllerAi) baseline.getController()).getAiVariant(),
                AiVariant.BASELINE);
    }
}
