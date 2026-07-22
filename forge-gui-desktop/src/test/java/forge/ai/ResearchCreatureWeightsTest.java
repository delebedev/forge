package forge.ai;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class ResearchCreatureWeightsTest {

    @Test
    public void parsesTermsCommentsAndBlanks() {
        ResearchCreatureWeights w = ResearchCreatureWeights.parse(
                List.of("# comment", "", "power = 1.5", "flying=0"), "abc");
        Assert.assertEquals(w.multiplier("power"), 1.5);
        Assert.assertEquals(w.multiplier("flying"), 0.0);
        Assert.assertEquals(w.termCount(), 2);
        Assert.assertEquals(w.sourceSha256(), "abc");
    }

    @Test
    public void missingTermDefaultsToIdentity() {
        ResearchCreatureWeights w = ResearchCreatureWeights.parse(List.of("power = 2"), "x");
        Assert.assertEquals(w.multiplier("toughness"), 1.0);
    }

    @Test
    public void variableLabelsNormalizeToTermPrefix() {
        Assert.assertEquals(ResearchCreatureWeights.termKey("toughness: 3"), "toughness");
        Assert.assertEquals(ResearchCreatureWeights.termKey("sa: Some Ability"), "sa");
        Assert.assertEquals(ResearchCreatureWeights.termKey("power"), "power");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void unknownTermFailsLoudly() {
        ResearchCreatureWeights.parse(List.of("no-such-term = 1.0"), "x");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void virtualTermsAreNotWeightable() {
        ResearchCreatureWeights.parse(List.of("_base = 2.0"), "x");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void malformedMultiplierFailsLoudly() {
        ResearchCreatureWeights.parse(List.of("power = fast"), "x");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void negativeMultiplierFailsLoudly() {
        ResearchCreatureWeights.parse(List.of("power = -1"), "x");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void duplicateTermFailsLoudly() {
        ResearchCreatureWeights.parse(List.of("power = 1", "power = 2"), "x");
    }

    @Test
    public void identityTableIsExactOnAnyContribution() {
        ResearchCreatureWeights identity = ResearchCreatureWeights.parse(List.of(), "x");
        ResearchWeightedCreatureEvaluator eval = new ResearchWeightedCreatureEvaluator(identity);
        for (int v = -1000; v <= 1000; v++) {
            Assert.assertEquals(eval.addValue(v, "power"), v);
        }
    }
}
