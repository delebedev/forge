package forge.game.event;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.util.Localizer;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Path;

public class DamageSourceKindTest {

    @BeforeClass
    public void initializeLocalizer() {
        Localizer.getInstance().initialize(
                "en-US",
                Path.of("..", "forge-gui", "res", "languages").toAbsolutePath().toString());
    }

    @Test
    public void classifiesCombatFightAndOrdinaryAbilityDamage() {
        Card source = new Card(1, null);
        SpellAbility fight = new SpellAbility.EmptySa(ApiType.Fight, source);
        SpellAbility ordinary = new SpellAbility.EmptySa(ApiType.DealDamage, source);

        AssertJUnit.assertEquals(DamageSourceKind.Combat, DamageSourceKind.from(true, fight));
        AssertJUnit.assertEquals(DamageSourceKind.Fight, DamageSourceKind.from(false, fight));
        AssertJUnit.assertEquals(DamageSourceKind.SpellOrAbility, DamageSourceKind.from(false, ordinary));
        AssertJUnit.assertEquals(DamageSourceKind.SpellOrAbility, DamageSourceKind.from(false, null));
    }
}
