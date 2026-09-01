package forge.game.event;

import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;
import forge.util.Lang;
import forge.util.TextUtil;

public record GameEventShuffle(PlayerView player, SpellAbilityView source) implements GameEvent {

    public GameEventShuffle(Player player) {
        this(player, null);
    }

    public GameEventShuffle(Player player, SpellAbility source) {
        this(PlayerView.get(player), SpellAbilityView.get(source));
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
        return TextUtil.concatWithSpace(player.toString(), Lang.joinVerb(player.getName(), "shuffle"), "their library");
    }
}
