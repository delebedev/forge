package forge.game.event;

import forge.game.card.Card;

public record GameEventExtrinsicKeywordAdded(Card card, String keyword, long timestamp, long staticId)
        implements GameEvent {
    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
    @Override
    public String toString() {
        return card.getName() + " gained extrinsic keyword " + keyword;
    }
}
