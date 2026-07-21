package forge.view;

import forge.deck.Deck;
import forge.game.player.RegisteredPlayer;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ResearchControlTest {
    @Test
    public void parsesOnlyDeclaredExternalNames() {
        Assert.assertEquals(ResearchControl.fromExternalName(null), ResearchControl.NONE);
        Assert.assertEquals(ResearchControl.fromExternalName("sham"), ResearchControl.SHAM);
        Assert.assertEquals(
                ResearchControl.fromExternalName("start-at-zero"),
                ResearchControl.START_AT_ZERO);
        Assert.assertEquals(ResearchControl.fromExternalName("fail"), ResearchControl.FAIL);
        Assert.expectThrows(
                IllegalArgumentException.class,
                () -> ResearchControl.fromExternalName("unknown"));
    }

    @Test
    public void shamTraversesTheControlWithoutChangingThePlayer() {
        final RegisteredPlayer player = new RegisteredPlayer(new Deck());
        ResearchControl.SHAM.applyTo(player);
        Assert.assertEquals(player.getStartingLife(), 20);
    }

    @Test
    public void startAtZeroChangesOnlyTheSelectedPlayerObject() {
        final RegisteredPlayer selected = new RegisteredPlayer(new Deck());
        final RegisteredPlayer other = new RegisteredPlayer(new Deck());
        ResearchControl.START_AT_ZERO.applyTo(selected);
        Assert.assertEquals(selected.getStartingLife(), 0);
        Assert.assertEquals(other.getStartingLife(), 20);
    }

    @Test
    public void failDeclaresTheProcessExitPath() {
        Assert.assertTrue(ResearchControl.FAIL.isForcedFailure());
        Assert.assertFalse(ResearchControl.SHAM.isForcedFailure());
    }
}
