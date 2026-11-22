package forge.ai;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AiAttackControllerOrderTest extends AITest {

    @Test
    public void forcedAttackersEnterCombatInAttackerOrderWhenTasksFinishInReverse() throws Exception {
        Game game = initAndCreateGame();
        Player attacker = game.getPlayers().get(1);
        Player defender = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, attacker);
        List<Card> attackers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Card bears = addCard("Grizzly Bears", attacker);
            bears.setSickness(false);
            attackers.add(bears);
        }
        game.getAction().checkStateEffects(true);

        // each task waits for the task after it, so they finish last-to-first
        List<CountDownLatch> finished = new ArrayList<>();
        for (int i = 0; i < attackers.size(); i++) {
            finished.add(new CountDownLatch(1));
        }
        List<Integer> completionOrder = new ArrayList<>();
        List<Callable<GameEntity>> tasks = new ArrayList<>();
        for (int i = 0; i < attackers.size(); i++) {
            final int index = i;
            tasks.add(() -> {
                if (index + 1 < finished.size()) {
                    finished.get(index + 1).await(10, TimeUnit.SECONDS);
                }
                synchronized (completionOrder) {
                    completionOrder.add(index);
                }
                finished.get(index).countDown();
                return defender;
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(attackers.size());
        List<GameEntity> defenders;
        try {
            defenders = AiAttackController.invokeAllInOrder(executor, tasks, TimeUnit.SECONDS.toNanos(30));
        } finally {
            executor.shutdownNow();
        }
        AssertJUnit.assertEquals(List.of(2, 1, 0), completionOrder);

        Combat combat = new Combat(attacker);
        List<Card> attackersLeft = new ArrayList<>(attackers);
        int added = AiAttackController.addForcedAttackers(combat, attackers, defenders, attackersLeft);

        AssertJUnit.assertEquals(3, added);
        AssertJUnit.assertEquals(attackers, new ArrayList<>(combat.getAttackers()));
        AssertJUnit.assertTrue(attackersLeft.isEmpty());
    }

    @Test
    public void unfinishedTasksForceNoAttack() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<Callable<String>> tasks = List.of(() -> "done", () -> {
            Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            return "late";
        });
        List<String> results;
        try {
            results = AiAttackController.invokeAllInOrder(executor, tasks, TimeUnit.MILLISECONDS.toNanos(200));
        } finally {
            executor.shutdownNow();
        }
        AssertJUnit.assertEquals("done", results.get(0));
        AssertJUnit.assertNull(results.get(1));
    }
}
