---
paths:
  - "forge-gui/res/cardsfolder/**"
---

# Card Scripting DSL

Cards defined in `forge-gui/res/cardsfolder/` (~32k cards). Text-based DSL:

```
Name:Abrupt Decay
ManaCost:B G
Types:Instant
R:Event$ Counter | ValidCard$ Card.Self | ValidSA$ Spell | Layer$ CantHappen | ...
A:SP$ Destroy | ValidTgts$ Permanent.nonLand+cmcLE3 | TgtPrompt$ Select target...
Oracle:This spell can't be countered.\nDestroy target nonland permanent...
```

- `A:` = Ability (SP$ = spell ability, AB$ = activated, T$ = triggered)
- `R:` = Replacement effect
- `S:` = Static ability
- `T:` = Triggered ability
- `K:` = Keyword
