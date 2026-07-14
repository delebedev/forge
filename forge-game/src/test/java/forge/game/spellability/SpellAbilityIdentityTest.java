package forge.game.spellability;

import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;
import forge.util.Localizer;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Path;

public class SpellAbilityIdentityTest {

    @BeforeClass
    public void initializeLocalizer() {
        Localizer.getInstance().initialize(
                "en-US",
                Path.of("..", "forge-gui", "res", "languages").toAbsolutePath().toString());
    }

    @Test
    public void copyChainPreservesDefinitionIdAndMintsRuntimeIds() {
        Card card = new Card(1, null);
        SpellAbility original = new AbilityActivated(card, Cost.Zero, null) {
            @Override
            public void resolve() {
            }
        };

        SpellAbility firstCopy = original.copy();
        SpellAbility secondCopy = firstCopy.copy();

        AssertJUnit.assertTrue(original.getId() != firstCopy.getId());
        AssertJUnit.assertTrue(firstCopy.getId() != secondCopy.getId());
        AssertJUnit.assertEquals(original.getDefinitionId(), firstCopy.getDefinitionId());
        AssertJUnit.assertEquals(original.getDefinitionId(), secondCopy.getDefinitionId());
        AssertJUnit.assertEquals(original.getDefinitionId(), secondCopy.getView().getDefinitionId());
    }

    @Test
    public void triggerAndStaticCopiesPreserveDefinitionIds() {
        Card card = new Card(1, null);
        Trigger trigger = TriggerHandler.parseTrigger(
                "Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | ValidCard$ Card.Self",
                card,
                true);
        Trigger triggerCopy = trigger.copy(card, false);
        StaticAbility staticAbility = StaticAbility.create("Mode$ CantAttack | ValidCard$ Card.Self", card, null, true);
        StaticAbility staticCopy = staticAbility.copy(card, false);
        SpellAbility triggerDefinition = new AbilityActivated(card, Cost.Zero, null) {
            @Override
            public void resolve() {
            }
        };
        triggerDefinition.setTrigger(triggerCopy);
        SpellAbility firstFiring = triggerDefinition.copy();
        SpellAbility secondFiring = triggerDefinition.copy();

        AssertJUnit.assertTrue(trigger.getId() != triggerCopy.getId());
        AssertJUnit.assertEquals(trigger.getDefinitionId(), triggerCopy.getDefinitionId());
        AssertJUnit.assertTrue(staticAbility.getId() != staticCopy.getId());
        AssertJUnit.assertEquals(staticAbility.getDefinitionId(), staticCopy.getDefinitionId());
        AssertJUnit.assertTrue(firstFiring.getId() != secondFiring.getId());
        AssertJUnit.assertEquals(
                trigger.getDefinitionId(), firstFiring.getView().getSourceTriggerDefinitionId());
        AssertJUnit.assertEquals(
                firstFiring.getView().getSourceTriggerDefinitionId(),
                secondFiring.getView().getSourceTriggerDefinitionId());
    }
}
