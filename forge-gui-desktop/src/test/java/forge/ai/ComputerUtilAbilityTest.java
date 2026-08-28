package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.AlternativeCost;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ComputerUtilAbilityTest extends AITest {
    @Test
    public void spectacleOrderCandidateRestoresComparatorContract() {
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
        Comparator<SpellAbility> baseline = new AiController(player, game, AiVariant.BASELINE)
                .spellAbilityComparator();
        Assert.assertTrue(baseline.compare(spectacle, normal) > 0);
        Assert.assertTrue(baseline.compare(normal, spectacle) > 0);

        SpellAbility equalCostSpectacle = normal.copyWithDefinedCost(normal.getPayCosts());
        equalCostSpectacle.setAlternativeCost(AlternativeCost.Spectacle);
        Assert.assertEquals(baseline.compare(equalCostSpectacle, normal), 0);
        Assert.assertEquals(baseline.compare(normal, equalCostSpectacle), 0);

        Comparator<SpellAbility> candidate = new AiController(player, game, AiVariant.CANDIDATE)
                .spellAbilityComparator();
        Assert.assertTrue(candidate.compare(spectacle, normal) < 0);
        Assert.assertTrue(candidate.compare(normal, spectacle) > 0);
        Assert.assertEquals(candidate.compare(equalCostSpectacle, normal), 0);
        Assert.assertEquals(candidate.compare(normal, equalCostSpectacle), 0);

        List<SpellAbility> set = List.of(normal, spectacle, equalCostSpectacle);
        assertComparatorContract(candidate, set);
        for (int i = 0; i < 10; i++) {
            List<SpellAbility> sorted = new ArrayList<>(set);
            sorted.sort(candidate);
            Assert.assertEquals(sorted, List.of(spectacle, normal, equalCostSpectacle));
        }
    }

    private static void assertComparatorContract(final Comparator<SpellAbility> comparator,
            final List<SpellAbility> abilities) {
        for (SpellAbility a : abilities) {
            Assert.assertEquals(comparator.compare(a, a), 0);
            for (SpellAbility b : abilities) {
                Assert.assertEquals(Integer.signum(comparator.compare(a, b)),
                        -Integer.signum(comparator.compare(b, a)));
                for (SpellAbility c : abilities) {
                    if (comparator.compare(a, b) <= 0 && comparator.compare(b, c) <= 0) {
                        Assert.assertTrue(comparator.compare(a, c) <= 0);
                    }
                }
            }
        }
    }
}
