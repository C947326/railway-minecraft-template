import { mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { deflateRawSync } from "node:zlib";

const sourceDir = path.resolve("plugins/fugitive-baron/resource-pack-template");
const outputZip = path.resolve("plugins/fugitive-baron/fugitive-baron-resource-pack.zip");

type Entry = {
	relativePath: string;
	data: Buffer;
	crc32: number;
	compressed: Buffer;
	localHeaderOffset: number;
};

await rm(outputZip, { force: true });
await mkdir(path.dirname(outputZip), { recursive: true });

const filePaths = await collectFiles(sourceDir);
const entries: Entry[] = [];

for (const filePath of filePaths) {
	const relativePath = path
		.relative(sourceDir, filePath)
		.split(path.sep)
		.join("/");
	const data = await readFile(filePath);
	entries.push({
		relativePath,
		data,
		crc32: crc32(data),
		compressed: deflateRawSync(data),
		localHeaderOffset: 0,
	});
}

let offset = 0;
const localParts: Buffer[] = [];
for (const entry of entries) {
	entry.localHeaderOffset = offset;
	const localHeader = buildLocalHeader(entry);
	localParts.push(localHeader, entry.compressed);
	offset += localHeader.length + entry.compressed.length;
}

const centralParts: Buffer[] = [];
for (const entry of entries) {
	centralParts.push(buildCentralDirectoryHeader(entry));
}
const centralDirectory = Buffer.concat(centralParts);
const centralDirectoryOffset = offset;
const centralDirectorySize = centralDirectory.length;

const endOfCentralDirectory = buildEndOfCentralDirectory(
	entries.length,
	centralDirectorySize,
	centralDirectoryOffset,
);

await writeFile(
	outputZip,
	Buffer.concat([...localParts, centralDirectory, endOfCentralDirectory]),
);

console.log(`Packaged ${path.relative(process.cwd(), outputZip)}`);

async function collectFiles(root: string): Promise<string[]> {
	const files: string[] = [];
	const items = await readdir(root, { withFileTypes: true });
	for (const item of items) {
		const fullPath = path.join(root, item.name);
		if (item.isDirectory()) {
			files.push(...(await collectFiles(fullPath)));
			continue;
		}
		if (item.isFile()) {
			files.push(fullPath);
		}
	}
	return files.sort();
}

function buildLocalHeader(entry: Entry) {
	const fileName = Buffer.from(entry.relativePath, "utf8");
	const header = Buffer.alloc(30 + fileName.length);
	let cursor = 0;
	cursor = writeUInt32LE(header, 0x04034b50, cursor);
	cursor = writeUInt16LE(header, 20, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt16LE(header, 8, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt32LE(header, entry.crc32, cursor);
	cursor = writeUInt32LE(header, entry.compressed.length, cursor);
	cursor = writeUInt32LE(header, entry.data.length, cursor);
	cursor = writeUInt16LE(header, fileName.length, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	fileName.copy(header, cursor);
	return header;
}

function buildCentralDirectoryHeader(entry: Entry) {
	const fileName = Buffer.from(entry.relativePath, "utf8");
	const header = Buffer.alloc(46 + fileName.length);
	let cursor = 0;
	cursor = writeUInt32LE(header, 0x02014b50, cursor);
	cursor = writeUInt16LE(header, 20, cursor);
	cursor = writeUInt16LE(header, 20, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt16LE(header, 8, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt32LE(header, entry.crc32, cursor);
	cursor = writeUInt32LE(header, entry.compressed.length, cursor);
	cursor = writeUInt32LE(header, entry.data.length, cursor);
	cursor = writeUInt16LE(header, fileName.length, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt32LE(header, 0, cursor);
	cursor = writeUInt32LE(header, entry.localHeaderOffset, cursor);
	fileName.copy(header, cursor);
	return header;
}

function buildEndOfCentralDirectory(
	entryCount: number,
	centralDirectorySize: number,
	centralDirectoryOffset: number,
) {
	const header = Buffer.alloc(22);
	let cursor = 0;
	cursor = writeUInt32LE(header, 0x06054b50, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt16LE(header, 0, cursor);
	cursor = writeUInt16LE(header, entryCount, cursor);
	cursor = writeUInt16LE(header, entryCount, cursor);
	cursor = writeUInt32LE(header, centralDirectorySize, cursor);
	cursor = writeUInt32LE(header, centralDirectoryOffset, cursor);
	writeUInt16LE(header, 0, cursor);
	return header;
}

function writeUInt16LE(buffer: Buffer, value: number, offset: number) {
	buffer.writeUInt16LE(value, offset);
	return offset + 2;
}

function writeUInt32LE(buffer: Buffer, value: number, offset: number) {
	buffer.writeUInt32LE(value >>> 0, offset);
	return offset + 4;
}

function crc32(buffer: Buffer) {
	let crc = 0xffffffff;
	for (const byte of buffer) {
		crc ^= byte;
		for (let i = 0; i < 8; i++) {
			const mask = -(crc & 1);
			crc = (crc >>> 1) ^ (0xedb88320 & mask);
		}
	}
	return (crc ^ 0xffffffff) >>> 0;
}
