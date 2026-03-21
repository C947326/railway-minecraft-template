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
RUN bun build ./src/index.ts --compile --outfile=server

FROM gradle:8.14.3-jdk21 AS plugin-builder

WORKDIR /build/plugins/fugitive-baron
COPY plugins/fugitive-baron/settings.gradle.kts ./
COPY plugins/fugitive-baron/build.gradle.kts ./
COPY plugins/fugitive-baron/src ./src
RUN gradle --no-daemon build

FROM itzg/minecraft-server:latest

ENV CONTROL_PORT=3000
ARG CITIZENS_VERSION=2.0.41-SNAPSHOT

WORKDIR /app
COPY --from=builder /build/server ./server
COPY --from=builder /build/plugins/fugitive-baron/fugitive-baron-resource-pack.zip /app/resource-pack/fugitive-baron-resource-pack.zip
COPY --from=plugin-builder /build/plugins/fugitive-baron/build/libs/*.jar /app/plugins-bundled/
ADD https://maven.citizensnpcs.co/repo/net/citizensnpcs/citizens/${CITIZENS_VERSION}/citizens-${CITIZENS_VERSION}.jar /app/plugins-bundled/Citizens.jar
COPY docker/start.sh /app/docker/start.sh

RUN chmod +x /app/docker/start.sh

ENV CREATE_CONSOLE_IN_PIPE=true
ENV MC_AUTO_START=false

EXPOSE 3000

WORKDIR /data

ENTRYPOINT ["/app/docker/start.sh"]
