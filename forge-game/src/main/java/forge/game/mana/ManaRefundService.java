package forge.game.mana;

import forge.game.event.EventValueChangeType;
import forge.game.event.GameEventZone;
import forge.game.player.Player;
import forge.game.player.PlayerCollection;
import forge.game.spellability.SpellAbility;
import forge.game.zone.MagicStack;
import forge.game.zone.ZoneType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ManaRefundService {

    private final SpellAbility sa;

    public ManaRefundService(SpellAbility sa) {
        this.sa = sa;
    }

    public void refundManaPaid() {
        PlayerCollection payers = new PlayerCollection(sa.getActivatingPlayer());

        // move non-undoable paying mana back to floating
        for (Map.Entry<Player, List<Mana>> e : sa.getPayingMana().stream().collect(Collectors.groupingBy(Mana::getPlayer)).entrySet()) {
            e.getKey().getManaPool().addMana(e.getValue());
            payers.add(e.getKey());
        }

        sa.getPayingMana().clear();

        List<SpellAbility> payingAbilities = sa.getPayingManaAbilities();

        // start with the most recent
        Collections.reverse(payingAbilities);

        final MagicStack stack = sa.getHostCard().getGame().getStack();
        for (final SpellAbility am : payingAbilities) {
            // What if am is owned by a different player?
            if (am.undo()) {
                // Refund what paid for this ability before reversing an earlier one: the earlier
                // ability's mana may have paid this one's cost (a Signet paying a filter land)
                // and must be back in the pool for that ability to account for it.
                new ManaRefundService(am).refundManaPaid();
            } else {
                // CR 733.1: a mana ability that could not be reversed keeps the mana
                // spent on it, so the abilities that paid for it are not reversed either.
                for (final Mana pay : am.getPayingMana()) {
                    if (pay.getManaAbility() != null) {
                        stack.clearUndoStack(pay.getManaAbility().getSourceSA());
                    }
                }
            }
            stack.clearUndoStack(am);
        }

        payingAbilities.clear();

        // update battlefield of all activating players - to redraw cards used to pay mana as untapped
        for (Player p : payers) {
            p.getGame().fireEvent(new GameEventZone(ZoneType.Battlefield, p, EventValueChangeType.ComplexUpdate, null));
        }
    }
}
