package forge.game.event;

import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;

public record GameEventFlipCoin(PlayerView player, SpellAbilityView sa, boolean won) implements GameEvent {

    public GameEventFlipCoin() {
        this((PlayerView) null, (SpellAbilityView) null, false);
    }

    public GameEventFlipCoin(final Player player, final SpellAbility sa, final boolean won) {
        this(PlayerView.get(player), SpellAbilityView.get(sa), won);
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
        return "Flipped coin";
    }
}
