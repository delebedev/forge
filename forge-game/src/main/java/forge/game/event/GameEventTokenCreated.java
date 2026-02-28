package forge.game.event;

import forge.game.card.Card;

import java.util.List;

public record GameEventTokenCreated(List<Card> tokens) implements GameEvent {

    /** Backward-compatible no-arg constructor for callsites that don't have token refs. */
    public GameEventTokenCreated() {
        this(List.of());
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return "Token created (" + tokens.size() + " tokens)";
    }
}
