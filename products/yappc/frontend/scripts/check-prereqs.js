#!/usr/bin/env node

// Prerequisite checker for YAPPC App Creator
const { execSync } = require('child_process');
const os = require('os');

function check(command, name, installHint) {
  try {
    execSync(command, { stdio: 'ignore' });
    console.log(`✓ ${name} found`);
    return true;
  } catch {
    console.warn(`⚠️  ${name} not found. ${installHint}`);
    return false;
  }
}

console.log('\n🔍 Checking system prerequisites...\n');

// Node.js
check(
  'node --version',
  'Node.js',
  'Install Node.js 18+ from https://nodejs.org/'
);
// pnpm
check('pnpm --version', 'pnpm', 'Install pnpm 8+ from https://pnpm.io/');
// Python
check(
  'python3 --version',
  'Python 3.10+',
  'Install Python 3.10+ from your package manager.'
);
// Git
check('git --version', 'Git', 'Install Git from https://git-scm.com/');

if (os.platform() === 'linux') {
  // libvips-dev for sharp
  check(
    'dpkg -s libvips-dev',
    'libvips-dev (sharp)',
    'Run: sudo apt-get install libvips-dev'
  );
  // chromium-browser for Lighthouse
  check(
    'which chromium-browser',
    'chromium-browser (Lighthouse)',
    'Run: sudo apt-get install chromium-browser'
  );
}

console.log('\nSystem prerequisite check complete.\n');
