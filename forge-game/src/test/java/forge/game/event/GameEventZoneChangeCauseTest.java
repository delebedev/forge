package forge.game.event;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPayment;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.CostPaymentStack;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Map;

public class GameEventZoneChangeCauseTest {
    @BeforeClass
    public void initializeLocalizer() {
        Localizer.getInstance().initialize("en-US",
                Path.of("..", "forge-gui", "res", "languages").toAbsolutePath().toString());
    }

    @Test
    public void imprintComesFromTheExactOperationAndRemainsFrozen() {
        Card card = new Card(1, null);
        SpellAbility root = new SpellAbility.EmptySa(ApiType.Draw, card);
        AbilitySub imprint = new AbilitySub(ApiType.ChangeZone, card, null, Map.of("Imprint", "True"));
        imprint.setParent(root);
        GameEventZoneChangeCause cause = GameEventZoneChangeCause.from(imprint);
        Assert.assertTrue(cause.imprint());
        Assert.assertEquals(cause.sourceCardId(), card.getId());
        Assert.assertEquals(cause.abilityId(), imprint.getId());
        imprint.removeParam("Imprint");
        root.putParam("Imprint", "True");
        Assert.assertTrue(cause.imprint());
        Assert.assertFalse(GameEventZoneChangeCause.from(imprint).imprint());
        Assert.assertFalse(GameEventZoneChangeCause.from(root).imprint());
    }

    @Test
    public void costPaymentsDoNotInheritTheEffectImprintFlag() {
        Card card = new Card(1, null);
        SpellAbility imprint = new SpellAbility.EmptySa(ApiType.ChangeZone, card);
        imprint.putParam("Imprint", "True");
        CostPaymentStack.Entry payment = new CostPaymentStack.Entry(null, new CostPayment(Cost.Zero, imprint));
        GameEventZoneChangeCause cause = GameEventZoneChangeCause.from(imprint, null, payment);
        Assert.assertTrue(cause.costPayment());
        Assert.assertFalse(cause.imprint());
    }
}
