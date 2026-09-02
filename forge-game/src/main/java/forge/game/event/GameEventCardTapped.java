package forge.game.event;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;

public record GameEventCardTapped(CardView card, boolean tapped, SpellAbilityView cause,
                                  boolean spellCause, int rootAbilityId) implements GameEvent {

    public GameEventCardTapped(Card card, boolean tapped) {
        this(CardView.get(card), tapped, null, false, 0);
    }

    public GameEventCardTapped(Card card, boolean tapped, SpellAbility cause) {
        this(CardView.get(card), tapped, SpellAbilityView.get(cause),
                cause != null && cause.getRootAbility().isSpell(),
                cause == null ? 0 : cause.getRootAbility().getId());
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
        return "" + card.getController() + (tapped ? " tapped " : " untapped ") + card;
    }
}
