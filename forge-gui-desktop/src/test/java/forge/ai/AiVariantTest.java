package forge.ai;

import org.testng.Assert;
import org.testng.annotations.Test;

public class AiVariantTest {
    @Test
    public void defaultsToBaseline() {
        LobbyPlayerAi player = new LobbyPlayerAi("baseline", null);
        Assert.assertEquals(player.getAiVariant(), AiVariant.BASELINE);
    }

    @Test
    public void carriesCandidateOnLobbyPlayer() {
        LobbyPlayerAi player = new LobbyPlayerAi("candidate", null);
        player.setAiVariant(AiVariant.CANDIDATE);
        Assert.assertEquals(player.getAiVariant(), AiVariant.CANDIDATE);
    }

    @Test
    public void parsesOnlyDeclaredExternalNames() {
        Assert.assertEquals(AiVariant.fromExternalName(null), AiVariant.BASELINE);
        Assert.assertEquals(AiVariant.fromExternalName("candidate"), AiVariant.CANDIDATE);
        Assert.expectThrows(
                IllegalArgumentException.class,
                () -> AiVariant.fromExternalName("experimental"));
    }
}
