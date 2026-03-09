#!/usr/bin/env node
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));

const { generateCssVariables, tokens } = await import('../dist/index.js');

const outputPath = process.argv[2];
const css = generateCssVariables(tokens, { selector: ':root', prefix: 'gh' });

if (outputPath) {
  const path = resolve(process.cwd(), outputPath);
  writeFileSync(path, `${css}\n`, 'utf8');
  console.log(`✅ CSS variables written to ${path}`);
} else {
  process.stdout.write(`${css}\n`);
}
