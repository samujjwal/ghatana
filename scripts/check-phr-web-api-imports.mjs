#!/usr/bin/env node

import { readdirSync, readFileSync, statSync } from 'fs';
import { join, relative, resolve } from 'path';

const root = resolve(process.cwd(), 'products/phr/apps/web/src');
const scannedDirs = ['pages', 'components', 'layout'].map(dir => join(root, dir));
const apiDir = join(root, 'api');
const allowedDirectFiles = new Set([
  'api/phrApi.ts',
  'api/requestApi.ts',
]);

const violations = [];

function walk(dir) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    const stat = statSync(path);
    if (stat.isDirectory()) {
      if (entry === '__tests__') {
        continue;
      }
      walk(path);
      continue;
    }
    if (!/\.(ts|tsx)$/.test(entry)) {
      continue;
    }
    const rel = relative(root, path).replaceAll('\\', '/');
    if (allowedDirectFiles.has(rel)) {
      continue;
    }
    const content = readFileSync(path, 'utf8');
    if (content.includes("from '../api/phrApi'") || content.includes('from "../api/phrApi"')) {
      violations.push(rel);
    }
  }
}

for (const dir of scannedDirs) {
  walk(dir);
}

for (const entry of readdirSync(apiDir)) {
  if (!/Api\.ts$/.test(entry) || entry === 'phrApi.ts') {
    continue;
  }
  const path = join(apiDir, entry);
  const rel = relative(root, path).replaceAll('\\', '/');
  const content = readFileSync(path, 'utf8');
  if (content.includes("from './phrApi'") || content.includes('from "./phrApi"')) {
    violations.push(rel);
  }
}

const coreClientPath = join(apiDir, 'phrApiCore.ts');
const coreClient = readFileSync(coreClientPath, 'utf8');
if (/^const\s+\w+Schema\s*=/m.test(coreClient)) {
  violations.push('api/phrApiCore.ts');
}

if (violations.length > 0) {
  console.error('FAIL: PHR web API boundaries drifted from domain/request/schema modules:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('PASS: Production web surfaces use domain API modules');
