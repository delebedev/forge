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
