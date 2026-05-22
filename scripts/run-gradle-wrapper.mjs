#!/usr/bin/env node

import { spawnSync } from 'node:child_process';

const gradleCommand = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
const args = process.argv.slice(2);

if (args.length === 0) {
  console.error('Usage: node scripts/run-gradle-wrapper.mjs <gradle-args...>');
  process.exit(1);
}

const result =
  process.platform === 'win32'
    ? spawnSync(
        'cmd.exe',
        ['/d', '/c', ['call', gradleCommand, ...args].join(' ')],
        { stdio: 'inherit' },
      )
    : spawnSync(gradleCommand, args, { stdio: 'inherit' });

if (typeof result.status === 'number') {
  process.exit(result.status);
}

if (result.error) {
  console.error(result.error.message);
}

process.exit(1);
