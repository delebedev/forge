package forge.ai;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

final class ResearchDecisionLogger {
    private static final String LOG_PATH = System.getenv("FORGE_AI_DECISION_LOG");
    private static final String RUN_ID = System.getenv("FORGE_AI_RUN_ID");
    private static final String GAME_ID_BASE = System.getenv("FORGE_AI_GAME_ID");
    private static final String PAIR_ID_BASE = System.getenv("FORGE_AI_PAIR_ID");
    private static final String SEED = System.getenv("FORGE_AI_SEED");
    private static final String PILOT = System.getenv("FORGE_AI_PILOT");
    private static final String OPPONENT = System.getenv("FORGE_AI_OPPONENT");

    // Tracks game index across calls in a single JVM (for batch mode `-n N`).
    // Increments when we observe a new Game instance.
    private static int gameIndex = -1;
    private static int lastGameIdentity = 0;

    private ResearchDecisionLogger() {
    }

    private static synchronized int gameIndexFor(Game game) {
        int identity = System.identityHashCode(game);
        if (identity != lastGameIdentity) {
            gameIndex++;
            lastGameIdentity = identity;
        }
        return gameIndex;
    }

    static String gameIdFor(Game game) {
        if (GAME_ID_BASE == null) {
            return null;
        }
        return GAME_ID_BASE + "-g" + gameIndexFor(game);
    }

    private static String pairIdFor(Game game) {
        if (PAIR_ID_BASE == null) {
            return null;
        }
        return PAIR_ID_BASE + "-g" + gameIndexFor(game);
    }

    static void logPriorityDecision(Game game, Player player, List<SpellAbility> candidates, SpellAbility chosen,
            boolean policyUsed, double policyScore, int policySeen, boolean neuralUsed, double neuralScore, double neuralMargin,
            Map<SpellAbility, AiPlayDecision> evaluatedReasons) {
        if (LOG_PATH == null || LOG_PATH.isEmpty()) {
            return;
        }

        String json = buildPriorityDecisionJson(game, player, candidates, chosen, policyUsed, policyScore, policySeen,
                neuralUsed, neuralScore, neuralMargin, evaluatedReasons);
        synchronized (ResearchDecisionLogger.class) {
            try (FileWriter writer = new FileWriter(LOG_PATH, true)) {
                writer.write(json);
                writer.write(System.lineSeparator());
            } catch (IOException ignored) {
                // Research logging must never affect game execution.
            }
        }
    }

    static String buildPriorityDecisionJson(Game game, Player player, List<SpellAbility> candidates, SpellAbility chosen,
            boolean policyUsed, double policyScore, int policySeen, boolean neuralUsed, double neuralScore, double neuralMargin,
            Map<SpellAbility, AiPlayDecision> evaluatedReasons) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendField(sb, "schema", "priority_decision_v4");
        appendNumberField(sb, "schema_version", 4);
        appendField(sb, "kind", "priority");
        appendField(sb, "run_id", RUN_ID);
        appendField(sb, "game_id", gameIdFor(game));
        appendField(sb, "pair_id", pairIdFor(game));
        appendField(sb, "seed", SEED);
        appendField(sb, "pilot", PILOT);
        appendField(sb, "opponent", OPPONENT);
        appendField(sb, "player", player.getName());
        appendField(sb, "active_player", playerName(game.getPhaseHandler().getPlayerTurn()));
        appendField(sb, "priority_player", playerName(game.getPhaseHandler().getPriorityPlayer()));
        appendField(sb, "next_turn_player", playerName(game.getPhaseHandler().getNextTurn()));
        appendField(sb, "phase", game.getPhaseHandler().getPhase().name());
        appendNumberField(sb, "turn", game.getPhaseHandler().getTurn());
        // schema_version 4: RNG draws consumed so far this game. Two arms that
        // agree on this at every decision compared share a random state, so a
        // divergence between them is a policy difference; a mismatch means the
        // stream was displaced and the divergence cannot be attributed.
        if (forge.util.CountingRandom.instrumented()) {
            appendNumberField(sb, "draws", (int) forge.util.CountingRandom.draws());
        }
        appendNumberField(sb, "stack_size", game.getStack().size());
        appendNumberField(sb, "player_life", player.getLife());
        appendNumberField(sb, "player_hand_size", zoneSize(player, ZoneType.Hand));
        appendNumberField(sb, "player_library_size", zoneSize(player, ZoneType.Library));
        appendNumberField(sb, "player_battlefield_size", zoneSize(player, ZoneType.Battlefield));
        appendBoardSummary(sb, "player", player);
        appendNumberField(sb, "player_available_mana_estimate", ComputerUtilMana.getAvailableManaEstimate(player, true));
        Player opponent = firstOpponent(player);
        appendNumberField(sb, "opponent_life", opponent == null ? -1 : opponent.getLife());
        appendNumberField(sb, "opponent_hand_size", opponent == null ? -1 : zoneSize(opponent, ZoneType.Hand));
        appendNumberField(sb, "opponent_battlefield_size", opponent == null ? -1 : zoneSize(opponent, ZoneType.Battlefield));
        if (opponent != null) {
            appendBoardSummary(sb, "opponent", opponent);
        }
        appendBoolField(sb, "policy_used", policyUsed);
        appendField(sb, "policy_score", String.valueOf(policyScore));
        appendNumberField(sb, "policy_seen", policySeen);
        appendBoolField(sb, "neural_used", neuralUsed);
        appendField(sb, "neural_score", String.valueOf(neuralScore));
        appendField(sb, "neural_margin", String.valueOf(neuralMargin));
        appendNumberField(sb, "chosen_candidate_index", chosenCandidateIndex(candidates, chosen));
        appendField(sb, "chosen", describe(chosen));
        sb.append(",\"candidates\":[");
        appendCandidate(sb, 0, null, "");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append(',');
            appendCandidate(sb, i + 1, candidates.get(i), aiPlayDecisionFor(evaluatedReasons, candidates.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    // The AiPlayDecision the greedy scan computed for this candidate, or "" when
    // it was never evaluated. schema_version 2: only the prefix the scan reached
    // before the first WillPlay carries a decision (see AiController); candidates
    // after the winner — and the synthetic PASS candidate — are blank by design,
    // not missing data. Present-and-blank so every candidate has the same keys.
    private static String aiPlayDecisionFor(Map<SpellAbility, AiPlayDecision> evaluatedReasons, SpellAbility sa) {
        if (evaluatedReasons == null || sa == null) {
            return "";
        }
        AiPlayDecision decision = evaluatedReasons.get(sa);
        return decision == null ? "" : decision.name();
    }

    private static void appendField(StringBuilder sb, String name, String value) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":\"").append(escape(value)).append('"');
    }

    private static void appendNumberField(StringBuilder sb, String name, int value) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":").append(value);
    }

    private static void appendBoolField(StringBuilder sb, String name, boolean value) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":").append(value);
    }

    private static void appendCandidate(StringBuilder parent, int index, SpellAbility sa, String aiPlayDecision) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendNumberField(sb, "index", index);
        appendField(sb, "action_schema", "priority_action_v2");
        appendField(sb, "ai_play_decision", aiPlayDecision);
        appendField(sb, "action_id", actionId(sa));
        appendField(sb, "id", describe(sa));
        appendField(sb, "host_card", hostName(sa));
        appendField(sb, "api", sa == null ? "PASS" : String.valueOf(sa.getApi()));
        appendField(sb, "zone", hostZone(sa));
        appendField(sb, "mana_cost", manaCost(sa));
        appendField(sb, "pay_cost", payCost(sa));
        appendNumberField(sb, "mana_value", manaValue(sa));
        appendBoolField(sb, "is_spell", sa != null && sa.isSpell());
        appendBoolField(sb, "is_ability", sa != null && sa.isAbility());
        appendBoolField(sb, "is_land_ability", sa != null && sa.isLandAbility());
        appendBoolField(sb, "uses_targeting", sa != null && sa.usesTargeting());
        appendField(sb, "text", sa == null ? "PASS" : sa.toString());
        sb.append('}');
        parent.append(sb);
    }

    private static String describe(SpellAbility sa) {
        if (sa == null) {
            return "PASS";
        }
        String host = sa.getHostCard() == null ? "NO_CARD" : sa.getHostCard().getName();
        return host + "|" + sa.getApi() + "|" + sa.toString();
    }

    private static int chosenIndex(List<SpellAbility> candidates, SpellAbility chosen) {
        if (chosen == null) {
            return -1;
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i) == chosen) {
                return i;
            }
        }
        return -1;
    }

    private static int chosenCandidateIndex(List<SpellAbility> candidates, SpellAbility chosen) {
        if (chosen == null) {
            return 0;
        }
        int index = chosenIndex(candidates, chosen);
        return index < 0 ? -1 : index + 1;
    }

    static Player firstOpponent(Player player) {
        for (Player opponent : player.getOpponents()) {
            return opponent;
        }
        return null;
    }

    private static String playerName(Player player) {
        return player == null ? null : player.getName();
    }

    static int zoneSize(Player player, ZoneType zoneType) {
        return player.getCardsIn(zoneType).size();
    }

    private static String hostName(SpellAbility sa) {
        Card host = sa == null ? null : sa.getHostCard();
        return host == null ? "NO_CARD" : host.getName();
    }

    private static String hostZone(SpellAbility sa) {
        Card host = sa == null ? null : sa.getHostCard();
        if (host == null || host.getZone() == null) {
            return "NO_ZONE";
        }
        return host.getZone().getZoneType().name();
    }

    private static String manaCost(SpellAbility sa) {
        Card host = sa == null ? null : sa.getHostCard();
        if (host == null || host.getManaCost() == null) {
            return "";
        }
        return host.getManaCost().getSimpleString();
    }

    private static String payCost(SpellAbility sa) {
        if (sa == null || sa.getPayCosts() == null) {
            return "";
        }
        return sa.getPayCosts().toSimpleString();
    }

    private static int manaValue(SpellAbility sa) {
        Card host = sa == null ? null : sa.getHostCard();
        return host == null ? -1 : host.getCMC();
    }

    private static String actionId(SpellAbility sa) {
        return String.join("|", sa == null ? "PASS" : String.valueOf(sa.getApi()), hostName(sa), hostZone(sa), manaCost(sa),
                payCost(sa), String.valueOf(sa != null && sa.isSpell()), String.valueOf(sa != null && sa.isAbility()),
                String.valueOf(sa != null && sa.isLandAbility()), String.valueOf(sa != null && sa.usesTargeting()));
    }

    /** Battlefield census shared by the JSON decision log and {@link ResearchValueNet}'s feature extraction. */
    static final class BoardSummary {
        final int creatures;
        final int tappedCreatures;
        final int creaturePower;
        final int creatureToughness;
        final int maxCreaturePower;
        final int artifacts;
        final int enchantments;
        final int planeswalkers;
        final int lands;

        BoardSummary(int creatures, int tappedCreatures, int creaturePower, int creatureToughness,
                int maxCreaturePower, int artifacts, int enchantments, int planeswalkers, int lands) {
            this.creatures = creatures;
            this.tappedCreatures = tappedCreatures;
            this.creaturePower = creaturePower;
            this.creatureToughness = creatureToughness;
            this.maxCreaturePower = maxCreaturePower;
            this.artifacts = artifacts;
            this.enchantments = enchantments;
            this.planeswalkers = planeswalkers;
            this.lands = lands;
        }
    }

    static BoardSummary boardSummary(Player player) {
        int creatures = 0;
        int tappedCreatures = 0;
        int totalPower = 0;
        int totalToughness = 0;
        int maxPower = 0;
        int artifacts = 0;
        int enchantments = 0;
        int planeswalkers = 0;
        int lands = 0;
        for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
            if (card.isCreature()) {
                creatures++;
                if (card.isTapped()) {
                    tappedCreatures++;
                }
                totalPower += card.getNetPower();
                totalToughness += card.getNetToughness();
                maxPower = Math.max(maxPower, card.getNetPower());
            }
            if (card.isArtifact()) {
                artifacts++;
            }
            if (card.isEnchantment()) {
                enchantments++;
            }
            if (card.isPlaneswalker()) {
                planeswalkers++;
            }
            if (card.isLand()) {
                lands++;
            }
        }
        return new BoardSummary(creatures, tappedCreatures, totalPower, totalToughness, maxPower,
                artifacts, enchantments, planeswalkers, lands);
    }

    private static void appendBoardSummary(StringBuilder sb, String prefix, Player player) {
        BoardSummary summary = boardSummary(player);
        appendNumberField(sb, prefix + "_creatures", summary.creatures);
        appendNumberField(sb, prefix + "_tapped_creatures", summary.tappedCreatures);
        appendNumberField(sb, prefix + "_creature_power", summary.creaturePower);
        appendNumberField(sb, prefix + "_creature_toughness", summary.creatureToughness);
        appendNumberField(sb, prefix + "_max_creature_power", summary.maxCreaturePower);
        appendNumberField(sb, prefix + "_artifacts", summary.artifacts);
        appendNumberField(sb, prefix + "_enchantments", summary.enchantments);
        appendNumberField(sb, prefix + "_planeswalkers", summary.planeswalkers);
        appendNumberField(sb, prefix + "_lands", summary.lands);
    }

    /**
     * Inputs the attack controller collapses into one aggression level, captured
     * where that collapse happens. See docs/forge-ai.md.
     */
    static final class AttackTelemetry {
        int aggression = -1;
        double ratioDiff;
        int outNumber;
        double aiLifeToPlayerDamageRatio;
        double humanLifeToDamageRatio;
        double turnsUntilDeathByUnblockable;
        boolean attritional;
        boolean unblockableOnly;
        boolean playAggro;
    }

    /**
     * Which escalation tier of {@code AiBlockController.assignBlockers} produced the
     * final assignment. Later tiers discard every earlier assignment, so the tier —
     * not the pass sequence — is what identifies the decision.
     */
    static final class BlockTelemetry {
        int tier = 1;
        int resets;
        boolean dangerAfterFirstPass;
        boolean seriousDanger;
        boolean fogEffect;
    }

    private static boolean enabled() {
        return LOG_PATH != null && !LOG_PATH.isEmpty();
    }

    private static void write(String json) {
        synchronized (ResearchDecisionLogger.class) {
            try (FileWriter writer = new FileWriter(LOG_PATH, true)) {
                writer.write(json);
                writer.write(System.lineSeparator());
            } catch (IOException ignored) {
                // Research logging must never affect game execution.
            }
        }
    }

    // Combat records deliberately carry no "candidates" key: bench triage builds its
    // candidate_events view by UNNESTing that column, so its absence drops these rows
    // from the priority grain instead of corrupting it.
    private static void appendCombatEnvelope(StringBuilder sb, String schema, String kind, Game game, Player player) {
        appendField(sb, "schema", schema);
        appendNumberField(sb, "schema_version", 1);
        appendField(sb, "kind", kind);
        appendField(sb, "run_id", RUN_ID);
        appendField(sb, "game_id", gameIdFor(game));
        appendField(sb, "pair_id", pairIdFor(game));
        appendField(sb, "seed", SEED);
        appendField(sb, "pilot", PILOT);
        appendField(sb, "opponent", OPPONENT);
        appendField(sb, "player", player.getName());
        appendField(sb, "phase", game.getPhaseHandler().getPhase().name());
        appendNumberField(sb, "turn", game.getPhaseHandler().getTurn());
        appendNumberField(sb, "player_life", player.getLife());
        Player opp = firstOpponent(player);
        appendNumberField(sb, "opponent_life", opp == null ? -1 : opp.getLife());
        // Posture is a per-seat treatment variable that no other artifact records:
        // pilotsNonAggroDeck is inferred once from the deck name and average CMC.
        appendBoolField(sb, "pilots_non_aggro_deck", pilotsNonAggroDeck(player));
    }

    private static boolean pilotsNonAggroDeck(Player player) {
        return player.getController() instanceof PlayerControllerAi pc && pc.pilotsNonAggroDeck();
    }

    static void logAttackDecision(Game game, Player player, AttackTelemetry telemetry, Combat combat,
            List<Card> available) {
        if (!enabled() || telemetry == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendCombatEnvelope(sb, "combat_attack_v1", "combat_attack", game, player);
        appendNumberField(sb, "aggression", telemetry.aggression);
        appendField(sb, "ratio_diff", String.valueOf(telemetry.ratioDiff));
        appendNumberField(sb, "out_number", telemetry.outNumber);
        appendField(sb, "ai_life_to_player_damage_ratio", String.valueOf(telemetry.aiLifeToPlayerDamageRatio));
        appendField(sb, "human_life_to_damage_ratio", String.valueOf(telemetry.humanLifeToDamageRatio));
        appendField(sb, "turns_until_death_by_unblockable", String.valueOf(telemetry.turnsUntilDeathByUnblockable));
        appendBoolField(sb, "attritional", telemetry.attritional);
        appendBoolField(sb, "unblockable_only", telemetry.unblockableOnly);
        appendBoolField(sb, "play_aggro", telemetry.playAggro);
        List<Card> attacked = combat == null ? List.of() : combat.getAttackers();
        appendNumberField(sb, "attacker_count", attacked.size());
        appendNumberField(sb, "available_count", available == null ? -1 : available.size());
        appendCreatureArray(sb, "attackers", attacked);
        appendCreatureArray(sb, "available", available);
        sb.append('}');
        write(sb.toString());
    }

    static void logBlockDecision(Game game, Player player, BlockTelemetry telemetry, Combat combat,
            List<Card> availableBlockers) {
        if (!enabled() || telemetry == null || combat == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendCombatEnvelope(sb, "combat_block_v1", "combat_block", game, player);
        appendNumberField(sb, "tier", telemetry.tier);
        appendNumberField(sb, "resets", telemetry.resets);
        appendBoolField(sb, "danger_after_first_pass", telemetry.dangerAfterFirstPass);
        appendBoolField(sb, "serious_danger", telemetry.seriousDanger);
        appendBoolField(sb, "fog_effect", telemetry.fogEffect);
        appendNumberField(sb, "available_blocker_count", availableBlockers == null ? -1 : availableBlockers.size());
        appendCreatureArray(sb, "available_blockers", availableBlockers);
        sb.append(",\"blocks\":[");
        boolean first = true;
        for (Card attacker : combat.getAttackers()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            StringBuilder entry = new StringBuilder();
            entry.append('{');
            appendCreatureFields(entry, "attacker", attacker);
            GameEntity defender = combat.getDefenderByAttacker(attacker);
            appendField(entry, "defender", defender == null ? "" : defender.getName());
            List<Card> blockers = combat.getBlockers(attacker);
            appendNumberField(entry, "blocker_count", blockers.size());
            appendCreatureArray(entry, "blockers", blockers);
            entry.append('}');
            sb.append(entry);
        }
        sb.append("]}");
        write(sb.toString());
    }

    private static void appendCreatureArray(StringBuilder sb, String name, List<Card> cards) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":[");
        if (cards != null) {
            for (int i = 0; i < cards.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                StringBuilder entry = new StringBuilder();
                entry.append('{');
                appendCreatureFields(entry, null, cards.get(i));
                entry.append('}');
                sb.append(entry);
            }
        }
        sb.append(']');
    }

    // Enough to re-enumerate a block assignment offline: identity, body, evasion.
    private static void appendCreatureFields(StringBuilder sb, String prefix, Card card) {
        String p = prefix == null ? "" : prefix + "_";
        appendField(sb, p + "name", card == null ? "" : card.getName());
        appendNumberField(sb, p + "power", card == null ? 0 : card.getNetPower());
        appendNumberField(sb, p + "toughness", card == null ? 0 : card.getNetToughness());
        appendNumberField(sb, p + "damage", card == null ? 0 : card.getDamage());
        appendField(sb, p + "keywords", card == null ? "" : combatKeywords(card));
    }

    private static final Keyword[] COMBAT_KEYWORDS = {
        Keyword.FLYING, Keyword.REACH, Keyword.TRAMPLE, Keyword.FIRST_STRIKE, Keyword.DOUBLE_STRIKE,
        Keyword.DEATHTOUCH, Keyword.MENACE, Keyword.VIGILANCE, Keyword.INDESTRUCTIBLE, Keyword.LIFELINK,
    };

    private static String combatKeywords(Card card) {
        StringBuilder sb = new StringBuilder();
        for (Keyword keyword : COMBAT_KEYWORDS) {
            if (card.hasKeyword(keyword)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(keyword.name());
            }
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
