package forge.ai;

import forge.card.mana.ManaCost;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.cost.Cost;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.List;

/** Candidate-only ordering for an affordable two-spell lethal burn bundle. */
final class ResearchTurnBundlePriority {
    private ResearchTurnBundlePriority() {
    }

    static void prioritize(final List<SpellAbility> abilities, final Game game, final Player player) {
        if (!game.getStack().isEmpty()
                || !game.getPhaseHandler().isPlayerTurn(player)
                || !game.getPhaseHandler().getPhase().isMain()) {
            return;
        }

        for (final Player opponent : player.getOpponents()) {
            for (int firstIndex = 0; firstIndex < abilities.size(); firstIndex++) {
                final SpellAbility first = abilities.get(firstIndex);
                final int firstDamage = fixedPlayerDamage(first, player, opponent);
                if (firstDamage < 0) {
                    continue;
                }
                for (int secondIndex = firstIndex + 1; secondIndex < abilities.size(); secondIndex++) {
                    final SpellAbility second = abilities.get(secondIndex);
                    final int secondDamage = fixedPlayerDamage(second, player, opponent);
                    if (secondDamage < 0 || firstDamage + secondDamage < opponent.getLife()) {
                        continue;
                    }
                    final ManaCost total = ManaCost.combine(
                            first.getPayCosts().getTotalMana(), second.getPayCosts().getTotalMana());
                    final SpellAbility combined = second.copyWithDefinedCost(new Cost(total, false));
                    if (ComputerUtilMana.canPayManaCost(combined, player, 0, false)) {
                        abilities.remove(firstIndex);
                        abilities.add(0, first);
                        return;
                    }
                }
            }
        }
    }

    private static int fixedPlayerDamage(final SpellAbility ability, final Player player, final Player opponent) {
        if (ability.getApi() != ApiType.DealDamage
                || !ability.isSpell()
                || !player.getZone(ZoneType.Hand).contains(ability.getHostCard())
                || ability.getAlternativeCost() != null
                || ability.getPayCosts() == null
                || !ability.getPayCosts().isOnlyManaCost()
                || ability.getPayCosts().getTotalMana().countX() != 0
                || !ability.usesTargeting()
                || !ability.canTarget(opponent)) {
            return -1;
        }
        final String value = ability.getParamOrDefault("NumDmg", "");
        if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        return Integer.parseInt(value);
    }
}
