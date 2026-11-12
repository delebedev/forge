package forge.ai;

import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

public class ComputerUtilManaSacrificeTest extends AITest {
    private boolean canPay(Player player, String spellName) {
        SpellAbility spell = addCardToZone(spellName, player, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(player);
        return ComputerUtilMana.canPayManaCost(spell, player, 0, false);
    }

    @Test
    public void repeatableSourceReservesDistinctSacrificesWithoutChangingTheBoard() {
        Game game = initAndCreateGame();
        Player player = game.getPlayers().get(1);
        addCard("Utopia Mycon", player);
        addTokens("g_1_1_saproling", 2, player);
        AssertJUnit.assertTrue(canPay(player, "Golgari Charm"));
        AssertJUnit.assertEquals(3, player.getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertEquals(0, player.getManaPool().totalMana());
        AssertJUnit.assertTrue(canPay(player, "Golgari Charm"));
    }

    @Test
    public void oneSacrificeCannotFundTwoMana() {
        Player player = initAndCreateGame().getPlayers().get(1);
        addCard("Utopia Mycon", player);
        addTokens("g_1_1_saproling", 1, player);
        AssertJUnit.assertFalse(canPay(player, "Golgari Charm"));
    }

    @Test
    public void twoSourcesCannotSpendTheSameSacrifice() {
        Player player = initAndCreateGame().getPlayers().get(1);
        addCards("Utopia Mycon", 2, player);
        addTokens("g_1_1_saproling", 1, player);
        AssertJUnit.assertFalse(canPay(player, "Golgari Charm"));
    }

    @Test
    public void sourceModesShareSacrificeResources() {
        Player player = initAndCreateGame().getPlayers().get(1);
        Card source = addCard("Grizzly Bears", player);
        source.addSpellAbility(AbilityFactory.getAbility("AB$ Mana | Cost$ Sac<1/Saproling> | Produced$ B", source));
        source.addSpellAbility(AbilityFactory.getAbility("AB$ Mana | Cost$ Sac<1/Saproling> | Produced$ G", source));
        addTokens("g_1_1_saproling", 1, player);
        AssertJUnit.assertFalse(canPay(player, "Golgari Charm"));
        addTokens("g_1_1_saproling", 1, player);
        AssertJUnit.assertTrue(canPay(player, "Golgari Charm"));
    }

    @Test
    public void exhaustedSacrificeSourceCannotProduceMana() {
        Player player = initAndCreateGame().getPlayers().get(1);
        addCard("Utopia Mycon", player);
        AssertJUnit.assertFalse(canPay(player, "Golgari Charm"));
    }

    @Test
    public void tappedAndSelfSacrificingSourcesAreNotReusable() {
        Player player = initAndCreateGame().getPlayers().get(1);
        Card land = addCard("Command Tower", player);
        land.setTapped(true);
        addCard("Lotus Petal", player);
        AssertJUnit.assertFalse(canPay(player, "Golgari Charm"));
    }

    @Test
    public void tapSacrificeCostCannotRepeatEvenWithMoreResources() {
        Player player = initAndCreateGame().getPlayers().get(1);
        Card source = addCard("Sol Ring", player);
        source.addSpellAbility(AbilityFactory.getAbility("AB$ Mana | Cost$ T Sac<1/Saproling> | Produced$ Any", source));
        addTokens("g_1_1_saproling", 2, player);
        AssertJUnit.assertFalse(canPay(player, "Golgari Charm"));
    }

    @Test
    public void activationLimitPreventsRepeatEvenWithMoreResources() {
        Player player = initAndCreateGame().getPlayers().get(1);
        Card source = addCard("Grizzly Bears", player);
        source.addSpellAbility(AbilityFactory.getAbility("AB$ Mana | Cost$ Sac<1/Saproling> | Produced$ Any | ActivationLimit$ 1", source));
        addTokens("g_1_1_saproling", 2, player);
        AssertJUnit.assertFalse(canPay(player, "Golgari Charm"));
    }
}
