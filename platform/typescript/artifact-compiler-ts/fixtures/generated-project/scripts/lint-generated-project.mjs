#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import path from 'node:path';

const root = path.resolve(process.cwd(), 'platform/typescript/artifact-compiler-ts/fixtures/generated-project');
const source = readFileSync(path.join(root, 'src/component.ts'), 'utf-8');

if (source.includes('TODO') || source.includes('FIXME')) {
  console.error('generated fixture lint failed: TODO/FIXME markers are not allowed');
  process.exit(1);
}

console.log('generated fixture lint passed');
