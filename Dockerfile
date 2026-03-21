FROM oven/bun:latest AS builder

WORKDIR /build
COPY package.json bun.lock ./
RUN bun install

COPY src ./src
COPY bunfig.toml tsconfig.json postcss.config.cjs tailwind.config.ts components.json ./

RUN bunx tailwindcss -c tailwind.config.ts -i src/index.css -o src/tailwind.css --minify
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
COPY --from=plugin-builder /build/plugins/fugitive-baron/build/libs/*.jar /app/plugins-bundled/
COPY docker/start.sh /app/docker/start.sh

RUN chmod +x /app/docker/start.sh

ENV CREATE_CONSOLE_IN_PIPE=true
ENV MC_AUTO_START=false

EXPOSE 3000

WORKDIR /data

ENTRYPOINT ["/app/docker/start.sh"]
