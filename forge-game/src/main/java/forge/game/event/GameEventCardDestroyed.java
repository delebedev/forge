package forge.game.event;

import forge.game.card.Card;

/** Fired when a permanent is destroyed (GameAction.destroy). */
public record GameEventCardDestroyed(Card card, Card activator) implements GameEvent {

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
