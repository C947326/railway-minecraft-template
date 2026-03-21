import { readdir, rm } from "node:fs/promises";
import { spawn } from "node:child_process";
import path from "node:path";

const voiceRoot = path.resolve(
	"plugins/fugitive-baron/resource-pack-template/assets/fugitivebaron/sounds/voice",
);

const files = await collectMp3Files(voiceRoot);
for (const filePath of files) {
	const outputPath = filePath.replace(/\.mp3$/i, ".ogg");
	await convertMp3ToOgg(filePath, outputPath);
	await rm(filePath, { force: true });
	console.log(`Converted ${path.relative(process.cwd(), filePath)} -> ${path.relative(process.cwd(), outputPath)}`);
}

console.log(`Converted ${files.length} Baron voice files to ogg.`);

async function collectMp3Files(root: string): Promise<string[]> {
	const entries = await readdir(root, { withFileTypes: true });
	const files: string[] = [];
	for (const entry of entries) {
		const fullPath = path.join(root, entry.name);
		if (entry.isDirectory()) {
			files.push(...(await collectMp3Files(fullPath)));
			continue;
		}
		if (entry.isFile() && fullPath.toLowerCase().endsWith(".mp3")) {
			files.push(fullPath);
		}
	}
	return files.sort();
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
