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

## feat(sim): add seat-scoped cascade calibration controls

Adds `FORGE_RESEARCH_CONTROL=sham|start-at-zero|fail` plus the required
one-based `FORGE_RESEARCH_CONTROL_SEAT`. These controls exist only to exercise
the external promotion harness against real Forge batches:

- `sham` traverses parsing and seat routing without changing the game;
- `start-at-zero` deterministically gives the selected registered player zero
  starting life, providing the same signed control on either harness arm;
- `fail` exits with status 42 before Forge starts background services so the
  harness receives a prompt nonzero evaluator exit and must surface a hard
  canary.

The control name and seat travel through the harness arm environment and are
therefore part of experiment provenance. With the environment unset, upstream
and baseline behavior are unchanged. These are calibration sentinels, never AI
strength candidates.

- `forge-gui-desktop/src/main/java/forge/view/ResearchControl.java` (new)
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`
- `forge-gui-desktop/src/test/java/forge/view/ResearchControlTest.java` (new)
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

## feat(ai): per-seat baseline/candidate research variant

Adds a typed variant carried from `LobbyPlayerAi` through
`PlayerControllerAi` into `AiController`. Headless match simulation parses one
variant and seat at startup; every other seat remains baseline. Candidate
artifacts can guard a proposed policy with `usesCandidateVariant()` while both
experiment arms execute the same jar.

- `forge-ai/src/main/java/forge/ai/AiVariant.java`
- `forge-ai/src/main/java/forge/ai/LobbyPlayerAi.java`
- `forge-ai/src/main/java/forge/ai/PlayerControllerAi.java`
- `forge-ai/src/main/java/forge/ai/AiController.java`
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`
- `forge-gui-desktop/src/test/java/forge/ai/AiVariantTest.java`
- `forge-gui-desktop/src/test/java/forge/ai/AiVariantControllerTest.java`

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

## fix(ai): total-order mana tie-breaks — deterministic tap order across JVM launches

Two mana-payment tie-breaks in `ComputerUtilMana` were resolved only up to a
stable sort's *input* order, and that input order is the iteration order of
`ManaCostShard`-keyed HashMaps. `ManaCostShard` is an enum, so its keys iterate
in JVM identity-hashcode order, which varies per JVM launch (HotSpot's default
`hashCode=5` is a per-launch xorshift). Equal-scoring mana sources (e.g. two
equal dual lands) therefore tapped in a per-launch-varying order: a two-land tap swap when
casting a board wipe cascades into a scry (top vs bottom) and a spell-choice
flip that changes the game outcome. This set the real cross-invocation floor
for paired comparison; pinned by a per-decision mana-order
diff across two JVM launches (identical board, identical card ids, only the
insertion order differed).

Both sites get a total, launch-stable secondary key:
- `sortManaAbilities`: `orderedCards.sort` was keyed on the score alone, so ties
  kept insertion order (the `sourcesForShards.keySet()` iteration). Added a
  card-id secondary key (`Card::getId`).
- `getNextShardToPay`: `shardsToPay.sort` was keyed on source-count alone, so
  ties kept `getDistinctShards()` (a HashMap keySet) order. Added an enum-ordinal
  secondary key — the enum's declaration order is deliberately "fewest ways to
  pay first," so this is a sensible as well as stable order.

Card ids and enum ordinals are deterministic within a game (verified: the card
ids at the divergent decision are identical across launches), so both tie-breaks
are now launch-stable. This is a deliberate, minimal engine fix (not
instrumentation): it deterministically changes which of two equal sources taps,
so some game outcomes change vs the nondeterministic baseline — expected, since
the goal is determinism, not preservation of the old (coin-flip) outcomes. The
crucible puzzle corpus was re-checked against this jar; expectations unchanged.

Verified by re-measurement: with the fix, K JVM launches of the fragile
coordinate (mono-blue-winds vs esper-control, seed 13000) collapse to one
trajectory (result-flip floor 0), where the clean pin split ~2 trajectories.

- `forge-ai/src/main/java/forge/ai/ComputerUtilMana.java`

## feat(ai): log per-candidate AiPlayDecision (evaluated prefix) — schema_version 2

Threads each candidate's veto-gauntlet `AiPlayDecision` into
`ResearchDecisionLogger` so `bench triage` can build a refusal-reason
histogram. Previously the JSONL recorded which candidate was chosen but never
why the losers lost.

Instrumentation only — no gameplay change. The greedy scan
(`AiController.chooseSpellAbilityToPlayFromList`) already computes an
`AiPlayDecision` per candidate and discards it; this captures the
already-computed value into a thread-confined `IdentityHashMap` and hands it
back via the eval task's result. **It records only the prefix the scan
actually evaluated** — the scan short-circuits at the first `WillPlay`, so
candidates sorted after the winner are never evaluated and stay blank.
Deliberately does NOT force `canPlaySa`/`canPlayAndPayFor` on post-winner
candidates: that would do work Forge skips and trigger its side effects (mana
reservations, `AiCardMemory`, predicted-combat caches), changing play.

The map is published only on the eval task's normal completion, so a timed-out
scan never has its partial map read while the eval thread may still mutate it.

Schema: bumps `schema_version` 1 → 2 (and the `schema` / candidate
`action_schema` strings to `*_v2`); each candidate object gains an
`ai_play_decision` string — the `AiPlayDecision.name()` for evaluated
candidates, blank (`""`) for the synthetic PASS candidate and any candidate
after the winner. Verified no puzzle-state change vs. baseline and contract
tests green.

- `forge-ai/src/main/java/forge/ai/AiController.java`
- `forge-ai/src/main/java/forge/ai/ResearchDecisionLogger.java`
- `forge-ai/src/main/java/forge/ai/ResearchNeuralReranker.java`
