#!/usr/bin/env node
/**
 * Documentation Index Validation
 *
 * Validates that canonical docs are properly linked in DOCUMENTATION_INDEX.md,
 * excludes archive docs from source of truth checks, and prevents scattered docs.
 *
 * Exit: 0 = clean, 1 = violations found
 *
 * Usage: node scripts/check-documentation-index.mjs
 *
 * @doc.type   tooling
 * @doc.purpose Validate documentation index completeness and prevent scattered docs
 * @doc.layer  infrastructure
 */

import { readFileSync, readdirSync, statSync, existsSync } from 'fs';
import { join, resolve, dirname, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');
const YAPPC_DOCS_DIR = join(REPO_ROOT, 'products/yappc/docs');
const DOCUMENTATION_INDEX = join(YAPPC_DOCS_DIR, 'DOCUMENTATION_INDEX.md');
const ARCHIVE_DIR = join(YAPPC_DOCS_DIR, 'archive');

const violations = [];

// ---------------------------------------------------------------------------
// Helper functions
// ---------------------------------------------------------------------------

/**
 * Extract all markdown file links from DOCUMENTATION_INDEX.md
 * @returns {Set<string>}
 */
function extractLinkedDocs() {
  if (!existsSync(DOCUMENTATION_INDEX)) {
    violations.push('DOCUMENTATION_INDEX.md not found');
    return new Set();
  }

  const content = readFileSync(DOCUMENTATION_INDEX, 'utf-8');
  const linkedDocs = new Set();

  // Match markdown links: [text](path.md) or [text](path/)
  const linkRegex = /\[([^\]]+)\]\(([^)]+\.md(?:#[^)]*)?)\)|\[([^\]]+)\]\(([^)]+\/)\)/g;
  let match;

  while ((match = linkRegex.exec(content)) !== null) {
    // Extract the path (group 2 for .md, group 4 for /)
    const path = match[2] || match[4];
    if (path) {
      // Resolve relative paths from DOCUMENTATION_INDEX.md
      const resolvedPath = path.startsWith('..') 
        ? join(YAPPC_DOCS_DIR, path)
        : join(YAPPC_DOCS_DIR, path);
      linkedDocs.add(resolvedPath.replace(/\\/g, '/'));
    }
  }

  return linkedDocs;
}

/**
 * Get all markdown files in a directory recursively
 * @param {string} dir
 * @param {string[]} excludeDirs
 * @returns {string[]}
 */
function getMarkdownFiles(dir, excludeDirs = []) {
  if (!existsSync(dir)) return [];
  
  const files = [];
  
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = join(dir, entry.name);
    const relPath = relative(REPO_ROOT, fullPath).replace(/\\/g, '/');

    // Skip excluded directories
    if (excludeDirs.some(excluded => relPath.includes(excluded))) {
      continue;
    }

    if (entry.isDirectory()) {
      files.push(...getMarkdownFiles(fullPath, excludeDirs));
    } else if (entry.name.endsWith('.md') && entry.name !== 'DOCUMENTATION_INDEX.md') {
      files.push(fullPath.replace(/\\/g, '/'));
    }
  }

  return files;
}

// ---------------------------------------------------------------------------
// Validation checks
// ---------------------------------------------------------------------------

console.log('🔍 Validating documentation index...\n');

// Check 1: DOCUMENTATION_INDEX.md exists
if (!existsSync(DOCUMENTATION_INDEX)) {
  violations.push('DOCUMENTATION_INDEX.md not found in products/yappc/docs/');
}

// Check 2: Archive directory exists and is not used as source of truth
if (!existsSync(ARCHIVE_DIR)) {
  violations.push('archive/ directory not found in products/yappc/docs/');
} else {
  console.log('✓ archive/ directory exists');
}

// Check 3: All canonical docs are linked in DOCUMENTATION_INDEX.md
const linkedDocs = extractLinkedDocs();
const canonicalDirs = [
  join(YAPPC_DOCS_DIR, 'architecture'),
  join(YAPPC_DOCS_DIR, 'guidelines'),
  join(YAPPC_DOCS_DIR, 'operations'),
  join(YAPPC_DOCS_DIR, 'usage'),
  join(YAPPC_DOCS_DIR, 'adr'),
  join(YAPPC_DOCS_DIR, 'implementation-plans'),
];

const excludeDirs = ['archive', 'generated', 'node_modules', '.git'];
let unlinkedDocs = [];

for (const dir of canonicalDirs) {
  if (existsSync(dir)) {
    const docs = getMarkdownFiles(dir, excludeDirs);
    for (const doc of docs) {
      // Check if doc is linked (either exact match or directory match)
      const isLinked = Array.from(linkedDocs).some(linked => {
        // Exact match
        if (linked === doc) return true;
        // Directory match (if linked is a directory and doc is inside it)
        if (linked.endsWith('/') && doc.startsWith(linked)) return true;
        return false;
      });

      if (!isLinked) {
        unlinkedDocs.push(relative(REPO_ROOT, doc).replace(/\\/g, '/'));
      }
    }
  }
}

if (unlinkedDocs.length > 0) {
  violations.push(`Found ${unlinkedDocs.length} canonical docs not linked in DOCUMENTATION_INDEX.md:`);
  unlinkedDocs.forEach(doc => violations.push(`  - ${doc}`));
} else {
  console.log('✓ All canonical docs are linked in DOCUMENTATION_INDEX.md');
}

// Check 4: Archive docs are not referenced as source of truth
const archiveDocs = getMarkdownFiles(ARCHIVE_DIR, []);
const indexContent = existsSync(DOCUMENTATION_INDEX) ? readFileSync(DOCUMENTATION_INDEX, 'utf-8') : '';
let archiveReferences = [];

for (const archiveDoc of archiveDocs) {
  const docName = relative(ARCHIVE_DIR, archiveDoc).replace(/\\/g, '/');
  // Check if archive doc is referenced as a primary source (not in archive section)
  if (indexContent.includes(docName) && !indexContent.includes('archive')) {
    // More precise check: look for references outside the "Scattered Docs" or archive sections
    const lines = indexContent.split('\n');
    let inArchiveSection = false;
    
    for (const line of lines) {
      if (line.includes('archive') || line.includes('Archive') || line.includes('Scattered')) {
        inArchiveSection = true;
      } else if (line.startsWith('##') || line.startsWith('---')) {
        inArchiveSection = false;
      }
      
      if (!inArchiveSection && line.includes(docName)) {
        archiveReferences.push(docName);
        break;
      }
    }
  }
}

if (archiveReferences.length > 0) {
  violations.push(`Found ${archiveReferences.length} archive docs referenced as source of truth:`);
  archiveReferences.forEach(doc => violations.push(`  - ${doc}`));
} else {
  console.log('✓ Archive docs are not referenced as source of truth');
}

// Check 5: No new scattered docs added outside canonical locations
// The DOCUMENTATION_INDEX.md has a "Scattered Docs — To Be Consolidated" section
// We should check if there are docs outside the listed scattered locations
const scatteredDocPattern = /`products\/yappc\/([^`]+)`/g;
const scatteredList = [];
let match;

while ((match = scatteredDocPattern.exec(indexContent)) !== null) {
  scatteredList.push(match[1]);
}

// Check for .md files in products/yappc/ that are not in docs/, frontend/web/docs/, or the scattered list
const yappcDir = join(REPO_ROOT, 'products/yappc');
const allowedLocations = [
  'docs/',
  'frontend/web/docs/',
  'frontend/web/src/test-utils/',
  'core/',
  'frontend/',
];

let newScatteredDocs = [];

function checkDir(dir, baseRel) {
  if (!existsSync(dir)) return;
  
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = join(dir, entry.name);
    const relPath = relative(REPO_ROOT, fullPath).replace(/\\/g, '/');

    // Skip allowed locations
    if (allowedLocations.some(loc => relPath.includes(loc))) {
      continue;
    }

    if (entry.isDirectory()) {
      checkDir(fullPath, baseRel);
    } else if (entry.name.endsWith('.md') && entry.name !== 'README.md') {
      // Check if this is in the scattered list
      const isInScatteredList = scatteredList.some(scattered => relPath.includes(scattered));
      if (!isInScatteredList) {
        newScatteredDocs.push(relPath);
      }
    }
  }
}

checkDir(yappcDir, 'products/yappc');

if (newScatteredDocs.length > 0) {
  violations.push(`Found ${newScatteredDocs.length} new scattered docs not in DOCUMENTATION_INDEX.md:`);
  newScatteredDocs.forEach(doc => violations.push(`  - ${doc}`));
} else {
  console.log('✓ No new scattered docs found');
}

// ---------------------------------------------------------------------------
// Report results
// ---------------------------------------------------------------------------

if (violations.length === 0) {
  console.log('\n✅ Documentation index validation passed.');
  process.exit(0);
}

console.log('\n❌ Documentation index validation failed:\n');
violations.forEach(v => console.log(v));
console.log(`\nTotal violations: ${violations.length}`);
process.exit(1);
