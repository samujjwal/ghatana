#!/usr/bin/env node
/*
  e2e-run.ts - Orchestrates local e2e run:
    1. Build extension with test hooks
    2. Serve e2e fixtures
    3. Launch Chromium with the unpacked extension
    4. Open fixture page and trigger metric
    5. Capture network and console output

  Usage: node --loader ts-node/esm ./scripts/e2e-run.ts
*/

import path from 'path';
import { spawn } from 'child_process';
import fs from 'fs';

const projectRoot = process.cwd();
const scriptsDist = path.resolve(projectRoot, 'dist-scripts');

function runCmd(cmd: string, args: string[], opts: any = {}): Promise<void> {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: 'inherit', ...opts });
    p.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`${cmd} ${args.join(' ')} exited ${code}`))));
  });
}

(async () => {
  try {
    console.log('1) Ensure build with test hooks: pnpm run build:testhooks');
    if (!fs.existsSync(path.resolve(__dirname, '..', 'dist', 'chrome'))) {
      console.log('dist/chrome not found. Running build:testhooks...');
      await runCmd('pnpm', ['run', 'build:testhooks'], { cwd: path.resolve(__dirname, '..') });
    } else {
      console.log('Found dist/chrome; skipping build.');
    }

    // 2) start fixture server
    console.log('2) Starting fixture server (background)');
    const serverScript = fs.existsSync(path.join(scriptsDist, 'serve-e2e-fixtures.js'))
      ? path.join(scriptsDist, 'serve-e2e-fixtures.js')
      : path.resolve(projectRoot, 'scripts', 'serve-e2e-fixtures.ts');
    const server = spawn('node', [serverScript], { cwd: projectRoot, stdio: 'inherit' });

    // 3) launch playwright script (use node to run a small script that uses playwright)
    console.log('3) Launching Playwright runner (this will open a browser)');
    const runnerScript = fs.existsSync(path.join(scriptsDist, 'e2e-run-playwright.js'))
      ? path.join(scriptsDist, 'e2e-run-playwright.js')
      : path.resolve(projectRoot, 'scripts', 'e2e-run-playwright.ts');
  const runnerArgs = process.argv.slice(2);
  const runner = spawn('node', [runnerScript, ...runnerArgs], { cwd: projectRoot, stdio: 'inherit' });

    runner.on('close', (code) => {
      console.log('Playwright runner finished with code', code);
      server.kill();
      process.exit(code ?? 0);
    });
  } catch (err) {
    console.error('e2e-run failed:', err);
    process.exit(1);
  }
})();
