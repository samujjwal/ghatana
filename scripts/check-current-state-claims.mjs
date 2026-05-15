#!/usr/bin/env node
// Authoritative Source: config/documentation-truth-scope.json

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const CLAIM_PATTERN = /\b(fully implemented|production-ready|complete|enabled|executable|supports)\b/i;
const CLASSIFICATION_PATTERN = /\b(Existing and executable|Existing but partial|Declared only|Target architecture|Anti-pattern)\b/i;

// Claim vocabulary mapping to registry classifications
const CLAIM_VOCABULARY = {
  'executable': ['executable', 'fully implemented', 'production-ready', 'complete', 'enabled'],
  'non-executable': ['declared only', 'target architecture', 'anti-pattern'],
  'partial': ['existing but partial', 'partial', 'in progress']
};

// Registry classification mapping
const REGISTRY_CLASSIFICATIONS = {
  'executable': ['executable', 'existing-executable'],
  'non-executable': ['declared-only', 'target-architecture', 'anti-pattern'],
  'partial': ['existing-partial', 'partial']
};

function loadDomainRegistry() {
  try {
    return JSON.parse(readFileSync(path.join(repoRoot, 'config/domain-registry.json'), 'utf8'));
  } catch (error) {
    console.warn('Warning: Failed to load domain registry:', error.message);
    return { domains: [] };
  }
}

function shouldScan(relativePath) {
  const normalized = normalize(relativePath);
  if (/\/(?:adr|archive)\//i.test(normalized)) {
    return false;
  }
  if (/(PLAN|CHECKLIST|TRACKER|IMPLEMENTATION|ADR)-?.*\.md$/i.test(path.basename(normalized))) {
    return false;
  }
  return (
    normalized === 'docs/architecture/DOMAIN_WORKSTREAM_MAP.md' ||
    /(?:CURRENT_STATE|TRUTH)\.md$/i.test(path.basename(normalized)) ||
    normalized.includes('/truth/')
  );
}

function normalize(filePath) {
  return filePath.replace(/\\/g, '/');
}

function listFiles() {
  try {
    return execFileSync(
      'rg',
      ['--files', 'docs', 'config', '.', '-g', '*.md', '-g', '*.markdown', '-g', '*PLAN*.md', '-g', '*ARCHITECTURE*.md'],
      { cwd: repoRoot, encoding: 'utf8' },
    )
      .split(/\r?\n/)
      .filter(Boolean)
      .map(normalize)
      .filter((file) => !file.startsWith('node_modules/'))
      .filter((file) => shouldScan(file));
  } catch {
    const files = [];
    for (const rootSegment of ['docs', 'config']) {
      const absoluteRoot = path.join(repoRoot, rootSegment);
      if (!existsSync(absoluteRoot)) {
        continue;
      }
      walkDirectory(absoluteRoot, files);
    }
    return files;
  }
}

function walkDirectory(directory, files) {
  for (const entry of readdirSync(directory)) {
    const fullPath = path.join(directory, entry);
    const relativePath = normalize(path.relative(repoRoot, fullPath));
    const stats = statSync(fullPath);
    if (stats.isDirectory()) {
      walkDirectory(fullPath, files);
      continue;
    }
    if (/(\.md|\.markdown)$/i.test(entry) && shouldScan(relativePath)) {
      files.push(relativePath);
    }
  }
}

export function findCurrentStateClaimViolations(files, options = {}) {
  const violations = [];
  const domainRegistry = options.domainRegistry ?? loadDomainRegistry();
  
  // Build domain classification lookup
  const domainClassifications = new Map();
  for (const domain of domainRegistry.domains || []) {
    const normalizedClassification = normalizeClassification(domain.classification);
    domainClassifications.set(domain.id, normalizedClassification);
  }

  for (const file of files) {
    const lines = file.source.split(/\r?\n/);

    for (let index = 0; index < lines.length; index += 1) {
      const line = lines[index];
      if (!CLAIM_PATTERN.test(line)) {
        continue;
      }

      const window = lines.slice(Math.max(0, index - 2), Math.min(lines.length, index + 3)).join(' ');
      if (!CLASSIFICATION_PATTERN.test(window)) {
        violations.push(`${file.path}:${index + 1}: current-state claim '${line.trim()}' is unclassified. Add one of: Existing and executable, Existing but partial, Declared only, Target architecture, Anti-pattern.`);
      }

      // Validate executability claims against registry state
      const claimType = classifyClaim(line);
      if (claimType === 'executable') {
        // Check if there's evidence for this executability claim
        const hasEvidence = checkForEvidence(file.source, index, lines);
        if (!hasEvidence) {
          violations.push(`${file.path}:${index + 1}: executability claim '${line.trim()}' lacks evidence. Add evidence refs pointing to tests, CI gates, or implementation.`);
        }
      }
    }
  }

  return violations;
}

function normalizeClassification(classification) {
  if (!classification) return 'unknown';
  const normalized = classification.toLowerCase().replace(/[-_]/g, '');
  if (REGISTRY_CLASSIFICATIONS.executable.some(c => normalized.includes(c.replace(/[-_]/g, '')))) {
    return 'executable';
  }
  if (REGISTRY_CLASSIFICATIONS.partial.some(c => normalized.includes(c.replace(/[-_]/g, '')))) {
    return 'partial';
  }
  if (REGISTRY_CLASSIFICATIONS['non-executable'].some(c => normalized.includes(c.replace(/[-_]/g, '')))) {
    return 'non-executable';
  }
  return 'unknown';
}

function classifyClaim(line) {
  const lowerLine = line.toLowerCase();
  if (CLAIM_VOCABULARY.executable.some(term => lowerLine.includes(term))) {
    return 'executable';
  }
  if (CLAIM_VOCABULARY.partial.some(term => lowerLine.includes(term))) {
    return 'partial';
  }
  if (CLAIM_VOCABULARY['non-executable'].some(term => lowerLine.includes(term))) {
    return 'non-executable';
  }
  return 'unknown';
}

function checkForEvidence(source, lineIndex, lines) {
  // Look for evidence patterns in the surrounding context
  const contextStart = Math.max(0, lineIndex - 5);
  const contextEnd = Math.min(lines.length, lineIndex + 10);
  const context = lines.slice(contextStart, contextEnd).join(' ').toLowerCase();
  
  // Evidence patterns
  const evidencePatterns = [
    'evidence:',
    'see:',
    'test:',
    'ci:',
    'implementation:',
    'ref:',
    'reference:',
    'validated by:',
    'verified by:',
    'checked by:',
    'enforced by:'
  ];
  
  return evidencePatterns.some(pattern => context.includes(pattern));
}

export function checkCurrentStateClaims(options = {}) {
  const files = options.files ?? listFiles().map((file) => ({
    path: file,
    source: readFileSync(path.join(repoRoot, file), 'utf8'),
  }));
  return findCurrentStateClaimViolations(files);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const violations = checkCurrentStateClaims();

  if (violations.length === 0) {
    console.log('OK: current-state claim checks passed.');
    process.exit(0);
  }

  console.error('FAIL: current-state claim checks found violations:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}