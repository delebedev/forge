package forge.game.event;

import forge.game.ability.ApiType;
import forge.game.spellability.SpellAbility;

/** Structural source classification for damage presentation. */
public enum DamageSourceKind {
    Combat,
    SpellOrAbility,
    Fight;

    public static DamageSourceKind from(final boolean isCombat, final SpellAbility cause) {
        if (isCombat) {
            return Combat;
        }
        return cause != null && cause.getApi() == ApiType.Fight ? Fight : SpellOrAbility;
    }
}
