package forge.view;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.StopWatch;

import forge.LobbyPlayer;
import forge.ai.AiController;
import forge.ai.AiVariant;
import forge.ai.LobbyPlayerAi;
import forge.ai.ResearchTreatmentTelemetry;
import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.io.DeckSerializer;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.tournament.system.AbstractTournament;
import forge.gamemodes.tournament.system.TournamentBracket;
import forge.gamemodes.tournament.system.TournamentPairing;
import forge.gamemodes.tournament.system.TournamentPlayer;
import forge.gamemodes.tournament.system.TournamentRoundRobin;
import forge.gamemodes.tournament.system.TournamentSwiss;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.util.Lang;
import forge.util.MyRandom;
import forge.util.TextUtil;
import forge.util.WordUtil;
import forge.util.storage.IStorage;

public class SimulateMatch {
    // Per-decision "Game AI Eval" watchdog ceiling for this JVM, seconds.
    // 0 = leave Forge's own default (Game.AI_TIMEOUT, 5s) alone.
    private static final int AI_EVAL_TIMEOUT_SECONDS =
            parseAiEvalTimeout(System.getenv("FORGE_RESEARCH_AI_EVAL_TIMEOUT"));

    public static void simulate(String[] args) {
        final ResearchControl researchControl = ResearchControl.fromExternalName(
                System.getenv("FORGE_RESEARCH_CONTROL"));
        final int researchControlSeat = parseSeat(System.getenv("FORGE_RESEARCH_CONTROL_SEAT"));
        if (researchControl != ResearchControl.NONE && researchControlSeat < 1) {
            throw new IllegalArgumentException(
                    "FORGE_RESEARCH_CONTROL requires FORGE_RESEARCH_CONTROL_SEAT=<positive seat>");
        }
        if (researchControl.isForcedFailure()) {
            System.err.println("Forced research-control failure for seat " + researchControlSeat);
            System.exit(42);
            return;
        }
        FModel.initialize(null, null);

        System.out.println("Simulation mode");
        if (args.length < 4) {
            argumentHelp();
            return;
        }

        final Map<String, List<String>> params = new HashMap<>();
        List<String> options = null;

        for (int i = 1; i < args.length; i++) {
            // "sim" is in the 0th slot
            final String a = args[i];

            if (a.charAt(0) == '-') {
                if (a.length() < 2) {
                    System.err.println("Error at argument " + a);
                    argumentHelp();
                    return;
                }

                options = new ArrayList<>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                System.err.println("Illegal parameter usage");
                return;
            }
        }

        int nGames = 1;
        if (params.containsKey("n")) {
            // Number of games should only be a single string
            nGames = Integer.parseInt(params.get("n").get(0));
        }

        int matchSize = 0;
        if (params.containsKey("m")) {
            // Match size ("best of X games")
            matchSize = Integer.parseInt(params.get("m").get(0));
        }

        boolean outputGamelog = !params.containsKey("q");

        Long seed = null;
        if (params.containsKey("s")) {
            seed = Long.parseLong(params.get("s").get(0));
            MyRandom.setRandom(new Random(seed));
        }

        final boolean perGameSeeding = parseSeedProtocol(System.getenv("FORGE_RESEARCH_SEED_PROTOCOL"));
        if (perGameSeeding && seed == null) {
            throw new IllegalArgumentException(
                    "FORGE_RESEARCH_SEED_PROTOCOL=game requires a -s seed");
        }

        GameType type = GameType.Constructed;
        if (params.containsKey("f")) {
            type = GameType.valueOf(WordUtil.capitalize(params.get("f").get(0)));
        }

        GameRules rules = new GameRules(type);
        rules.setAppliedVariants(EnumSet.of(type));

        if (matchSize != 0) {
            rules.setGamesPerMatch(matchSize);
        }

        if (params.containsKey("t")) {
            simulateTournament(params, rules, outputGamelog);
            System.out.flush();
            return;
        }

        List<RegisteredPlayer> pp = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        final AiVariant selectedVariant = AiVariant.fromExternalName(System.getenv("FORGE_AI_VARIANT"));
        final int variantSeat = parseSeat(System.getenv("FORGE_AI_VARIANT_SEAT"));
        final int simSeat = parseSeat(System.getenv("FORGE_AI_SIM_SEAT"));
        ResearchTreatmentTelemetry.reset();
        if (selectedVariant == AiVariant.CANDIDATE && variantSeat < 1) {
            throw new IllegalArgumentException(
                    "FORGE_AI_VARIANT=candidate requires FORGE_AI_VARIANT_SEAT=<positive seat>");
        }

        // Research hook: FORGE_AI_EVAL_WEIGHTS=<file> + FORGE_AI_EVAL_WEIGHTS_SEAT=<n>
        // reweights CreatureEvaluator for one seat. Load fails loudly (a candidate
        // arm must never silently run stock); the ack line is exposure evidence.
        final String evalWeightsPath = System.getenv("FORGE_AI_EVAL_WEIGHTS");
        final int evalWeightsSeat = parseSeat(System.getenv("FORGE_AI_EVAL_WEIGHTS_SEAT"));
        final forge.ai.ResearchCreatureWeights evalWeights;
        if (evalWeightsPath != null && !evalWeightsPath.isEmpty()) {
            if (evalWeightsSeat < 1) {
                throw new IllegalArgumentException(
                        "FORGE_AI_EVAL_WEIGHTS requires FORGE_AI_EVAL_WEIGHTS_SEAT=<positive seat>");
            }
            evalWeights = forge.ai.ResearchCreatureWeights.load(evalWeightsPath);
            System.out.println("RESEARCH_EVAL_WEIGHTS seat=" + evalWeightsSeat + " file=" + evalWeightsPath
                    + " sha256=" + evalWeights.sourceSha256() + " terms=" + evalWeights.termCount());
        } else {
            if (evalWeightsSeat >= 1) {
                throw new IllegalArgumentException(
                        "FORGE_AI_EVAL_WEIGHTS_SEAT set without FORGE_AI_EVAL_WEIGHTS");
            }
            evalWeights = null;
        }

        int i = 1;

        if (params.containsKey("d")) {
            for (String deck : params.get("d")) {
                Deck d = deckFromCommandLineParameter(deck, type);
                if (d == null) {
                    System.out.println(TextUtil.concatNoSpace("Could not load deck - ", deck, ", match cannot start"));
                    return;
                }
                if (i > 1) {
                    sb.append(" vs ");
                }
                String name = TextUtil.concatNoSpace("Ai(", String.valueOf(i), ")-", d.getName());
                sb.append(name);

                RegisteredPlayer rp;

                if (type.equals(GameType.Commander)) {
                    rp = RegisteredPlayer.forCommander(d);
                } else {
                    rp = new RegisteredPlayer(d);
                }
                // Research hook: FORGE_AI_SIM_SEAT=<n> puts seat n (1-based) on the
                // simulation-based AI (AIOption.USE_SIMULATION) instead of default AI.
                final LobbyPlayer lobbyPlayer;
                if (simSeat == i) {
                    Set<forge.ai.AIOption> aiOpts = EnumSet.of(forge.ai.AIOption.USE_SIMULATION);
                    lobbyPlayer = GamePlayerUtil.createAiPlayer(name, i - 1, 0, aiOpts);
                } else {
                    lobbyPlayer = GamePlayerUtil.createAiPlayer(name, i - 1);
                }
                final LobbyPlayerAi aiPlayer = (LobbyPlayerAi) lobbyPlayer;
                aiPlayer.setAiVariant(i == variantSeat ? selectedVariant : AiVariant.BASELINE);
                if (i == evalWeightsSeat) {
                    aiPlayer.setEvalWeights(evalWeights);
                }
                rp.setPlayer(aiPlayer);
                if (i == researchControlSeat) {
                    researchControl.applyTo(rp);
                }
                pp.add(rp);
                i++;
            }
        }
        if (selectedVariant == AiVariant.CANDIDATE && variantSeat > pp.size()) {
            throw new IllegalArgumentException(
                    "FORGE_AI_VARIANT_SEAT=" + variantSeat + " exceeds player count " + pp.size());
        }
        if (evalWeights != null && evalWeightsSeat > pp.size()) {
            throw new IllegalArgumentException(
                    "FORGE_AI_EVAL_WEIGHTS_SEAT=" + evalWeightsSeat + " exceeds player count " + pp.size());
        }
        if (researchControl != ResearchControl.NONE && researchControlSeat > pp.size()) {
            throw new IllegalArgumentException(
                    "FORGE_RESEARCH_CONTROL_SEAT=" + researchControlSeat
                            + " exceeds player count " + pp.size());
        }

        if (params.containsKey("c")) {
            rules.setSimTimeout(Integer.parseInt(params.get("c").get(0)));
        }

        sb.append(" - ").append(Lang.nounWithNumeral(nGames, "game")).append(" of ").append(type);
        if (seed != null) {
            sb.append(" seed ").append(seed);
            if (perGameSeeding) {
                sb.append(" protocol game");
            }
        }

        System.out.println(sb.toString());

        Match mc = new Match(rules, pp, "Test");

        if (matchSize != 0) {
            int iGame = 0;
            while (!mc.isMatchOver()) {
                // play games until the match ends
                simulateSingleMatch(mc, iGame, outputGamelog);
                iGame++;
            }
        } else {
            for (int iGame = 0; iGame < nGames; iGame++) {
                if (perGameSeeding) {
                    // Per-game reseed + fresh match: game i's opening state
                    // depends only on (seed, i) — not on how many random draws
                    // games 0..i-1 consumed, and not on their outcomes either
                    // (a reused Match hands the previous loser the play). Batch
                    // game i is therefore the same game across arms until
                    // in-game play diverges, and a batch can be sharded by
                    // game without changing any game.
                    MyRandom.setRandom(new Random(perGameSeed(seed, iGame)));
                    mc = new Match(rules, pp, "Test");
                }
                simulateSingleMatch(mc, iGame, outputGamelog);
            }
        }

        System.out.println(ResearchTreatmentTelemetry.summary(selectedVariant, variantSeat, simSeat));
        System.out.flush();
    }

    /**
     * FORGE_RESEARCH_SEED_PROTOCOL: unset or "batch" keeps the historical
     * one-seeding-per-JVM stream; "game" reseeds every batch game from
     * {@link #perGameSeed}. Anything else fails fast so a typo can never
     * silently run the wrong pairing protocol.
     */
    private static boolean parseSeedProtocol(final String value) {
        if (value == null || value.isEmpty() || "batch".equals(value)) {
            return false;
        }
        if ("game".equals(value)) {
            return true;
        }
        throw new IllegalArgumentException(
                "FORGE_RESEARCH_SEED_PROTOCOL must be batch or game, got: " + value);
    }

    /**
     * FORGE_RESEARCH_AI_EVAL_TIMEOUT: per-decision AI-eval watchdog ceiling in
     * seconds for headless sim runs. Unset or empty keeps Forge's default; a
     * research run raises it so a truncated scan is exceptional rather than a
     * routine, silent source of divergence. Anything non-positive or
     * unparseable fails fast — a typo must never quietly restore the low
     * ceiling the raise exists to avoid.
     */
    static int parseAiEvalTimeout(final String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        final int seconds;
        try {
            seconds = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "FORGE_RESEARCH_AI_EVAL_TIMEOUT must be a positive number of seconds, got: " + value);
        }
        if (seconds <= 0) {
            throw new IllegalArgumentException(
                    "FORGE_RESEARCH_AI_EVAL_TIMEOUT must be a positive number of seconds, got: " + value);
        }
        return seconds;
    }

    /**
     * SplitMix64 output i+1 of a stream seeded at batchSeed. Raw sequential
     * seeds (seed + i) produce correlated java.util.Random streams; the
     * finalizer mix decorrelates neighbouring games.
     */
    private static long perGameSeed(final long batchSeed, final int iGame) {
        long z = batchSeed + (iGame + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static void argumentHelp() {
        System.out.println("Syntax: forge.exe sim -d <deck1[.dck]> ... <deckX[.dck]> -D [D] -n [N] -m [M] -t [T] -p [P] -f [F] -s [S] -q");
        System.out.println("\tsim - stands for simulation mode");
        System.out.println("\tdeck1 (or deck2,...,X) - constructed deck name or filename (has to be quoted when contains multiple words)");
        System.out.println("\tdeck is treated as file if it ends with a dot followed by three numbers or letters");
        System.out.println("\tD - absolute directory to load decks from");
        System.out.println("\tN - number of games, defaults to 1 (Ignores match setting)");
        System.out.println("\tM - Play full match of X games, typically 1,3,5 games. (Optional, overrides N)");
        System.out.println("\tT - Type of tournament to run with all provided decks (Bracket, RoundRobin, Swiss)");
        System.out.println("\tP - Amount of players per match (used only with Tournaments, defaults to 2)");
        System.out.println("\tF - format of games, defaults to constructed");
        System.out.println("\tS - RNG seed for deterministic simulation");
        System.out.println("\tc - Clock flag. Set the maximum time in seconds before calling the match a draw, defaults to 120.");
        System.out.println("\tq - Quiet flag. Output just the game result, not the entire game log.");
    }

    public static void simulateSingleMatch(final Match mc, int iGame, boolean outputGamelog) {
        final StopWatch sw = new StopWatch();
        sw.start();

        final Game g1 = mc.createGame();
        if (AI_EVAL_TIMEOUT_SECONDS > 0) {
            g1.AI_TIMEOUT = AI_EVAL_TIMEOUT_SECONDS;
        }
        AiController.resetEvalTimeoutFires();
        // will run match in the same thread
        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> {
                mc.startGame(g1);
                sw.stop();
            }, mc.getRules().getSimTimeout(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("Stopping slow match as draw");
        } catch (Exception | StackOverflowError e) {
            e.printStackTrace();
        } finally {
            if (sw.isStarted()) {
                sw.stop();
            }
            g1.setGameOver(GameEndReason.Draw);
        }

        List<GameLogEntry> log;
        if (outputGamelog) {
            log = g1.getGameLog().getLogEntries(null);
        } else {
            log = g1.getGameLog().getLogEntries(GameLogEntryType.MATCH_RESULTS);
        }
        Collections.reverse(log);
        for (GameLogEntry l : log) {
            System.out.println(l);
        }

        // Precedes this game's result line so a harness reading the batch stream
        // attributes the fire to the right game.
        final int evalTimeoutFires = AiController.evalTimeoutFires();
        if (evalTimeoutFires > 0) {
            System.out.printf("Research Game Canary: game=%d kind=ai_eval_timeout fires=%d%n",
                    1 + iGame, evalTimeoutFires);
        }

        // If both players life totals to 0 in a single turn, the game should end in a draw
        if (g1.getOutcome().isDraw()) {
            System.out.printf("\nGame Result: Game %d ended in a Draw! Took %d ms.%n", 1 + iGame, sw.getTime());
        } else {
            System.out.printf("\nGame Result: Game %d ended in %d ms. %s has won!\n%n", 1 + iGame, sw.getTime(), g1.getOutcome().getWinningLobbyPlayer().getName());
        }
    }

    private static void simulateTournament(Map<String, List<String>> params, GameRules rules, boolean outputGamelog) {
        String tournament = params.get("t").get(0);
        AbstractTournament tourney = null;
        int matchPlayers = params.containsKey("p") ? Integer.parseInt(params.get("p").get(0)) : 2;

        DeckGroup deckGroup = new DeckGroup("SimulatedTournament");
        List<TournamentPlayer> players = new ArrayList<>();
        int numPlayers = 0;
        if (params.containsKey("d")) {
            for (String deck : params.get("d")) {
                Deck d = deckFromCommandLineParameter(deck, rules.getGameType());
                if (d == null) {
                    System.out.println(TextUtil.concatNoSpace("Could not load deck - ", deck, ", match cannot start"));
                    return;
                }

                deckGroup.addAiDeck(d);
                players.add(new TournamentPlayer(GamePlayerUtil.createAiPlayer(d.getName(), 0), numPlayers));
                numPlayers++;
            }
        }

        if (params.containsKey("D")) {
            // Direc
            String foldName = params.get("D").get(0);
            File folder = new File(foldName);
            if (!folder.isDirectory()) {
                System.out.println("Directory not found - " + foldName);
            } else {
                for (File deck : folder.listFiles((dir, name) -> name.endsWith(".dck"))) {
                    Deck d = DeckSerializer.fromFile(deck);
                    if (d == null) {
                        System.out.println(TextUtil.concatNoSpace("Could not load deck - ", deck.getName(), ", match cannot start"));
                        return;
                    }
                    deckGroup.addAiDeck(d);
                    players.add(new TournamentPlayer(GamePlayerUtil.createAiPlayer(d.getName(), 0), numPlayers));
                    numPlayers++;
                }
            }
        }

        if (numPlayers == 0) {
            System.out.println("No decks/Players found. Please try again.");
        }

        if ("bracket".equalsIgnoreCase(tournament)) {
            tourney = new TournamentBracket(players, matchPlayers);
        } else if ("roundrobin".equalsIgnoreCase(tournament)) {
            tourney = new TournamentRoundRobin(players, matchPlayers);
        } else if ("swiss".equalsIgnoreCase(tournament)) {
            tourney = new TournamentSwiss(players, matchPlayers);
        }
        if (tourney == null) {
            System.out.println("Failed to initialize tournament, bailing out");
            return;
        }

        tourney.initializeTournament();

        String lastWinner = "";
        int curRound = 0;
        System.out.println(TextUtil.concatNoSpace("Starting a ", tournament, " tournament with ",
                String.valueOf(numPlayers), " players over ",
                String.valueOf(tourney.getTotalRounds()), " rounds"));
        while (!tourney.isTournamentOver()) {
            if (tourney.getActiveRound() != curRound) {
                if (curRound != 0) {
                    System.out.println(TextUtil.concatNoSpace("End Round - ", String.valueOf(curRound)));
                }
                curRound = tourney.getActiveRound();
                System.out.println();
                System.out.println(TextUtil.concatNoSpace("Round ", String.valueOf(curRound), " Pairings:"));

                for (TournamentPairing pairing : tourney.getActivePairings()) {
                    System.out.println(pairing.outputHeader());
                }
                System.out.println();
            }

            TournamentPairing pairing = tourney.getNextPairing();
            List<RegisteredPlayer> regPlayers = AbstractTournament.registerTournamentPlayers(pairing, deckGroup);

            StringBuilder sb = new StringBuilder();
            sb.append("Round ").append(tourney.getActiveRound()).append(" - ");
            sb.append(pairing.outputHeader());
            System.out.println(sb.toString());

            if (!pairing.isBye()) {
                Match mc = new Match(rules, regPlayers, "TourneyMatch");

                int exceptions = 0;
                int iGame = 0;
                while (!mc.isMatchOver()) {
                    // play games until the match ends
                    try {
                        simulateSingleMatch(mc, iGame, outputGamelog);
                        iGame++;
                    } catch (Exception e) {
                        exceptions++;
                        System.out.println(e.toString());
                        if (exceptions > 5) {
                            System.out.println("Exceeded number of exceptions thrown. Abandoning match...");
                            break;
                        } else {
                            System.out.println("Game threw exception. Abandoning game and continuing...");
                        }
                    }

                }
                LobbyPlayer winner = mc.getWinner().getPlayer();
                for (TournamentPlayer tp : pairing.getPairedPlayers()) {
                    if (winner.equals(tp.getPlayer())) {
                        pairing.setWinner(tp);
                        lastWinner = winner.getName();
                        System.out.println(TextUtil.concatNoSpace("Match Winner - ", lastWinner, "!"));
                        System.out.println();
                        break;
                    }
                }
            }

            tourney.reportMatchCompletion(pairing);
        }
        tourney.outputTournamentResults();
    }

    public static Match simulateOffthreadGame(List<Deck> decks, GameType format, int games) {
        return null;
    }

    private static int parseSeat(final String value) {
        return value == null ? 0 : Integer.parseInt(value.trim());
    }

    private static Deck deckFromCommandLineParameter(String deckname, GameType type) {
        int dotpos = deckname.lastIndexOf('.');
        if (dotpos > 0 && dotpos == deckname.length() - 4) {
            File directFile = new File(deckname);
            if (directFile.exists()) {
                return DeckSerializer.fromFile(directFile);
            }

            String baseDir = type.equals(GameType.Commander) ?
                    ForgeConstants.DECK_COMMANDER_DIR : ForgeConstants.DECK_CONSTRUCTED_DIR;

            File f = new File(baseDir + deckname);
            if (!f.exists()) {
                System.out.println("No deck found in " + baseDir);
            }

            return DeckSerializer.fromFile(f);
        }

        IStorage<Deck> deckStore = null;

        // Add other game types here...
        if (type.equals(GameType.Commander)) {
            deckStore = FModel.getDecks().getCommander();
        } else {
            deckStore = FModel.getDecks().getConstructed();
        }

        return deckStore.get(deckname);
    }

}
