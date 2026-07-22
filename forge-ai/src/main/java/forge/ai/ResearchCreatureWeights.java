package forge.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-seat multiplier table over CreatureEvaluator's named terms.
 *
 * Each CreatureEvaluator contribution flows through addValue(value, label);
 * a table maps normalized labels to multipliers applied to the stock
 * contribution. Stock behavior is the identity table (every multiplier 1).
 * The base constant and any value not routed through addValue (the
 * AIEvaluationModifier SVar, reset residuals) cannot be reweighted and are
 * deliberately not accepted as table keys.
 *
 * Evaluation sites are static, so the active table travels via an
 * InheritableThreadLocal set at the PlayerControllerAi decision entry points
 * (the "Game AI Eval" thread is spawned per decision and inherits it).
 * No context means stock evaluation.
 */
public final class ResearchCreatureWeights {

    /** Every normalized addValue/subValue label in CreatureEvaluator. */
    static final Set<String> TERMS;
    static {
        Set<String> t = new LinkedHashSet<>(List.of(
                "non-token", "power", "toughness", "transforming", "cmc",
                "flying", "horses", "unblockable", "thorns", "fear", "intimidate", "menace", "skulk",
                "ds", "fs", "dt", "lifelink", "trample", "vigilance", "infect", "wither",
                "toxic", "afflict", "rampage", "eldrazi", "absorb",
                "outlast", "bushido", "flanking", "exalted", "melee", "prowess",
                "reach", "shadow-block",
                "darksteel", "shielded", "cho-manno", "fogbank", "hexproof", "shroud", "ward", "protection",
                "paired", "encoded", "revive",
                "defender", "sac-end", "detained", "useless", "cant-block", "goaded",
                "must-attack", "must-attack-player", "dies-to-dmg", "dies",
                "untapped", "tapped-useless", "doesnt-untap", "stunned",
                "sa", "manadork", "phasing", "eot-leaves", "landfall",
                "cupkeep", "echo-unpaid", "fading", "vanishing", "upkeep-dmg", "sac-unless"));
        TERMS = Collections.unmodifiableSet(t);
    }

    private static final InheritableThreadLocal<ResearchCreatureWeights> CONTEXT = new InheritableThreadLocal<>();

    private final Map<String, Double> multipliers;
    private final String sourceSha256;

    private ResearchCreatureWeights(Map<String, Double> multipliers, String sourceSha256) {
        this.multipliers = Collections.unmodifiableMap(multipliers);
        this.sourceSha256 = sourceSha256;
    }

    /** Normalized term key for an addValue label: the prefix before ':' for variable labels. */
    static String termKey(String label) {
        if (label == null) {
            return "";
        }
        int idx = label.indexOf(':');
        return idx < 0 ? label : label.substring(0, idx);
    }

    public double multiplier(String label) {
        Double m = multipliers.get(termKey(label));
        return m == null ? 1.0 : m;
    }

    public int termCount() {
        return multipliers.size();
    }

    public String sourceSha256() {
        return sourceSha256;
    }

    /**
     * Parse a weights file: '# comment' and blank lines ignored, otherwise
     * 'term = multiplier'. Unknown terms and malformed lines fail loudly —
     * a candidate arm must never silently degrade to stock.
     */
    public static ResearchCreatureWeights parse(List<String> lines, String sourceSha256) {
        Map<String, Double> parsed = new HashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("eval-weights line has no '=': " + raw);
            }
            String term = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (!TERMS.contains(term)) {
                throw new IllegalArgumentException("eval-weights unknown term: " + term);
            }
            final double multiplier;
            try {
                multiplier = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("eval-weights bad multiplier for " + term + ": " + value);
            }
            if (!Double.isFinite(multiplier) || multiplier < 0) {
                throw new IllegalArgumentException("eval-weights multiplier must be finite and >= 0: " + term + " = " + value);
            }
            if (parsed.put(term, multiplier) != null) {
                throw new IllegalArgumentException("eval-weights duplicate term: " + term);
            }
        }
        return new ResearchCreatureWeights(parsed, sourceSha256);
    }

    public static ResearchCreatureWeights load(String path) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            String content = new String(bytes, StandardCharsets.UTF_8);
            return parse(List.of(content.split("\n", -1)), sha256Hex(bytes));
        } catch (IOException e) {
            throw new IllegalArgumentException("eval-weights file unreadable: " + path, e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void setContext(ResearchCreatureWeights weights) {
        if (weights == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(weights);
        }
    }

    static ResearchCreatureWeights current() {
        return CONTEXT.get();
    }
}
