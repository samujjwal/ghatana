#!/usr/bin/env node
// Authoritative Source: config/documentation-truth-scope.json

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const DOC_SCOPE_FILE = path.join(repoRoot, 'config', 'documentation-truth-scope.json');

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

function loadDocumentationScope() {
  try {
    const scope = JSON.parse(readFileSync(DOC_SCOPE_FILE, 'utf8'));
    return {
      includeFiles: new Set((scope.includeFiles ?? []).map((file) => normalize(file))),
      archivedPathSegments: (scope.archivedPathSegments ?? []).map(normalize),
    };
  } catch {
    return null;
  }
}

function shouldScan(relativePath) {
  const normalized = normalize(relativePath);
  return normalized === 'docs/architecture/DOMAIN_WORKSTREAM_MAP.md';
}

function normalize(filePath) {
  return filePath.replace(/\\/g, '/');
}

function listFiles() {
  const documentationScope = loadDocumentationScope();
  try {
    if (documentationScope) {
      return [...documentationScope.includeFiles]
        .filter((file) => existsSync(path.join(repoRoot, file)))
        .filter((file) => !documentationScope.archivedPathSegments.some((segment) => file.startsWith(segment)))
        .filter((file, index, files) => files.indexOf(file) === index);
    }

    return execFileSync(
      'rg',
      ['--files', 'docs', 'config', '-g', '*.md', '-g', '*.markdown', '-g', '*.json'],
      { cwd: repoRoot, encoding: 'utf8' },
    )
      .split(/\r?\n/)
      .filter(Boolean)
      .map(normalize)
      .filter((file) => !file.startsWith('node_modules/'))
      .filter((file) => shouldScan(file));
  } catch {
    const files = [];
    for (const scopeFile of [...(documentationScope?.includeFiles ?? [])]) {
      const absolutePath = path.join(repoRoot, scopeFile);
      if (!existsSync(absolutePath)) {
        continue;
      }
      files.push(normalize(scopeFile));
    }
    return files.filter((file) => shouldScan(file));
  }
}

export function findCurrentStateClaimViolations(files, options = {}) {
  const violations = [];
  const domainRegistry = options.domainRegistry ?? loadDomainRegistry();

  for (const file of files) {
    const lines = file.source.split(/\r?\n/);

    for (let index = 0; index < lines.length; index += 1) {
      const line = lines[index];
      if (isClassificationLabel(line)) {
        continue;
      }
      if (!CLAIM_PATTERN.test(line)) {
        continue;
      }

      const window = lines.slice(Math.max(0, index - 2), Math.min(lines.length, index + 3)).join(' ');
      if (!CLASSIFICATION_PATTERN.test(window)) {
        const registryClassification = lookupRegistryClassification(file.path, domainRegistry);
        const registryHint = registryClassification === 'unknown' ? '' : ` Registry classification for this file: ${registryClassification}.`;
        violations.push(`${file.path}:${index + 1}: current-state claim '${line.trim()}' is unclassified. Add one of: Existing and executable, Existing but partial, Declared only, Target architecture, Anti-pattern.${registryHint}`);
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

function isClassificationLabel(line) {
  return /^\s*(current-state\s+classification|classification)\s*:/i.test(line);
}

function lookupRegistryClassification(filePath, domainRegistry) {
  const normalizedFilePath = normalize(filePath);

  for (const domain of domainRegistry.domains ?? []) {
    const classification = normalizeClassification(domain.classification);
    const candidateLocations = [
      ...(domain.primaryLocations ?? []),
      ...(domain.secondaryLocations ?? []),
      ...(domain.currentStateEvidence ?? []),
      ...(domain.sourceOfTruth ? [domain.sourceOfTruth] : []),
    ];

    if (candidateLocations.some((location) => normalize(location) === normalizedFilePath)) {
      return classification;
    }
  }

  return 'unknown';
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

export { listFiles, shouldScan };

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