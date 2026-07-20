# PATCHES.md

Research instrumentation patches on branch `crucible`, applied on top of
upstream `master`. Each entry: name, purpose, files touched. Keep this list
tiny and in sync with the branch — one entry per commit.

## feat(ai): add research reranker harness hooks

Adds pluggable reranking seams (`ResearchPolicyReranker`,
`ResearchNeuralReranker`, `ResearchPolicySearch`) into `AiController`'s
priority decision path, plus `ResearchDecisionLogger` to record each
decision as a JSONL record for offline analysis. `SimulateMatch` gains the
env-driven wiring to enable these seams from a headless sim run.

- `forge-ai/src/main/java/forge/ai/AiController.java`
- `forge-ai/src/main/java/forge/ai/ResearchDecisionLogger.java`
- `forge-ai/src/main/java/forge/ai/ResearchNeuralReranker.java`
- `forge-ai/src/main/java/forge/ai/ResearchPolicyReranker.java`
- `forge-ai/src/main/java/forge/ai/ResearchPolicySearch.java`
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`

## feat(ai): per-game ID suffix in ResearchDecisionLogger for batch mode

Batch sim runs (`sim -n N`) share one JVM and one game-id env var across N
games. Tracks the current `Game` instance by identity and suffixes the
logged `game_id` with `-g0`, `-g1`, ... so per-game decision logs are
distinguishable within a batch.

- `forge-ai/src/main/java/forge/ai/ResearchDecisionLogger.java`

## fix(sim): survive HeadlessException in GuiDesktop screen-scale init

Headless sim runs hit a `HeadlessException` during screen-scale
initialization on some environments. Catches it and falls back to a
default scale instead of aborting the run.

- `forge-gui-desktop/src/main/java/forge/GuiDesktop.java`

## feat(ai): FORGE_AI_SIM_SEAT env hook + copy-throughput probe test

Adds an env var to pin which seat the simulation AI drives, for reranker
seams that need to know which player they're evaluating for. Adds a
throughput probe test measuring game-copy cost during simulation search.

- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`
- `forge-gui-desktop/src/test/java/forge/ai/simulation/CopyThroughputProbeTest.java`

## test(ai): copy probe — tolerate broken GameSnapshot on this base

The copy-throughput probe hits a `GameSnapshot` limitation present on this
base. Adjusts the probe to tolerate it rather than fail on an artifact of
the probe setup itself.

- `forge-gui-desktop/src/test/java/forge/ai/simulation/CopyThroughputProbeTest.java`

## fix(ai): copy probe — set active player turn (GameSnapshot NPE was probe artifact)

Follow-up to the above: the `GameSnapshot` NPE was caused by the probe not
setting an active player turn, not by a real engine bug. Fixes the probe
setup.

- `forge-gui-desktop/src/test/java/forge/ai/simulation/CopyThroughputProbeTest.java`

## feat(ai): FORGE_AI_SIM_BUDGET_MS — per-decision wall-clock budget for simulation AI

Adds an env-driven wall-clock budget per decision for the simulation AI,
so long-running searches can be capped deterministically per run
configuration.

- `forge-ai/src/main/java/forge/ai/simulation/SimulationController.java`
- `forge-ai/src/main/java/forge/ai/simulation/SpellAbilityPicker.java`

## feat(ai): FORGE_AI_SIM_BUDGET_SIMS — work-based per-decision sim budget

Adds a companion budget expressed as a simulation-count ceiling rather
than wall-clock time, for configurations that want deterministic work
bounds independent of machine speed.

- `forge-ai/src/main/java/forge/ai/simulation/SimulationController.java`
- `forge-ai/src/main/java/forge/ai/simulation/SpellAbilityPicker.java`

## feat(ai): FORGE_AI_SIM_SNAPSHOT — route same-phase sim copies through GameSnapshot

Routes same-phase simulation copies through `GameSnapshot` behind an env
flag, as a cheaper alternative to a full game copy when the simulation
stays within the same phase.

- `forge-ai/src/main/java/forge/ai/simulation/GameCopier.java`

## feat(ai): version field in research decision log schema

Adds a `schema_version` field to every JSONL record emitted by
`ResearchDecisionLogger`, so downstream consumers can detect schema drift
without inferring it from field presence.

- `forge-ai/src/main/java/forge/ai/ResearchDecisionLogger.java`

## feat(puzzle): headless puzzle mode (`puzzle -f <file.pzl> -s [seed]`)

Adds a headless sibling to `sim` for solving puzzles without a GUI: loads a
`.pzl` via `PuzzleIO`, puts the AI in both seats (seat 0 = the puzzle's
"human"/solver seat by `GameState`'s own convention, seat 1 = the puzzle's
"ai" seat), reuses `Puzzle`'s existing goal-enforcement trigger machinery to
detect success, and prints one line: `Puzzle Result: name=<basename>
verdict=PASS|FAIL|INVALID turn=<n> ms=<n> timeout_fired=<0|1>`.

Includes three small supporting changes, kept in this patch since none is
useful standalone:
- `PuzzleIO.loadPuzzle(File)`: single-file loader factored out of
  `loadPuzzles(String)`, needed since headless mode loads one explicit path
  rather than scanning a directory.
- `Puzzle.addGoalEnforcement`: falls back to seat 0 when no player
  `isGuiPlayer()` (true for any headless dual-AI game) — otherwise the
  goal-enforcement card has no owner and NPEs. No effect when a real GUI
  human is present.
- `AiController.evalTimeoutFired`: a static canary set when the "Game AI
  Eval" watchdog thread times out. Puzzle mode resets it per puzzle, sets a
  generous 30s per-decision deadline (`Game.AI_TIMEOUT`, default 5s) plus an
  independent 120s overall wall-clock ceiling, and reports `verdict=INVALID
  timeout_fired=1` if either fires — a puzzle that timed out is never
  PASS/FAIL.

The game runs on an explicitly named `Game-Puzzle` thread. This is load-bearing,
not cosmetic: `GameAction.invoke` only runs its `Runnable` synchronously when
`ThreadUtil.isGameThread()` (thread name starts with "Game") holds, and
`puzzle.applyToGame()` dispatches through it. On any other thread the board
setup is queued asynchronously into the Game pool and returns immediately, so
`PhaseHandler.setupFirstTurn`'s hook completes before the board exists and
`mainGameLoop()` races `applyGameOnThread` — observed as intermittent
`ConcurrentModificationException`s in AI combat evaluation and same-seed
verdict flips. The GUI avoids this by wrapping the whole match in
`HostedMatch`'s `game.getAction().invoke(...)`.

A missing `-f` path is reported as `Puzzle file not found: <abs path>` before
loading, since `FileUtil.readFile` returns empty for a missing file and would
otherwise make a bad path indistinguishable from a corrupt puzzle. Both still
yield `verdict=INVALID`.

Also adds `forge-gui/res/ai/ChanceFree.ai`, a data-only AI profile (copy of
`Default.ai` with every chance/percent knob pinned to a deterministic
endpoint) that both puzzle-mode seats default to, so a puzzle's verdict
reflects the AI's judgment rather than `MyRandom.percentTrue` dice inside its
decision tree. Per-knob reasoning is commented inline in the profile file.

- `forge-gui-desktop/src/main/java/forge/view/SimulatePuzzle.java` (new)
- `forge-gui-desktop/src/main/java/forge/view/Main.java`
- `forge-gui/src/main/java/forge/gamemodes/puzzle/PuzzleIO.java`
- `forge-gui/src/main/java/forge/gamemodes/puzzle/Puzzle.java`
- `forge-ai/src/main/java/forge/ai/AiController.java`
- `forge-gui/res/ai/ChanceFree.ai` (new)
