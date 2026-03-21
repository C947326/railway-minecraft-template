# Baron Audio Pipeline

This project now includes a direct Baron voice-generation scaffold using the free `eidosSpeech` API.

## What Exists

- canonical line manifest: [tools/baron-voice-lines.json](/Users/dao/Software/Crorgans%20Nest/tools/baron-voice-lines.json)
- generator script: [tools/generate-baron-audio.ts](/Users/dao/Software/Crorgans%20Nest/tools/generate-baron-audio.ts)
- resource-pack template: [plugins/fugitive-baron/resource-pack-template](/Users/dao/Software/Crorgans%20Nest/plugins/fugitive-baron/resource-pack-template)
- sound mapping manifest: [sounds.json](/Users/dao/Software/Crorgans%20Nest/plugins/fugitive-baron/resource-pack-template/assets/fugitivebaron/sounds.json)

## Requirements

- `EIDOS_API_KEY`
- Bun available in the repo

Optional environment overrides:

- `BARON_TTS_VOICE`

Defaults:

- voice: `en-US-AndrewMultilingualNeural`
- format: `mp3`

## Generate Audio

```bash
export EIDOS_API_KEY=...
bun run baron:audio
```

This writes generated files into:

[voice](/Users/dao/Software/Crorgans%20Nest/plugins/fugitive-baron/resource-pack-template/assets/fugitivebaron/sounds/voice)

with category folders for:

- `observe`
- `suspicious`
- `trust`
- `panic`
- `escape`
- `hideout`

## Voice Bake-Off

To compare candidate Baron voices on the same four test lines:

```bash
export EIDOS_API_KEY=...
bun run baron:voice-bakeoff
```

This writes comparison files into:

[baron-voice-bakeoff-output](/Users/dao/Software/Crorgans%20Nest/tools/baron-voice-bakeoff-output)

Default candidate voices:

- `en-US-AndrewMultilingualNeural`
- `en-US-GuyNeural`
- `en-US-DavisNeural`
- `en-GB-RyanNeural`

You can override the list:

```bash
export BARON_BAKEOFF_VOICES="en-US-AndrewMultilingualNeural,en-US-BrianNeural"
bun run baron:voice-bakeoff
```

## Next Wiring Step

Once the files exist, the Paper plugin should map state transitions to sound ids like:

- `baron.observe.observe_01`
- `baron.suspicious.suspicious_03`
- `baron.trust.trust_05`
- `baron.hideout.hideout_swamp_shack`

and play those for nearby players while preserving the current subtitle system as fallback.

## Current Limitation

No audio was generated in this workspace because there is no `EIDOS_API_KEY` in the current environment.
