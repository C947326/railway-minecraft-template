import { rm } from "node:fs/promises";
import path from "node:path";

const sourceDir = path.resolve("plugins/fugitive-baron/resource-pack-template");
const outputZip = path.resolve("plugins/fugitive-baron/fugitive-baron-resource-pack.zip");

await rm(outputZip, { force: true });

const proc = Bun.spawn(["zip", "-qr", outputZip, "."], {
	cwd: sourceDir,
	stdout: "inherit",
	stderr: "inherit",
});

const exitCode = await proc.exited;
if (exitCode !== 0) {
	throw new Error(`zip exited with code ${exitCode}`);
}

console.log(`Packaged ${path.relative(process.cwd(), outputZip)}`);
