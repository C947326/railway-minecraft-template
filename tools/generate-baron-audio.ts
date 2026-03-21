import { access, mkdir, rm, writeFile } from "node:fs/promises";
import { spawn } from "node:child_process";
import path from "node:path";

type VoiceLine = {
	id: string;
	text: string;
};

type VoiceManifest = Record<string, VoiceLine[]>;

const manifestPath = path.resolve("tools/baron-voice-lines.json");
const outputRoot = path.resolve(
	"plugins/fugitive-baron/resource-pack-template/assets/fugitivebaron/sounds/voice",
);
const apiKey = process.env.EIDOS_API_KEY;
const voice = process.env.BARON_TTS_VOICE ?? "en-US-AndrewMultilingualNeural";
const format = "ogg";
const overwrite = process.env.BARON_TTS_OVERWRITE === "true";

if (!apiKey) {
	throw new Error(
		"EIDOS_API_KEY is required to generate Baron audio. Set the variable and rerun `bun run baron:audio`.",
	);
}

const rawManifest = await Bun.file(manifestPath).text();
const manifest = JSON.parse(rawManifest) as VoiceManifest;

await mkdir(outputRoot, { recursive: true });

for (const [category, lines] of Object.entries(manifest)) {
	const categoryDir = path.join(outputRoot, category);
	await mkdir(categoryDir, { recursive: true });

	for (const line of lines) {
		const outputPath = path.join(categoryDir, `${line.id}.${format}`);
		if (!overwrite && await fileExists(outputPath)) {
			console.log(`Skipping existing ${path.relative(process.cwd(), outputPath)}`);
			continue;
		}

		const bytes = await generateWithRetry(category, line);
		const tempPath = path.join(categoryDir, `${line.id}.mp3`);
		await writeFile(tempPath, bytes);
		await convertMp3ToOgg(tempPath, outputPath);
		await rm(tempPath, { force: true });
		console.log(`Generated ${path.relative(process.cwd(), outputPath)}`);
	}
}

console.log("Baron audio generation complete.");

async function generateWithRetry(category: string, line: VoiceLine): Promise<Uint8Array> {
	for (;;) {
		const response = await fetch("https://eidosspeech.xyz/api/v1/tts", {
			method: "POST",
			headers: {
				"X-API-Key": apiKey,
				"Content-Type": "application/json",
			},
			body: JSON.stringify({
				voice,
				text: line.text,
			}),
		});

		if (response.ok) {
			return new Uint8Array(await response.arrayBuffer());
		}

		if (response.status === 429) {
			const errorBody = await response.text();
			const retryAfterSeconds = extractRetryAfterSeconds(errorBody) ?? 60;
			console.log(
				`Rate limited while generating ${category}/${line.id}. Waiting ${retryAfterSeconds}s before retrying...`,
			);
			await sleep((retryAfterSeconds + 1) * 1000);
			continue;
		}

		const errorBody = await response.text();
		throw new Error(
			`Failed to generate ${category}/${line.id}: ${response.status} ${response.statusText}\n${errorBody}`,
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

async function convertMp3ToOgg(inputPath: string, outputPath: string) {
	await new Promise<void>((resolve, reject) => {
		const process = spawn("ffmpeg", [
			"-y",
			"-i",
			inputPath,
			"-c:a",
			"libvorbis",
			"-q:a",
			"5",
			outputPath,
		], {
			stdio: ["ignore", "ignore", "pipe"],
		});

		let stderr = "";
		process.stderr.on("data", (chunk) => {
			stderr += chunk.toString();
		});
		process.on("error", reject);
		process.on("close", (code) => {
			if (code === 0) {
				resolve();
				return;
			}
			reject(new Error(`ffmpeg failed for ${path.basename(inputPath)}: ${stderr}`));
		});
	});
}
