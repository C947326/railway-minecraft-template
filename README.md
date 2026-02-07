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
- `CREATE_CONSOLE_IN_PIPE` (must be `true` to enable the named pipe)
- `MC_CONSOLE_PIPE` (optional, default: `/tmp/minecraft-console-in`)
- `MC_LOG_PATH` (optional, default: `/data/logs/latest.log`)
- `MC_LOG_TAIL_BYTES` (optional, default: `20000`)
- `MC_LOG_POLL_MS` (optional, default: `1000`)
- `MC_SERVER_HOST` (optional, default: `127.0.0.1`)
- `MC_SERVER_PORT` (optional, default: `25565` or `SERVER_PORT`)
- `MC_STATUS_CACHE_MS` (optional, default: `8000`)
- `CONTROL_PORT` (optional, default: `3000`, keep different from Minecraft)

If you override the pipe path, also set `CONSOLE_IN_NAMED_PIPE` for the
itzg container so both sides use the same location.

On Railway, ensure `CONTROL_PORT` is not the same port as the Minecraft
`SERVER_PORT` (default `25565`) or the server will fail to bind.

## Container usage

The included `Dockerfile` wraps `itzg/minecraft-server` and runs the Bun
control UI in the same container. This is required to write to the console
pipe directly. See the itzg docs for console pipe behavior and other
command options: https://docker-minecraft-server.readthedocs.io/en/latest/sending-commands/commands/

## Notes

- The auth guard is a placeholder that always allows access.
- File preview is limited to small text files.

## License

See `LICENSE`. In short: free to use/modify/contribute (including commercially), but you **may not** redistribute it as part of a competing Minecraft server template/starter/boilerplate.
