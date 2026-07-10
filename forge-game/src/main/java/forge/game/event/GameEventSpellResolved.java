package forge.game.event;

import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;

public record GameEventSpellResolved(
        SpellAbilityView spell,
        boolean hasFizzled,
        String stackDescription,
        GameEventZoneChangeCause cause) implements GameEvent {

    public GameEventSpellResolved(SpellAbilityView spell, boolean hasFizzled, String stackDescription) {
        this(spell, hasFizzled, stackDescription, null);
    }

    public GameEventSpellResolved(SpellAbility spell, boolean hasFizzled) {
        this(SpellAbilityView.get(spell), hasFizzled, spell.getStackDescription(), GameEventZoneChangeCause.from(spell));
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
        return "Stack resolved " + spell + (hasFizzled ? " (fizzled)" : "");
    }
}
