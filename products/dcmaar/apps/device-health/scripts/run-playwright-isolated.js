import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Clear any existing matchers
const matcherSymbol = Symbol.for('$$jest-matchers-object');
if (globalThis[matcherSymbol] !== undefined) {
  delete globalThis[matcherSymbol];
}

// Get the path to the Playwright CLI
const playwrightCli = path.join(__dirname, '..', 'node_modules', '.bin', 'playwright');

// Spawn a new process to run Playwright
const args = ['test', '--project=chromium', ...process.argv.slice(2)];

console.log(`Running: playwright ${args.join(' ')}`);

const child = spawn(playwrightCli, args, {
  stdio: 'inherit',
  shell: true,
  env: {
    ...process.env,
    // Ensure we don't inherit any problematic globals
    NODE_OPTIONS: undefined,
  },
});

child.on('exit', (code) => {
  process.exit(code || 0);
});

// Handle process termination
process.on('SIGINT', () => {
  child.kill('SIGINT');
});

process.on('SIGTERM', () => {
  child.kill('SIGTERM');
});
