package forge.game.event;

import forge.game.card.CardView;
import forge.game.player.PlayerView;

public record GameEventPlayerDamaged(PlayerView target, CardView source, int amount, DamageSourceKind sourceKind, boolean infect) implements GameEvent {

    public GameEventPlayerDamaged(PlayerView target, CardView source, int amount, boolean combat, boolean infect) {
        this(target, source, amount, combat ? DamageSourceKind.Combat : DamageSourceKind.SpellOrAbility, infect);
    }

    public boolean combat() {
        return sourceKind == DamageSourceKind.Combat;
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
        return "" + target + " took " + amount + (infect ? " infect" : combat() ? " combat" : "") + " damage from " + source;
    }
}
