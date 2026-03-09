#!/usr/bin/env node
// scripts/make-addon-keys-json.cjs
// Usage: node scripts/make-addon-keys-json.cjs [glob|paths...] [--out file]
// Reads PEM files and emits a JSON array suitable for VITE_ADDON_PUBLIC_KEYS

const fs = require('fs');
const path = require('path');

const glob = require('glob');

function readPem(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8').trim();
    // Normalize line endings to \n
    return content.replace(/\r\n/g, '\n');
  } catch (e) {
    console.error('Failed to read', filePath, e.message || e);
    return null;
  }
}

function idFromFilename(filePath) {
  return path.basename(filePath, path.extname(filePath)).replace(/[^a-zA-Z0-9._-]/g, '-');
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length === 0) {
    console.log(
      'Usage: node scripts/make-addon-keys-json.cjs <glob|file> [<glob|file> ...] [--out file]'
    );
    process.exit(1);
  }

  let outFile = null;
  const inputs = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--out' && args[i + 1]) {
      outFile = args[i + 1];
      i++;
      continue;
    }
    inputs.push(args[i]);
  }

  const files = new Set();
  for (const inp of inputs) {
    if (inp.includes('*')) {
      const matches = glob.sync(inp, { nodir: true });
      matches.forEach((m) => files.add(m));
    } else if (fs.existsSync(inp)) files.add(inp);
  }

  if (files.size === 0) {
    console.error('No files found for inputs:', inputs.join(', '));
    process.exit(2);
  }

  const entries = [];
  for (const f of Array.from(files)) {
    const pem = readPem(f);
    if (!pem) continue;
    const id = idFromFilename(f);
    entries.push({ id, spkiPem: pem });
  }

  const json = JSON.stringify(entries, null, 2);
  if (outFile) {
    fs.writeFileSync(outFile, json, 'utf8');
    console.log('Wrote', outFile);
  } else {
    console.log(json);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(3);
});
