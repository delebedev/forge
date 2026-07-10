package forge.game.event;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.ability.AbilityKey;
import forge.game.spellability.SpellAbility;
import forge.game.zone.CostPaymentStack;
import forge.game.zone.Zone;
import forge.game.zone.ZoneView;
import forge.util.TextUtil;

import java.util.Map;

public record GameEventCardChangeZone(
        CardView card,
        ZoneView from,
        ZoneView to,
        GameEventZoneChangeCause cause) implements GameEvent {

    public GameEventCardChangeZone(Card card, Zone zoneFrom, Zone zoneTo) {
        this(CardView.get(card),
             zoneFrom == null ? null : zoneFrom.getView(),
             zoneTo == null ? null : zoneTo.getView(),
             null);
    }

    public GameEventCardChangeZone(
            Card card,
            Zone zoneFrom,
            Zone zoneTo,
            SpellAbility directCause,
            Map<AbilityKey, Object> params,
            CostPaymentStack.Entry payment) {
        this(CardView.get(card),
             zoneFrom == null ? null : zoneFrom.getView(),
             zoneTo == null ? null : zoneTo.getView(),
             GameEventZoneChangeCause.from(directCause, params, payment));
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
        final String fromStr = from != null ? "" + from.zoneType() : "null";
        final String toStr = to != null ? "" + to.zoneType() : "null";
        return TextUtil.concatWithSpace("" + card, ":", TextUtil.enclosedBracket(fromStr), "->", TextUtil.enclosedBracket(toStr));
    }
}
