#!/usr/bin/env node
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

// Walk upwards from current file until we find repository root (package.json name === ghatana-monorepo) or .git
function findRepoRoot(startDir) {
  let dir = startDir;
  while (dir && dir !== path.parse(dir).root) {
    try {
      const pkg = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'));
      if (pkg && pkg.name && pkg.name.includes('ghatana')) return dir;
    } catch (_e) {
      // ignore
    }
    if (fs.existsSync(path.join(dir, '.git'))) return dir;
    dir = path.dirname(dir);
  }
  return null;
}

const repoRoot = findRepoRoot(__dirname) || process.cwd();
const nodeModulesPath = path.join(repoRoot, 'node_modules');

// Path to the vite binary in the pnpm store or installed node_modules
let viteCli;
try {
  // Prefer local package resolution
  viteCli = require.resolve('vite/bin/vite.js', { paths: [process.cwd(), repoRoot] });
} catch (_e) {
  // fallback to pnpm store layout (best-effort)
  viteCli = path.join(repoRoot, 'node_modules', '.pnpm');
}

if (!viteCli || viteCli.includes('.pnpm') && !fs.existsSync(viteCli)) {
  // Try to find any vite binary under node_modules/.pnpm
  const pnpmDir = path.join(repoRoot, 'node_modules', '.pnpm');
  if (fs.existsSync(pnpmDir)) {
    const files = fs.readdirSync(pnpmDir);
    const viteFolder = files.find(f => f.startsWith('vite@'));
    if (viteFolder) {
      const candidate = path.join(pnpmDir, viteFolder, 'node_modules', 'vite', 'bin', 'vite.js');
      if (fs.existsSync(candidate)) viteCli = candidate;
    }
  }
}

if (!viteCli || !fs.existsSync(viteCli)) {
  console.error('Could not locate vite CLI. Make sure vite is installed in the workspace or package.');
  process.exit(1);
}

// Set NODE_PATH so ESM resolution can find hoisted packages
const env = Object.assign({}, process.env);
env.NODE_PATH = nodeModulesPath;

console.log('Starting Vite via:', viteCli);
console.log('Using NODE_PATH:', env.NODE_PATH);

const child = spawn(process.execPath, [viteCli], {
  stdio: 'inherit',
  env,
  cwd: process.cwd(),
});

child.on('exit', code => process.exit(code));
child.on('error', err => {
  console.error('Failed to start Vite:', err);
  process.exit(1);
});
