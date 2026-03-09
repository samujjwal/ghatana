#!/usr/bin/env node
// Quick verification script to ensure no generated artifacts exist under `src/`.
// It will exit with code 1 if any .d.ts, .map, or .jsx files are found under src
// (except when they are intentionally whitelisted).

const { execSync } = require('child_process');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..');
const patterns = ['**/src/**/*.d.ts', '**/src/**/*.map', '**/src/**/*.jsx'];

// Whitelist files that are allowed to exist in src (relative to repo root)
const whitelist = new Set([
  // example: 'libs/some-lib/src/types/some-ambient.d.ts'
  // Temporary shims allowed during migration
  'libs/canvas/src/types/shims-yjs.d.ts',
  // (no temporary shim whitelists remain)
]);

function run() {
  try {
    const args = patterns.map((p) => `-name "${p}"`).join(' -o ');
    // Use git ls-files to list tracked files and also check the filesystem for
    // untracked files via glob.
    const found = [];

    // Check tracked files
    const gitCmd = `git ls-files -- "**/src/**/*.d.ts" "**/src/**/*.map" "**/src/**/*.jsx"`;
    const tracked = execSync(gitCmd, { cwd: repoRoot, encoding: 'utf8' })
      .split('\n')
      .filter(Boolean);
    tracked.forEach((f) => {
      if (!whitelist.has(f)) found.push({ file: f, tracked: true });
    });

    // Check filesystem for untracked files
    const glob = require('glob');
    const fsMatches = patterns.flatMap((p) =>
      glob.sync(p, { cwd: repoRoot, nodir: true })
    );
    fsMatches.forEach((f) => {
      if (!tracked.includes(f) && !whitelist.has(f))
        found.push({ file: f, tracked: false });
    });

    if (found.length > 0) {
      console.error('\nFound generated artifacts under src/:');
      found.forEach(({ file, tracked }) => {
        console.error(` - ${file} ${tracked ? '(tracked)' : '(untracked)'}`);
      });
      console.error(
        '\nPlease move generated .d.ts/.map files into the package dist (e.g. dist/types) or delete them and commit the removal.'
      );
      process.exit(1);
    }

    console.log(
      'verify-src-clean: OK — no .d.ts/.map/.jsx files found under src/ (untracked or tracked).'
    );
    process.exit(0);
  } catch (err) {
    console.error('verify-src-clean: failed', err.message || err);
    process.exit(2);
  }
}

run();
