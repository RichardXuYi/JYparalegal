# syntax=docker/dockerfile:1
# GrandPoem Studio Web - multi-stage image (Fastify host server + built renderer).
#
# Build from the REPO ROOT (the pnpm workspace context is required):
#   node studio-web/scripts/prepare-linux-binaries.mjs   # once: bundle Linux uv/agent-browser
#   docker build -f studio-web/Dockerfile -t jy-studio-web .
#
# Run standalone (or via the root docker-compose.yml `studio-web` service):
#   docker run -p 8788:8788 \
#     -e BACKEND_URL=http://host.docker.internal:8181 \
#     -e JWT_SECRET=change-me \
#     -v studio_web_data:/data jy-studio-web

# ===========================================================================
# Stage 1: Builder - install all deps (incl. devDependencies) and build frontend
# ===========================================================================
FROM node:22-slim AS builder

RUN corepack enable

WORKDIR /repo

# Workspace manifests + install scripts first for better layer caching.
COPY pnpm-workspace.yaml pnpm-lock.yaml package.json .npmrc ./
COPY studio-web/package.json studio-web/package.json
COPY studio-web/scripts studio-web/scripts

# Known pitfall: exotic transitive deps can break frozen installs; fall back once.
RUN pnpm install --filter jy-studio-web --frozen-lockfile \
  || pnpm install --filter jy-studio-web --no-frozen-lockfile

COPY studio-web studio-web

# Vite production build (outputs studio-web/dist, served by the Fastify host).
# The renderer bundle needs more heap than the Node default (OOM at 2 GB).
RUN NODE_OPTIONS=--max-old-space-size=6144 pnpm --filter jy-studio-web run build

# ===========================================================================
# Stage 2: Runner - production deps only, built artifacts from builder
# ===========================================================================
FROM node:22-slim AS runner

RUN corepack enable

WORKDIR /repo

# Workspace manifests + install scripts for prod-only install.
COPY pnpm-workspace.yaml pnpm-lock.yaml package.json .npmrc ./
COPY studio-web/package.json studio-web/package.json
COPY studio-web/scripts studio-web/scripts

# Install only production dependencies (excludes devDependencies).
RUN pnpm install --filter jy-studio-web --prod --frozen-lockfile \
  || pnpm install --filter jy-studio-web --prod --no-frozen-lockfile

# Copy application source (server TS, shared, resources, etc.).
COPY studio-web studio-web

# Overwrite dist with the freshly built renderer from the builder stage.
COPY --from=builder /repo/studio-web/dist studio-web/dist

# process.cwd() must be studio-web so resources/bin/<platform>-<arch> resolves.
WORKDIR /repo/studio-web

ENV NODE_ENV=production \
    PORT=8788 \
    DATA_DIR=/data \
    BACKEND_URL=http://localhost:8181

VOLUME /data
EXPOSE 8788

# server/tsconfig.json is noEmit by design - the host runs the TS sources via tsx.
CMD ["pnpm", "run", "start:server"]
