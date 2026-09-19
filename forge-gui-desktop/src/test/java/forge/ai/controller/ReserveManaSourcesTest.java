package forge.ai.controller;

import forge.ai.AiCardMemory;
import forge.ai.ComputerUtilMana;
import forge.ai.AiController;
import forge.ai.PlayerControllerAi;
import forge.ai.simulation.SimulationTest;
import forge.card.mana.ManaAtom;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.mana.Mana;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * reserveManaSources holds back the sources needed for a spell the AI wants to cast later.
 * getManaSourcesToPayCost hands back the sources of a payment it has already worked out, or null
 * when the cost can't be paid, so the answer only depends on whether such a payment exists.
 */
public class ReserveManaSourcesTest extends SimulationTest {

    private Game gameWith(String[] battlefield) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (String name : battlefield) {
            addCard(name, ai);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private SpellAbility spellInHand(Game game, String name) {
        Player ai = game.getPlayers().get(1);
        Card card = addCardToZone(name, ai, ZoneType.Hand);
        SpellAbility sa = card.getFirstSpellAbility();
        sa.setActivatingPlayer(ai);
        return sa;
    }

    private static AiController aiOf(Game game) {
        return ((PlayerControllerAi) game.getPlayers().get(1).getController()).getAi();
    }

    /**
     * Sol Ring makes two mana off one card, so three cards can cover a mana value four spell.
     * Reservation has to follow whether the payment exists, not how many cards it uses.
     */
    @Test
    public void reservesWhenOneSourceMakesSeveralMana() {
        Game game = gameWith(new String[] { "Sol Ring", "Mountain", "Mountain" });
        SpellAbility sa = spellInHand(game, "Hill Giant");

        AssertJUnit.assertTrue("Sol Ring plus two lands pays for a mana value four spell",
                aiOf(game).reserveManaSources(sa));
        AssertJUnit.assertFalse("the sources it will use should have been remembered",
                AiCardMemory.isMemorySetEmpty(game.getPlayers().get(1),
                        AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_MAIN2));
    }

    /** Nothing to reserve when the board can't pay for the spell at all. */
    @Test
    public void doesNotReserveWhatItCannotPay() {
        Game game = gameWith(new String[] { "Mountain", "Mountain" });
        SpellAbility sa = spellInHand(game, "Hill Giant");

        AssertJUnit.assertFalse("two lands cannot pay for a mana value four spell",
                aiOf(game).reserveManaSources(sa));
        AssertJUnit.assertTrue("nothing should have been remembered",
                AiCardMemory.isMemorySetEmpty(game.getPlayers().get(1),
                        AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_MAIN2));
    }

    /**
     * When chaining two spells the second can't be promised sources the first already needs. If
     * removing them leaves nothing, there is nothing to reserve.
     */
    @Test
    public void doesNotReserveSourcesTheChainedSpellNeeds() {
        Game game = gameWith(new String[] { "Mountain", "Mountain", "Mountain", "Mountain" });
        SpellAbility first = spellInHand(game, "Hill Giant");
        SpellAbility second = spellInHand(game, "Hill Giant");

        AssertJUnit.assertFalse("all four lands are spoken for by the first spell",
                aiOf(game).reserveManaSourcesForNextSpell(second, first));
    }

    @Test
    public void paysThroughNetPositiveManaFilter() {
        Game game = gameWith(new String[] { "Mountain", "Mossfire Valley" });
        SpellAbility sa = spellInHand(game, "Ruby Medallion");

        AssertJUnit.assertTrue("one mana can activate a filter that produces two",
                ComputerUtilMana.canPayManaCost(sa, game.getPlayers().get(1), 0, false));
    }

    @Test
    public void executesChainedNetPositiveManaFilters() {
        Game game = gameWith(new String[] { "Mountain", "Mossfire Valley", "Mossfire Valley" });
        Player ai = game.getPlayers().get(1);
        SpellAbility sa = spellInHand(game, "Luck Bobblehead");

        AssertJUnit.assertTrue("the free source should fund both filters in executable order",
                ComputerUtilMana.payManaCost(sa.getPayCosts(), ai, sa, false));
        AssertJUnit.assertTrue("every source in the chain should be consumed",
                ai.getCardsIn(ZoneType.Battlefield).stream().allMatch(Card::isTapped));
        AssertJUnit.assertTrue("the spell payment should leave no floating mana",
                ai.getManaPool().isEmpty());
    }

    @Test
    public void netPositiveManaFilterNeedsSeedMana() {
        Game game = gameWith(new String[] { "Mossfire Valley" });
        SpellAbility sa = spellInHand(game, "Ruby Medallion");

        AssertJUnit.assertFalse("a filter cannot pay its own activation cost",
                ComputerUtilMana.canPayManaCost(sa, game.getPlayers().get(1), 0, false));
    }

    @Test
    public void mutuallyDependentManaFiltersNeedSeedMana() {
        Game game = gameWith(new String[] { "Mossfire Valley", "Mossfire Valley" });
        SpellAbility sa = spellInHand(game, "Ruby Medallion");

        AssertJUnit.assertFalse("two filters cannot fund either activation without seed mana",
                ComputerUtilMana.canPayManaCost(sa, game.getPlayers().get(1), 0, false));
        AssertJUnit.assertTrue("an unavailable chain should not consume either filter",
                game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isTapped));
    }

    @Test
    public void floatingManaCanSeedNetPositiveManaFilter() {
        Game game = gameWith(new String[] { "Mossfire Valley" });
        Player ai = game.getPlayers().get(1);
        Card source = createCard("Mountain", ai);
        ai.getManaPool().addMana(new Mana((byte) ManaAtom.RED, source, null, ai));
        SpellAbility sa = spellInHand(game, "Ruby Medallion");

        AssertJUnit.assertTrue("floating mana can pay the filter activation cost",
                ComputerUtilMana.canPayManaCost(sa, ai, 0, false));
    }

    @Test
    public void netZeroManaFilterDoesNotIncreaseAvailableMana() {
        Game game = gameWith(new String[] { "Plains", "Plains", "Plains", "Plains", "Golden Egg" });
        SpellAbility sa = spellInHand(game, "Cavalier of Dawn");

        AssertJUnit.assertFalse("a filter that spends and produces one mana cannot pay a fifth mana",
                ComputerUtilMana.canPayManaCost(sa, game.getPlayers().get(1), 0, false));
    }
}
