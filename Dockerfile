FROM itzg/minecraft-server:latest

ENV BUN_INSTALL=/usr/local/bun
ENV PATH="${BUN_INSTALL}/bin:${PATH}"
ENV CONTROL_PORT=3000

RUN curl -fsSL https://bun.sh/install | bash

WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --production

COPY src ./src
COPY bunfig.toml tsconfig.json postcss.config.cjs tailwind.config.ts components.json ./
COPY docker/start.sh /app/docker/start.sh

RUN chmod +x /app/docker/start.sh

ENV CREATE_CONSOLE_IN_PIPE=true

EXPOSE 3000

WORKDIR /data

ENTRYPOINT ["/app/docker/start.sh"]
