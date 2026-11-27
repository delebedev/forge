package forge.game.spellability;

import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;
import forge.game.trigger.WrappedAbility;
import forge.util.Localizer;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
        SpellAbility lkiCopy = original.copy(card, true);

        AssertJUnit.assertTrue(original.getId() != firstCopy.getId());
        AssertJUnit.assertTrue(firstCopy.getId() != secondCopy.getId());
        AssertJUnit.assertEquals(original.getDefinitionId(), firstCopy.getDefinitionId());
        AssertJUnit.assertEquals(original.getDefinitionId(), secondCopy.getDefinitionId());
        AssertJUnit.assertEquals(original.getDefinitionId(), secondCopy.getView().getDefinitionId());
        AssertJUnit.assertEquals(original.getId(), lkiCopy.getId());
        AssertJUnit.assertEquals(original.getDefinitionId(), lkiCopy.getDefinitionId());
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
        SpellAbility firstFiring = new WrappedAbility(triggerCopy, triggerDefinition.copy(), null);
        SpellAbility secondFiring = new WrappedAbility(triggerCopy, triggerDefinition.copy(), null);

        AssertJUnit.assertTrue(trigger.getId() != triggerCopy.getId());
        AssertJUnit.assertEquals(trigger.getDefinitionId(), triggerCopy.getDefinitionId());
        AssertJUnit.assertTrue(staticAbility.getId() != staticCopy.getId());
        AssertJUnit.assertEquals(staticAbility.getDefinitionId(), staticCopy.getDefinitionId());
        AssertJUnit.assertTrue(firstFiring.getId() != secondFiring.getId());
        AssertJUnit.assertEquals(triggerDefinition.getDefinitionId(), firstFiring.getDefinitionId());
        AssertJUnit.assertEquals(triggerDefinition.getDefinitionId(), secondFiring.getDefinitionId());
        AssertJUnit.assertEquals(triggerDefinition.getDefinitionId(), firstFiring.getView().getDefinitionId());
        AssertJUnit.assertEquals(
                trigger.getDefinitionId(), firstFiring.getView().getSourceTriggerDefinitionId());
        AssertJUnit.assertEquals(
                firstFiring.getView().getSourceTriggerDefinitionId(),
                secondFiring.getView().getSourceTriggerDefinitionId());
    }

    @Test
    public void viewIdentitySurvivesSerializationRoundTrip() throws Exception {
        Card card = new Card(1, null);
        SpellAbility ability = new AbilityActivated(card, Cost.Zero, null) {
            @Override
            public void resolve() {
            }
        };
        SpellAbilityView original = ability.getView();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        SpellAbilityView restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (SpellAbilityView) input.readObject();
        }

        AssertJUnit.assertEquals(original.getDefinitionId(), restored.getDefinitionId());
        AssertJUnit.assertEquals(-1, restored.getSourceTriggerDefinitionId());
    }
}
