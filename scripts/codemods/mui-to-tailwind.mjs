#!/usr/bin/env node
/**
 * MUI → Tailwind Migration Codemod for dcmaar desktop module.
 *
 * Usage:
 *   node scripts/codemods/mui-to-tailwind.mjs [--dry-run] [--file <path>]
 *
 * What it does:
 * 1. Rewrites `import { X } from '@mui/material'` to `import { X } from '../ui/tw-compat'`
 *    (only for components that exist in tw-compat.tsx)
 * 2. Leaves `useTheme`, `sx={}` props as unfixed — flags them for manual review
 * 3. Reports files that need manual attention
 *
 * Components available in tw-compat.tsx:
 *   Box, Typography, Stack, Chip, Card, CardContent, CardHeader,
 *   CircularProgress, LinearProgress, Divider, Button, IconButton
 */
import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { join, relative, dirname } from 'path';

const TW_COMPAT_COMPONENTS = new Set([
  'Box', 'Typography', 'Stack', 'Chip', 'Card', 'CardContent', 'CardHeader',
  'CircularProgress', 'LinearProgress', 'Divider', 'Button', 'IconButton',
]);

const DESKTOP_SRC = 'products/dcmaar/modules/desktop/src';
const TW_COMPAT_PATH = 'ui/tw-compat';

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const fileIdx = args.indexOf('--file');
const singleFile = fileIdx >= 0 ? args[fileIdx + 1] : null;

function findTsxFiles(dir) {
  const results = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && entry.name !== 'node_modules') {
      results.push(...findTsxFiles(full));
    } else if (/\.(tsx?|jsx?)$/.test(entry.name)) {
      results.push(full);
    }
  }
  return results;
}

function computeRelativeImport(fromFile, toModule) {
  const fromDir = dirname(fromFile);
  const targetDir = join(DESKTOP_SRC, toModule);
  let rel = relative(fromDir, targetDir);
  if (!rel.startsWith('.')) rel = './' + rel;
  return rel;
}

function processFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  let modified = content;
  const issues = [];

  // Match: import { A, B, C } from '@mui/material';
  const muiImportRegex = /import\s*\{([^}]+)\}\s*from\s*['"]@mui\/material['"]\s*;?/g;

  modified = modified.replace(muiImportRegex, (match, imports) => {
    const components = imports.split(',').map(s => s.trim()).filter(Boolean);

    const twCompatComponents = [];
    const remainingMui = [];

    for (const comp of components) {
      if (TW_COMPAT_COMPONENTS.has(comp)) {
        twCompatComponents.push(comp);
      } else {
        remainingMui.push(comp);
      }
    }

    if (twCompatComponents.length === 0) {
      return match; // Nothing to migrate
    }

    const relPath = computeRelativeImport(filePath, TW_COMPAT_PATH);
    const twImport = `import { ${twCompatComponents.join(', ')} } from '${relPath}';`;

    if (remainingMui.length > 0) {
      issues.push(`Remaining MUI imports: ${remainingMui.join(', ')}`);
      return `${twImport}\nimport { ${remainingMui.join(', ')} } from '@mui/material';`;
    }

    return twImport;
  });

  // Flag sx prop usage
  const sxCount = (content.match(/sx=\{/g) || []).length;
  if (sxCount > 0) {
    issues.push(`${sxCount} sx={} prop(s) — convert to className/Tailwind`);
  }

  // Flag useTheme usage
  if (content.includes('useTheme')) {
    issues.push('useTheme() in use — replace with CSS variables or Tailwind dark: classes');
  }

  const changed = modified !== content;
  return { modified, changed, issues };
}

// Main
const files = singleFile ? [singleFile] : findTsxFiles(DESKTOP_SRC);
let migratedCount = 0;
let issueCount = 0;

console.log(`\n🔄 MUI → Tailwind Migration Codemod`);
console.log(`   Mode: ${dryRun ? 'DRY RUN' : 'APPLY'}`);
console.log(`   Files to scan: ${files.length}\n`);

for (const file of files) {
  if (file.includes('tw-compat') || file.includes('mui-compat')) continue;

  const { modified, changed, issues } = processFile(file);
  const rel = relative('.', file);

  if (changed || issues.length > 0) {
    console.log(`📄 ${rel}`);
    if (changed) {
      migratedCount++;
      console.log(`   ✅ Rewrote @mui/material imports → tw-compat`);
      if (!dryRun) {
        writeFileSync(file, modified, 'utf8');
      }
    }
    for (const issue of issues) {
      issueCount++;
      console.log(`   ⚠️  ${issue}`);
    }
  }
}

console.log(`\n─────────────────────────────────────`);
console.log(`✅ Migrated: ${migratedCount} files`);
console.log(`⚠️  Manual review needed: ${issueCount} issues`);
console.log(`─────────────────────────────────────\n`);

if (dryRun) {
  console.log('💡 Run without --dry-run to apply changes.\n');
}
