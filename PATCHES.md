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
- `GameEventFlipCoin` — enriched with flipper, source ability, and result

## Seams
- `PlayerControllerHuman.selectTargetsInteractively()` — overridable target selection
- `TargetSelectionResult` — result type for the seam
- `DraftPickStrategy` — injectable booster-draft bot pick strategy

## Utilities
- `MyRandom.setSeed()` + shuffle routing — deterministic replay support
- `CardDb.quietInit` — suppress card-init warnings in test harnesses
- `GameState` puzzle cards support `CommanderCast:N` — seed prior command-zone cast counts for commander-tax fixtures

## Engine fixes
- `EndureEffect` — set `tokenSpawningAbility` on the Spirit token, mirroring `TokenEffectBase` / `AmassEffect` / `CopyPermanentEffect` / `ReplaceTokenEffect`. Without it, `Card.tokenSpawningAbility` is null on Endure-spawned tokens.
- Commander replacement effects — include graveyard and exile destinations for non-Oathbreaker commander command-zone replacement. Without it, normal commander deaths and exile moves skip the replacement decision.
- `PARADIGM` keyword — fire the free exile-copy trigger at the beginning of upkeep instead of the first main phase, matching the keyword's intended timing. Touches the `ParadigmTrigger` phase in `CardFactoryUtil` and the reminder text in `Keyword.java`.
