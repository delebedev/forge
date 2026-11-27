package forge.game.spellability;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Map;

public class SpellAbilityRootTest {
    @BeforeClass
    public void initializeLocalizer() {
        Localizer.getInstance().initialize(
                "en-US",
                Path.of("..", "forge-gui", "res", "languages").toAbsolutePath().toString());
    }

    @Test
    public void rootAbilityTracksDirectAndDeepParentChains() {
        Card card = new Card(1, null);
        SpellAbility root = new SpellAbility.EmptySa(card);
        AbilitySub middle = subAbility(card);
        AbilitySub leaf = subAbility(card);

        middle.setParent(root);
        leaf.setParent(middle);

        Assert.assertSame(root.getRootAbility(), root);
        Assert.assertSame(middle.getRootAbility(), root);
        Assert.assertSame(leaf.getRootAbility(), root);
    }

    @Test
    public void rootAbilityReflectsReparenting() {
        Card card = new Card(1, null);
        SpellAbility firstRoot = new SpellAbility.EmptySa(card);
        SpellAbility secondRoot = new SpellAbility.EmptySa(card);
        AbilitySub child = subAbility(card);

        child.setParent(firstRoot);
        Assert.assertSame(child.getRootAbility(), firstRoot);

        child.setParent(secondRoot);
        Assert.assertSame(child.getRootAbility(), secondRoot);
    }

    private static AbilitySub subAbility(Card card) {
        return new AbilitySub(ApiType.Draw, card, null, Map.of());
    }
}
