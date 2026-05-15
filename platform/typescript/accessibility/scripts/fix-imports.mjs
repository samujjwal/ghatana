#!/usr/bin/env node

import { readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const distRoot = path.join(packageRoot, 'dist');

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const absolutePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...await walk(absolutePath));
    } else if (entry.isFile() && /\.(?:js|d\.ts)$/.test(entry.name)) {
      files.push(absolutePath);
    }
  }

  return files;
}

function withJsExtension(specifier) {
  if (!specifier.startsWith('.') || path.extname(specifier)) {
    return specifier;
  }
  return `${specifier}.js`;
}

function normalizeRelativeImports(source) {
  return source
    .replace(/(from\s+['"])(\.[^'"]+)(['"])/g, (_match, prefix, specifier, suffix) => {
      return `${prefix}${withJsExtension(specifier)}${suffix}`;
    })
    .replace(/(import\(\s*['"])(\.[^'"]+)(['"]\s*\))/g, (_match, prefix, specifier, suffix) => {
      return `${prefix}${withJsExtension(specifier)}${suffix}`;
    });
}

const files = await walk(distRoot);
await Promise.all(files.map(async (file) => {
  const source = await readFile(file, 'utf8');
  const normalized = normalizeRelativeImports(source);
  if (normalized !== source) {
    await writeFile(file, normalized);
  }
}));
