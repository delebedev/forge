package forge.player;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartWithList;
import forge.game.cost.CostTapType;
import forge.game.cost.PaymentDecision;
import forge.game.player.PlaySpellAbility;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.spellability.SpellAbility;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class PlaySpellAbilityTest {
    @DataProvider
    public Object[][] cardPayments() {
        return new Object[][] {
                { "1", 1, -1, false },
                { "1", 1, 0, false },
                { "2", 2, 1, false },
                { "2", 2, 2, true },
                { "X", 2, 1, false },
                { "X", 2, 2, true },
                { "0", 0, 0, true }
        };
    }

    @Test(dataProvider = "cardPayments")
    public void resolutionCostRequiresTheCalculatedNumberOfCards(
            String expression, int required, int selectedCount, boolean expectedPaid) throws ReflectiveOperationException {
        HumanControlledGame g = new HumanControlledGame();
        Card first = g.card(g.human, "First", "Types:Creature Bear", "PT:2/2");
        Card second = g.card(g.human, "Second", "Types:Creature Bear", "PT:2/2");
        CardCollection options = new CardCollection();
        options.add(first);
        options.add(second);
        CardCollection selected = selectedCount < 0 ? null : new CardCollection();
        if (selectedCount > 0) selected.add(first);
        if (selectedCount > 1) selected.add(second);
        PlayerControllerHuman controller = new PlayerControllerHuman(g.game, g.human, g.human.getLobbyPlayer()) {
            @Override
            public CardCollectionView chooseCardsForCost(CardCollectionView offered, SpellAbility ability,
                    CostPart part, int amount, boolean optional, String prompt) {
                assertThat(offered).containsExactlyElementsOf(options);
                assertThat(amount).isEqualTo(required);
                assertThat(optional).isTrue();
                return selected;
            }
        };
        AtomicInteger payments = new AtomicInteger();
        CostTapType cost = new CostTapType(expression, "Creature", "creatures", false) {
            @Override
            public boolean payAsDecided(Player payer, PaymentDecision decision, SpellAbility ability, boolean effect) {
                assertThat(decision.cards).containsExactlyElementsOf(selected);
                payments.incrementAndGet();
                return true;
            }
        };
        Method pay = PlaySpellAbility.class.getDeclaredMethod("payCostPart", PlayerController.class,
                Player.class, SpellAbility.class, boolean.class, CostPartWithList.class, int.class,
                CardCollectionView.class, String.class);
        pay.setAccessible(true);

        Object paid = pay.invoke(null, controller, g.human, new SpellAbility.EmptySa(first), true,
                cost, required, options, "tap");

        assertThat(paid).isEqualTo(expectedPaid);
        assertThat(payments.get()).isEqualTo(expectedPaid ? 1 : 0);
    }
}
