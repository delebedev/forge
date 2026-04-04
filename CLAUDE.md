# Forge — MTG Rules Engine

Open-source Magic: The Gathering game client. Java 17+, Maven, libGDX.

This is a fork of Card-Forge/forge. We largely do searching and seam-style changes unless specified otherwise.

## Git Setup

```
origin   → https://github.com/delebedev/forge.git (fork)
upstream → https://github.com/Card-Forge/forge.git (public, original)
```

## Modules

- **forge-core/** — card definitions, mana system
- **forge-game/** — rules engine, game state, turns, phases, stack
- **forge-ai/** — AI logic
- **forge-gui/** — shared GUI resources
- **forge-gui-desktop/** — Swing+libGDX client

## Key Directories

- `forge-gui/res/cardsfolder/` — Card scripts (~32k cards, a-z subdirs)
- `forge-gui/res/editions/` — Set definitions
- `forge-game/src/main/java/forge/game/` — Core rules engine

## Build & Run

Never use bare `mvn` (hits JDK 25).

Build the desktop JAR:
```
mvn -pl forge-gui-desktop package -am -DskipTests -Dcheckstyle.skip=true -Denforcer.skip=true
```

Run the desktop client (must run from `forge-gui/` dir for resources path):
```
cd forge-gui
java -Xmx4096m -jar ../forge-gui-desktop/target/forge-gui-desktop-*-jar-with-dependencies.jar
```

## PR Policy

- Branch + PR for all changes. Never commit to master.
- One PR per logical change — don't batch unrelated patches.
- Commit messages: no references to downstream consumers, client protocols, or proprietary software. Frame as generic engine improvements.
- Upstream PRs to Card-Forge/forge require explicit approval. Don't open without asking.
- Update `PATCHES.md` when adding or removing fork-local patches.
- Merge strategy: squash if PR is one logical change; rebase if commits are independently meaningful.

## Testing (reference only — we don't run tests in this fork)

Tests in `*/src/test/java/`. Key test classes:
- `GameSimulationTest` — AI game simulations
- `BaseGameSimulationTest` — Rule scenario harness
- `ComprehensiveRulesSection*` — MTG rules compliance

## Resources

- Upstream: https://github.com/Card-Forge/forge
- Wiki: https://github.com/Card-Forge/forge/wiki
