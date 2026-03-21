# Baron World Seeding Implementation

This is the concrete next-step checklist for implementing plugin-driven world seeding in the current `FugitiveBaron` codebase.

## Current Plugin Fit

Existing useful entry points:

- `plugins/fugitive-baron/src/main/java/nest/fugitivebaron/FugitiveBaronPlugin.java`
- `plugins/fugitive-baron/src/main/java/nest/fugitivebaron/HideoutService.java`
- `plugins/fugitive-baron/src/main/java/nest/fugitivebaron/BaronCommand.java`

Existing facts:

- the plugin already loads successfully on Paper
- only `antenna_nest` is enabled
- the command surface already has admin subcommands
- the plugin data folder can be used for persistent seed state

## Recommended First Coding Slice

Do not attempt all world seeding at once. Implement these in order:

1. `SeedStateRepository`
2. `WorldContentLibrary`
3. `WorldSeedService`
4. `/fugitivebaron seed status`
5. `/fugitivebaron seed antenna`
6. `/fugitivebaron seed boards`

Only after that:

- remote clue sites
- auto-detected settlements
- reseeding or force modes

## File Additions

Recommended new Java files:

- `SeedStateRepository.java`
- `WorldSeedService.java`
- `WorldContentLibrary.java`
- `StructurePainter.java`
- `SettlementLocator.java`

## File Changes

### FugitiveBaronPlugin.java

Add:

- creation of `WorldSeedService`
- wiring of any content/persistence services
- getter methods if command executors need access

### BaronCommand.java

Extend subcommands with:

- `seed`
- `seed status`
- `seed antenna`
- `seed boards`
- later `seed clues`

### config.yml

Eventually add:

- optional settlement centers
- board style defaults
- seeding toggles

Phase one can defer config additions if `seed antenna` is implemented first.

## Phase 1A: Seed State

Implement `SeedStateRepository` backed by a YAML file in the plugin data folder.

Suggested schema:

```yaml
seed-version: 1
antenna-nest:
  seeded: true
  world: world
  x: 128
  y: 76
  z: -212
rumor-boards: []
clue-sites: []
```

Minimum operations:

- load state
- save state
- report whether antenna has been seeded
- record seeded board locations

## Phase 1B: Content Library

`WorldContentLibrary` should centralize:

- book titles
- book page lists
- rumor board variants
- item names
- container loadouts

Pull this content from the existing docs, not from scattered constants.

Immediate targets:

- `To The Men Still Pretending This Is About Accounting`
- `Never Trust Someone Else's Software`
- `On Liquor, Noise, and Detection`
- settlement board variants A-F

## Phase 1C: Structure Painter

For phase one, `StructurePainter` does not need to be a full schematic engine.

It only needs:

- fill a rectangular footprint safely
- place individual blocks from offsets
- place directional blocks
- place barrels, lecterns, signs, campfires, lanterns, and item frames

This is sufficient for:

- the Antenna Nest shell
- rumor boards

## Phase 1D: Antenna Nest Seeder

`WorldSeedService.seedAntennaNest()` should:

1. read active hideout location from `HideoutService`
2. validate world exists
3. choose a centered footprint
4. do a simple occupancy check
5. place the structure blocks
6. populate lectern with the main book
7. populate containers
8. record the seeded result

Success output should include:

- world
- coordinates
- whether it was placed or skipped

## Phase 1E: Settlement Boards

For the first coding pass, do not auto-detect everything.

Simpler options:

- seed boards only at configured settlements
- or seed one board near world spawn for proof of concept

Then expand to village detection once the board builder is proven.

Board builder needs:

- small footprint logic
- sign text assignment
- optional lantern

## Suggested Command UX

Examples:

```mcfunction
/fugitivebaron seed status
/fugitivebaron seed antenna
/fugitivebaron seed boards
```

Good command feedback:

- `Seed status: antenna=true boards=4 clues=0 version=1`
- `Seeded Antenna Nest at world 128 76 -212`
- `Seeded 3 rumor boards, skipped 1 occupied site`

## Logging

Use the existing debug style:

- `[BaronDebug] Seeded Antenna Nest ...`
- `[BaronDebug] Seeded rumor board ...`
- `[BaronDebug] Skipped board site due to occupied footprint ...`

## First Acceptable Outcome

The first implementation is successful if:

- `seed antenna` creates a convincing authored safehouse
- `seed boards` places at least one rumor board successfully
- rerunning the commands does not duplicate content blindly

That is enough to move from design fiction to persistent world fiction.
