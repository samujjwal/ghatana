#!/usr/bin/env sh
# generate-extension-sbom.sh
# Create a clean temporary npm install of the extension package and run cyclonedx-npm there
set -eu
echo "Helper scripts available:
 - ./scripts/generate-extension-sbom-docker.sh  # runs in Docker for reproducible SBOM
 - ./scripts/generate-extension-sbom.sh         # temp-npm-install fallback generator
"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXT_DIR="$ROOT/services/extension"

echo "Generating SBOM for services/extension (using temporary npm install)..."

TMPDIR="$(mktemp -d 2>/dev/null || mktemp -d -t 'ext-sbom')"
echo "tmp: $TMPDIR"
cp "$EXT_DIR/package.json" "$TMPDIR/package.json"
if [ -f "$EXT_DIR/package-lock.json" ]; then
  cp "$EXT_DIR/package-lock.json" "$TMPDIR/package-lock.json"
fi

cd "$TMPDIR"

echo "Running npm install in temp dir (this may take a while)..."
# Install all dependencies (including dev) to get a predictable npm tree for cyclonedx
npm install --no-audit --no-fund --loglevel=error

echo "Installing cyclonedx-npm tool..."
npm install --no-audit --no-fund --no-save @cyclonedx/cyclonedx-npm@latest --loglevel=error

echo "Running cyclonedx-npm to generate sbom.json..."
if ./node_modules/.bin/cyclonedx-npm --output-format json --output-file sbom.json; then
  cp sbom.json "$EXT_DIR/sbom.json"
  echo "Wrote $EXT_DIR/sbom.json (full SBOM)"
else
  echo "cyclonedx-npm failed in temp dir; generating fallback minimal SBOM (top-level deps)" >&2
  node -e '
const fs=require("fs");
const p=JSON.parse(fs.readFileSync("package.json","utf8"));
const sbom={
  metadata:{name:p.name,version:p.version,generatedBy:"fallback-minimal-sbom"},
  components:[]
};
const add=(deps,type)=>{ if(!deps) return; for(const [name,ver] of Object.entries(deps)){ sbom.components.push({type:"library",name,version:ver,scope:type}); }};
add(p.dependencies,"required"); add(p.devDependencies,"dev");
fs.writeFileSync("sbom.json",JSON.stringify(sbom,null,2));
'
  cp sbom.json "$EXT_DIR/sbom.json"
  echo "Wrote fallback $EXT_DIR/sbom.json"
fi

echo "Cleaning up..."
rm -rf "$TMPDIR"


