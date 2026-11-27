package forge.game.event;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.spellability.SpellAbility;

/** Fired when a permanent is destroyed (GameAction.destroy). */
public record GameEventCardDestroyed(
        CardView card,
        CardView activator,
        GameEventZoneChangeCause cause) implements GameEvent {

    public GameEventCardDestroyed(Card card, Card activator) {
        this(CardView.get(card), CardView.get(activator), null);
    }

    public GameEventCardDestroyed(Card card, SpellAbility source) {
        this(
                CardView.get(card),
                source == null ? null : CardView.get(source.getHostCard()),
                GameEventZoneChangeCause.from(source));
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        String cardName = card != null ? card.getName() : "?";
        String causeName = activator != null ? activator.getName() : "?";
        return cardName + " destroyed by " + causeName;
    }
}
