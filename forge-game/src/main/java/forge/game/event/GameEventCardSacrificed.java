package forge.game.event;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.spellability.SpellAbility;
import forge.game.zone.CostPaymentStack;

public record GameEventCardSacrificed(CardView card, GameEventZoneChangeCause cause) implements GameEvent {

    public GameEventCardSacrificed(CardView card) {
        this(card, null);
    }

    public GameEventCardSacrificed(Card card, SpellAbility source, CostPaymentStack.Entry payment) {
        this(CardView.get(card), GameEventZoneChangeCause.from(source, null, payment));
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "" + card.getController() + " sacrificed " + card;
    }
}
