#!/usr/bin/env node

/**
 * Wave 1: Store Failure-Injection Reports as Release Artifacts
 *
 * Aggregates all failure-injection evidence and stores it as release artifacts:
 * - Atomic workflow failure-injection reports
 * - Runtime dependency failure-injection reports
 * - AI governance behavioral proof reports
 * - i18n conformance reports
 * - a11y behavioral proof reports
 * - OpenAPI release quality reports
 *
 * This ensures that all failure-injection evidence is captured and stored
 * as part of the release artifacts for production-grade traceability.
 *
 * Usage: node scripts/store-failure-injection-reports.mjs [--release <version>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const RELEASE_VERSION = process.argv.find(arg => arg.startsWith('--release='))?.split('=')[1] || 'latest';

const evidenceDir = path.join(repoRoot, '.kernel', 'evidence');
const releaseArtifactsDir = path.join(repoRoot, '.kernel', 'release-artifacts', RELEASE_VERSION);

function logInfo(message) {
  console.log(`ℹ️  ${message}`);
}

function logSuccess(message) {
  console.log(`✓ ${message}`);
}

/**
 * Collect all evidence from the evidence directory
 */
function collectEvidence() {
  const evidence = {};
  
  if (!existsSync(evidenceDir)) {
    logInfo('Evidence directory does not exist, creating it');
    mkdirSync(evidenceDir, { recursive: true });
    return evidence;
  }

  const evidenceTypes = [
    'atomic-workflow-failure-injection',
    'runtime-dependency-failure-injection',
    'ai-governance-behavioral-proof',
    'i18n-conformance',
    'a11y-behavioral-proof',
    'openapi-release-quality'
  ];

  for (const evidenceType of evidenceTypes) {
    const typeDir = path.join(evidenceDir, evidenceType);
    
    if (!existsSync(typeDir)) {
      logInfo(`Evidence type ${evidenceType} not found`);
      continue;
    }

    const files = readdirSync(typeDir);
    const evidenceFiles = files.filter(f => f.endsWith('.json'));
    
    evidence[evidenceType] = evidenceFiles.map(file => {
      const filePath = path.join(typeDir, file);
      const content = readFileSync(filePath, 'utf-8');
      return JSON.parse(content);
    });

    logSuccess(`Collected ${evidenceFiles.length} ${evidenceType} reports`);
  }

  return evidence;
}

/**
 * Generate aggregated release artifact
 */
function generateReleaseArtifact(evidence) {
  const timestamp = new Date().toISOString();
  
  const artifact = {
    version: RELEASE_VERSION,
    timestamp,
    evidence: {
      atomicWorkflow: evidence['atomic-workflow-failure-injection'] || [],
      runtimeDependency: evidence['runtime-dependency-failure-injection'] || [],
      aiGovernance: evidence['ai-governance-behavioral-proof'] || [],
      i18nConformance: evidence['i18n-conformance'] || [],
      a11yBehavioral: evidence['a11y-behavioral-proof'] || [],
      openapiQuality: evidence['openapi-release-quality'] || []
    },
    summary: {
      totalReports: Object.values(evidence).flat().length,
      totalViolations: Object.values(evidence).flat().reduce((sum, report) => sum + (report.summary?.totalViolations || 0), 0),
      totalWarnings: Object.values(evidence).flat().reduce((sum, report) => sum + (report.summary?.totalWarnings || 0), 0),
      totalEvidence: Object.values(evidence).flat().reduce((sum, report) => sum + (report.summary?.totalEvidence || 0), 0)
    }
  };

  return artifact;
}

/**
 * Store release artifact
 */
function storeReleaseArtifact(artifact) {
  if (!existsSync(releaseArtifactsDir)) {
    mkdirSync(releaseArtifactsDir, { recursive: true });
  }

  const artifactPath = path.join(releaseArtifactsDir, `failure-injection-report-${RELEASE_VERSION}.json`);
  writeFileSync(artifactPath, JSON.stringify(artifact, null, 2));
  
  logSuccess(`Release artifact stored: ${artifactPath}`);
  
  return artifactPath;
}

/**
 * Generate human-readable summary
 */
function generateSummary(artifact) {
  const summaryPath = path.join(releaseArtifactsDir, `failure-injection-summary-${RELEASE_VERSION}.md`);
  
  let summary = `# Failure-Injection Report - ${RELEASE_VERSION}\n\n`;
  summary += `Generated: ${artifact.timestamp}\n\n`;
  summary += `## Summary\n\n`;
  summary += `- **Total Reports**: ${artifact.summary.totalReports}\n`;
  summary += `- **Total Violations**: ${artifact.summary.totalViolations}\n`;
  summary += `- **Total Warnings**: ${artifact.summary.totalWarnings}\n`;
  summary += `- **Total Evidence Items**: ${artifact.summary.totalEvidence}\n\n`;
  
  summary += `## Evidence Categories\n\n`;
  
  for (const [category, reports] of Object.entries(artifact.evidence)) {
    summary += `### ${category}\n`;
    summary += `- Reports: ${reports.length}\n`;
    
    if (reports.length > 0) {
      const violations = reports.reduce((sum, r) => sum + (r.summary?.totalViolations || 0), 0);
      const warnings = reports.reduce((sum, r) => sum + (r.summary?.totalWarnings || 0), 0);
      const evidence = reports.reduce((sum, r) => sum + (r.summary?.totalEvidence || 0), 0);
      
      summary += `- Violations: ${violations}\n`;
      summary += `- Warnings: ${warnings}\n`;
      summary += `- Evidence: ${evidence}\n`;
    }
    
    summary += '\n';
  }
  
  if (artifact.summary.totalViolations > 0) {
    summary += `## ⚠️ Critical Issues\n\n`;
    summary += `This release has ${artifact.summary.totalViolations} critical violations that must be resolved before production deployment.\n\n`;
  }
  
  if (artifact.summary.totalWarnings > 0) {
    summary += `## ℹ️ Warnings\n\n`;
    summary += `This release has ${artifact.summary.totalWarnings} warnings that should be reviewed.\n\n`;
  }
  
  if (artifact.summary.totalViolations === 0 && artifact.summary.totalWarnings === 0) {
    summary += `## ✅ Release Ready\n\n`;
    summary += `All failure-injection checks passed. This release is ready for production deployment.\n\n`;
  }
  
  writeFileSync(summaryPath, summary);
  logSuccess(`Summary generated: ${summaryPath}`);
  
  return summaryPath;
}

/**
 * Main execution
 */
function main() {
  console.log(`Storing failure-injection reports for release ${RELEASE_VERSION}...\n`);
  
  // Collect evidence
  const evidence = collectEvidence();
  
  // Generate release artifact
  const artifact = generateReleaseArtifact(evidence);
  
  // Store release artifact
  storeReleaseArtifact(artifact);
  
  // Generate summary
  generateSummary(artifact);
  
  console.log('\n--- Summary ---');
  console.log(`Total Reports: ${artifact.summary.totalReports}`);
  console.log(`Total Violations: ${artifact.summary.totalViolations}`);
  console.log(`Total Warnings: ${artifact.summary.totalWarnings}`);
  console.log(`Total Evidence: ${artifact.summary.totalEvidence}`);
  
  if (artifact.summary.totalViolations > 0) {
    console.log('\n❌ Release has critical violations - not ready for production');
    process.exit(1);
  }
  
  console.log('\n✅ Failure-injection reports stored successfully');
}

main();
