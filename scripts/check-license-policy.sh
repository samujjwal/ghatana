#!/bin/bash
#
# Enforce OSS license denylist policy for pnpm workspaces.
# Denies AGPL/GPL/SSPL licenses in dependency tree.
#

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "Checking OSS license policy (deny: AGPL/GPL/SSPL)..."

if ! command -v pnpm >/dev/null 2>&1; then
  echo "pnpm is required to run license policy checks." >&2
  exit 1
fi

LICENSE_FILE="$(mktemp)"
trap 'rm -f "$LICENSE_FILE"' EXIT

pnpm licenses list --json > "$LICENSE_FILE"

node - "$LICENSE_FILE" <<'NODE'
const fs = require('fs');
const raw = fs.readFileSync(process.argv[2], 'utf8');

let data;
try {
  data = JSON.parse(raw);
} catch (err) {
  console.error(`Failed to parse pnpm license JSON: ${err.message}`);
  process.exit(1);
}

const denied = [];

function isDenied(license) {
  if (!license || typeof license !== 'string') return false;

  const isDeniedPart = (part) => {
    const value = part.toUpperCase().replace(/[()]/g, '').trim();
    if (value.includes('AGPL') || value.includes('SSPL')) return true;
    if (value.includes('LGPL')) return false;
    return value.includes('GPL');
  };

  const options = license
    .split(/\s+OR\s+/i)
    .map((part) => part.trim())
    .filter(Boolean);

  // Dual-licensed deps are acceptable when at least one non-denied option exists.
  if (options.length > 1) {
    return options.every((part) => isDeniedPart(part));
  }

  return isDeniedPart(license);
}

for (const [license, packages] of Object.entries(data)) {
  if (!isDenied(license)) continue;
  if (!Array.isArray(packages)) continue;

  for (const pkg of packages) {
    denied.push({
      license,
      name: pkg && pkg.name ? pkg.name : 'unknown',
      versions: pkg && Array.isArray(pkg.versions) ? pkg.versions.join(', ') : 'unknown'
    });
  }
}

if (denied.length === 0) {
  console.log('License policy check passed.');
  process.exit(0);
}

console.error('Denied licenses detected:');
for (const item of denied) {
  console.error(`  - ${item.name}@${item.versions} -> ${item.license}`);
}

process.exit(1);
NODE
