#!/usr/bin/env node
// Emit a single-line escaped JSON for use as a GitHub secret value for VITE_ADDON_PUBLIC_KEYS
// Usage: node scripts/make-addon-keys-secret.cjs <pem1> [pem2 ...]

const fs = require('fs');
const path = require('path');

function readPem(p) {
  return fs.readFileSync(p, 'utf8').replace(/\r\n/g, '\n').trim();
}

if (process.argv.length < 3) {
  console.error('Usage: node scripts/make-addon-keys-secret.cjs <pem-file> [pem-file ...]');
  process.exit(2);
}

const files = process.argv.slice(2);
const entries = files.map((f) => ({
  id: path.basename(f, path.extname(f)).replace(/[^a-zA-Z0-9._-]/g, '-'),
  spkiPem: readPem(f),
}));
const json = JSON.stringify(entries);
console.log(json);
