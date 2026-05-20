#!/usr/bin/env node

/**
 * Quick test to verify Prisma client import works.
 */

const fs = require('fs');
const path = require('path');

function writeLine(message = '') {
  process.stdout.write(`${message}\n`);
}

function writeError(message = '') {
  process.stderr.write(`${message}\n`);
}

writeLine('Testing Prisma client import...');
writeLine();

try {
  writeLine('1. Checking generated folder exists...');

  const generatedPath = path.join(process.cwd(), 'generated', 'prisma');
  if (!fs.existsSync(generatedPath)) {
    writeError('Generated folder not found.');
    process.exit(1);
  }
  writeLine(`   OK: ${generatedPath} exists`);
  writeLine();

  writeLine('2. Checking index.js exists...');
  const indexPath = path.join(generatedPath, 'index.js');
  if (!fs.existsSync(indexPath)) {
    writeError(`${indexPath} not found.`);
    process.exit(1);
  }
  writeLine(`   OK: ${indexPath} exists`);
  writeLine();

  writeLine('3. Attempting to import PrismaClient...');
  const { PrismaClient } = require('./generated/prisma/index.js');
  writeLine('   OK: import successful');
  writeLine();

  writeLine('4. Checking PrismaClient type...');
  writeLine(`   Type: ${typeof PrismaClient}`);
  writeLine(`   Constructor: ${PrismaClient.name}`);
  writeLine();

  writeLine('All tests passed.');
  writeLine('Ready to run: pnpm run dev');
} catch (error) {
  writeError('Test failed.');
  writeError(error instanceof Error ? error.message : String(error));
  writeError('Full error:');
  writeError(error instanceof Error && error.stack ? error.stack : String(error));
  process.exit(1);
}
