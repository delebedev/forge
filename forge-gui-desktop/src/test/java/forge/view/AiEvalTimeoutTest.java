package forge.view;

import org.testng.Assert;
import org.testng.annotations.Test;

import forge.ai.AiController;

public class AiEvalTimeoutTest {
    @Test
    public void unsetKeepsForgeDefaultCeiling() {
        Assert.assertEquals(SimulateMatch.parseAiEvalTimeout(null), 0);
        Assert.assertEquals(SimulateMatch.parseAiEvalTimeout(""), 0);
    }

    @Test
    public void parsesPositiveSeconds() {
        Assert.assertEquals(SimulateMatch.parseAiEvalTimeout("120"), 120);
        Assert.assertEquals(SimulateMatch.parseAiEvalTimeout(" 45 "), 45);
    }

    @Test
    public void rejectsNonPositiveAndUnparseable() {
        Assert.expectThrows(
                IllegalArgumentException.class, () -> SimulateMatch.parseAiEvalTimeout("0"));
        Assert.expectThrows(
                IllegalArgumentException.class, () -> SimulateMatch.parseAiEvalTimeout("-5"));
        Assert.expectThrows(
                IllegalArgumentException.class, () -> SimulateMatch.parseAiEvalTimeout("120s"));
    }

    @Test
    public void fireCounterIsResettablePerGame() {
        AiController.resetEvalTimeoutFires();
        Assert.assertEquals(AiController.evalTimeoutFires(), 0);
    }
}
