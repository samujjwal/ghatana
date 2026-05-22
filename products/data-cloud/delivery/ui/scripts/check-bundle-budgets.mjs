#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, '..');

const args = new Set(process.argv.slice(2));
const skipBuild = args.has('--skip-build');

const chunkBudgetKb = Number.parseInt(process.env.CHUNK_BUDGET_KB ?? '600', 10);
const vendorBudgetKb = Number.parseInt(process.env.VENDOR_BUDGET_KB ?? '1000', 10);
const distDir = process.env.DIST_DIR
  ? path.resolve(process.env.DIST_DIR)
  : path.join(rootDir, 'dist', 'assets');

function fail(message) {
  console.error(message);
  process.exit(1);
}

function collectJsFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const entryPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectJsFiles(entryPath));
      continue;
    }
    if (entry.name.endsWith('.js')) {
      files.push(entryPath);
    }
  }
  return files;
}

if (!Number.isFinite(chunkBudgetKb) || chunkBudgetKb <= 0) {
  fail(`ERROR: CHUNK_BUDGET_KB must be a positive integer, received '${process.env.CHUNK_BUDGET_KB ?? ''}'`);
}
if (!Number.isFinite(vendorBudgetKb) || vendorBudgetKb <= 0) {
  fail(`ERROR: VENDOR_BUDGET_KB must be a positive integer, received '${process.env.VENDOR_BUDGET_KB ?? ''}'`);
}

const shouldBuild = !skipBuild || !existsSync(distDir);
if (skipBuild && !existsSync(distDir)) {
  console.warn('dist/assets not found while --skip-build was requested; running build fallback.');
}

if (shouldBuild) {
  console.log('==> Building Data Cloud UI...');
  const build = spawnSync('pnpm', ['build'], {
    cwd: rootDir,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });
  if (build.status !== 0) {
    process.exit(build.status ?? 1);
  }
}

let files;
try {
  files = collectJsFiles(distDir);
} catch {
  fail(`ERROR: dist/assets directory not found at ${distDir}\n       Run without --skip-build or run 'pnpm build' first.`);
}

let pass = 0;
let failCount = 0;
const warnings = [];

for (const file of files) {
  const filename = path.basename(file);
  const sizeBytes = statSync(file).size;
  const sizeKb = Math.floor(sizeBytes / 1024);
  const budgetKb = filename.startsWith('vendor') ? vendorBudgetKb : chunkBudgetKb;

  if (sizeKb > budgetKb) {
    warnings.push(`  OVER BUDGET  ${filename}  ${sizeKb} kB  (limit: ${budgetKb} kB)`);
    failCount += 1;
  } else {
    console.log(`  OK           ${filename}  ${sizeKb} kB  (limit: ${budgetKb} kB)`);
    pass += 1;
  }
}

console.log('');
console.log(`Bundle budget check: ${pass} OK, ${failCount} OVER BUDGET`);

if (failCount > 0) {
  console.log('');
  console.log('FAILED - the following chunks exceed their budget:');
  for (const warning of warnings) {
    console.log(warning);
  }
  console.log('');
  console.log('To fix: split the chunk further in vite.config.ts (manualChunks)');
  console.log('        or reduce imported dependencies.');
  process.exit(1);
}

console.log('All chunks within budget.');
