#!/usr/bin/env node

/**
 * Quick test to verify Prisma client setup.
 */

const fs = require('fs');

function writeLine(message = '') {
  process.stdout.write(`${message}\n`);
}

function writeError(message = '') {
  process.stderr.write(`${message}\n`);
}

writeLine('Testing Prisma client setup...');
writeLine();

try {
  writeLine('1. Testing generated/prisma/index.js import...');
  const { PrismaClient } = require('./generated/prisma/index.js');
  writeLine('   OK: imported PrismaClient');
  writeLine();

  writeLine('2. Checking PrismaClient type...');
  writeLine(`   Type: ${typeof PrismaClient}`);
  writeLine(`   Name: ${PrismaClient.name}`);
  writeLine();

  writeLine('3. Checking schema.prisma...');
  const schema = fs.readFileSync('./generated/prisma/schema.prisma', 'utf-8');

  if (schema.includes('provider = "postgresql"')) {
    writeLine('   OK: schema uses PostgreSQL');
    writeLine();
  } else {
    writeError('   Schema does not use PostgreSQL.');
    process.exit(1);
  }

  writeLine('4. Checking DATABASE_URL...');
  require('dotenv').config();
  if (process.env.DATABASE_URL) {
    writeLine('   OK: DATABASE_URL set');
    writeLine();
  } else {
    writeError('   DATABASE_URL not set.');
    process.exit(1);
  }

  writeLine('All checks passed.');
  writeLine('Ready to start: pnpm run dev');
} catch (error) {
  writeError('Test failed.');
  writeError(error instanceof Error ? error.message : String(error));
  writeError('Full error:');
  writeError(error instanceof Error && error.stack ? error.stack : String(error));
  process.exit(1);
}
