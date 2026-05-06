#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

function readText(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

const checks = [
  {
    label: 'PHR app shell alias',
    file: 'products/phr/apps/web/src/layout/AppShell.tsx',
    requiredTokens: ['PhrProductShell as AppShell', './PhrProductShell'],
  },
  {
    label: 'PHR shared shell implementation',
    file: 'products/phr/apps/web/src/layout/PhrProductShell.tsx',
    requiredTokens: ['@ghatana/product-shell', 'ProductShell', 'ProductShellConfig'],
  },
  {
    label: 'DMOS shared shell implementation',
    file: 'products/digital-marketing/ui/src/layout/DmosProductShell.tsx',
    requiredTokens: ['@ghatana/product-shell', 'ProductShell', 'ProductShellConfig'],
  },
  {
    label: 'DMOS shell adoption',
    file: 'products/digital-marketing/ui/src/App.tsx',
    requiredTokens: ['DmosProductShell', '<DmosProductShell>'],
  },
  {
    label: 'FlashIt shared shell implementation',
    file: 'products/flashit/client/web/src/components/FlashitProductShell.tsx',
    requiredTokens: ['@ghatana/product-shell', 'ProductShell', 'ProductShellConfig'],
  },
  {
    label: 'FlashIt shell adoption in app',
    file: 'products/flashit/client/web/src/App.tsx',
    requiredTokens: ['FlashitProductShell', '<FlashitProductShell>'],
  },
  {
    label: 'FlashIt shell adoption in layout wrapper',
    file: 'products/flashit/client/web/src/components/Layout.tsx',
    requiredTokens: ['FlashitProductShell', '<FlashitProductShell>'],
  },
];

const violations = [];

for (const check of checks) {
  const source = readText(check.file);
  for (const token of check.requiredTokens) {
    if (!source.includes(token)) {
      violations.push(`${check.file}: ${check.label} is missing required token ${JSON.stringify(token)}`);
    }
  }
}

if (violations.length > 0) {
  console.error('Shared product shell contract violations:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Shared product shell contract passed.');
