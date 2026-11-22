package forge.game.event;

import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;

public record GameEventFlipCoin(PlayerView flipper, SpellAbilityView sa, boolean heads, boolean won, boolean startingToss) implements GameEvent {

    public GameEventFlipCoin() {
        this((PlayerView) null, (SpellAbilityView) null, false, false, false);
    }

    public GameEventFlipCoin(final PlayerView flipper, final boolean heads, final boolean startingToss) {
        this(flipper, null, heads, heads, startingToss);
    }

    public GameEventFlipCoin(final Player flipper, final SpellAbility sa, final boolean heads, final boolean won, final boolean startingToss) {
        this(PlayerView.get(flipper), SpellAbilityView.get(sa), heads, won, startingToss);
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return flipper + (heads ? " flipped heads" : " flipped tails");
    }
}
