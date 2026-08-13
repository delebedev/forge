package forge.game.phase;

import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class PhaseHandlerTest {
    @BeforeClass
    public void initializeLocalizer() {
        Localizer.getInstance().initialize(
                "en-US",
                Path.of("..", "forge-gui", "res", "languages").toAbsolutePath().toString());
    }

    @Test
    public void hookPublicationSupportsCrossThreadTeardown() throws ReflectiveOperationException {
        Assert.assertTrue(Modifier.isVolatile(PhaseHandler.class.getDeclaredField("mainGameLoopStartedHook").getModifiers()));
        Assert.assertTrue(Modifier.isVolatile(PhaseHandler.class.getDeclaredField("mainLoopStepCompletionHook").getModifiers()));
        Assert.assertTrue(Modifier.isVolatile(PhaseHandler.class.getDeclaredField("attackersDeclaredCompletionHook").getModifiers()));
        Assert.assertTrue(Modifier.isVolatile(PhaseHandler.class.getDeclaredField("blockersDeclaredCompletionHook").getModifiers()));
        Assert.assertTrue(Modifier.isVolatile(PhaseHandler.class.getDeclaredField("combatEndedCompletionHook").getModifiers()));
    }

    @Test
    public void completionRunsOnceAfterNormalBody() {
        final AtomicInteger bodyCalls = new AtomicInteger();
        final AtomicInteger completionCalls = new AtomicInteger();

        PhaseHandler.runCompletedStep(bodyCalls::incrementAndGet, () -> completionCalls::incrementAndGet);

        Assert.assertEquals(bodyCalls.get(), 1);
        Assert.assertEquals(completionCalls.get(), 1);
    }

    @Test
    public void completionRunsOnceAfterLexicalEarlyReturn() {
        final AtomicInteger completionCalls = new AtomicInteger();

        PhaseHandler.runCompletedStep(() -> earlyReturn(true), () -> completionCalls::incrementAndGet);

        Assert.assertEquals(completionCalls.get(), 1);
    }

    @Test
    public void bodyFailureSkipsCompletion() {
        final AtomicInteger completionCalls = new AtomicInteger();
        final IllegalStateException failure = new IllegalStateException("body failure");

        final IllegalStateException thrown =
                Assert.expectThrows(
                        IllegalStateException.class,
                        () -> PhaseHandler.runCompletedStep(() -> { throw failure; }, () -> completionCalls::incrementAndGet));

        Assert.assertSame(thrown, failure);
        Assert.assertEquals(completionCalls.get(), 0);
    }

    @Test
    public void completionFailurePropagates() {
        final IllegalStateException failure = new IllegalStateException("completion failure");

        final IllegalStateException thrown =
                Assert.expectThrows(
                        IllegalStateException.class,
                        () -> PhaseHandler.runCompletedStep(() -> {}, () -> () -> { throw failure; }));

        Assert.assertSame(thrown, failure);
    }

    @Test
    public void hookClearedDuringBodyDoesNotRunAfterCompletion() {
        final AtomicInteger completionCalls = new AtomicInteger();
        final AtomicReference<Runnable> hook = new AtomicReference<>(completionCalls::incrementAndGet);

        PhaseHandler.runCompletedStep(() -> hook.set(null), hook::get);

        Assert.assertEquals(completionCalls.get(), 0);
    }

    @Test
    public void eachCombatCompletionRunsOnceAfterItsWholeBody() {
        forEachCombatHook((handler, setHook, runMutation) -> {
            final StringBuilder chronology = new StringBuilder();
            final AtomicInteger completionCalls = new AtomicInteger();
            setHook.set(handler, () -> {
                chronology.append("-completion");
                completionCalls.incrementAndGet();
            });

            runMutation.run(handler, () -> chronology.append("event-triggers-view-unfreeze"));

            Assert.assertEquals(chronology.toString(), "event-triggers-view-unfreeze-completion");
            Assert.assertEquals(completionCalls.get(), 1);
        });
    }

    @Test
    public void eachCombatBodyFailureSkipsCompletion() {
        forEachCombatHook((handler, setHook, runMutation) -> {
            final AtomicInteger completionCalls = new AtomicInteger();
            final IllegalStateException failure = new IllegalStateException("combat mutation failure");
            setHook.set(handler, completionCalls::incrementAndGet);

            final IllegalStateException thrown =
                    Assert.expectThrows(
                            IllegalStateException.class,
                            () -> runMutation.run(handler, () -> { throw failure; }));

            Assert.assertSame(thrown, failure);
            Assert.assertEquals(completionCalls.get(), 0);
        });
    }

    @Test
    public void eachCombatCompletionFailurePropagates() {
        forEachCombatHook((handler, setHook, runMutation) -> {
            final IllegalStateException failure = new IllegalStateException("combat completion failure");
            setHook.set(handler, () -> { throw failure; });

            final IllegalStateException thrown =
                    Assert.expectThrows(
                            IllegalStateException.class,
                            () -> runMutation.run(handler, () -> {}));

            Assert.assertSame(thrown, failure);
        });
    }

    @Test
    public void eachCombatHookClearedDuringItsBodyDoesNotRun() {
        forEachCombatHook((handler, setHook, runMutation) -> {
            final AtomicInteger completionCalls = new AtomicInteger();
            setHook.set(handler, completionCalls::incrementAndGet);

            runMutation.run(handler, () -> setHook.set(handler, null));

            Assert.assertEquals(completionCalls.get(), 0);
        });
    }

    private static void forEachCombatHook(final CombatHookAssertion assertion) {
        assertion.verify(
                new PhaseHandler(null),
                PhaseHandler::setAttackersDeclaredCompletionHook,
                PhaseHandler::runAttackersDeclaredMutation);
        assertion.verify(
                new PhaseHandler(null),
                PhaseHandler::setBlockersDeclaredCompletionHook,
                PhaseHandler::runBlockersDeclaredMutation);
        assertion.verify(
                new PhaseHandler(null),
                PhaseHandler::setCombatEndedCompletionHook,
                PhaseHandler::runCombatEndedMutation);
    }

    @FunctionalInterface
    private interface CombatHookAssertion {
        void verify(PhaseHandler handler, HookSetter setHook, MutationRunner runMutation);
    }

    @FunctionalInterface
    private interface HookSetter {
        void set(PhaseHandler handler, Runnable hook);
    }

    @FunctionalInterface
    private interface MutationRunner {
        void run(PhaseHandler handler, Runnable body);
    }

    private static void earlyReturn(final boolean stop) {
        if (stop) {
            return;
        }
        throw new AssertionError("unreachable");
    }
}
