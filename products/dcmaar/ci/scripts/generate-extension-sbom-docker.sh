#!/usr/bin/env sh
set -eu
# Dockerized SBOM generator for services/extension
# - Runs a clean `npm ci` inside a Node 20 container and executes cyclonedx-npm
# - Writes `sbom.json` into `services/extension` (host-mounted)
# - If the dockerized run fails, fall back to the existing repo helper which
#   attempts a temp npm install and will emit a minimal SBOM on failure.

WORKDIR="$(pwd)"
EXT_DIR="$WORKDIR/services/extension"
IMAGE="node:20-bullseye-slim"

echo "Using image: $IMAGE"
echo "Extension dir: $EXT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found in PATH. Falling back to repo helper script."
  sh ./scripts/generate-extension-sbom.sh
  exit $?
fi

echo "Pulling Docker image (if necessary)..."
docker pull "$IMAGE" >/dev/null 2>&1 || true

echo "Running cyclonedx inside container..."
set +e
docker run --rm -v "$EXT_DIR":/work/extension -w /work/extension "$IMAGE" sh -c '\
  set -e
  if [ -f package-lock.json ] || [ -f npm-shrinkwrap.json ]; then
    echo "Lockfile detected; running npm ci..."
    npm ci --no-audit --no-fund >/tmp/npm_install.log 2>&1 || (cat /tmp/npm_install.log && exit 1)
  else
    echo "No lockfile found; running npm install (non-hermetic)..."
    npm install --no-audit --no-fund >/tmp/npm_install.log 2>&1 || (cat /tmp/npm_install.log && exit 1)
  fi
  echo "Running cyclonedx-npm to generate sbom.json..."
  npx --yes @cyclonedx/cyclonedx-npm -o sbom.json >/tmp/cyclone.log 2>&1 || (cat /tmp/cyclone.log && exit 2)
'
STATUS=$?
set -e

if [ $STATUS -eq 0 ]; then
  echo "SBOM successfully generated at services/extension/sbom.json"
  exit 0
fi

echo "Dockerized SBOM generation failed (exit code: $STATUS)."
echo "Falling back to the repository helper (scripts/generate-extension-sbom.sh)..."
sh ./scripts/generate-extension-sbom.sh
exit $?
