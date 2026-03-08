package forge.game.event;

import forge.game.card.Card;

/**
 * Fired per card that is moved to the graveyard as part of a surveil action.
 * Complements GameEventSurveil (summary with counts) with per-card granularity
 * so consumers can distinguish surveil moves from mill moves (both Library→GY).
 *
 * @param card the card moved to graveyard
 * @param causeCard the host card of the SpellAbility that caused the surveil
 *                  (e.g. Wary Thespian for its ETB trigger). Used by the bridge
 *                  to set affectorId on ZoneTransfer annotations.
 */
public record GameEventCardSurveiled(Card card, Card causeCard) implements GameEvent {

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return "" + card.getController() + " surveiled " + card + " to graveyard";
    }
}
