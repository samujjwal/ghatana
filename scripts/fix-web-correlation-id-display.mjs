#!/usr/bin/env node

/**
 * G11-T02: Automated fix to display correlation IDs in web error states.
 * 
 * This script:
 * 1. Adds SafeError import to pages that don't have it
 * 2. Replaces error divs with SafeError components that include correlationId
 */

import { readFileSync, writeFileSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const PAGES_DIR = join(__dirname, '..', 'products', 'phr', 'apps', 'web', 'src', 'pages');

function fixFile(filePath) {
  let content = readFileSync(filePath, 'utf-8');
  let modified = false;
  const fileName = filePath.split('\\').pop().split('/').pop();

  // Check if SafeError is already imported
  if (!content.includes("import { SafeError }")) {
    // Find the last import line and add SafeError import after it
    const importMatch = content.match(/^(import .+;)$/m);
    if (importMatch) {
      const lastImportIndex = content.lastIndexOf(importMatch[0]);
      const insertPosition = content.indexOf('\n', lastImportIndex) + 1;
      content = content.slice(0, insertPosition) + 
                "import { SafeError } from '../components/SafeError';\n" + 
                content.slice(insertPosition);
      modified = true;
    }
  }

  // Replace error divs with SafeError components
  // Pattern: <div className="error" role="alert">{t('xxx')}: {error}</div>
  content = content.replace(
    /<div className="error" role="alert">\{t\('([^']+)'\)\}:\s*\{error\}<\/div>/g,
    (match, i18nKey) => {
      if (match.includes('SafeError')) return match;
      modified = true;
      return `<SafeError title={t('${i18nKey}')} message={error} correlationId={session?.tenantId + '-' + session?.principalId} />`;
    }
  );

  // Pattern: <div className="error" role="alert">{t('xxx.error')}: {error}</div>
  content = content.replace(
    /<div className="error" role="alert">\{t\('([^']+)'\)\}:\s*\{error\}<\/div>/g,
    (match, i18nKey) => {
      if (match.includes('SafeError')) return match;
      modified = true;
      return `<SafeError title={t('${i18nKey}')} message={error} correlationId={session?.tenantId + '-' + session?.principalId} />`;
    }
  );

  // Pattern: <div className="error" role="alert">{t('xxx')}: {error}</div>
  content = content.replace(
    /<div className="error" role="alert">\{t\('([^']+)'\)\}:\s*\{[^}]+\}<\/div>/g,
    (match, i18nKey) => {
      if (match.includes('SafeError')) return match;
      modified = true;
      return `<SafeError title={t('${i18nKey}')} message={error} correlationId={session?.tenantId + '-' + session?.principalId} />`;
    }
  );

  // Pattern: <div className="error">{t('xxx')}: {error}</div>
  content = content.replace(
    /<div className="error">\{t\('([^']+)'\)\}:\s*\{error\}<\/div>/g,
    (match, i18nKey) => {
      if (match.includes('SafeError')) return match;
      modified = true;
      return `<SafeError title={t('${i18nKey}')} message={error} correlationId={session?.tenantId + '-' + session?.principalId} />`;
    }
  );

  // Pattern: <div className="error" role="alert">{t('xxx')}</div>
  content = content.replace(
    /<div className="error" role="alert">\{t\('([^']+)'\)\}<\/div>/g,
    (match, i18nKey) => {
      if (match.includes('SafeError')) return match;
      modified = true;
      return `<SafeError title={t('${i18nKey}')} message={t('${i18nKey}')} correlationId={session?.tenantId + '-' + session?.principalId} />`;
    }
  );

  // Pattern: <p className="error">{error}</p>
  content = content.replace(
    /<p className="error">\{error\}<\/p>/g,
    (match) => {
      if (match.includes('SafeError')) return match;
      modified = true;
      return `<SafeError message={error} correlationId={session?.tenantId + '-' + session?.principalId} />`;
    }
  );

  if (modified) {
    writeFileSync(filePath, content, 'utf-8');
    return true;
  }
  return false;
}

function main() {
  try {
    const files = readdirSync(PAGES_DIR).filter(f => f.endsWith('Page.tsx') && !f.includes('.test.'));
    let fixedCount = 0;
    
    for (const file of files) {
      if (fixFile(join(PAGES_DIR, file))) {
        console.log(`Fixed: ${file}`);
        fixedCount++;
      }
    }
    
    console.log(`\nFixed ${fixedCount} files`);
    process.exit(0);
  } catch (error) {
    console.error('Error running fix:', error.message);
    process.exit(1);
  }
}

main();
