# Baron Test Runbook

This is the shortest path to getting the Baron encounter running and testable.

## What You Need

- the Railway deployment that already bundles the plugin jar
- the Baron resource pack zip
- a Paper server start from the dashboard
- operator access in Minecraft

## 1. Package The Resource Pack

From the repo root:

```bash
bun run baron:pack-resource-pack
```

This creates:

[fugitive-baron-resource-pack.zip](/Users/dao/Software/Crorgans%20Nest/plugins/fugitive-baron/fugitive-baron-resource-pack.zip)

## 2. Deploy The Server

Deploy the current branch to Railway so the updated plugin source is built into the container image.

The Docker pipeline already:

- builds the Paper plugin jar
- copies it into the container
- syncs it into `/data/plugins` on startup

## 3. Start Minecraft From The Dashboard

When the container is live:

1. open the Bun control UI
2. press `Power on`
3. wait for Paper to boot

With `encounter.debug-logging: true`, the server logs should show Baron debug lines when the NPC is spawned, changes state, speaks, or escapes.

Look for messages containing:

- `[BaronDebug] Spawned Baron`
- `[BaronDebug] State change`
- `[BaronDebug] Delivered line`
- `[BaronDebug] Played sound`

## 4. Install The Resource Pack On Your Client

Because this setup does not yet host the pack through `server.properties`, install it manually in your Minecraft client:

1. take [fugitive-baron-resource-pack.zip](/Users/dao/Software/Crorgans%20Nest/plugins/fugitive-baron/fugitive-baron-resource-pack.zip)
2. open Minecraft
3. go to `Options -> Resource Packs`
4. click `Open Pack Folder`
5. copy the zip into that folder
6. enable the pack

Without this step, you will still see subtitles but hear no Baron voice audio.

## 5. Manifest The Baron In-Game

Once you join the server as an operator:

1. give yourself the test radar if needed:

```mcfunction
/fugitivebaron radar
```

2. give yourself the recognition item:

```mcfunction
/fugitivebaron item
```

3. pick or randomize the active hideout:

```mcfunction
/fugitivebaron hideout
/fugitivebaron hideout random
```

Right now, only `antenna_nest` is enabled in config, so the radar and spawn flow are intentionally constrained to that one finished hideout.

4. spawn the Baron at the active hideout:

```mcfunction
/fugitivebaron spawn
```

## 6. Test The Encounter

Recommended sequence:

1. approach without gunpowder
2. confirm he enters `OBSERVE`, then `SUSPICIOUS`, then `FLEE`
3. check logs for state transitions and delivered sound ids
4. approach again while holding the powder item
5. confirm he enters `TRUST`
6. right-click him
7. confirm you receive the trust-intel message and hear the Baron line

## 7. What Should Work Today

These voice banks are already regenerated and should match the new script:

- `observe`
- `suspicious`
- `trust`
- `panic`
- `escape`

Only one hideout ambient line is refreshed today:

- `hideout_antenna_nest`

The remaining hideout ambient lines still need tomorrow's TTS quota window.

## 8. If You Want The Fastest Validation

Use `antenna_nest` as the active hideout first, because it is presently the only enabled hideout and the one ambient hideout line already regenerated and aligned with the current script.
