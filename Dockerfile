FROM oven/bun:latest AS builder

WORKDIR /build
COPY package.json bun.lock ./
RUN bun install

COPY src ./src
COPY tools ./tools
COPY plugins/fugitive-baron/resource-pack-template ./plugins/fugitive-baron/resource-pack-template
COPY bunfig.toml tsconfig.json postcss.config.cjs tailwind.config.ts components.json ./

RUN bunx tailwindcss -c tailwind.config.ts -i src/index.css -o src/tailwind.css --minify
RUN bun run baron:pack-resource-pack
RUN bun -e 'const apiUrl = "https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/api/json"; const build = await fetch(apiUrl).then(r => { if (!r.ok) throw new Error(`build api ${r.status}`); return r.json(); }); const artifact = (build.artifacts || []).find(a => typeof a.fileName === "string" && a.fileName.startsWith("Citizens-") && a.fileName.endsWith(".jar")); if (!artifact) throw new Error("missing Citizens artifact"); const jarUrl = `${build.url}artifact/${artifact.relativePath}`; const jar = await fetch(jarUrl).then(r => { if (!r.ok) throw new Error(`jar ${r.status}`); return r.arrayBuffer(); }); await Bun.write("/build/Citizens.jar", jar);'
RUN bun build ./src/index.ts --compile --outfile=server

FROM gradle:8.14.3-jdk21 AS plugin-builder

WORKDIR /build/plugins/fugitive-baron
COPY plugins/fugitive-baron/settings.gradle.kts ./
COPY plugins/fugitive-baron/build.gradle.kts ./
COPY plugins/fugitive-baron/src ./src
RUN gradle --no-daemon build

FROM itzg/minecraft-server:latest

ENV CONTROL_PORT=3000

WORKDIR /app
COPY --from=builder /build/server ./server
COPY --from=builder /build/plugins/fugitive-baron/fugitive-baron-resource-pack.zip /app/resource-pack/fugitive-baron-resource-pack.zip
COPY --from=builder /build/Citizens.jar /app/plugins-bundled/Citizens.jar
COPY --from=plugin-builder /build/plugins/fugitive-baron/build/libs/*.jar /app/plugins-bundled/
COPY docker/start.sh /app/docker/start.sh

RUN chmod +x /app/docker/start.sh

ENV CREATE_CONSOLE_IN_PIPE=true
ENV MC_AUTO_START=false

EXPOSE 3000

WORKDIR /data

ENTRYPOINT ["/app/docker/start.sh"]
