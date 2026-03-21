import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { deflateSync } from "node:zlib";

const width = 16;
const height = 16;
const outPath = path.resolve(
	"plugins/fugitive-baron/resource-pack-template/assets/fugitivebaron/textures/item/dragon_radar.png",
);

const pixels: number[] = [];

const rgba = (r: number, g: number, b: number, a = 255) => [r, g, b, a];
const transparent = rgba(0, 0, 0, 0);
const bezel = rgba(28, 37, 28);
const ring = rgba(74, 97, 70);
const screen = rgba(88, 188, 126);
const glow = rgba(136, 243, 171);
const orange = rgba(245, 153, 47);
const orange2 = rgba(255, 208, 92);
const dark = rgba(17, 21, 17);

for (let y = 0; y < height; y++) {
	for (let x = 0; x < width; x++) {
		const dx = x - 7.5;
		const dy = y - 7.5;
		const d = Math.sqrt(dx * dx + dy * dy);

		let color = transparent;
		if (d <= 7.5) color = bezel;
		if (d <= 6.4) color = ring;
		if (d <= 5.8) color = screen;
		if (d <= 5.0 && (x === 7 || y === 7 || x === 8 || y === 8)) color = glow;
		if (d <= 0.8) color = glow;

		if ((x === 4 && y === 5) || (x === 11 && y === 6) || (x === 9 && y === 11)) {
			color = orange;
		}
		if ((x === 5 && y === 5) || (x === 10 && y === 6) || (x === 9 && y === 10)) {
			color = orange2;
		}

		if (y === 14 && x >= 6 && x <= 9) color = dark;
		if (y === 15 && x >= 5 && x <= 10) color = dark;
		if (y === 13 && x >= 7 && x <= 8) color = dark;

		pixels.push(...color);
	}
}

await mkdir(path.dirname(outPath), { recursive: true });
await writeFile(outPath, encodePng(width, height, new Uint8Array(pixels)));
console.log(`Generated ${path.relative(process.cwd(), outPath)}`);

function encodePng(w: number, h: number, rgbaPixels: Uint8Array) {
	const stride = w * 4;
	const rgbaBuffer = Buffer.from(rgbaPixels);
	const raw = Buffer.alloc((stride + 1) * h);
	for (let y = 0; y < h; y++) {
		raw[y * (stride + 1)] = 0;
		rgbaBuffer.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
	}

	const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0);
	ihdr.writeUInt32BE(h, 4);
	ihdr[8] = 8;
	ihdr[9] = 6;
	ihdr[10] = 0;
	ihdr[11] = 0;
	ihdr[12] = 0;

	const idat = deflateSync(raw);

	return Buffer.concat([
		signature,
		chunk("IHDR", ihdr),
		chunk("IDAT", idat),
		chunk("IEND", Buffer.alloc(0)),
	]);
}

function chunk(type: string, data: Buffer) {
	const typeBuf = Buffer.from(type, "ascii");
	const length = Buffer.alloc(4);
	length.writeUInt32BE(data.length, 0);
	const crcInput = Buffer.concat([typeBuf, data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(crcInput), 0);
	return Buffer.concat([length, typeBuf, data, crc]);
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
