package forge.ai.simulation;

import forge.game.Game;
import forge.game.GameSnapshot;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Research probe: measures GameCopier / GameSnapshot throughput on a
 * mid-game-shaped board. Feeds the MCTS sim-budget estimate in
 * ai-research/magezero-replication.md (section 4.6). Not a correctness test.
 */
public class CopyThroughputProbeTest extends SimulationTest {

    private static final int WARMUP = 30;
    private static final int ITERS = 200;

    private Game buildMidGameState() {
        Game game = initAndCreateGame();
        Player p0 = game.getPlayers().get(0);
        Player p1 = game.getPlayers().get(1);

        for (Player p : new Player[] { p0, p1 }) {
            addCards("Plains", 3, p);
            addCards("Forest", 3, p);
            addCard("Serra Angel", p);
            addCard("Grizzly Bears", p);
            addCard("Ajani's Pridemate", p);
            Card counters = addCard("Grizzly Bears", p);
            counters.addCounterInternal(CounterEnumType.P1P1, 3, p, false, null, null);
            addCardToZone("Pacifism", p, ZoneType.Hand);
            addCardToZone("Serra Angel", p, ZoneType.Hand);
            addCardToZone("Plains", p, ZoneType.Hand);
            for (int i = 0; i < 5; i++) {
                addCardToZone("Forest", p, ZoneType.Graveyard);
            }
            for (int i = 0; i < 30; i++) {
                addCardToZone("Plains", p, ZoneType.Library);
            }
        }
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void probeGameCopierThroughput() {
        Game game = buildMidGameState();
        for (int i = 0; i < WARMUP; i++) {
            new GameCopier(game).makeCopy();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
            Game copy = new GameCopier(game).makeCopy();
            Assert.assertEquals(copy.getPlayers().size(), 2);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        double copiesPerSec = ITERS * 1000.0 / Math.max(elapsedMs, 1);
        System.out.println(String.format(
                "[copy-probe] GameCopier: %d copies in %d ms => %.1f copies/sec (%.1f ms/copy)",
                ITERS, elapsedMs, copiesPerSec, (double) elapsedMs / ITERS));
    }

    @Test
    public void probeGameSnapshotThroughput() {
        Game game = buildMidGameState();
        try {
            new GameSnapshot(game).makeCopy();
        } catch (Throwable t) {
            // Probe, not a gate: snapshot path is known-experimental. Report and bail.
            System.out.println("[copy-probe] GameSnapshot: BROKEN on this base: " + t);
            return;
        }
        for (int i = 0; i < WARMUP; i++) {
            new GameSnapshot(game).makeCopy();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
            Game copy = new GameSnapshot(game).makeCopy();
            Assert.assertEquals(copy.getPlayers().size(), 2);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        double copiesPerSec = ITERS * 1000.0 / Math.max(elapsedMs, 1);
        System.out.println(String.format(
                "[copy-probe] GameSnapshot: %d copies in %d ms => %.1f copies/sec (%.1f ms/copy)",
                ITERS, elapsedMs, copiesPerSec, (double) elapsedMs / ITERS));
    }
}
