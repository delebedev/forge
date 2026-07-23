# PATCHES.md

Research instrumentation patches on branch `crucible`, applied on top of
upstream `master`. Each entry: name, purpose, files touched. Keep this list
tiny and in sync with the branch — one entry per commit.

## perf(startup): FORGE_PERF_SKIP_DECKGEN — headless runs skip the deck-gen matrix

`FModel.initialize` eagerly builds the deck-builder card-relation matrix and
archetype LDA (`FPref.DECKGEN_CARDBASED`, default on), deserializing ~22.5k
PaperCards across every set. A headless `sim` or `puzzle` run plays pre-built
decks and never reads that data, so it is dead weight on every JVM launch —
and launches dominate at batch scale. `FORGE_PERF_SKIP_DECKGEN=true` skips it,
matching the `FORGE_PERF_*` shape already used by the trait fast-path. Default
(unset) is upstream behaviour untouched.

- `forge-gui/src/main/java/forge/model/FModel.java`

## feat(sim): AI-eval watchdog is configurable and loud — never a silent truncation

The `"Game AI Eval"` watchdog abandons a scan after `Game.AI_TIMEOUT` (5s by
default) and falls through to whatever the AI had, usually PASS: the game log
looks normal, but the decision was truncated and the shared RNG stream shifted,
surfacing as divergence many decisions later. Measured firing in ordinary
single-worker headless runs, so it silently degraded evidence at every worker
cap. Now `FORGE_RESEARCH_AI_EVAL_TIMEOUT=<seconds>` sets the ceiling for a
`sim` JVM (unset = upstream default; non-positive/unparseable fails fast), and
each fire prints `Research AI Eval Timeout: turn=… phase=… player=… seconds=…`
plus, per batch game, `Research Game Canary: game=<n> kind=ai_eval_timeout
fires=<k>` before that game's result line — the sim-mode parity of puzzle
mode's `timeout_fired`, so a harness can invalidate the game instead of
scoring a truncated transcript. Fire count is per game (reset at game start);
`evalTimeoutFired` keeps its puzzle-mode meaning.

- `forge-ai/src/main/java/forge/ai/AiController.java`
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`
- `forge-gui-desktop/src/test/java/forge/view/AiEvalTimeoutTest.java`

## fix(ai): research probes are strictly one-ply — never recurse into the sim-brain follow-up search

`GameSimulator.simulateSpellAbility` consults `controller.shouldRecurse()`
after resolving and, when allowed, spawns a full `SpellAbilityPicker`
follow-up search on the sim game. Research probes passed a fresh depth-0
`SimulationController`, so every "one-ply" probe was a depth-bounded tree of
nested game copies — OOM at 6g on grindy boards (T1 canary, candidate
esper-control vs mono-green-stompy, all 5 games exit 1).
`ResearchOnePlySimulationController` refuses recursion; used by
`ResearchTopKRerank` and `ResearchPolicySearch` (same latent defect, never
exercised in shipped arms). Verified: the crashing coordinate completes 5/5
games in seconds on the fixed jar.

- `forge-ai/src/main/java/forge/ai/ResearchOnePlySimulationController.java`
- `forge-ai/src/main/java/forge/ai/ResearchTopKRerank.java`
- `forge-ai/src/main/java/forge/ai/ResearchPolicySearch.java`
- `forge-gui-desktop/src/test/java/forge/ai/ResearchTopKRerankTest.java`

## feat(ai): topk-rerank candidate — bounded top-k one-ply rerank after the greedy veto scan

The greedy scan returns the first WillPlay candidate and never compares
playable options (upstream's own TODO). On a candidate seat with the
`topk-rerank` feature, own main phase, empty stack, the scan keeps going and
collects up to `FORGE_AI_TOPK_K - 1` (default k=3, clamped 1..8) further
WillPlay candidates; `ResearchTopKRerank` then simulates one resolution of
each (fresh `GameSimulator` per probe — live game never mutated) and overrides
the greedy choice only when an alternative beats its simulated score by
`FORGE_AI_TOPK_MIN_DELTA` (default 1) via the kind-aware `Score.meetsThreshold`
(a failed simulation never overrides and is never overridden). Runs after the
neural/policy seams and outside the eval-thread watchdog, so a timeout can
never eat the greedy fallback. Probed-but-unchosen SpellAbilities are restored
(targets + X); modal (Charm) chains are excluded from probing in v1. Disabled
paths (baseline seat, feature unselected, reactive/stack decisions) are
byte-identical — verified by a wrong-feature candidate decision-log parity
probe against the parent jar. Contract in `ResearchTopKRerankTest` (8 tests).

- `forge-ai/src/main/java/forge/ai/ResearchTopKRerank.java`
- `forge-ai/src/main/java/forge/ai/ResearchCandidateFeatures.java`
- `forge-ai/src/main/java/forge/ai/AiController.java`
- `forge-gui-desktop/src/test/java/forge/ai/ResearchTopKRerankTest.java`

## fix(ai): typed Score kinds — terminal, failure, and no-score states never enter arithmetic

`GameStateEvaluator.Score` used raw int sentinels: `Integer.MAX_VALUE` for a
terminal win and `Integer.MIN_VALUE` ambiguously for terminal loss, simulation
failure, and the best-so-far initializer. Sentinels entered arithmetic
(`fallbackScore.value + MIN_DELTA`, `currentScore.value + effect.scoreDelta`,
`score.value - initialScore.value`) where overflow can invert a gate or cache
nonsense, and a failed simulation was indistinguishable from a lost game.
Now `Score` carries a `Kind` (`FINITE`/`WIN`/`LOSS`/`SIM_FAILURE`/`NONE`) with
factories, and all threshold/delta math goes through kind-aware APIs:
`meetsThreshold` (long arithmetic, failure never beats and is never beatable),
saturating `addDelta`, and `finiteDelta` plus effect-cache suppression for
non-finite scores. `.value` keeps the legacy sentinel ordinals so untouched
comparison sites are behavior-identical; `equals` is kind-aware. Contract in
`ScoreSafetyTest` (23 tests); `SpellAbilityPickerSimulationTest` (135) green.

- `forge-ai/src/main/java/forge/ai/simulation/GameStateEvaluator.java`
- `forge-ai/src/main/java/forge/ai/simulation/GameSimulator.java`
- `forge-ai/src/main/java/forge/ai/simulation/SimulationController.java`
- `forge-ai/src/main/java/forge/ai/simulation/SpellAbilityChoicesIterator.java`
- `forge-ai/src/main/java/forge/ai/simulation/SpellAbilityPicker.java`
- `forge-ai/src/main/java/forge/ai/ResearchPolicySearch.java`
- `forge-gui-desktop/src/test/java/forge/ai/simulation/ScoreSafetyTest.java`

## feat(sim): FORGE_RESEARCH_SEED_PROTOCOL — per-game RNG reseed in seeded batches

`SimulateMatch`: `FORGE_RESEARCH_SEED_PROTOCOL=game` (with `-s SEED -n N`)
reseeds `MyRandom` at the top of every batch game from a SplitMix64 mix of
`(seed, gameIndex)` instead of seeding once per JVM, and gives each game a
fresh `Match` (a reused Match hands the previous game's loser the play, so
game i's opening would otherwise depend on game i-1's outcome). Game i's
opening state then depends only on `(seed, i)` — so game i is the same
game across arms until in-game play diverges (true game-level pairing),
and a batch can be sharded by game without changing any game. The mix is
mandatory: raw sequential seeds correlate `java.util.Random` streams.
Unset or `batch` keeps the historical stream byte-for-byte (parent-parity
contracts stay valid); unknown values and `game` without `-s` fail fast.
Match mode (`-m`) and puzzle-mode seeding are unchanged.

- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`

## feat(ai): FORGE_AI_CANDIDATE_FEATURES — per-feature candidate gating

`ResearchCandidateFeatures`: comma-separated env subset of the known
candidate features (`cast-trigger-main1`, `mandatory-etb`) that the
`AiVariant.CANDIDATE` seat evaluates. Unset = all features (plain candidate
seat); unknown names fail fast so a typo can never silently run a different
experiment. Lets several candidate policies coexist in one pinned jar while
each experiment activates exactly one, preserving causal attribution.
Baseline seats never consult it.

- `forge-ai/src/main/java/forge/ai/ResearchCandidateFeatures.java` (new)
- `forge-ai/src/main/java/forge/ai/ComputerUtil.java`
- `forge-ai/src/main/java/forge/ai/AiController.java`
- `forge-gui-desktop/src/test/java/forge/ai/ResearchCandidateFeaturesTest.java` (new)

## feat(ai): mandatory-ETB candidate — zero-legal-target fizzle escape

Extension of the mandatory-ETB admission candidate: a forced trigger made
entirely of required-target effects with zero legal candidates never
resolves — it fizzles, harming nobody — so it is no reason to veto the
cast. Same `AiVariant.CANDIDATE` gating.

- `forge-ai/src/main/java/forge/ai/AiController.java`

## feat(ai): candidate seat-scoped mandatory-ETB admission under mandatory semantics

`AiController.mandatoryTriggerResolvesAgainstOpponent`: when permanent
admission would veto a cast because the AI declined its mandatory ETB
trigger as an opt-in (`BadEtbEffects`), re-evaluate a fresh copy under
`doTrigger(mandatory=true)`; lift the veto only when that evaluation
accepts and commits at least one target with every chosen target belonging
to an opponent — a forced resolution that is at worst neutral for the
activator. Non-targeted mandatory effects (sacrifice/self-damage shapes)
never take the escape. Evaluated only for the `AiVariant.CANDIDATE` seat.

- `forge-ai/src/main/java/forge/ai/AiController.java`

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

## fix(ai): absolute sim-nesting cap in shouldRecurse (simulatorStack bound)

Cherry-picked from `research/feat/ai-research-harness-upstream` (`ba6d14432e5`).
Hardening against plan-level depth under-counting nested simulators; kept
although the observed snapshot-mode crash proved to be heap exhaustion, not
unbounded recursion.

- `forge-ai/src/main/java/forge/ai/simulation/SimulationController.java`

## fix(game): GameSnapshot fidelity — counters, SVars, layered traits, PT tables, exert

Cherry-picked from `research/feat/ai-research-harness-upstream` (`cc2ee0ed29c`).
Canary-driven fidelity fixes (`ensureGameCopyScoreMatches`): copy-error fires
6/6 -> 2/6 games on a selesnya-vs-red-aggro probe. Fixes counters (P/T,
loyalty were lost), state SVars + the changedSVars layer, `Card.copyFrom` for
layered types/colors/keywords/traits, PT tables (animation P/T), and
exert-by-player state. Not required for the value-net MVP eval path itself,
but the value net trains on and scores simulated states, so snapshot fidelity
during simulation is load-bearing for its inputs. Remaining fires are small
(+/-1-2pt) nits, documented in the source commit.

- `forge-game/src/main/java/forge/game/card/Card.java`

## feat(ai): ResearchValueNet — in-JVM learned state-value blend

Reworked from `research/feat/ai-research-harness-upstream` (`5037139a8c8`):
same `GameStateEvaluator` blend seam in `SpellAbilityPicker` (post-simulation
score adjustment), but the value net is a small MLP loaded from a weights
JSON and run in-JVM — no HTTP client, no network, no fail-open: per-decision
HTTP dies at simulation call volume (tens-100+ scored states per decision) and
timeout/fallback made transcripts nondeterministic. `FORGE_AI_VALUE_NET=<path>` loads once per JVM;
`FORGE_AI_VALUE_NET_SHA`, if set, must match the file's sha256 or loading
fails fast. Seat-scoped via `AiVariant.CANDIDATE` — env unset means the class
never runs. `FORGE_AI_VALUE_BLEND` sets the blend lambda (default 0).
Feature extraction shares field names 1:1 with the `priority_decision_v3`
JSON schema via a new shared `ResearchDecisionLogger.boardSummary()`, so the
Python trainer (crucible `scripts/valuenet/`) and this forward pass can't
drift apart silently — see crucible `docs/neural.md`.

- `forge-ai/src/main/java/forge/ai/ResearchValueNet.java` (new)
- `forge-ai/src/main/java/forge/ai/ResearchDecisionLogger.java`
- `forge-ai/src/main/java/forge/ai/simulation/GameSimulator.java`
- `forge-ai/src/main/java/forge/ai/simulation/SpellAbilityPicker.java`
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`

## feat(ai): per-seat CreatureEvaluator weight tables + turn-level feature capture

Research seam for evaluator-weight fitting. Every CreatureEvaluator term
already flows through `addValue(value, label)`; this patch makes those
contributions (a) per-seat reweightable and (b) capturable as per-turn
feature vectors, with stock behavior bit-identical whenever the env is unset.

- **Injection**: `FORGE_AI_EVAL_WEIGHTS=<file>` + `FORGE_AI_EVAL_WEIGHTS_SEAT=<n>`
  load a `term = multiplier` table (validated against the canonical term set;
  any parse error aborts the run — a candidate arm must never silently run
  stock). The table rides `LobbyPlayerAi → PlayerControllerAi → AiController`
  like `AiVariant`, and reaches the static `ComputerUtilCard.evaluateCreature`
  funnel via an `InheritableThreadLocal` context set at the three decision
  surfaces that price creatures: priority (whose "Game AI Eval" thread inherits
  it), `declareAttackers`, `declareBlockers`. No context → stock singleton.
  Startup prints `RESEARCH_EVAL_WEIGHTS seat=… sha256=… terms=…` as exposure
  evidence. The sim brain's `SimulationCreatureEvaluator` is out of scope.
- **Capture**: `FORGE_AI_EVAL_FEATURES_LOG=<file>` records, once per (game,
  turn) at the first observed priority decision, one JSONL record per player:
  per-term contribution sums over that player's battlefield creatures
  (`ResearchCollectingCreatureEvaluator`, stock arithmetic; `_base` +
  `_residual` make sum(features) == stock score exact by construction, with an
  independently recomputed `reconcile_delta` tripwire).

- `forge-ai/src/main/java/forge/ai/ResearchCreatureWeights.java` (new)
- `forge-ai/src/main/java/forge/ai/ResearchWeightedCreatureEvaluator.java` (new)
- `forge-ai/src/main/java/forge/ai/ResearchCollectingCreatureEvaluator.java` (new)
- `forge-ai/src/main/java/forge/ai/ResearchEvalFeatureLogger.java` (new)
- `forge-ai/src/main/java/forge/ai/ComputerUtilCard.java`
- `forge-ai/src/main/java/forge/ai/AiController.java`
- `forge-ai/src/main/java/forge/ai/LobbyPlayerAi.java`
- `forge-ai/src/main/java/forge/ai/PlayerControllerAi.java`
- `forge-ai/src/main/java/forge/ai/ResearchDecisionLogger.java`
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`
- `forge-gui-desktop/src/test/java/forge/ai/ResearchCreatureWeightsTest.java` (new)
- `forge-gui-desktop/src/test/java/forge/ai/ResearchEvalWeightsAiTest.java` (new)
