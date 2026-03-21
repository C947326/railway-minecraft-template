# Fugitive Tech Baron NPC

This document specifies a Minecraft server encounter inspired by a paranoid, on-the-run software tycoon. The character should be an obvious parody rather than a direct recreation of any real person.

## Design Intent

The NPC is curious about players from a distance, suspicious at medium range, and panics when strangers get too close. The exception is a specific recognition item held in the player's main hand. If the player presents that item, the NPC holds position long enough to talk, trade, or offer quests.

This produces three useful effects:

- the NPC reads as a personality rather than a static vendor
- players quickly learn the rule through behavior instead of tutorial text
- the encounter can branch into trust, flight, or disappearance

## Narrative Hook

The fugitive is the vanished investor behind `Techtonic`, a hugely successful podcast created by the players. After the show became profitable, he stole the money, disappeared into a network of hideouts, and left behind only bad paperwork, erratic notes, and expensive nonsense.

This gives the encounter a clear purpose:

- the players are hunting him for their money
- the hideouts are evidence of his flight
- earning trust does not restore the funds so much as unlock his excuses, clues, and ridiculous substitute repayments

## Hunt Loop

Players should begin with a `Brothel Radar` rather than perfect knowledge. The radar does not identify the Baron directly. It identifies seven possible hideouts, one of which is active.

Recommended rules:

- there are exactly `7` fixed hideouts
- each player receives one Brothel Radar automatically
- the radar updates on a short timer rather than every tick
- the radar points toward the nearest hideout signal, not the Baron himself
- the active hideout may contain the Baron, while the others contain clues, supplies, or evidence of movement

This keeps the Baron singular and exciting without making him effectively impossible to locate.

## Core Mechanic

Recognition item:

- base item: `GUNPOWDER`
- display name: `Suspicious White Powder`
- detection rule: the player must hold the item in the main hand

Behavior summary:

- player far away: NPC watches and studies them
- player mid-range: NPC becomes visibly uneasy and plays suspicious lines
- player too close without the item: NPC runs
- player too close with the item: NPC does not flee and may speak or offer interaction

## Encounter States

Recommended state machine:

`idle -> observe -> suspicious -> flee`

or

`idle -> observe -> suspicious -> trust`

depending on the player's held item.

### Idle

The NPC wanders within a small radius, pauses often, and occasionally looks over their shoulder.

Entry conditions:

- initial spawn
- no players in detection radius

Actions:

- random short patrols
- occasional crouch or head-turn behavior
- rare ambient mutter line

Exit conditions:

- player enters awareness radius

### Observe

The NPC has seen the player and is now interested, but not yet alarmed.

Recommended radius:

- `12` blocks

Actions:

- turn to face the nearest player
- move sideways or circle rather than approach directly
- play a low-intensity curiosity line on cooldown

Exit conditions:

- player leaves awareness radius -> `idle`
- player enters suspicion radius -> `suspicious`

### Suspicious

The NPC is concerned and starts deciding whether to run.

Recommended radius:

- `7` blocks

Actions:

- backpedal from the player
- increase movement speed slightly
- play suspicious voice lines on a separate cooldown
- check the player's main hand every tick or on a short interval

Exit conditions:

- player leaves suspicion radius -> `observe`
- player enters panic radius without recognition item -> `flee`
- player enters panic radius with recognition item -> `trust`

### Trust

The NPC accepts the player's presence only while the recognition item remains visible.

Recommended radius:

- `3` blocks

Actions:

- stop retreating
- face the player
- play rarer acceptance lines
- unlock right-click interaction, dialogue, barter, or quest options

Exit conditions:

- player switches away from the item while inside trust radius -> `flee`
- player attacks the NPC -> `escape`
- player walks away -> `suspicious` or `observe`, depending on distance

### Flee

The NPC sprints away from the player and tries to break line of sight.

Actions:

- apply a temporary speed boost
- path away from the triggering player
- optionally drop a smoke or particle effect
- play a panic line once per trigger

Exit conditions:

- player loses proximity after a timeout -> `idle` or despawn
- NPC reaches safe distance -> `observe`

### Escape

Reserved for hard failure conditions, such as being attacked.

Actions:

- play a unique line
- spawn smoke particles
- despawn after a brief delay

Trigger conditions:

- player damages the NPC
- repeated aggression or trapping

## Distance Bands

Suggested defaults:

- awareness radius: `12`
- suspicion radius: `7`
- panic/trust radius: `3`
- give-up distance while fleeing: `16`

These should be configurable so server owners can tune the encounter in cramped bases versus open terrain.

## Recognition Item Rules

The simplest implementation is to key off the material type alone:

- accept any `GUNPOWDER`

The more flavorful implementation is to require a tagged custom item:

- material: `GUNPOWDER`
- custom display name: `Suspicious White Powder`
- optional custom model data or data component for a resource-pack icon

Recommendation:

- implement phase 1 using plain `GUNPOWDER`
- add named-item support later if the server wants a more curated questline

This keeps the first iteration legible and easy to test.

## Dialogue Pools

Do not use real interview audio unless you have a clear legal right to do so. The safer design is parody writing, an original voice actor, or a synthetic voice that evokes the archetype without imitating a real person exactly.

### Curiosity Lines

- "You noticed me before I noticed you. Interesting."
- "No one wanders this far by accident."
- "Stand still a moment. I am assessing probabilities."

### Suspicion Lines

- "Too calm. That is always a warning sign."
- "Keep your hands where ambition can see them."
- "I dislike patterns, and you are beginning to form one."

### Acceptance Lines

- "Ah. A person of unusual inventory."
- "Good. You speak the language of discretion."
- "Very well. We may proceed as professionals."

### Panic Lines

- "No, no, no. Too close."
- "Absolutely not."
- "Distance is the foundation of trust."

### Escape Lines

- "You have ruined a perfectly good conspiracy."
- "This location is now compromised."

## Interaction Rewards

Once the NPC enters `trust`, the player can unlock one of the following:

- cryptic coordinates to a buried chest
- access to a black-market barter table
- one-step delivery quests
- lore fragments about hidden outposts
- Techtonic-related evidence explaining where the money supposedly went

Recommendation:

- first ship with dialogue and simple barter
- add quests only after the base encounter feels reliable

## Spawn and Despawn Rules

Recommended behavior:

- spawn in unusual or remote biomes
- avoid permanent residence at spawn
- despawn after a long period without player interaction
- despawn immediately after `escape`

Suggested spawn flavor:

- jungle edge
- swamp
- warm beach
- sparse savanna hills

## Anti-Abuse Rules

To prevent trivial farming or grief loops:

- add a cooldown before the same player can retrigger the trust state
- do not allow repeated line spam more often than every few seconds
- despawn if trapped in a tiny enclosure
- treat damage as an immediate escape event

## Implementation Sketch For A Paper Plugin

Useful components:

- `NpcController`: owns current state, current target player, and state transitions
- `DistanceEvaluator`: computes nearest relevant player and radius band
- `RecognitionItemChecker`: validates the player's main-hand item
- `DialogueService`: chooses line pools and enforces cooldowns
- `MovementService`: handles backing away, fleeing, and facing behavior
- `InteractionService`: opens barter or quest interactions in trust state

Likely event hooks:

- entity tick or scheduled task for state evaluation
- player move events only if you want early wake-up optimization
- player interact event for trust-state interaction
- entity damage event for forced escape
- chunk unload or world save hooks if persistence matters

## Pseudocode

```text
every tick:
  nearestPlayer = findNearestPlayer(npc, awarenessRadius)
  if no nearestPlayer:
    state = idle
    return

  distance = getDistance(npc, nearestPlayer)
  hasRecognitionItem = isHoldingRecognitionItem(nearestPlayer)

  switch state:
    idle:
      if distance <= awarenessRadius:
        state = observe

    observe:
      face(nearestPlayer)
      if distance > awarenessRadius:
        state = idle
      else if distance <= suspicionRadius:
        state = suspicious

    suspicious:
      backAwayFrom(nearestPlayer)
      if distance > suspicionRadius:
        state = observe
      else if distance <= panicRadius and hasRecognitionItem:
        state = trust
      else if distance <= panicRadius:
        state = flee

    trust:
      face(nearestPlayer)
      if !hasRecognitionItem and distance <= panicRadius:
        state = flee
      else if distance > suspicionRadius:
        state = observe

    flee:
      runAwayFrom(nearestPlayer)
      if distance >= giveUpDistance:
        state = idle
```

## First Playtest Goals

The initial build is successful if:

- players can infer the held-item rule without reading documentation
- the NPC feels curious before feeling frightened
- trust and panic transitions happen immediately and read clearly
- sound or subtitle lines do not spam or overlap

## Recommended First Slice

Build the encounter in this order:

1. spawn the NPC and implement `idle`, `observe`, `suspicious`, `flee`
2. add the recognition-item exception that converts panic into `trust`
3. add dialogue pools with cooldowns
4. add a simple right-click reward while in `trust`
5. add audio, subtitles, and polish
