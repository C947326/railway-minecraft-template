import { access, mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type SampleLine = {
	id: string;
	text: string;
};

const apiKey = process.env.EIDOS_API_KEY;
const manifestPath = path.resolve("tools/baron-voice-bakeoff.json");
const outputRoot = path.resolve("tools/baron-voice-bakeoff-output");
const voices = (
	process.env.BARON_BAKEOFF_VOICES ??
	[
		"en-US-AndrewMultilingualNeural",
		"en-US-GuyNeural",
		"en-US-DavisNeural",
		"en-GB-RyanNeural",
	].join(",")
)
	.split(",")
	.map((voice) => voice.trim())
	.filter(Boolean);

if (!apiKey) {
	throw new Error(
		"EIDOS_API_KEY is required to generate the Baron voice bake-off set.",
	);
}

const samples = JSON.parse(
	await Bun.file(manifestPath).text(),
) as SampleLine[];

await mkdir(outputRoot, { recursive: true });

for (const voice of voices) {
	const voiceDir = path.join(outputRoot, voice);
	await mkdir(voiceDir, { recursive: true });

	for (const sample of samples) {
		const outputPath = path.join(voiceDir, `${sample.id}.mp3`);
		if (await fileExists(outputPath)) {
			console.log(`Skipping existing ${path.relative(process.cwd(), outputPath)}`);
			continue;
		}

		const bytes = await generateWithRetry(voice, sample);
		await writeFile(outputPath, bytes);
		console.log(`Generated ${path.relative(process.cwd(), outputPath)}`);
	}
}

console.log("Baron voice bake-off generation complete.");

async function generateWithRetry(
	voice: string,
	sample: SampleLine,
): Promise<Uint8Array> {
	for (;;) {
		const response = await fetch("https://eidosspeech.xyz/api/v1/tts", {
			method: "POST",
			headers: {
				"X-API-Key": apiKey,
				"Content-Type": "application/json",
			},
			body: JSON.stringify({
				voice,
				text: sample.text,
			}),
		});

		if (response.ok) {
			return new Uint8Array(await response.arrayBuffer());
		}

		if (response.status === 429) {
			const errorBody = await response.text();
			const retryAfterSeconds = extractRetryAfterSeconds(errorBody) ?? 60;
			console.log(
				`Rate limited while generating ${voice}/${sample.id}. Waiting ${retryAfterSeconds}s before retrying...`,
			);
			await sleep((retryAfterSeconds + 1) * 1000);
			continue;
		}

		const errorBody = await response.text();
		throw new Error(
			`Failed to generate ${voice}/${sample.id}: ${response.status} ${response.statusText}\n${errorBody}`,
		);
	}
}

async function fileExists(filePath: string) {
	try {
		await access(filePath);
		return true;
	} catch {
		return false;
	}
}

function extractRetryAfterSeconds(body: string) {
	try {
		const parsed = JSON.parse(body) as {
			detail?: {
				retry_after?: number;
			};
		};
		return parsed.detail?.retry_after ?? null;
	} catch {
		return null;
	}
}

function sleep(ms: number) {
	return new Promise((resolve) => setTimeout(resolve, ms));
}
