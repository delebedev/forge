package forge.ai;

import forge.ai.ResearchDecisionLogger.BoardSummary;
import forge.ai.simulation.GameStateEvaluator.Score;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Research hook: in-JVM learned state-value blend for the simulation AI.
 *
 * {@code FORGE_AI_VALUE_NET=<weights.json path>} loads a small MLP (schema
 * {@code valuenet_mlp_v1}, produced by scripts/valuenet/train_value_net.py)
 * once per JVM. {@code FORGE_AI_VALUE_NET_SHA}, if set, must match the file's
 * sha256 or loading fails fast — no silent drift between a spec's declared
 * weights and what actually ran. No network, no timeout, no fail-open: with
 * the env set, a bad weights file crashes the JVM rather than running an
 * unintended experiment; with it unset, this class never runs.
 *
 * Seat-scoped like other candidates: only fires for a player whose
 * {@link AiVariant} is {@link AiVariant#CANDIDATE}. Post-simulation states are
 * scored (win-prob v in [0,1]) and blended into the heuristic score:
 * {@code value' = (1-lambda)*value + lambda*(value+shift)}, where
 * {@code shift = (v-0.5)*2*NET_SCALE} maps the net's probability delta into
 * {@link Score} units. {@code FORGE_AI_VALUE_BLEND} sets lambda (default 0,
 * i.e. inert even if a net is loaded); {@code FORGE_AI_VALUE_NET_SCALE}
 * (default 400) sets the score-unit scale.
 *
 * Feature order (below) matches the {@code priority_decision_v3} JSON schema
 * field names field-for-field (see {@link ResearchDecisionLogger} and
 * docs/neural.md) so the Python trainer and this forward pass agree by
 * construction — a mismatched weights file fails fast at load rather than
 * silently scoring on shifted columns.
 */
public final class ResearchValueNet {
    static final String[] FEATURE_NAMES = {
            "turn", "stack_size",
            "player_life", "opponent_life",
            "player_hand_size", "opponent_hand_size",
            "player_library_size",
            "player_battlefield_size", "opponent_battlefield_size",
            "player_creatures", "opponent_creatures",
            "player_creature_power", "opponent_creature_power",
            "player_creature_toughness", "opponent_creature_toughness",
            "player_available_mana_estimate",
    };

    private static final String WEIGHTS_PATH = System.getenv("FORGE_AI_VALUE_NET");
    private static final String WEIGHTS_SHA = System.getenv("FORGE_AI_VALUE_NET_SHA");
    private static final double LAMBDA = Double.parseDouble(System.getenv().getOrDefault("FORGE_AI_VALUE_BLEND", "0"));
    private static final int NET_SCALE = Integer.parseInt(System.getenv().getOrDefault("FORGE_AI_VALUE_NET_SCALE", "400"));
    private static final Mlp NET = load();

    private ResearchValueNet() {
    }

    public static boolean enabled() {
        return NET != null;
    }

    /**
     * Scores the post-simulation state for simPlayer and blends it into score.
     * Sentinel scores and non-CANDIDATE seats pass through untouched.
     */
    public static Score adjust(Game simGame, Player simPlayer, Score score) {
        if (!enabled() || score == null
                || score.value == Integer.MIN_VALUE || score.value == Integer.MAX_VALUE
                || !isCandidateSeat(simPlayer)) {
            return score;
        }
        double v = NET.forward(extractFeatures(simGame, simPlayer));
        int shift = (int) Math.round((v - 0.5) * 2 * NET_SCALE);
        int blended = (int) Math.round(score.value + LAMBDA * shift);
        int blendedSummonSick = (int) Math.round(score.summonSickValue + LAMBDA * shift);
        return new Score(blended, blendedSummonSick);
    }

    private static boolean isCandidateSeat(Player player) {
        return player.getController() instanceof PlayerControllerAi
                && ((PlayerControllerAi) player.getController()).getAi().usesCandidateVariant();
    }

    static double[] extractFeatures(Game game, Player player) {
        Player opponent = ResearchDecisionLogger.firstOpponent(player);
        BoardSummary mine = ResearchDecisionLogger.boardSummary(player);
        BoardSummary theirs = opponent == null
                ? new BoardSummary(0, 0, 0, 0, 0, 0, 0, 0, 0)
                : ResearchDecisionLogger.boardSummary(opponent);
        return new double[] {
                game.getPhaseHandler().getTurn(),
                game.getStack().size(),
                player.getLife(),
                opponent == null ? 0 : opponent.getLife(),
                ResearchDecisionLogger.zoneSize(player, ZoneType.Hand),
                opponent == null ? 0 : ResearchDecisionLogger.zoneSize(opponent, ZoneType.Hand),
                ResearchDecisionLogger.zoneSize(player, ZoneType.Library),
                ResearchDecisionLogger.zoneSize(player, ZoneType.Battlefield),
                opponent == null ? 0 : ResearchDecisionLogger.zoneSize(opponent, ZoneType.Battlefield),
                mine.creatures, theirs.creatures,
                mine.creaturePower, theirs.creaturePower,
                mine.creatureToughness, theirs.creatureToughness,
                ComputerUtilMana.getAvailableManaEstimate(player, true),
        };
    }

    @SuppressWarnings("unchecked")
    private static Mlp load() {
        if (WEIGHTS_PATH == null || WEIGHTS_PATH.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(WEIGHTS_PATH));
            if (WEIGHTS_SHA != null && !WEIGHTS_SHA.isEmpty()) {
                String actual = sha256Hex(bytes);
                if (!actual.equalsIgnoreCase(WEIGHTS_SHA)) {
                    throw new IllegalStateException(
                            "FORGE_AI_VALUE_NET_SHA mismatch: expected " + WEIGHTS_SHA + " got " + actual);
                }
            }
            Map<String, Object> root = (Map<String, Object>) MiniJson.parse(new String(bytes, StandardCharsets.UTF_8));
            if (!"valuenet_mlp_v1".equals(root.get("schema"))) {
                throw new IllegalStateException("FORGE_AI_VALUE_NET: unsupported schema " + root.get("schema"));
            }
            List<Object> names = (List<Object>) root.get("feature_names");
            if (names.size() != FEATURE_NAMES.length) {
                throw new IllegalStateException("FORGE_AI_VALUE_NET: feature_names length mismatch");
            }
            for (int i = 0; i < FEATURE_NAMES.length; i++) {
                if (!FEATURE_NAMES[i].equals(names.get(i))) {
                    throw new IllegalStateException("FORGE_AI_VALUE_NET: feature_names[" + i + "] expected "
                            + FEATURE_NAMES[i] + " got " + names.get(i));
                }
            }
            double[] mean = toDoubleArray((List<Object>) root.get("input_mean"));
            double[] std = toDoubleArray((List<Object>) root.get("input_std"));
            double[][] hiddenW = toMatrix((List<Object>) root.get("hidden_w"));
            double[] hiddenB = toDoubleArray((List<Object>) root.get("hidden_b"));
            double[] outputW = toDoubleArray((List<Object>) root.get("output_w"));
            double outputB = ((Number) root.get("output_b")).doubleValue();
            return new Mlp(mean, std, hiddenW, hiddenB, outputW, outputB);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("FORGE_AI_VALUE_NET failed to load " + WEIGHTS_PATH + ": " + e.getMessage(), e);
        }
    }

    private static double[] toDoubleArray(List<Object> list) {
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ((Number) list.get(i)).doubleValue();
        }
        return out;
    }

    private static double[][] toMatrix(List<Object> rows) {
        double[][] out = new double[rows.size()][];
        for (int i = 0; i < out.length; i++) {
            out[i] = toDoubleArray((List<Object>) rows.get(i));
        }
        return out;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** One hidden layer (ReLU) + one sigmoid output unit; z-score input normalization. */
    private static final class Mlp {
        private final double[] mean;
        private final double[] std;
        private final double[][] hiddenW;
        private final double[] hiddenB;
        private final double[] outputW;
        private final double outputB;

        Mlp(double[] mean, double[] std, double[][] hiddenW, double[] hiddenB, double[] outputW, double outputB) {
            this.mean = mean;
            this.std = std;
            this.hiddenW = hiddenW;
            this.hiddenB = hiddenB;
            this.outputW = outputW;
            this.outputB = outputB;
        }

        double forward(double[] x) {
            double[] hidden = new double[hiddenB.length];
            for (int j = 0; j < hidden.length; j++) {
                double sum = hiddenB[j];
                for (int i = 0; i < x.length; i++) {
                    double stdI = std[i] == 0 ? 1 : std[i];
                    sum += hiddenW[j][i] * (x[i] - mean[i]) / stdI;
                }
                hidden[j] = Math.max(0, sum);
            }
            double out = outputB;
            for (int j = 0; j < hidden.length; j++) {
                out += outputW[j] * hidden[j];
            }
            return 1.0 / (1.0 + Math.exp(-out));
        }
    }

    /** Minimal recursive-descent JSON reader — objects/arrays/strings/numbers/bool/null only. */
    private static final class MiniJson {
        private final String s;
        private int i;

        private MiniJson(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            MiniJson p = new MiniJson(text);
            p.skipWs();
            return p.parseValue();
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private Object parseValue() {
            char c = s.charAt(i);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't') {
                expect("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                expect("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                expect("null");
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            skipWs();
            if (s.charAt(i) == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                i++; // ':'
                skipWs();
                map.put(key, parseValue());
                skipWs();
                if (s.charAt(i++) == '}') {
                    break;
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++;
            skipWs();
            if (s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(parseValue());
                skipWs();
                if (s.charAt(i++) == ']') {
                    break;
                }
            }
            return list;
        }

        private String parseString() {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            i++; // closing quote
            return sb.toString();
        }

        private Double parseNumber() {
            int start = i;
            while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            return Double.parseDouble(s.substring(start, i));
        }

        private void expect(String lit) {
            if (!s.startsWith(lit, i)) {
                throw new IllegalArgumentException("expected " + lit + " at " + i);
            }
            i += lit.length();
        }
    }
}
