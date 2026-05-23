#!/usr/bin/env node

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const productReleaseReadinessPath = path.join(evidenceDir, 'product-release-readiness.json');

function readJsonIfExists(filePath) {
  if (!existsSync(filePath)) {
    return null;
  }
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function generateReleaseReadinessRecord(productId, productVersion, releaseTarget, scorecard) {
  return {
    product_id: productId,
    product_version: productVersion,
    release_target: releaseTarget,
    release_verdict: scorecard.releaseVerdict,
    average_score: scorecard.averageScore,
    release_target_score: scorecard.releaseTargetScore,
    generated_at: scorecard.generatedAt,
    evidence: {
      dimensions: scorecard.dimensions,
      below_target_dimensions: scorecard.belowTargetDimensions,
      evidence_paths: scorecard.evidencePaths,
    },
    blocking_gaps: scorecard.blockingGaps,
    below_target_dimensions: scorecard.belowTargetDimensions,
    tenant_id: 'default', // TODO: Make this configurable
  };
}

function producePhrReleaseReadiness() {
  const phrScorecardPath = path.join(evidenceDir, 'product-release-readiness.phr.json');
  const phrScorecard = readJsonIfExists(phrScorecardPath);
  
  if (!phrScorecard) {
    console.warn('PHR release readiness scorecard not found, skipping');
    return null;
  }

  const record = generateReleaseReadinessRecord(
    'phr',
    '1.0.0', // TODO: Read from package.json or build.gradle
    'production',
    phrScorecard
  );

  return {
    productId: 'phr',
    record,
    sourcePath: phrScorecardPath,
  };
}

function produceDmosReleaseReadiness() {
  const dmosScorecardPath = path.join(evidenceDir, 'product-release-readiness.digital-marketing.json');
  const dmosScorecard = readJsonIfExists(dmosScorecardPath);
  
  if (!dmosScorecard) {
    console.warn('Digital Marketing release readiness scorecard not found, skipping');
    return null;
  }

  const record = generateReleaseReadinessRecord(
    'digital-marketing',
    '1.0.0', // TODO: Read from package.json or build.gradle
    'production',
    dmosScorecard
  );

  return {
    productId: 'digital-marketing',
    record,
    sourcePath: dmosScorecardPath,
  };
}

function main() {
  console.log('Producing release readiness evidence for PHR and DMOS...\n');

  const phrResult = producePhrReleaseReadiness();
  const dmosResult = produceDmosReleaseReadiness();

  const results = [phrResult, dmosResult].filter(Boolean);

  if (results.length === 0) {
    console.log('No release readiness evidence to produce');
    process.exit(0);
  }

  console.log(`Producing release readiness evidence for ${results.length} products:\n`);

  for (const result of results) {
    console.log(`📦 ${result.productId}`);
    console.log(`  Release verdict: ${result.record.release_verdict}`);
    console.log(`  Average score: ${result.record.average_score}`);
    console.log(`  Blocking gaps: ${result.record.blocking_gaps.length}`);
    console.log(`  Source: ${result.sourcePath}`);
    console.log('');
  }

  // Write combined evidence file
  const combinedEvidence = {
    generatedAt: new Date().toISOString(),
    products: results.map(r => ({
      productId: r.productId,
      record: r.record,
    })),
  };

  const outputPath = path.join(evidenceDir, 'release-readiness-production-evidence.json');
  writeFileSync(outputPath, JSON.stringify(combinedEvidence, null, 2), 'utf8');
  
  console.log(`✓ Release readiness evidence written to: ${path.relative(repoRoot, outputPath)}`);

  // Check for any failures
  const failures = results.filter(r => r.record.release_verdict === 'fail');
  if (failures.length > 0) {
    console.error(`\n❌ ${failures.length} product(s) failed release readiness check`);
    for (const failure of failures) {
      console.error(`  - ${failure.productId}: ${failure.record.blocking_gaps.length} blocking gaps`);
    }
    process.exit(1);
  }

  console.log('\n✓ All products passed release readiness check');
  process.exit(0);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
