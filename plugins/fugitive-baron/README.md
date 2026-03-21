# Fugitive Baron Paper Plugin

This module contains a minimal Paper plugin skeleton for the "fugitive tech baron" encounter described in [../../docs/fugitive-tech-baron-npc.md](/Users/dao/Software/Crorgans%20Nest/docs/fugitive-tech-baron-npc.md).

## What It Does

- spawns a wandering-trader-based NPC called `The Baron`
- defines exactly `7` possible hideouts in config
- ties the Baron's spawn to one active enabled hideout at a time
- runs a tick-based state machine with `IDLE`, `OBSERVE`, `SUSPICIOUS`, `TRUST`, `FLEE`, and `ESCAPE`
- treats `GUNPOWDER` as the recognition item
- gives each player a one-time `Brothel Radar` on join
- points that radar toward nearby hideout signals rather than the Baron directly
- currently ships with only `antenna_nest` enabled for testing
- lets players right-click the Brothel Radar to ping the nearest signals and reveal nearby hideout intel
- gives each hideout mood, clue text, ambient line, and trust intel in config
- plays Baron voice clips from custom `baron.*` sound ids when the resource pack is installed
- lets `/fugitivebaron item` mint a named powder packet for testing
- lets `/fugitivebaron radar` mint a replacement radar
- turns right-click into a simple trust interaction and damage into an immediate disappearance

## Build

```bash
cd plugins/fugitive-baron
gradle build
```

No Gradle wrapper is committed yet, so this presently expects a local Gradle installation.

The resulting JAR belongs in your Paper server's `plugins/` directory.

For spoken Baron lines, you also need the generated resource-pack assets from:

[resource-pack-template](/Users/dao/Software/Crorgans%20Nest/plugins/fugitive-baron/resource-pack-template)

Without the resource pack, the subtitles still appear but the custom voice sounds will not resolve.

## Commands

- `/fugitivebaron spawn`
- `/fugitivebaron despawn`
- `/fugitivebaron item`
- `/fugitivebaron radar`
- `/fugitivebaron hideout [id|random]`
- `/fugitivebaron reload`

At the moment, only enabled hideouts appear in `/fugitivebaron hideout`, and the default test setup keeps just `antenna_nest` live until the other sites are fully dressed and voiced.

## Next Sensible Steps

- persist the NPC and trust cooldowns across restarts
- add an actual barter table or quest chain
- make the Baron relocate automatically after being spooked
- generate the packaged voice lines with the free `eidosSpeech` API and add a proper resource-pack audio layer on top of the current subtitle system
