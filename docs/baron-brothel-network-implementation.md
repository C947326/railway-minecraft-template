# Baron Brothel Network Implementation

This document converts the vice-network concept into the next practical plugin steps.

## Immediate Goal

Seed a believable near-spawn vice network without breaking the current Baron hunt.

## Recommended First Coding Slice

Add support for a new site category:

- `vice_house`

Then implement:

1. seeded vice-site placement within `300` blocks of spawn
2. one or two small structure variants first
3. clue-bearing containers and sign text
4. radar support for vice sites as signals

## Keep The Scope Tight

Do not begin with:

- full NPC automation
- dynamic bar workers
- sexualized interactions
- all possible vice-house archetypes

Begin with:

- structures
- signs
- containers
- notes
- radar integration

The NPC layer can come later.

## Seeding Count

For the first implementation:

- `4` vice sites around spawn
- plus the existing rumor boards and Antenna Nest

That is enough density to prove the loop.

## Placement Rules

Generate vice sites between:

- minimum `80` blocks from spawn
- maximum `300` blocks from spawn

This keeps spawn readable while still making the sites feel local.

## First Structure Variants

Implement only two variants first:

- `velvet_lantern`
- `dockside_parlour`

Both can be modest in size and share much of the same placement logic.

## Radar Change

The Brothel Radar should evolve from:

- nearest hideout signal

to:

- nearest Baron-network signal

But the UI text should still communicate what kind of signal was found if possible:

- `Brothel Radar picks up: Velvet Lantern SW 120m`
- `Brothel Radar picks up: Antenna Nest E 940m`

## Seed State Additions

Extend `seed-state.yml` to track:

- `vice-sites`
- their type
- their location
- optional clue strength

## Suggested Commands

Add later:

```mcfunction
/fugitivebaron seed vice
/fugitivebaron seed world
```

Where:

- `seed vice`
  places only the near-spawn vice network
- `seed world`
  becomes the combined seed command

## First Acceptance Test

The vice-network expansion is ready when:

- the radar finds multiple near-spawn sites
- those sites contain notes, valuables, and Techtonic traces
- at least one site points players toward Antenna Nest
- the world feels like John has a support ecosystem rather than one lonely shack
