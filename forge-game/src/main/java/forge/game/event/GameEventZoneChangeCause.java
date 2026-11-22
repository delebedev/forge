package forge.game.event;

import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.CostPaymentStack;

import java.io.Serializable;
import java.util.Map;

/** Immutable identity of the Forge operation responsible for a zone change. */
public record GameEventZoneChangeCause(
        int sourceCardId,
        int abilityId,
        int rootAbilityId,
        ApiType api,
        boolean costPayment,
        int stackAbilityId) implements Serializable {

    public GameEventZoneChangeCause(
            int sourceCardId,
            int abilityId,
            int rootAbilityId,
            ApiType api,
            boolean costPayment) {
        this(sourceCardId, abilityId, rootAbilityId, api, costPayment, 0);
    }

    public static GameEventZoneChangeCause from(SpellAbility cause) {
        return from(cause, null, null);
    }

    public static GameEventZoneChangeCause from(
            SpellAbility directCause,
            Map<AbilityKey, Object> params,
            CostPaymentStack.Entry payment) {
        SpellAbility effective = payment == null ? null : payment.payment().getAbility();
        if (effective == null) {
            effective = directCause;
        }
        if (effective == null && params != null && params.get(AbilityKey.StackSa) instanceof SpellAbility stackSa) {
            effective = stackSa;
        }
        if (effective == null && params != null && params.get(AbilityKey.Cause) instanceof SpellAbility paramCause) {
            effective = paramCause;
        }
        if (effective == null) {
            return null;
        }
        SpellAbility root = effective.getRootAbility();
        Card source = effective.getHostCard();
        SpellAbility stackSa = params != null && params.get(AbilityKey.StackSa) instanceof SpellAbility value
                ? value
                : null;
        Card stackSource = stackSa == null ? null : stackSa.getHostCard();
        int stackAbilityId;
        if (effective instanceof WrappedAbility wrapped) {
            stackAbilityId = wrapped.getWrappedAbility().getId();
        } else {
            stackAbilityId = source != null && stackSource != null && source.getId() == stackSource.getId()
                    ? stackSa.getId()
                    : 0;
        }
        return new GameEventZoneChangeCause(
                source == null ? 0 : source.getId(),
                effective.getId(),
                root == null ? effective.getId() : root.getId(),
                effective.getApi(),
                payment != null,
                stackAbilityId);
    }
}
