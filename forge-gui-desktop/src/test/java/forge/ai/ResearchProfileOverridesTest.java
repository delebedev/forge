package forge.ai;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ResearchProfileOverridesTest {

    private static ResearchProfileOverrides parse(String... lines) {
        return ResearchProfileOverrides.parse(List.of(lines), "sha");
    }

    @Test
    public void parsesDeclaredPropertiesAndIgnoresCommentsAndBlanks() {
        ResearchProfileOverrides overrides = parse(
                "# a comment",
                "",
                "CHANCE_TO_ATTACK_INTO_TRADE = 40",
                "PLAY_AGGRO=true");

        Assert.assertEquals(overrides.propCount(), 2);
        Assert.assertEquals(overrides.value(AiProps.CHANCE_TO_ATTACK_INTO_TRADE), "40");
        Assert.assertEquals(overrides.value(AiProps.PLAY_AGGRO), "true");
    }

    @Test
    public void unlistedPropertyFallsThroughToTheProfile() {
        ResearchProfileOverrides overrides = parse("PLAY_AGGRO=true");

        Assert.assertNull(overrides.value(AiProps.MULLIGAN_THRESHOLD));
    }

    @Test
    public void emptyTableIsStock() {
        ResearchProfileOverrides overrides = parse("# nothing here");

        Assert.assertEquals(overrides.propCount(), 0);
        Assert.assertNull(overrides.value(AiProps.PLAY_AGGRO));
    }

    @Test
    public void unknownPropertyFails() {
        // Forge's own profile loader ignores these; a research arm must not.
        Assert.expectThrows(IllegalArgumentException.class, () -> parse("NOT_A_REAL_PROP=1"));
    }

    @Test
    public void malformedLineFails() {
        Assert.expectThrows(IllegalArgumentException.class, () -> parse("PLAY_AGGRO true"));
    }

    @Test
    public void duplicatePropertyFails() {
        Assert.expectThrows(
                IllegalArgumentException.class,
                () -> parse("PLAY_AGGRO=true", "PLAY_AGGRO=false"));
    }

    @Test
    public void emptyValueFails() {
        // An empty profile value silently means "use the default".
        Assert.expectThrows(IllegalArgumentException.class, () -> parse("PLAY_AGGRO="));
    }

    @Test
    public void valueShapeMustMatchTheDeclaredDefault() {
        Assert.expectThrows(
                IllegalArgumentException.class,
                () -> parse("MULLIGAN_THRESHOLD=aggressive"));
        Assert.expectThrows(
                IllegalArgumentException.class,
                () -> parse("PLAY_AGGRO=40"));
    }

    @Test
    public void stringValuedPropertyAcceptsItsOwnVocabulary() {
        ResearchProfileOverrides overrides = parse("MOVE_EQUIPMENT_TO_BETTER_CREATURES=always");

        Assert.assertEquals(overrides.value(AiProps.MOVE_EQUIPMENT_TO_BETTER_CREATURES), "always");
    }

    @Test
    public void seatWithoutOverridesReadsStock() {
        LobbyPlayerAi player = new LobbyPlayerAi("stock", null);

        Assert.assertNull(player.getProfileOverrides());
    }

    @Test
    public void tableRidesTheLobbyPlayerSeat() {
        LobbyPlayerAi player = new LobbyPlayerAi("candidate", null);
        ResearchProfileOverrides overrides = parse("PLAY_AGGRO=true");

        player.setProfileOverrides(overrides);

        Assert.assertSame(player.getProfileOverrides(), overrides);
    }

    @Test
    public void nullPlayerHasNoOverrides() {
        Assert.assertNull(ResearchProfileOverrides.forPlayer(null));
    }
}
