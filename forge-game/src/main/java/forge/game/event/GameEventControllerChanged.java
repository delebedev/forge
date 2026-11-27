package forge.game.event;

import forge.game.card.Card;
import forge.game.player.Player;

/** Fired when a permanent's controller changes (steal, revert, aura-based control). */
public record GameEventControllerChanged(Card card, Player oldController, Player newController) implements GameEvent {

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return card.getName() + " controller: " + oldController.getName() + " -> " + newController.getName();
    }
}
