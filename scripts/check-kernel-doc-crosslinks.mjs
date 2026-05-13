#!/usr/bin/env node

/**
 * Kernel Doc Crosslinks Check
 * 
 * Validates that kernel docs have proper crosslinks:
 * - Links between docs are valid
 * - No broken internal links
 * - External links are optional but should be valid
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const kernelDocsPath = path.join(repoRoot, 'docs/kernel');

const errors = [];

function extractLinks(content) {
  const linkRegex = /\[([^\]]+)\]\(([^)]+)\)/g;
  const links = [];
  let match;
  while ((match = linkRegex.exec(content)) !== null) {
    links.push({
      text: match[1],
      url: match[2],
    });
  }
  return links;
}

function isInternalLink(url) {
  return url.startsWith('./') || url.startsWith('../') || url.startsWith('/');
}

function resolveInternalLink(basePath, url) {
  if (url.startsWith('./')) {
    return path.resolve(path.dirname(basePath), url.slice(2));
  } else if (url.startsWith('../')) {
    return path.resolve(path.dirname(basePath), url.slice(3));
  } else if (url.startsWith('/')) {
    return path.resolve(repoRoot, url.slice(1));
  }
  return path.resolve(path.dirname(basePath), url);
}

function checkDocLinks(docPath) {
  if (!existsSync(docPath)) {
    return;
  }
  
  const content = readFileSync(docPath, 'utf8');
  const links = extractLinks(content);
  
  for (const link of links) {
    if (isInternalLink(link.url)) {
      const resolvedPath = resolveInternalLink(docPath, link.url);
      
      // Handle markdown links without .md extension
      const possiblePaths = [
        resolvedPath,
        resolvedPath + '.md',
        resolvedPath.replace(/\.md$/, ''),
      ];
      
      if (!possiblePaths.some(p => existsSync(p))) {
        errors.push(`Broken internal link in ${path.basename(docPath)}: [${link.text}](${link.url}) -> ${resolvedPath}`);
      }
    }
  }
}

function checkAllDocLinks() {
  const files = [
    'README.md',
    '00-VISION.md',
    '01-ARCHITECTURE.md',
    '02-PRODUCT_LIFECYCLE.md',
    '03-TOOLCHAIN_ADAPTERS.md',
    '04-ARTIFACTS.md',
    '05-DEPLOYMENT.md',
    '06-PLUGIN_PLATFORM.md',
    '07-CONFORMANCE.md',
    '08-SECURITY_PRIVACY_OBSERVABILITY.md',
    '09-PRODUCT_DEVELOPER_GUIDE.md',
    '10-POWER_USER_EXTENSION_GUIDE.md',
    '11-MIGRATION_GUIDE.md',
  ];
  
  for (const file of files) {
    const docPath = path.join(kernelDocsPath, file);
    checkDocLinks(docPath);
  }
}

function main() {
  console.log('=== Kernel Doc Crosslinks Check ===\n');
  
  checkAllDocLinks();
  
  if (errors.length > 0) {
    console.error('❌ Doc crosslinks check failed:\n');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }
  
  console.log('✅ Doc crosslinks check passed');
  console.log('  - All internal links are valid');
}

main();
