# Fork-Local Patches

Changes on top of upstream Card-Forge/forge. Update this file when adding or removing patches.

## Event enrichments
- `GameEventCardChangeZone` — enriched with immutable source, exact/root/stack ability, API, and cost-payment context
- `GameEventCardTapped` — enriched with the source ability that caused the tap
- `GameEventCardSurveiled` — new, carries cause card for per-card surveil tracking
- `GameEventTokenCreated` — enriched with `List<Card>` token refs
- `GameEventCardDestroyed` — immutable affected/source views plus stable source ability context
- `GameEventCardDamaged` and `GameEventPlayerDamaged` — enriched with structural damage source kind (combat, spell/ability, or fight)
- `GameEventCardSacrificed` — enriched with stable source ability and cost-payment context
- `GameEventSpellAbilityCast` — enriched with mana payment source, color, producing ability definition, and stable source ability context
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
- `PhaseHandler` main-loop and combat-mutation completion hooks — transient UI-neutral lifecycle callbacks
- `SpellAbility`, `Trigger`, and `StaticAbility` definition IDs — stable identity retained across runtime copy chains; spell and source-trigger identity exposed through `SpellAbilityView`
- `PlayerControllerHuman.selectTargetsInteractively()` — overridable target selection
- `DiscardEffect` — routes revealed empty valid-card choices through the controller callback while the default human controller auto-resolves zero-card selections
- `TriggerHandler.getDelayedTriggersSnapshot()` — read-only delayed trigger inspection
- `TargetSelectionResult` — result type for the seam
- `DraftPickStrategy` — injectable booster-draft bot pick strategy
- `HumanCostDecision` sacrifice, discard, return, unattach, ordinary tap, untap, ordinary exile, enlist, forage, exert, gain-control, and hand-to-library selection — routes exact-count choices through `PlayerController.chooseCardsForCost()`
- `HumanCostDecision` reveal and behold selection — routes constrained reveal choices through `PlayerController.chooseCardsForRevealCost()`
- `HumanCostDecision` collect-evidence selection — routes weighted mana-value choices through `PlayerController.chooseCardsForCollectEvidence()`

## Utilities
- `CardDb.quietInit` — suppress card-init warnings in test harnesses
- `GameState` puzzle cards support `CommanderCast:N` — seed prior command-zone cast counts for commander-tax fixtures

## Fixes
- `AbstractMulligan.mulligan()` — dropped unconditional 100ms pacing sleep (GUI animation pacing; headless callers paid it per mulligan)
- `InvestigateEffect` — token-created events contain only tokens from the current player iteration
- `MagicStack.peekAbility()` — returns no ability when the stack is empty
- `MagicStack` — synchronizes an existing stack-item view with its spell ability before publishing a stack-entry event
