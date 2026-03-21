# Fugitive Baron Content Bible

This brief expands the current Baron plugin from a functional prototype into a satisfying server encounter. It covers the Baron's look, voice and subtitle design, the seven hideouts, clue progression, and the recommended implementation path on Paper.

## Target Experience

Players should feel three things:

- they are hunting a person with habits, not a random vendor
- each hideout tells a story, even when the Baron is absent
- finding the Baron is valuable because trust unlocks rare information and rewards

The encounter should work as layered discovery:

1. the Brothel Radar tells you where to search
2. the hideout tells you whether he has been there recently
3. the Baron encounter tests whether you know how to approach him
4. trust unlocks clues, loot, or progression

## Core Backstory

The Baron is not merely some strange millionaire on the run. He is the disgraced investor behind `Techtonic`, a wildly successful podcast created by the players. Once the show took off, he siphoned the profits, liquidated everything worth touching, and vanished into a chain of hideouts with your money.

This reframes the encounter:

- the Brothel Radar is not a novelty, it is a debt-collection instrument
- the hideouts are not random safehouses, they are places he fled through while laundering or burning the Techtonic money
- the players are not admirers, they are aggrieved co-founders trying to recover what he stole

The comedy works best if the players slowly realize the obvious truth:

- the money is almost certainly gone
- the Baron has spent, hidden, destroyed, or mythologized most of it
- if you earn his trust, his "repayment" is liable to be bizarre, worthless, or insultingly theatrical

## Quest Motivation

The player fantasy is not "kill the fugitive." It is "corner the bastard who stole our podcast money and force him to explain himself."

This should color all his dialogue:

- he knows exactly who you are
- he treats the theft as a complicated business dispute rather than outright robbery
- he alternates between charm, contempt, self-justification, and paranoia
- he keeps implying the money was transformed into "assets," "infrastructure," or "strategic necessities"

## Techtonic-Specific Clues

Each hideout should contain evidence linking the Baron to the missing Techtonic money.

Possible clue props:

- invoice books labeled `Techtonic Media`
- shipping manifests for studio equipment that never arrived
- "temporary" expense ledgers full of absurd purchases
- burnt contracts with the players' names partially visible
- a crate of low-value merch that he insists is "equity"
- episode notes, sponsorship drafts, or listener numbers pinned next to escape routes

The hideouts should reveal a pattern:

- the podcast really was successful
- the Baron really did steal the money
- he has rationalized the theft into a grand theory of survival, leverage, or freedom

## Visual Identity

The Baron should read as "once-luxurious, now feral and evasive."

Core look:

- white or cream suit, slightly disheveled
- open collar or loud tropical shirt beneath the jacket
- dark sunglasses
- gray or silver hair
- beard or heavy stubble
- a satchel, briefcase, or field bag

Silhouette notes:

- never polished enough to feel like a casino host
- never ragged enough to look like a standard beggar NPC
- should look like a man who fled with money, vanity, and no sleep

## Best Visual Implementation Path On Paper

### Option A: Citizens NPC plus custom skin

Best overall result.

Use when:

- you are willing to install one companion plugin
- you want a genuine humanoid model and skin

Why it is best:

- easiest way to get a proper human-shaped Baron
- supports skins, poses, pathing, and interaction more cleanly than disguising a mob
- lets your custom plugin focus on behavior and content

Recommended split:

- Citizens owns the NPC body and skin
- `FugitiveBaron` owns state, hideouts, radar, dialogue, and reward logic

### Option B: Wandering Trader fallback

Best for zero dependencies, but visibly inferior.

Use when:

- you want only one plugin jar

Compensating measures:

- strong custom name and subtitle work
- white-dyed leather or item props nearby
- unique particles and hideout staging
- treat him as a disguised recluse rather than a literal likeness

Recommendation:

- ship phase 1 with the existing wandering trader fallback
- move to Citizens for the proper production version

## Audio And Subtitle Design

Do not ship real interview audio without clear rights. Use parody lines, a voice actor, or synthetic voice that evokes the archetype without cloning a real person.

### Delivery Rules

- every line should also have a subtitle
- proximity bark lines should be short: `1.5` to `3.5` seconds
- only one bark at a time
- a line should not repeat within the same player session if alternatives exist
- hideout ambient lines should be rarer than encounter lines

### Subtitle Style

Format:

- `[Baron] Too calm. That is always a warning sign.`

Timing:

- subtitle appears with the sound
- subtitle lingers `2` to `3` seconds after playback

Fallback if no resource pack:

- use action bar for bark lines
- use chat only for major trust or quest lines

### Audio Asset Plan

Organize by state:

- `baron.observe.01-06`
- `baron.suspicious.01-08`
- `baron.trust.01-06`
- `baron.panic.01-06`
- `baron.escape.01-03`
- `baron.hideout.active.01-05`
- `baron.hideout.decoy.01-05`

Recommended first recording target:

- 6 observe
- 8 suspicious
- 6 trust
- 6 panic
- 3 escape
- 2 lines unique to each hideout

That yields enough variation to avoid fatigue.

## Voice Line Bank

### Observe

- "Interesting. You are looking, but not pretending not to."
- "You came with intent. I approve of intent."
- "No one arrives here by accident. Not twice."
- "You have the posture of a witness."
- "Pause there. Let me adjust my suspicions."
- "Curiosity is expensive. I hope you are funded."

### Suspicious

- "Too calm. That is always the first mistake."
- "No sudden enthusiasm."
- "I know an information predator when I see one."
- "Do not close the distance unless you mean it."
- "Patterns. I detest patterns."
- "You are standing like a person with questions and no warrant."
- "I have survived friendlier faces than yours."
- "Hands visible. Ambition concealed."

### Trust

- "Ah. A civilized signal."
- "Good. Then perhaps this need not become athletic."
- "You understand ceremony. Excellent."
- "Very well. We may trade in facts."
- "Discretion is the rarest currency. You have brought some."
- "Now we may speak like professionals."

### Panic

- "No, no. Too close."
- "Absolutely not."
- "Distance is the foundation of trust."
- "You have mistaken interest for permission."
- "Compromised. Entirely compromised."
- "Run first, explain later."

### Escape

- "You have ruined a perfectly good conspiracy."
- "This safehouse is now decorative."
- "Unacceptable. I am relocating."

## Seven Hideouts

Each hideout must be useful even when the Baron is absent. A player who reaches a decoy should still gain story, supplies, or a clue.

### 1. Swamp Shack

Mood:

- humid paranoia
- lantern light
- low, suspicious wealth

Visuals:

- mossy planks
- warped trapdoors
- barrels, maps, lanterns
- a cot and two locked chests

Clues:

- coded ledger pages
- gunpowder stash
- muddy boot trail leaving north

Unique line:

- "The swamp is honest. Everything announces its intentions."

### 2. Jungle Bunker

Mood:

- improvised fortress
- camouflage and tropical delusion

Visuals:

- bamboo, stripped logs, leaves, jungle wood
- hidden trapdoor entrance
- radio corner built from note blocks, redstone lamps, and copper

Clues:

- torn map with one hideout crossed out
- cocoa beans, feathers, suspicious notes

Unique line:

- "Jungles reward preparation and punish confidence."

### 3. Beach Cache

Mood:

- temporary luxury
- elegant collapse

Visuals:

- white wool awning
- birch deck chair
- campfire, ender chest, buried supplies

Clues:

- burned paper
- fishing barrel with contraband item
- note mentioning "dock compromised"

Unique line:

- "Coastlines are for exits, not holidays."

### 4. Cave Server Room

Mood:

- hidden infrastructure
- humming machines underground

Visuals:

- deepslate room
- lightning rods as antennae
- copper bulbs, redstone, note blocks, lecterns

Clues:

- coordinates fragment
- "root access revoked" note
- redstone torch sequence hint

Unique line:

- "Trust no machine you did not personally offend."

### 5. Ruined Dock

Mood:

- hurried departure
- failed extraction

Visuals:

- weathered pier
- mangrove and dark oak
- cargo crates
- broken boat

Clues:

- powder hidden in crate
- waterlogged ledger
- sign that one shipment never left

Unique line:

- "Ports are merely queues with seagulls."

### 6. Savanna Watchpoint

Mood:

- long sight lines
- surveillance mania

Visuals:

- acacia tower
- spyglass stand
- signal fire
- little shelter with maps pinned on walls

Clues:

- list of player names or aliases
- compass calibration notes
- spent arrows or crossbow bolts

Unique line:

- "Height is not safety, but it improves the argument."

### 7. Antenna Nest

Mood:

- absurd technical ritual
- conspiratorial majesty

Visuals:

- hilltop scaffolding
- lightning rods and chains
- banners, copper, redstone lamps
- a chair facing the horizon like a command throne

Clues:

- strongest active-signal readings
- advanced map fragments
- a final clue toward relocation behavior

Unique line:

- "If you cannot hear the air, you are not listening properly."

## Active Versus Decoy Hideouts

The active hideout should differ from decoys in clear but subtle ways.

Active signs:

- one fresh campfire or lantern
- one recently used container with better loot
- one ambient mutter line every few minutes
- the Baron himself, if currently spawned

Decoy signs:

- old supplies
- one note or clue
- evidence of abandonment

Recommendation:

- every hideout should have a `static` layer and an `active overlay`
- the plugin swaps only the overlay markers, not the entire structure

## Reward Structure

The encounter needs escalation or it will become a novelty that expires after two sightings.

### Trust Tier 1

- one clue note
- one powder packet
- one vague coordinate

### Trust Tier 2

- access to barter
- rare materials or maps
- more precise hideout intelligence

### Trust Tier 3

- relocation forecast
- access to special cache
- cosmetic trophy item

### Final Repayment Joke

The best ending is not recovering the actual money. It is receiving an object or "asset" that the Baron insists balances the books, while any sane person can see it does not.

Strong repayment candidates:

- a single framed `Techtonic Platinum Investor` certificate signed by the Baron
- a useless crypto seed phrase to a dead wallet
- a custom-named parrot egg he claims is "the future of media"
- a barrel of off-brand "promotional whiskey" from a failed launch event
- a map to a cache containing only podcast posters, gunpowder, and one emerald
- a signed note reading: `Your equity has been converted into influence.`

Recommendation:

- give the player something mechanically mediocre but socially hilarious
- the value should come from the story and trophy factor, not real balance power

Recommendation:

- track trust per player
- let repeated successful nonviolent encounters deepen the relationship

## Barter Table

The Baron should trade in odd, useful, and morally dubious wares.

Good candidates:

- gunpowder
- maps to suspicious caches
- spyglasses
- nametags
- rare redstone components
- a custom "sealed dossier" item
- one absurd Techtonic-branded keepsake item

Avoid:

- top-tier diamond gear
- farmable economic exploits

## Radar Loop

The Brothel Radar should support discovery, not certainty.

Recommended behavior:

- the radar points to the nearest hideout signal with drift
- right-clicking the radar emits a summary of the nearest `3` hideouts
- the active hideout is not specially marked
- reaching a decoy still improves the player's inference

If you want a stronger Dragon Ball homage later:

- let upgraded radars show `7/7` signal strength bars
- let clue items reduce drift

## Production Phases

### Phase 1: Satisfying Prototype

- seven built hideouts in the world
- radar blips and summary ping
- reliable trust and flee behavior
- subtitle text with action bar fallback
- clue notes and small loot at every hideout

### Phase 2: Proper Character

- Citizens-backed humanoid skin
- recorded or synthetic voice pack
- active hideout overlays
- barter menu

### Phase 3: Memorable Legend

- automatic relocation after spooking
- trust progression
- rare multi-step questline
- world rumors via villagers, signs, or books

## Concrete Next Implementation Steps

1. Add a right-click radar ping so the item prints the nearest three hideout signals on demand.
2. Add a hideout metadata file so each of the seven sites has clue text, loot pools, and ambient lines.
3. Add subtitle events as a first-class system before real audio.
4. Decide whether to stay dependency-free or adopt Citizens for a proper Baron model.
5. Build one hideout fully, test it with players, then replicate the pattern across the other six.
