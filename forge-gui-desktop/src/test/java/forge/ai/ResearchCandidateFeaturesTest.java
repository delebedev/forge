package forge.ai;

import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ResearchCandidateFeaturesTest {
    @Test
    public void unsetMeansAllFeatures() {
        Assert.assertNull(ResearchCandidateFeatures.parse(null));
        Assert.assertNull(ResearchCandidateFeatures.parse(""));
        Assert.assertNull(ResearchCandidateFeatures.parse("   "));
    }

    @Test
    public void parsesKnownSubset() {
        Set<String> one = ResearchCandidateFeatures.parse("mandatory-etb");
        Assert.assertEquals(one, Set.of(ResearchCandidateFeatures.MANDATORY_ETB));
        Set<String> both = ResearchCandidateFeatures.parse(" cast-trigger-main1 , mandatory-etb ");
        Assert.assertEquals(both,
                Set.of(ResearchCandidateFeatures.CAST_TRIGGER_MAIN1, ResearchCandidateFeatures.MANDATORY_ETB));
    }

    @Test
    public void rejectsUnknownAndEmptySelections() {
        Assert.expectThrows(IllegalArgumentException.class,
                () -> ResearchCandidateFeatures.parse("cast-trigger-main1,typo-feature"));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> ResearchCandidateFeatures.parse(","));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> ResearchCandidateFeatures.isEnabled("typo-feature"));
    }

    @Test
    public void unsetEnvironmentEnablesEveryKnownFeature() {
        // The test JVM runs without FORGE_AI_CANDIDATE_FEATURES.
        Assert.assertTrue(ResearchCandidateFeatures.isEnabled(ResearchCandidateFeatures.CAST_TRIGGER_MAIN1));
        Assert.assertTrue(ResearchCandidateFeatures.isEnabled(ResearchCandidateFeatures.MANDATORY_ETB));
    }
}
