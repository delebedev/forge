# PATCHES.md

Research instrumentation patches on branch `crucible`, applied on top of
upstream `master`. Each entry: name, purpose, files touched. Keep this list
tiny and in sync with the branch — one entry per commit.

## feat(ai): candidate seat-scoped generic cast-trigger MAIN1 admission

`ComputerUtil.castTriggerPumpsAttacker`: on the AI's own precombat main
phase, admit a spell whose cast would fire an active battlefield SpellCast
trigger with a Pump/PumpAll/PutCounter effect on a creature already
predicted to attack — the generic form of the hand-annotated `BuffedBy` /
keyword-Prowess special cases (keyword Prowess is itself an intrinsic
SpellCast trigger). Consulted from `castSpellInMain1` and
`castPermanentInMain1`, evaluated only for the `AiVariant.CANDIDATE` seat;
baseline seats are unchanged.

- `forge-ai/src/main/java/forge/ai/ComputerUtil.java`

## feat(puzzle): FORGE_AI_VARIANT seat routing in headless puzzle mode

`SimulatePuzzle` gains the same `FORGE_AI_VARIANT`/`FORGE_AI_VARIANT_SEAT`
research seam as `SimulateMatch` (seat 1 = solver, seat 2 = opponent);
unset = baseline both seats. Lets the puzzle tier exercise seat-scoped
candidates.

- `forge-gui-desktop/src/main/java/forge/view/SimulatePuzzle.java`

## feat(ai): explicit paired-state and turn identity — schema_version 3

Adds arm-independent `pair_id` plus `active_player`, `priority_player`, and
`next_turn_player` to each priority decision. This lets downstream analysis
distinguish paired games from doubled A/A executions and interpret flash or
deferred actions without reconstructing turn ownership from player names.

Schema: bumps `priority_decision_v2` / `schema_version` 2 to
`priority_decision_v3` / `schema_version` 3. Candidate action schema remains
version 2 because its shape is unchanged.

- `forge-ai/src/main/java/forge/ai/ResearchDecisionLogger.java`

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

## perf(game): opt-in getChangedCardTraitsList empty-case fast-path

A perf change (not instrumentation), carried opt-in and upstream-first, using
Forge's own flag shape: a new `FPref.PERFORMANCE_TRAIT_FASTPATH` ("false") cached
into a `Card` static at `FModel.initialize` — the same mechanism as
`PERFORMANCE_MODE`. Enabled via that FPref (canonical/upstream) or, for headless
harness toggling without the prefs file, the `FORGE_PERF_TRAIT_FASTPATH=true`
environment override (matching the fork's `FORGE_*` convention). When enabled and
both changed-trait tables are empty (the common case),
`Card.getChangedCardTraitsList` skips the `Iterables.concat` + Guava
`TreeBasedTable` cell iteration and returns only the Layer 4 land change.

Default off is byte-identical to upstream (concat of two empties + `[x]` == `[x]`);
verified flag-off preserves the fragile-coordinate transcript and the era-2019
pool. Flag-on measured ~+25% engine at low load, outcome-neutral across the pool
— but validate per-pool at low load before treating flag-on runs as evidence.
Off by default, so default behavior and all recorded runs are unaffected.

- `forge-game/src/main/java/forge/game/card/Card.java`
- `forge-gui/src/main/java/forge/localinstance/properties/ForgePreferences.java`
- `forge-gui/src/main/java/forge/model/FModel.java`

## fix(ai): deterministic mustAttack — run per-attacker checks synchronously

`AiAttackController.declareAttackers` ran each attacker's forced/mustAttack
requirement check in a parallel `CompletableFuture.supplyAsync` (a perf change).
The futures mutate shared `Combat` (`addAttacker`) and are bounded by
`completeOnTimeout`, so the forced-attacker set and combat insertion ORDER are
thread-scheduling-dependent — non-deterministic under CPU load, with no AI-eval
watchdog fire. Upstream #11161 synchronized `addAttacker` to stop the resulting
`ConcurrentModificationException` (which was silently dropping attackers), but
left the ordering non-determinism. This runs the suppliers synchronously on the
calling thread (`supplyAsync(..., Runnable::run)`), so the order is the
`this.attackers` iteration order — deterministic. Verified: a 6-concurrent load
probe that yields 2 distinct decision transcripts on the pinned jar collapses to
1 with this change.

- `forge-ai/src/main/java/forge/ai/AiAttackController.java`

## fix(game): deterministic simultaneous-trigger order — LinkedHashMap in TriggerWaiting

When several triggered abilities fire from one event, the game stacks them in an
order it must reproduce. `TriggerWaiting.setTriggers` collected the (ordered)
triggers into `Maps.newHashMap()`; `getTriggers()` returns `keySet()`, which
`TriggerHandler.runWaitingTrigger` iterates to put them on the stack. `Trigger`
uses identity hashCode, so that iteration order varies per JVM launch —
independent of the RNG seed — making the on-stack order of simultaneous
same-controller triggers (and thus the game outcome) non-reproducible on ~8% of
isolated launches on the era-2019 pool. Fix: `Maps.newLinkedHashMap()`, which
preserves the collected sequence; the downstream APNAP + AI ordering
(`orderPlaySa`) is unchanged. Confirmed with `-XX:hashCode=2` (constant identity
hashcode collapses the divergence) and an event-log diff (Cavalier of Night death
→ Midnight Reaper triggers stacked in a different order). Verified: isolated ×16
and a 6-concurrent load probe both collapse from multiple distinct decision
transcripts to one.

- `forge-game/src/main/java/forge/game/trigger/TriggerWaiting.java`

## feat(ai): batch treatment-assignment and exposure telemetry

Emits one deterministic end-of-batch summary for the selected AI variant,
variant seat, simulation seat, and per-seat callback, positive-work, total-work,
and maximum-work counts.
Crucible uses it to fail closed when a configured treatment is missing,
assigned to the wrong seat, or never reaches the simulation branch. The
counter is observation-only and resets before each headless batch.

- `forge-ai/src/main/java/forge/ai/AiController.java`
- `forge-ai/src/main/java/forge/ai/ResearchTreatmentTelemetry.java`
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`
