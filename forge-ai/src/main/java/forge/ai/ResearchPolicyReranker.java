package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ResearchPolicyReranker {
    private static final String POLICY_PATH = System.getenv("FORGE_AI_POLICY_TSV");
    private static final int MIN_SEEN = Integer.parseInt(System.getenv().getOrDefault("FORGE_AI_POLICY_MIN_SEEN", "3"));
    private static final double MIN_SCORE = Double.parseDouble(System.getenv().getOrDefault("FORGE_AI_POLICY_MIN_SCORE", "0.5"));
    private static Map<String, PolicyEntry> policy;

    private ResearchPolicyReranker() {
    }

    static Result choose(Game game, List<SpellAbility> candidates) {
        Map<String, PolicyEntry> loaded = policy();
        if (loaded.isEmpty()) {
            return Result.fallback();
        }

        SpellAbility best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int bestSeen = 0;
        for (SpellAbility candidate : candidates) {
            String key = key(game, candidate);
            PolicyEntry entry = loaded.get(key);
            if (entry == null || entry.seen < MIN_SEEN) {
                continue;
            }
            if (entry.score > bestScore) {
                best = candidate;
                bestScore = entry.score;
                bestSeen = entry.seen;
            }
        }

        if (bestSeen < MIN_SEEN || bestScore < MIN_SCORE) {
            return Result.fallback(bestScore, bestSeen);
        }
        return Result.use(best, bestScore, bestSeen);
    }

    private static synchronized Map<String, PolicyEntry> policy() {
        if (policy != null) {
            return policy;
        }
        policy = new HashMap<>();
        if (POLICY_PATH == null || POLICY_PATH.isEmpty()) {
            return policy;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(POLICY_PATH))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", -1);
                if (parts.length < 7) {
                    continue;
                }
                String key = key(parts[0], parts[1], parts[2], parts[3]);
                int seen = Integer.parseInt(parts[5]);
                double score = Double.parseDouble(parts[6]);
                policy.put(key, new PolicyEntry(seen, score));
            }
        } catch (IOException | NumberFormatException ignored) {
            policy.clear();
        }
        return policy;
    }

    private static String key(Game game, SpellAbility sa) {
        return key(game.getPhaseHandler().getPhase().name(), api(sa), host(sa), zone(sa));
    }

    private static String key(String phase, String api, String host, String zone) {
        return phase + "\t" + api + "\t" + host + "\t" + zone;
    }

    private static String api(SpellAbility sa) {
        return sa == null ? "PASS" : String.valueOf(sa.getApi());
    }

    private static String host(SpellAbility sa) {
        Card host = sa == null ? null : sa.getHostCard();
        return host == null ? "NO_CARD" : host.getName();
    }

    private static String zone(SpellAbility sa) {
        Card host = sa == null ? null : sa.getHostCard();
        if (host == null || host.getZone() == null) {
            return "NO_ZONE";
        }
        return host.getZone().getZoneType().name();
    }

    static final class Result {
        final boolean used;
        final SpellAbility choice;
        final double score;
        final int seen;

        private Result(boolean used, SpellAbility choice, double score, int seen) {
            this.used = used;
            this.choice = choice;
            this.score = score;
            this.seen = seen;
        }

        static Result use(SpellAbility choice, double score, int seen) {
            return new Result(true, choice, score, seen);
        }

        static Result fallback() {
            return fallback(Double.NEGATIVE_INFINITY, 0);
        }

        static Result fallback(double score, int seen) {
            return new Result(false, null, score, seen);
        }
    }

    private static final class PolicyEntry {
        final int seen;
        final double score;

        private PolicyEntry(int seen, double score) {
            this.seen = seen;
            this.score = score;
        }
    }
}
