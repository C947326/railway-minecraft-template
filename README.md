# Minecraft Control UI

Control a Minecraft server with a file browser (rooted at `/data`) and a
console connected via the itzg stdin pipe.

## Setup

```bash
bun install
```

## Development

Run Tailwind in one terminal:

```bash
bun run dev:css
```

Run the Bun server in another:

```bash
bun dev
```

## Production

Build assets and bundle the frontend:

```bash
bun run build
```

Start the server:

```bash
bun start
```

## Environment

- `FILES_ROOT` (optional, default: `/data`)
- `MC_AUTO_START` (optional, default: `false`)
- `CREATE_CONSOLE_IN_PIPE` (must be `true` to enable the named pipe)
- `MC_CONSOLE_PIPE` (optional, default: `/tmp/minecraft-console-in`)
- `MC_LOG_PATH` (optional, default: `/data/logs/latest.log`)
- `MC_LOG_TAIL_BYTES` (optional, default: `20000`)
- `MC_LOG_POLL_MS` (optional, default: `1000`)
- `MC_SERVER_HOST` (optional, default: `127.0.0.1`)
- `MC_SERVER_PORT` (optional, default: `25565` or `SERVER_PORT`)
- `MC_STATUS_CACHE_MS` (optional, default: `8000`)
- `MC_SHUTDOWN_NOTICE` (optional, default: a short shutdown message sent before stop)
- `PORT` (preferred on Railway for the HTTP control UI bind port)
- `CONTROL_PORT` (optional override, keep different from Minecraft)

If you override the pipe path, also set `CONSOLE_IN_NAMED_PIPE` for the
itzg container so both sides use the same location.

On Railway, the control UI should bind to Railway's injected `PORT`.
If you override it with `CONTROL_PORT`, ensure it is not the same port as the
Minecraft `SERVER_PORT` (default `25565`) or the server will fail to bind.

## Container usage

The included `Dockerfile` wraps `itzg/minecraft-server` and runs the Bun
control UI in the same container. This is required to write to the console
pipe directly. See the itzg docs for console pipe behavior and other
command options: https://docker-minecraft-server.readthedocs.io/en/latest/sending-commands/commands/

### Bundled plugin pipeline

This image now also builds the Paper plugin in
`/Users/dao/Software/Crorgans Nest/plugins/fugitive-baron`
during the Docker build using a Gradle JDK 21 stage.

On container startup, `/Users/dao/Software/Crorgans Nest/docker/start.sh`
copies any bundled plugin jars from `/app/plugins-bundled` into `/data/plugins`
before the dashboard starts. Because the Minecraft server itself is launched
later via `/start`, the plugin is already in the correct place by the time
Paper boots.

This makes Railway deploys deterministic:

- Railway builds the plugin jar as part of the image
- the container syncs bundled jars into the persistent plugins directory on boot
- pressing `Power on` starts Minecraft with the latest bundled plugin already installed

### Automatic resource pack delivery

The Docker image also bundles:

- `/app/resource-pack/fugitive-baron-resource-pack.zip`

On startup, `/app/docker/start.sh` computes the pack SHA-1 and exports:

- `RESOURCE_PACK`
- `RESOURCE_PACK_SHA1`
- `RESOURCE_PACK_ID`
- `RESOURCE_PACK_ENFORCE`

The Bun app serves the zip at:

- `/resource-pack/fugitive-baron-resource-pack.zip`

On Railway, that should resolve to:

- `https://$RAILWAY_PUBLIC_DOMAIN/resource-pack/fugitive-baron-resource-pack.zip`

So clients should be prompted automatically to download the pack when they join, provided the deployment is using the current image.

The pack is presently voice-only. The `Brothel Radar` intentionally keeps the
vanilla compass appearance until there is a genuinely good custom asset worth
shipping.

## Notes

- The Bun dashboard stays online while Minecraft is off. `Power off` now means
  the Java server is stopped cleanly and remains off until you explicitly press
  `Power on` again.
- To keep Minecraft off after deploys and Railway restarts, leave
  `MC_AUTO_START=false` (the default).
- For a light vanilla server with 3 total players, start with Railway replica
  limits around `0.5 vCPU` and `1-2 GB RAM`. `1 GB` is often enough for an
  otherwise quiet vanilla world; use `2 GB` if you expect newer versions,
  larger view distances, or mods/plugins.
- The auth guard is a placeholder that always allows access.
- File preview is limited to small text files.

## License

See `LICENSE`. In short: free to use/modify/contribute (including commercially), but you **may not** redistribute it as part of a competing Minecraft server template/starter/boilerplate.
