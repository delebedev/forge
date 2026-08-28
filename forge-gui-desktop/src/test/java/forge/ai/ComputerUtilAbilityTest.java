package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.AlternativeCost;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class ComputerUtilAbilityTest extends AITest {
    @Test
    public void spectaclePreferenceViolatesAntisymmetry() {
        Game game = initAndCreateGame();
        Player player = game.getPlayers().get(1);
        game.getPlayers().get(0).setLifeLostThisTurn(1);
        Card skewer = addCardToZone("Skewer the Critics", player, ZoneType.Hand);
        addCards("Mountain", 3, player);

        List<SpellAbility> abilities = ComputerUtilAbility.getSpellAbilities(
                ComputerUtilAbility.getAvailableCards(game, player), player);
        SpellAbility normal = abilities.stream()
                .filter(sa -> sa.getHostCard() == skewer && !sa.isSpectacle())
                .findFirst().orElseThrow();
        SpellAbility spectacle = abilities.stream()
                .filter(sa -> sa.getHostCard() == skewer && sa.isSpectacle())
                .findFirst().orElseThrow();

        Assert.assertTrue(normal.canPlay());
        Assert.assertTrue(spectacle.canPlay());
        Assert.assertTrue(ComputerUtilCost.canPayCost(normal, player, false));
        Assert.assertTrue(ComputerUtilCost.canPayCost(spectacle, player, false));
        Assert.assertTrue(ComputerUtilAbility.saEvaluator.compare(spectacle, normal) > 0);
        Assert.assertTrue(ComputerUtilAbility.saEvaluator.compare(normal, spectacle) > 0);

        SpellAbility equalCostSpectacle = normal.copyWithDefinedCost(normal.getPayCosts());
        equalCostSpectacle.setAlternativeCost(AlternativeCost.Spectacle);
        Assert.assertEquals(ComputerUtilAbility.saEvaluator.compare(equalCostSpectacle, normal), 0);
        Assert.assertEquals(ComputerUtilAbility.saEvaluator.compare(normal, equalCostSpectacle), 0);
    }
}
