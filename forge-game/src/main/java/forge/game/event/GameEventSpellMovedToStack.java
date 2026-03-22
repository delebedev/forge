package forge.game.event;

import forge.game.card.CardView;

/** Fired when a spell is placed on the stack, before costs are paid. */
public record GameEventSpellMovedToStack(CardView card) implements GameEvent {
    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return card + " moved to stack";
    }
}
