#!/usr/bin/env node

/**
 * Evidence centralization for Data-Cloud releases.
 *
 * This script centralizes all release evidence including:
 * - Test results
 * - Security scan reports
 * - Performance metrics
 * - Deployment logs
 * - Approval records
 *
 * Usage: node scripts/centralize-release-evidence.mjs <release-tag>
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..');

/**
 * Evidence types to collect.
 */
const EVIDENCE_TYPES = [
  {
    name: 'test-results',
    source: 'build/reports/tests/test',
    target: 'evidence/test-results.json',
  },
  {
    name: 'security-scan',
    source: 'build/reports/security/scan.json',
    target: 'evidence/security-scan.json',
  },
  {
    name: 'performance-metrics',
    source: 'build/reports/performance/metrics.json',
    target: 'evidence/performance-metrics.json',
  },
  {
    name: 'deployment-logs',
    source: 'build/reports/deployment/logs.json',
    target: 'evidence/deployment-logs.json',
  },
  {
    name: 'approval-records',
    source: 'build/reports/approval/records.json',
    target: 'evidence/approval-records.json',
  },
];

/**
 * Collects evidence for a release.
 */
function collectEvidence(releaseTag) {
  console.log(`Collecting evidence for release: ${releaseTag}\n`);

  const evidenceDir = join(REPO_ROOT, 'releases', releaseTag, 'evidence');
  
  // Create evidence directory
  if (!existsSync(evidenceDir)) {
    mkdirSync(evidenceDir, { recursive: true });
  }

  const evidenceManifest = {
    releaseTag,
    collectedAt: new Date().toISOString(),
    evidence: [],
  };

  for (const evidenceType of EVIDENCE_TYPES) {
    const sourcePath = join(REPO_ROOT, evidenceType.source);
    const targetPath = join(REPO_ROOT, 'releases', releaseTag, evidenceType.target);

    if (existsSync(sourcePath)) {
      try {
        const content = readFileSync(sourcePath, 'utf-8');
        writeFileSync(targetPath, content, 'utf-8');

        evidenceManifest.evidence.push({
          type: evidenceType.name,
          path: evidenceType.target,
          collected: true,
        });

        console.log(`✓ Collected ${evidenceType.name}`);
      } catch (error) {
        console.error(`✗ Failed to collect ${evidenceType.name}: ${error.message}`);

        evidenceManifest.evidence.push({
          type: evidenceType.name,
          path: evidenceType.target,
          collected: false,
          error: error.message,
        });
      }
    } else {
      console.log(`○ Skipped ${evidenceType.name} (not found)`);

      evidenceManifest.evidence.push({
        type: evidenceType.name,
        path: evidenceType.target,
        collected: false,
        reason: 'not found',
      });
    }
  }

  // Write evidence manifest
  const manifestPath = join(REPO_ROOT, 'releases', releaseTag, 'evidence-manifest.json');
  writeFileSync(manifestPath, JSON.stringify(evidenceManifest, null, 2), 'utf-8');

  console.log();
  console.log(`Evidence manifest written to: ${manifestPath}`);
  console.log(`Evidence centralization COMPLETED for: ${releaseTag}`);
}

/**
 * Main execution.
 */
const releaseTag = process.argv[2];

if (!releaseTag) {
  console.error('Usage: node scripts/centralize-release-evidence.mjs <release-tag>');
  process.exit(1);
}

collectEvidence(releaseTag);
