# Baron Brothel Network Plan

This document expands the Baron hunt from a small set of hideouts into a broader vice network around spawn. The purpose is to make the `Brothel Radar` feel properly named and mechanically useful.

## Core Idea

The `Brothel Radar` should not point directly to John. It should point to places inside his ecosystem:

- brothels
- vice houses
- back-room bars
- laundering spots
- rumor hubs
- hideouts

The player then uses those places to gather clues that narrow the search toward the active hideout.

That makes the loop:

1. radar finds vice-linked sites
2. site contains Baron traces, rumors, or receipts
3. clue points toward the next stronger lead
4. strongest lead points toward the active hideout
5. active hideout contains John or signs he has just fled

## World Layout

Around spawn, the world should feel noisy with his influence.

### Spawn Ring

Seed many vice sites within roughly `300` blocks of spawn.

Purpose:

- establish the Baron as locally notorious
- make the radar immediately useful
- give players many short-range leads early

Recommended density:

- `4-8` vice sites in the spawn ring
- at least `1-2` per major path or approach out of spawn

### Mid Ring

From roughly `300-800` blocks:

- place more suspicious camps, stash sites, and transit clues
- fewer brothels, more partial evidence and movement

### Outer Ring

From roughly `500-1500` blocks:

- real hideouts
- stronger clues
- the active Baron encounter

This creates a sensible escalation:

- spawn area = gossip and vice
- mid-world = logistics and fear
- outer ring = the Baron himself

## Site Taxonomy

Do not treat every location as the same kind of place.

## 1. Vice Houses

These are the core "brothel" locations the radar should find near spawn.

Function:

- social clue hubs
- places where John spent money, hid briefly, or traded favors
- places where players learn where he has been heading

Visual identity:

- warm lantern glow
- red or dark wood accents
- drinks, ledgers, bottles, notes
- a little more opulent than a normal village building

Not every vice house needs to be literally sexual in presentation. Some can feel like:

- a disreputable lounge
- a back-room parlor
- a dockside den
- a velvet gambling room

That keeps the world flavorful without requiring every build to be a caricature.

## 2. Transit Sites

These are not leisure spaces. They are functional traces.

Examples:

- abandoned camp
- stash cellar
- burned carriage stop
- ruined dock cache
- roadside safe box

Function:

- push the investigation outward
- imply recent movement
- deliver one clean directional clue

## 3. Primary Hideouts

These are the true major sites:

- Antenna Nest
- Swamp Shack
- Jungle Bunker
- Cave Server Room
- Ruined Dock
- Watchpoint
- Beach Cache

Only one should be truly active for John at a time.

## Brothel Radar Rules

The name now becomes mechanically honest.

## What It Should Detect

It should detect:

- seeded vice houses
- seeded rumor hubs
- seeded transit clues
- seeded hideouts

It should not distinguish between them at the item level.

The uncertainty is the point.

## How It Should Guide Players

Recommended model:

- near spawn: many signals
- the closer a site is to John’s recent route, the "hotter" or more promising the clue
- brothels often reveal the next zone, not the final answer
- only strong clue chains eventually guide players into the outer ring hideouts

So the radar becomes:

- an attention tool
- not a precision tracker

## Clue Strength Model

Every seeded site should have a clue strength:

- `0`: flavor only
- `1`: local rumor
- `2`: directional hint toward a stronger site
- `3`: active-route clue pointing toward current hideout region

Recommended site behaviour:

- most vice houses: `1-2`
- some transit sites: `2`
- one or two privileged sites at a time: `3`
- active hideout: final destination

## Vice House Content

Each vice house should contain:

- one rumor board or notice wall
- one ledger or account scrap
- one Baron-authored note or insult
- one clue item or location hint
- some valuables
- some vice props

## Vice House NPC Roster

Yes, female-presenting NPCs can and should be part of the atmosphere, but they should serve actual roles rather than existing as generic scenery.

Recommended roster per larger vice house:

- `1` Madam or Host
- `2-4` Hostesses
- `1` Bartender
- `1` Guard or Bouncer
- optional `1` Bookkeeper

For smaller vice houses:

- `1` Host
- `1-2` Hostesses
- `1` Bartender

## NPC Tone

They should feel like people who have dealt with John before.

Suggested attitudes:

- tired amusement
- contempt
- practical opportunism
- some fear, but not melodrama

Suggested witness lines:

- `He paid in emeralds, lies, and investor language.`
- `He rented the upstairs room and spent all night insulting the weather.`
- `Bought drinks for the room, then asked whether our walls could be bugged.`
- `Left a crate of posters and called it settlement.`
- `If you want your money, try the hills. If you want a story, stay seated.`

## Loot And Props

Vice houses should feel worth visiting even when they are not the key clue.

Common items:

- bottles
- potions
- emeralds
- gold nuggets
- papers
- maps
- low-grade valuables

Occasional special items:

- named ledgers
- Techtonic sponsor drafts
- listener-number sheets
- signed junk certificate
- powder stash

Rare:

- diamonds
- one meaningful map
- one stronger clue note

## Spawn-Area Density Rules

You wanted many such places around spawn within about `300` blocks. That is workable, but only if the content types vary.

Recommended distribution in the spawn ring:

- `2-3` larger vice houses
- `2-3` smaller rumor dens
- `1-2` transit clue sites
- rumor boards in settlements as separate objects

Do not seed eight identical brothels. Seed a network of distinct but related places.

## Site Archetypes For Spawn Ring

Use a mix like this:

### 1. Velvet Lantern

Role:

- proper vice house
- warm, expensive, slightly disreputable

Best NPCs:

- Madam
- bartender
- `2-3` hostesses

### 2. Dockside Parlour

Role:

- lower-class harbor vice den
- stronger smuggler energy

Best NPCs:

- host
- bartender
- bouncer

### 3. Red Ledger House

Role:

- laundering front
- less sensual, more financial corruption

Best NPCs:

- bookkeeper
- one hostess
- one guard

### 4. Soft Lamp Inn Room

Role:

- small quiet vice room above a tavern
- excellent for one clue and one witness line

## How The Clues Should Work

Every vice house should point outward.

Examples:

- `He was asking about ridge lines and copper.`
- `He wanted a cart going east and paid double to keep quiet.`
- `He kept repeating that the swamp remembers footsteps.`
- `He said the antenna was the only place the sky made sense.`

That way the player can visit multiple near-spawn sites and slowly infer which outer-ring hideout matters.

## Recommended First Expansion

Do not seed every hideout first. Seed the vice network and one true hideout.

Phase one:

- `4-6` vice sites within `300` blocks of spawn
- rumor boards in all settlements
- `1-2` transit clue sites
- `antenna_nest` as the active hideout

This gives the radar plenty to do without dissolving the chase.

## Plugin Implications

The plugin should eventually distinguish site categories in its seed state:

- `vice_house`
- `rumor_board`
- `transit_clue`
- `hideout`

The radar service should then target all site categories, not only hideouts.

The clue engine should decide which sites are currently "hot" based on the active hideout.

## Success Condition

This system is working if:

1. players can roam the spawn ring and find several Baron-adjacent places quickly
2. the Brothel Radar feels useful immediately
3. the clues gradually push players away from spawn rather than keeping them there
4. John remains hard to pin down, but the world feels full of his influence
