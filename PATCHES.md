# Fork-Local Patches

Changes on top of upstream Card-Forge/forge. Update this file when adding or removing patches.

## Event enrichments
- `GameEventCardChangeZone` — enriched with immutable source, exact/root/stack ability, API, and cost-payment context
- `GameEventCardSurveiled` — new, carries cause card for per-card surveil tracking
- `GameEventTokenCreated` — enriched with `List<Card>` token refs
- `GameEventCardDestroyed` — immutable affected/source views plus stable source ability context
- `GameEventCardSacrificed` — enriched with stable source ability and cost-payment context
- `GameEventSpellAbilityCast` — enriched with mana payment info and stable source ability context
- `GameEventSpellResolved` — enriched with stable source ability context

Legacy constructors remain for enriched event records. `GameEventCardDestroyed.card()` and
`.activator()` now return immutable `CardView` values instead of `Card`; callers assigning those
accessors to mutable `Card` references require migration.
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
