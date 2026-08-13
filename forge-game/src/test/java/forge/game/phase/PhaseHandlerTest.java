package forge.game.phase;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

public class PhaseHandlerTest {
    @Test
    public void hookPublicationSupportsCrossThreadTeardown() throws ReflectiveOperationException {
        Assert.assertTrue(Modifier.isVolatile(PhaseHandler.class.getDeclaredField("mainGameLoopStartedHook").getModifiers()));
        Assert.assertTrue(Modifier.isVolatile(PhaseHandler.class.getDeclaredField("mainLoopStepCompletionHook").getModifiers()));
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

    private static void earlyReturn(final boolean stop) {
        if (stop) {
            return;
        }
        throw new AssertionError("unreachable");
    }
}
