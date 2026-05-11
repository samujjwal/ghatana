#!/usr/bin/env tsx
/**
 * Migrate from local i18n to platform @ghatana/i18n
 * This script updates all files to use the platform i18n package
 */

import { readFileSync, writeFileSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const srcDir = join(__dirname, '../src');

function processFile(filePath: string): boolean {
  const content = readFileSync(filePath, 'utf-8');
  let modified = false;
  let newContent = content;

  // Replace local i18n imports with platform imports
  if (newContent.includes("from './i18n") || newContent.includes("from '../i18n") || newContent.includes("from '../../i18n")) {
    newContent = newContent.replace(
      /from ['"]\.\/i18n['"]/g,
      "from '@ghatana/i18n'"
    );
    newContent = newContent.replace(
      /from ['"]\.\.\/i18n['"]/g,
      "from '@ghatana/i18n'"
    );
    newContent = newContent.replace(
      /from ['"]\.\.\/\.\.\/i18n['"]/g,
      "from '@ghatana/i18n'"
    );
    modified = true;
  }

  // Replace useI18n hook with useTranslation
  if (newContent.includes('useI18n')) {
    newContent = newContent.replace(
      /\{ useI18n \}/g,
      "{ useTranslation }"
    );
    newContent = newContent.replace(
      /useI18n\(\)/g,
      "useTranslation('common')"
    );
    newContent = newContent.replace(
      /const \{ t \} = useI18n\(\)/g,
      "const { t } = useTranslation('common')"
    );
    newContent = newContent.replace(
      /const \{ t, locale \} = useI18n\(\)/g,
      "const { t, i18n } = useTranslation('common')"
    );
    newContent = newContent.replace(
      /const \{ locale \} = useI18n\(\)/g,
      "const { i18n } = useTranslation('common')"
    );
    modified = true;
  }

  // Replace locale references with i18n.language
  if (newContent.includes('.locale')) {
    newContent = newContent.replace(
      /\.locale/g,
      '.i18n.language'
    );
    modified = true;
  }

  if (modified) {
    writeFileSync(filePath, newContent, 'utf-8');
    return true;
  }

  return false;
}

function walkDirectory(dir: string, callback: (filePath: string) => void) {
  const files = readdirSync(dir, { withFileTypes: true });

  for (const file of files) {
    const filePath = join(dir, file.name);

    if (file.isDirectory()) {
      // Skip node_modules and __tests__ directories
      if (file.name !== 'node_modules' && file.name !== '__tests__' && file.name !== '.next') {
        walkDirectory(filePath, callback);
      }
    } else if (file.name.endsWith('.tsx') || file.name.endsWith('.ts')) {
      callback(filePath);
    }
  }
}

let modifiedCount = 0;
let processedCount = 0;

walkDirectory(srcDir, (filePath) => {
  processedCount++;
  if (processFile(filePath)) {
    modifiedCount++;
    console.log(`✅ Updated: ${filePath}`);
  }
});

console.log(`\nProcessed ${processedCount} files, modified ${modifiedCount} files`);
