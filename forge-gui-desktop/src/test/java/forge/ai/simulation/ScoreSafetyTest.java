package forge.ai.simulation;

import org.testng.annotations.Test;

import forge.ai.simulation.GameStateEvaluator.Score;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Score kind semantics: terminal win/loss, simulation failure, and the
 * no-score-yet initializer are typed states, never arithmetic operands.
 * Sentinel ints remain only as the legacy ordinal encoding in {@code value}
 * so untouched comparison sites keep their behavior.
 */
public class ScoreSafetyTest {

    // --- kinds and legacy ordinal encoding ---

    @Test
    public void winKindKeepsMaxSentinelButIsNotFinite() {
        Score win = Score.win();
        assertTrue(win.isWin());
        assertFalse(win.isFinite());
        assertEquals(Integer.MAX_VALUE, win.value);
    }

    @Test
    public void lossKindKeepsMinSentinelButIsNotFinite() {
        Score loss = Score.loss();
        assertTrue(loss.isLoss());
        assertFalse(loss.isFinite());
        assertEquals(Integer.MIN_VALUE, loss.value);
    }

    @Test
    public void simFailureIsNotALoss() {
        Score failure = Score.simFailure();
        assertTrue(failure.isSimFailure());
        assertFalse(failure.isLoss());
        assertFalse(failure.isFinite());
        // Legacy ordinal: failure must never out-compare anything at raw
        // .value comparison sites.
        assertEquals(Integer.MIN_VALUE, failure.value);
    }

    @Test
    public void noneIsNeitherLossNorFailure() {
        Score none = Score.none();
        assertTrue(none.isNone());
        assertFalse(none.isLoss());
        assertFalse(none.isSimFailure());
        assertFalse(none.isFinite());
        assertEquals(Integer.MIN_VALUE, none.value);
    }

    @Test
    public void intConstructorsAreFinite() {
        assertTrue(new Score(0).isFinite());
        assertTrue(new Score(123, 45).isFinite());
        assertTrue(new Score(Integer.MAX_VALUE - 1).isFinite());
        assertTrue(new Score(Integer.MIN_VALUE + 1).isFinite());
    }

    // --- equals is kind-aware ---

    @Test
    public void equalsDistinguishesKindsSharingASentinel() {
        assertFalse(Score.loss().equals(Score.simFailure()));
        assertFalse(Score.loss().equals(Score.none()));
        assertFalse(Score.simFailure().equals(Score.none()));
        assertTrue(Score.loss().equals(Score.loss()));
        assertTrue(Score.win().equals(Score.win()));
        assertTrue(new Score(7, 3).equals(new Score(7, 3)));
        assertFalse(new Score(Integer.MIN_VALUE).equals(Score.loss()));
    }

    // --- meetsThreshold: finite comparisons ---

    @Test
    public void finiteThresholdBasics() {
        assertTrue(Score.meetsThreshold(new Score(100), new Score(90), 5));
        assertFalse(Score.meetsThreshold(new Score(100), new Score(90), 15));
        assertTrue(Score.meetsThreshold(new Score(90), new Score(90), 0));
        assertFalse(Score.meetsThreshold(new Score(90), new Score(90), 1));
        assertTrue(Score.meetsThreshold(new Score(80), new Score(90), -10));
    }

    @Test
    public void positiveThresholdGateAtIntegerExtremesDoesNotWrap() {
        // Raw int arithmetic would wrap incumbent + delta negative and let
        // the gate pass; the typed gate must not.
        Score candidate = new Score(Integer.MAX_VALUE - 1);
        Score incumbent = new Score(Integer.MAX_VALUE - 2);
        assertFalse(Score.meetsThreshold(candidate, incumbent, Integer.MAX_VALUE));
    }

    @Test
    public void negativeThresholdGateAtIntegerExtremesDoesNotWrap() {
        // Raw int arithmetic would wrap incumbent + delta positive and block
        // a gate that should pass.
        Score candidate = new Score(Integer.MIN_VALUE + 5);
        Score incumbent = new Score(Integer.MIN_VALUE + 1);
        assertTrue(Score.meetsThreshold(candidate, incumbent, Integer.MIN_VALUE));
    }

    // --- meetsThreshold: terminal ordering ---

    @Test
    public void winBeatsAnyFiniteIncumbentRegardlessOfDelta() {
        assertTrue(Score.meetsThreshold(Score.win(), new Score(Integer.MAX_VALUE - 1), Integer.MAX_VALUE));
    }

    @Test
    public void finiteNeverBeatsAWinIncumbent() {
        assertFalse(Score.meetsThreshold(new Score(Integer.MAX_VALUE - 1), Score.win(), Integer.MIN_VALUE));
    }

    @Test
    public void winVersusWinFollowsDeltaSign() {
        assertTrue(Score.meetsThreshold(Score.win(), Score.win(), 0));
        assertTrue(Score.meetsThreshold(Score.win(), Score.win(), -5));
        assertFalse(Score.meetsThreshold(Score.win(), Score.win(), 1));
    }

    @Test
    public void anyFiniteBeatsALossIncumbent() {
        assertTrue(Score.meetsThreshold(new Score(Integer.MIN_VALUE + 1), Score.loss(), Integer.MAX_VALUE));
    }

    @Test
    public void lossCandidateNeverMeetsThreshold() {
        assertFalse(Score.meetsThreshold(Score.loss(), new Score(0), Integer.MIN_VALUE));
        assertFalse(Score.meetsThreshold(Score.loss(), Score.loss(), 0));
    }

    // --- meetsThreshold: failure is not a loss ---

    @Test
    public void failedCandidateSimulationNeverMeetsThreshold() {
        assertFalse(Score.meetsThreshold(Score.simFailure(), new Score(0), Integer.MIN_VALUE));
        assertFalse(Score.meetsThreshold(Score.simFailure(), Score.loss(), Integer.MIN_VALUE));
    }

    @Test
    public void failedIncumbentSimulationIsNotAFreeWin() {
        // An incumbent whose simulation failed has unknown value; the
        // candidate must not be preferred on the strength of a sentinel.
        assertFalse(Score.meetsThreshold(new Score(1000), Score.simFailure(), Integer.MIN_VALUE));
        assertFalse(Score.meetsThreshold(Score.win(), Score.simFailure(), 0));
    }

    @Test
    public void noneNeverMeetsAndIsNeverBeaten() {
        assertFalse(Score.meetsThreshold(Score.none(), new Score(0), Integer.MIN_VALUE));
        assertFalse(Score.meetsThreshold(new Score(1000), Score.none(), Integer.MIN_VALUE));
    }

    // --- addDelta: cached-effect shortcut arithmetic ---

    @Test
    public void addDeltaAppliesToValueAndPreservesSummonSick() {
        Score base = new Score(100, 80);
        Score result = base.addDelta(-30);
        assertEquals(70, result.value);
        assertEquals(80, result.summonSickValue);
        assertTrue(result.isFinite());
    }

    @Test
    public void addDeltaSaturatesWithoutFabricatingTerminals() {
        Score high = new Score(Integer.MAX_VALUE - 1).addDelta(Integer.MAX_VALUE);
        assertTrue(high.isFinite());
        assertFalse(high.isWin());
        assertTrue(high.value <= Integer.MAX_VALUE - 1);

        Score low = new Score(Integer.MIN_VALUE + 1).addDelta(Integer.MIN_VALUE);
        assertTrue(low.isFinite());
        assertFalse(low.isLoss());
        assertTrue(low.value >= Integer.MIN_VALUE + 1);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void addDeltaOnNonFiniteThrows() {
        Score.win().addDelta(1);
    }

    // --- finiteDelta: cache-entry computation ---

    @Test
    public void finiteDeltaIsExactInLong() {
        assertEquals(-30L, Score.finiteDelta(new Score(70), new Score(100)));
        long wide = Score.finiteDelta(new Score(Integer.MAX_VALUE - 1), new Score(Integer.MIN_VALUE + 1));
        assertEquals((long) (Integer.MAX_VALUE - 1) - (long) (Integer.MIN_VALUE + 1), wide);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void finiteDeltaOnNonFiniteThrows() {
        Score.finiteDelta(Score.simFailure(), new Score(0));
    }

    // --- cache suppression for non-finite scores ---

    @Test
    public void effectCacheAcceptsOnlyFinitePairs() {
        assertTrue(SimulationController.isCacheableDelta(new Score(50), new Score(100)));
        assertFalse(SimulationController.isCacheableDelta(Score.win(), new Score(100)));
        assertFalse(SimulationController.isCacheableDelta(Score.loss(), new Score(100)));
        assertFalse(SimulationController.isCacheableDelta(Score.simFailure(), new Score(100)));
        assertFalse(SimulationController.isCacheableDelta(new Score(50), Score.none()));
        assertFalse(SimulationController.isCacheableDelta(new Score(50), Score.win()));
    }
}
