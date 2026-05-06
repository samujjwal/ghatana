#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const expectedRepositoryUrl = 'git+https://github.com/samujjwal/ghatana.git';
const expectedBugsUrl = 'https://github.com/samujjwal/ghatana/issues';

const packagePaths = [
  'products/phr/apps/web/package.json',
  'products/digital-marketing/ui/package.json',
  'products/flashit/package.json',
  'products/flashit/client/web/package.json',
  'products/flashit/client/mobile/package.json',
  'products/flashit/backend/gateway/package.json',
  'products/flashit/libs/ts/shared/package.json',
];

const violations = [];

for (const packagePath of packagePaths) {
  const pkg = JSON.parse(readFileSync(path.join(repoRoot, packagePath), 'utf8'));
  const expectedDirectory = packagePath.replace(/\/package\.json$/, '');
  const expectedHomepage = `https://github.com/samujjwal/ghatana/tree/main/${expectedDirectory}#readme`;

  if (pkg.repository?.url !== expectedRepositoryUrl) {
    violations.push(`${packagePath}: repository.url must be ${expectedRepositoryUrl}`);
  }

  if (pkg.repository?.directory !== expectedDirectory) {
    violations.push(`${packagePath}: repository.directory must be ${expectedDirectory}`);
  }

  if (pkg.bugs?.url !== expectedBugsUrl) {
    violations.push(`${packagePath}: bugs.url must be ${expectedBugsUrl}`);
  }

  if (pkg.homepage !== expectedHomepage) {
    violations.push(`${packagePath}: homepage must be ${expectedHomepage}`);
  }

  if (typeof pkg.author !== 'string' || !pkg.author.includes('Ghatana')) {
    violations.push(`${packagePath}: author must identify Ghatana ownership`);
  }
}

if (violations.length > 0) {
  console.error('Product package metadata check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Product package metadata check passed.');
