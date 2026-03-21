package forge.game.event;

import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.spellability.SpellAbilityView;
import forge.game.spellability.StackItemView;
import forge.game.spellability.TargetChoices;

import java.util.ArrayList;
import java.util.List;

public record GameEventSpellAbilityCast(
    SpellAbilityView sa,
    StackItemView si,
    int stackIndex,
    String targetDescription,
    List<ManaPaymentInfo> manaPayments
) implements GameEvent {

    /** Mana globe used to pay for this spell. */
    public record ManaPaymentInfo(int sourceCardId, byte color) {}

    public GameEventSpellAbilityCast(SpellAbility sa, SpellAbilityStackInstance si, int stackIndex) {
        this(SpellAbilityView.get(sa), StackItemView.get(si), stackIndex,
             computeTargetDescription(sa), extractManaPayments(sa));
    }

    private static List<ManaPaymentInfo> extractManaPayments(SpellAbility sa) {
        List<ManaPaymentInfo> payments = new ArrayList<>();
        for (var mana : sa.getPayingMana()) {
            var source = mana.getSourceCard();
            if (source != null) {
                payments.add(new ManaPaymentInfo(source.getId(), mana.getColor()));
            }
        }
        return List.copyOf(payments);
    }

    private static String computeTargetDescription(SpellAbility sa) {
        if (sa.getTargetRestrictions() == null) return null;
        StringBuilder sb = new StringBuilder();
        for (TargetChoices ch : sa.getAllTargetChoices()) {
            if (ch != null) { if (sb.length() > 0) sb.append(" "); sb.append(ch); }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /* (non-Javadoc)
     * @see forge.game.event.GameEvent#visit(forge.game.event.IGameEventVisitor)
     */
    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "" + si.getActivatingPlayer() + (sa.isSpell() ? " cast " : si.isTrigger() ? " triggered " : " activated ") + sa;
    }
}
