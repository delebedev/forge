package forge.view;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.StopWatch;

import forge.LobbyPlayer;
import forge.ai.AiController;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.puzzle.Puzzle;
import forge.gamemodes.puzzle.PuzzleIO;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.util.MyRandom;

/**
 * Headless puzzle mode: `puzzle -f <file.pzl> [-s seed]`. Loads a .pzl,
 * puts the AI in both seats (seat 0 = the puzzle's "human"/solver seat by
 * GameState's own convention, seat 1 = the puzzle's "ai" seat), plays it out,
 * and prints one machine-parseable result line. Sibling to SimulateMatch,
 * split out to keep the puzzle-specific setup (goal detection, watchdog
 * canary, chance-free profile) out of the batch-sim path.
 */
public class SimulatePuzzle {
    // Both seats default to the chance-free profile (forge-gui/res/ai/ChanceFree.ai)
    // so a puzzle's verdict reflects the AI's judgment, not profile dice.
    private static final String PUZZLE_AI_PROFILE = "ChanceFree";

    // Generous per-decision watchdog (Game.AI_TIMEOUT defaults to 5s) so a slow
    // but not actually stuck AI eval doesn't produce a false INVALID.
    private static final int AI_EVAL_TIMEOUT_SECONDS = 30;

    // Overall wall-clock ceiling for one puzzle game, independent of the
    // per-decision watchdog above — catches a stuck game loop (e.g. an
    // infinite-mana line) that never trips a single decision's timeout.
    private static final int WALL_CLOCK_TIMEOUT_SECONDS = 120;

    public static void simulate(String[] args) {
        FModel.initialize(null, null);

        final Map<String, List<String>> params = new HashMap<>();
        List<String> options = null;
        for (int i = 1; i < args.length; i++) {
            final String a = args[i];
            if (a.isEmpty() || a.charAt(0) != '-') {
                if (options != null) {
                    options.add(a);
                }
                continue;
            }
            options = new ArrayList<>();
            params.put(a.substring(1), options);
        }

        if (!params.containsKey("f")) {
            argumentHelp();
            return;
        }

        if (params.containsKey("s")) {
            MyRandom.setRandom(new Random(Long.parseLong(params.get("s").get(0))));
        }

        System.out.println(runPuzzle(new File(params.get("f").get(0))));
        System.out.flush();
    }

    private static void argumentHelp() {
        System.out.println("Syntax: forge.exe puzzle -f <puzzle.pzl> -s [S]");
        System.out.println("\tpuzzle - headless puzzle mode: solve one .pzl with the AI in both seats");
        System.out.println("\tf - path to a .pzl puzzle file");
        System.out.println("\ts - RNG seed for deterministic simulation (optional)");
    }

    /** Runs one puzzle and returns its "Puzzle Result: ..." line. Package-visible for tests via reflection is not
     * worth it here; kept package-private-by-convention (public) since it's the whole point of this class. */
    public static String runPuzzle(File pzlFile) {
        String name = pzlFile.getName().replace(PuzzleIO.SUFFIX_DATA, "");
        AiController.evalTimeoutFired = false;

        final Puzzle puzzle;
        try {
            puzzle = PuzzleIO.loadPuzzle(pzlFile);
        } catch (Exception e) {
            e.printStackTrace();
            return resultLine(name, "INVALID", 0, 0, false);
        }

        List<RegisteredPlayer> players = new ArrayList<>();
        LobbyPlayer solver = GamePlayerUtil.createAiPlayer("Puzzle-Solver", 0, 0, null, PUZZLE_AI_PROFILE);
        RegisteredPlayer human = new RegisteredPlayer(new Deck()).setPlayer(solver);
        human.setStartingHand(0);
        players.add(human);

        RegisteredPlayer ai = new RegisteredPlayer(new Deck())
                .setPlayer(GamePlayerUtil.createAiPlayer("Puzzle-Opponent", 1, 0, null, PUZZLE_AI_PROFILE));
        ai.setStartingHand(0);
        players.add(ai);

        GameRules rules = new GameRules(GameType.Puzzle);
        rules.setGamesPerMatch(1);
        Match match = new Match(rules, players, "Puzzle");

        final Game game = match.createGame();
        game.AI_TIMEOUT = AI_EVAL_TIMEOUT_SECONDS;

        StopWatch sw = new StopWatch();
        sw.start();
        boolean wallClockTimeout = false;
        boolean crashed = false;
        try {
            TimeLimitedCodeBlock.runWithTimeout(
                    () -> match.startGame(game, () -> puzzle.applyToGame(game)),
                    WALL_CLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            wallClockTimeout = true;
        } catch (Exception | StackOverflowError e) {
            // An unexpected crash is never PASS/FAIL, same as a timeout — but it isn't
            // a timeout, so it's tracked separately and doesn't set timeout_fired.
            e.printStackTrace();
            crashed = true;
        } finally {
            sw.stop();
        }

        boolean timedOut = wallClockTimeout || AiController.evalTimeoutFired;
        int turn = game.getPhaseHandler().getTurn();
        String verdict;
        if (timedOut || crashed) {
            verdict = "INVALID";
        } else if (game.getOutcome() != null && game.getOutcome().isWinner(solver)) {
            verdict = "PASS";
        } else {
            verdict = "FAIL";
        }
        return resultLine(name, verdict, turn, sw.getTime(), timedOut);
    }

    private static String resultLine(String name, String verdict, int turn, long ms, boolean timeoutFired) {
        return String.format("Puzzle Result: name=%s verdict=%s turn=%d ms=%d timeout_fired=%d",
                name, verdict, turn, ms, timeoutFired ? 1 : 0);
    }
}
