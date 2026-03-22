package forge.game.event;

import forge.game.card.CardView;

/**
 * Fired when a mana ability finishes producing mana.
 */
public record GameEventManaAbilityActivated(CardView source, String produced) implements GameEvent {
    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return source + " produced " + produced;
    }
}
