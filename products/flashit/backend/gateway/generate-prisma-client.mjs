#!/usr/bin/env node

import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function writeLine(message = '') {
  process.stdout.write(`${message}\n`);
}

function writeError(message = '') {
  process.stderr.write(`${message}\n`);
}

writeLine('Starting Prisma client generation...');
writeLine();
writeLine(`Current directory: ${process.cwd()}`);
writeLine(`Script directory: ${__dirname}`);
writeLine();

const generatedPath = path.join(__dirname, 'generated', 'prisma');
writeLine(`Cleaning ${generatedPath}...`);

if (fs.existsSync(generatedPath)) {
  fs.rmSync(generatedPath, { recursive: true, force: true });
  writeLine('Cleaned generated folder.');
  writeLine();
} else {
  writeLine('Generated folder does not exist yet.');
  writeLine();
}

const generatedParent = path.join(__dirname, 'generated');
if (!fs.existsSync(generatedParent)) {
  fs.mkdirSync(generatedParent, { recursive: true });
  writeLine(`Created ${generatedParent}`);
  writeLine();
}

writeLine('Running Prisma generate...');
writeLine();

const schemaPath = path.join(__dirname, 'prisma', 'schema.prisma');
writeLine(`Using schema: ${schemaPath}`);
writeLine();

const prismaProcess = spawn('pnpm', ['exec', 'prisma', 'generate', '--schema', schemaPath], {
  cwd: __dirname,
  stdio: 'inherit',
});

prismaProcess.on('close', (code) => {
  writeLine();
  writeLine('='.repeat(60));
  if (code === 0) {
    writeLine('Prisma client generated successfully.');
    writeLine(`Location: ${generatedPath}`);

    const indexFile = path.join(generatedPath, 'index.js');
    if (fs.existsSync(indexFile)) {
      writeLine(`Verified: ${indexFile} exists`);
      writeLine('Ready to start the server with: pnpm run dev');
    } else {
      writeError(`ERROR: ${indexFile} not found.`);
      process.exit(1);
    }
  } else {
    writeError(`Prisma generation failed with exit code ${code}`);
    process.exit(code ?? 1);
  }
  writeLine('='.repeat(60));
  writeLine();
});
