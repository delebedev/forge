# Fork-Local Patches

Changes on top of upstream Card-Forge/forge. Update this file when adding or removing patches.

## Event enrichments
- `GameEventCardSurveiled` — new, carries cause card for per-card surveil tracking
- `GameEventTokenCreated` — enriched with `List<Card>` token refs
- `GameEventCardDestroyed` — enriched with card + activator refs
- `GameEventSpellAbilityCast` — enriched with mana payment info
- `GameEventManaAbilityActivated` — new, fired on mana ability resolution
- `GameEventSpellMovedToStack` — new, fired on stack entry
- `GameEventControllerChanged` — new, fired on controller change
- `GameEventExtrinsicKeywordAdded` — new, fired on keyword grants

## Seams
- `PlayerControllerHuman.selectTargetsInteractively()` — overridable target selection
- `TargetSelectionResult` — result type for the seam

## Utilities
- `MyRandom.setSeed()` + shuffle routing — deterministic replay support
- `CardDb.quietInit` — suppress card-init warnings in test harnesses
- `GameState` puzzle cards support `CommanderCast:N` — seed prior command-zone cast counts for commander-tax fixtures

## Engine fixes
- `EndureEffect` — set `tokenSpawningAbility` on the Spirit token, mirroring `TokenEffectBase` / `AmassEffect` / `CopyPermanentEffect` / `ReplaceTokenEffect`. Without it, `Card.tokenSpawningAbility` is null on Endure-spawned tokens.
