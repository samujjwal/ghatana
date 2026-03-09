#!/usr/bin/env node
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

// Walk upwards from current file until we find repository root (package.json name contains 'ghatana') or .git
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
let viteCli = null;
// Try to locate the vite package directory via package.json resolution, then look for known CLI entry points
try {
  const vitePkgJson = require.resolve('vite/package.json', { paths: [process.cwd(), repoRoot] });
  const vitePkgDir = path.dirname(vitePkgJson);
  const candidates = [
    path.join(vitePkgDir, 'dist', 'node', 'cli.js'),
    path.join(vitePkgDir, 'bin', 'vite.js'),
    path.join(vitePkgDir, 'bin', 'vite.cjs'),
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) {
      viteCli = c;
      break;
    }
  }
} catch (_e) {
  // ignore - we'll try pnpm layout below
}

// Fallback: scan pnpm store folders under node_modules/.pnpm for vite package and look for CLI candidates
if (!viteCli) {
  const pnpmDir = path.join(repoRoot, 'node_modules', '.pnpm');
  if (fs.existsSync(pnpmDir)) {
    const files = fs.readdirSync(pnpmDir);
    const viteFolder = files.find(f => f.startsWith('vite@'));
    if (viteFolder) {
      const base = path.join(pnpmDir, viteFolder, 'node_modules', 'vite');
      const candidates = [
        path.join(base, 'dist', 'node', 'cli.js'),
        path.join(base, 'bin', 'vite.js'),
      ];
      for (const c of candidates) if (fs.existsSync(c)) { viteCli = c; break; }
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
