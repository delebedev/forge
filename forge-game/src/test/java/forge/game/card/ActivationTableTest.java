package forge.game.card;

import forge.game.ability.ApiType;
import forge.game.cost.Cost;
import forge.game.spellability.AbilityActivated;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;
import forge.util.Localizer;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Path;

public class ActivationTableTest {
    @BeforeClass
    public void initializeLocalizer() {
        Localizer.getInstance().initialize(
                "en-US",
                Path.of("..", "forge-gui", "res", "languages").toAbsolutePath().toString());
    }

    @Test
    public void resolvesRootOriginalAndTriggerAbilities() {
        Card card = new Card(1, null);
        SpellAbility root = new AbilityActivated(card, Cost.Zero, null) {
            @Override
            public void resolve() {
            }
        };
        SpellAbility copy = root.copy();
        copy.setOriginalAbility(root);

        Trigger trigger = TriggerHandler.parseTrigger(
                "Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | ValidCard$ Card.Self",
                card,
                true);
        SpellAbility overriding = new SpellAbility.EmptySa(ApiType.DealDamage, card);
        trigger.setOverridingAbility(overriding);
        SpellAbility triggered = new SpellAbility.EmptySa(ApiType.DealDamage, card);
        triggered.setTrigger(trigger);

        ActivationTable table = new ActivationTable();
        AssertJUnit.assertSame(root, table.getOriginal(root));
        AssertJUnit.assertSame(root, table.getOriginal(copy));
        AssertJUnit.assertSame(overriding, table.getOriginal(triggered));
    }
}
