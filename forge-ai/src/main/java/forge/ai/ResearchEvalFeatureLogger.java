package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Once per turn, records each player's battlefield creature-evaluator
 * feature vector as one JSONL record per player. Features come from
 * ResearchCollectingCreatureEvaluator (stock arithmetic), so
 * sum(features) == sum of stock creature scores — reconcile_delta is a
 * tripwire and must always be 0.
 *
 * Sampled at the first priority decision observed for a (game, turn), i.e.
 * a stable early-turn snapshot. Enabled by FORGE_AI_EVAL_FEATURES_LOG;
 * inert when unset. Logging must never affect game execution.
 */
final class ResearchEvalFeatureLogger {
    private static final String LOG_PATH = System.getenv("FORGE_AI_EVAL_FEATURES_LOG");
    private static final String RUN_ID = System.getenv("FORGE_AI_RUN_ID");
    private static final String SEED = System.getenv("FORGE_AI_SEED");

    private static int lastGameIdentity = 0;
    private static int lastTurn = -1;

    private ResearchEvalFeatureLogger() {
    }

    static void logTurnFeatures(Game game, Player decidingPlayer) {
        if (LOG_PATH == null || LOG_PATH.isEmpty()) {
            return;
        }
        int turn = game.getPhaseHandler().getTurn();
        synchronized (ResearchEvalFeatureLogger.class) {
            int identity = System.identityHashCode(game);
            if (identity == lastGameIdentity && turn == lastTurn) {
                return;
            }
            lastGameIdentity = identity;
            lastTurn = turn;
            StringBuilder out = new StringBuilder();
            int seat = 1;
            for (Player player : game.getPlayers()) {
                out.append(buildRecordJson(game, player, seat, turn)).append(System.lineSeparator());
                seat++;
            }
            try (FileWriter writer = new FileWriter(LOG_PATH, true)) {
                writer.write(out.toString());
            } catch (IOException ignored) {
                // Research logging must never affect game execution.
            }
        }
    }

    static String buildRecordJson(Game game, Player player, int seat, int turn) {
        ResearchCollectingCreatureEvaluator collector = new ResearchCollectingCreatureEvaluator();
        // Independent stock evaluation for the reconcile tripwire — never the
        // context-routed ComputerUtilCard funnel, which may be weighted.
        CreatureEvaluator stock = new CreatureEvaluator();
        Map<String, Long> features = new LinkedHashMap<>();
        long scoreTotal = 0;
        int creatures = 0;
        for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
            if (!card.isCreature()) {
                continue;
            }
            creatures++;
            Map<String, Long> cardFeatures = collector.featuresFor(card);
            for (Map.Entry<String, Long> e : cardFeatures.entrySet()) {
                features.merge(e.getKey(), e.getValue(), Long::sum);
            }
            scoreTotal += stock.evaluateCreature(card);
        }
        long featureSum = features.values().stream().mapToLong(Long::longValue).sum();

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendString(sb, "schema", "eval_features_v1");
        appendNumber(sb, "schema_version", 1);
        appendString(sb, "kind", "eval_features");
        appendString(sb, "run_id", RUN_ID);
        appendString(sb, "game_id", ResearchDecisionLogger.gameIdFor(game));
        appendString(sb, "seed", SEED);
        appendNumber(sb, "turn", turn);
        appendString(sb, "player", player.getName());
        appendNumber(sb, "seat", seat);
        appendNumber(sb, "player_life", player.getLife());
        appendNumber(sb, "creatures", creatures);
        appendNumber(sb, "score_total", scoreTotal);
        appendNumber(sb, "reconcile_delta", scoreTotal - featureSum);
        sb.append(",\"features\":{");
        boolean first = true;
        for (Map.Entry<String, Long> e : features.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":").append(e.getValue());
        }
        sb.append("}}");
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String name, String value) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":\"").append(escape(value)).append('"');
    }

    private static void appendNumber(StringBuilder sb, String name, long value) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":").append(value);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
