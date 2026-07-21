package forge.ai;

import forge.game.Game;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResearchNeuralReranker {
    private static final String SCORER_URL = System.getenv("FORGE_AI_SCORER_URL");
    private static final int TIMEOUT_MS = Integer.parseInt(System.getenv().getOrDefault("FORGE_AI_SCORER_TIMEOUT_MS", "200"));
    private static final double MIN_MARGIN = Double.parseDouble(System.getenv().getOrDefault("FORGE_AI_SCORER_MIN_MARGIN", "0.25"));
    private static final Pattern CHOICE_INDEX = Pattern.compile("\"choice_index\"\\s*:\\s*(-?\\d+)");
    private static final Pattern CHOICE_SCORE = Pattern.compile("\"choice_score\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");
    private static final Pattern MARGIN = Pattern.compile("\"margin\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(TIMEOUT_MS)).build();

    private ResearchNeuralReranker() {
    }

    static Result choose(Game game, Player player, List<SpellAbility> candidates, SpellAbility forgeChoice) {
        if (SCORER_URL == null || SCORER_URL.isEmpty()) {
            return Result.fallback();
        }
        // Scorer payload: no evaluated-reason map here (the reranker runs after the
        // greedy scan), so ai_play_decision stays blank — the scorer never used it.
        String payload = ResearchDecisionLogger.buildPriorityDecisionJson(game, player, candidates, forgeChoice,
                false, Double.NEGATIVE_INFINITY, 0, false, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, null);
        HttpRequest request = HttpRequest.newBuilder(URI.create(SCORER_URL))
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Result.fallback();
            }
            int choiceIndex = parseInt(CHOICE_INDEX, response.body(), -1);
            double choiceScore = parseDouble(CHOICE_SCORE, response.body(), Double.NEGATIVE_INFINITY);
            double margin = parseDouble(MARGIN, response.body(), Double.NEGATIVE_INFINITY);
            if (choiceIndex <= 0 || choiceIndex > candidates.size() || margin < MIN_MARGIN) {
                return Result.fallback(choiceScore, margin);
            }
            SpellAbility choice = candidates.get(choiceIndex - 1);
            if (!isHandSpell(choice)) {
                return Result.fallback(choiceScore, margin);
            }
            return Result.use(choice, choiceScore, margin);
        } catch (IOException e) {
            return Result.fallback();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fallback();
        } catch (IllegalArgumentException e) {
            return Result.fallback();
        }
    }

    private static int parseInt(Pattern pattern, String body, int fallback) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static double parseDouble(Pattern pattern, String body, double fallback) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : fallback;
    }

    private static boolean isHandSpell(SpellAbility choice) {
        return choice != null && choice.isSpell() && choice.getHostCard() != null && choice.getHostCard().getZone() != null
                && choice.getHostCard().getZone().getZoneType() == ZoneType.Hand;
    }

    static final class Result {
        final boolean used;
        final SpellAbility choice;
        final double score;
        final double margin;

        private Result(boolean used, SpellAbility choice, double score, double margin) {
            this.used = used;
            this.choice = choice;
            this.score = score;
            this.margin = margin;
        }

        static Result use(SpellAbility choice, double score, double margin) {
            return new Result(true, choice, score, margin);
        }

        static Result fallback() {
            return fallback(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        }

        static Result fallback(double score, double margin) {
            return new Result(false, null, score, margin);
        }
    }
}
