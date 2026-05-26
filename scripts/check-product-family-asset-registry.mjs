#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const schemaPath = path.join(root, 'config/product-family-asset-registry.schema.json');
const registryPath = path.join(root, 'config/product-family-asset-registry.json');
const releaseEvidenceByProduct = new Map([
  ['phr', path.join(root, '.kernel/evidence/phr/reusable-assets-registration.json')],
  [
    'digital-marketing',
    path.join(root, '.kernel/evidence/digital-marketing/reusable-assets-registration.json'),
  ],
]);

function fail(message) {
  console.error(message);
  process.exit(1);
}

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function writeJson(filePath, value) {
  writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`);
}

function currentGitSha() {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf-8' }).trim();
  } catch {
    return null;
  }
}

function currentTargetCommit() {
  return process.env.TARGET_COMMIT_SHA ?? process.env.AUDIT_TARGET_COMMIT ?? currentGitSha();
}

const REQUIRED_ASSET_FIELDS = [
  'id',
  'name',
  'type',
  'sourceProduct',
  'status',
  'maturity',
  'reuseMode',
  'targetPromotion',
  'owner',
  'ownerApproval',
  'freshness',
  'paths',
  'tests',
  'dependencies',
  'requiredFoundations',
  'constraints',
  'compatibility',
  'productUsage',
  'evidenceRefs'
];

const VALID_TYPES = new Set(['ui-component', 'service', 'workflow', 'policy', 'gate-pack', 'pattern', 'connector']);
const VALID_SOURCE_PRODUCTS = new Set(['phr', 'digital-marketing']);
const VALID_STATUSES = new Set(['candidate', 'hardened', 'production', 'shared']);
const VALID_MATURITIES = new Set(['draft', 'validated', 'release-ready']);
const VALID_REUSE_MODES = new Set(['reference', 'template', 'shared-package', 'kernel-plugin', 'schema-pack']);
const VALID_TARGET_PROMOTIONS = new Set(['shared-package', 'kernel-plugin', 'template', 'schema-pack', 'none']);
const VALID_USAGE_STATES = new Set(['production-used', 'recommended', 'planned']);

function assertArray(asset, field, { min = 1 } = {}) {
  if (!Array.isArray(asset[field]) || asset[field].length < min) {
    fail(`Asset ${asset.id} must include ${field} with at least ${min} entr${min === 1 ? 'y' : 'ies'}`);
  }
}

function assertExistingRefs(asset, field) {
  for (const ref of asset[field]) {
    if (typeof ref !== 'string' || ref.length < 3) {
      fail(`Asset ${asset.id} has invalid ${field} ref: ${ref ?? '<missing>'}`);
    }
    if (ref.startsWith('platform:')) {
      continue;
    }
    if (!existsSync(path.join(root, ref))) {
      fail(`Asset ${asset.id} ${field} ref does not exist: ${ref}`);
    }
  }
}

function assertApproval(asset) {
  const approval = asset.ownerApproval;
  if (!approval || approval.approvalStatus !== 'approved' || !approval.approvedBy || !approval.approvedAt) {
    fail(`Asset ${asset.id} must include approved ownerApproval metadata`);
  }
}

function assertFreshness(asset) {
  const freshness = asset.freshness;
  if (
    !freshness ||
    typeof freshness.sourceCommitSha !== 'string' ||
    typeof freshness.targetCommitSha !== 'string' ||
    typeof freshness.validatedAt !== 'string'
  ) {
    fail(`Asset ${asset.id} must include source/target commit freshness metadata`);
  }
  if (freshness.sourceCommitSha !== freshness.targetCommitSha) {
    fail(`Asset ${asset.id} sourceCommitSha must match targetCommitSha`);
  }
}

function assertCompatibility(asset) {
  const compatibility = asset.compatibility;
  if (
    !compatibility ||
    !Array.isArray(compatibility.productTypes) ||
    compatibility.productTypes.length === 0 ||
    typeof compatibility.runtime !== 'string' ||
    !Array.isArray(compatibility.constraints)
  ) {
    fail(`Asset ${asset.id} must include compatibility metadata`);
  }
}

function assertProductUsage(asset) {
  assertArray(asset, 'productUsage');
  const hasSourceUsage = asset.productUsage.some(
    (usage) => usage.productId === asset.sourceProduct && usage.state === 'production-used'
  );
  if (!hasSourceUsage) {
    fail(`Asset ${asset.id} must record production-used usage for ${asset.sourceProduct}`);
  }
  for (const usage of asset.productUsage) {
    if (!usage?.productId || !usage?.usage || !VALID_USAGE_STATES.has(usage.state)) {
      fail(`Asset ${asset.id} has invalid productUsage record`);
    }
  }
}

function stampReleaseEvidence(productId, targetCommit, now) {
  const evidencePath = releaseEvidenceByProduct.get(productId);
  if (!evidencePath || !existsSync(evidencePath)) {
    return;
  }

  const evidence = readJson(evidencePath);
  evidence.generatedAt = now;
  evidence.commitSha = targetCommit;
  evidence.sourceCommitSha = targetCommit;
  evidence.targetCommitSha = targetCommit;
  evidence.evidenceRun = {
    ...(evidence.evidenceRun ?? {}),
    commit: targetCommit,
    sourceCommitSha: targetCommit,
    targetCommitSha: targetCommit,
    generatedAt: now,
    source: 'product-family-asset-registry',
  };

  if (evidence.promotionPolicy) {
    evidence.promotionPolicy.promotionLockSha = targetCommit;
    evidence.promotionPolicy.lockedAt = now;
  }

  for (const asset of evidence.assets ?? []) {
    if (asset.promotion_lock) {
      asset.promotion_lock.lockedAtSha = targetCommit;
      asset.promotion_lock.lockedAt = now;
    }
  }

  writeJson(evidencePath, evidence);
}

function main() {
  const schema = readJson(schemaPath);
  const registry = readJson(registryPath);
  const targetCommit = currentTargetCommit();
  const now = new Date().toISOString();

  if (!targetCommit) {
    fail('Unable to resolve target commit for product family asset evidence');
  }

  if (schema?.type !== 'object') {
    fail('Asset registry schema must declare a root object type');
  }
  if (!Array.isArray(registry?.assets)) {
    fail('Asset registry must contain assets array');
  }

  const ids = new Set();
  for (const asset of registry.assets) {
    for (const field of REQUIRED_ASSET_FIELDS) {
      if (!(field in asset)) {
        fail(`Asset ${asset?.id ?? '<missing>'} is missing required field: ${field}`);
      }
    }
    if (!asset?.id || !/^[a-z0-9-]+$/.test(asset.id)) {
      fail(`Invalid asset id: ${asset?.id ?? '<missing>'}`);
    }
    if (ids.has(asset.id)) {
      fail(`Duplicate asset id: ${asset.id}`);
    }
    ids.add(asset.id);

    if (!VALID_TYPES.has(asset.type)) {
      fail(`Asset ${asset.id} has invalid type: ${asset.type}`);
    }
    if (!VALID_SOURCE_PRODUCTS.has(asset.sourceProduct)) {
      fail(`Asset ${asset.id} has invalid sourceProduct: ${asset.sourceProduct}`);
    }
    if (!VALID_STATUSES.has(asset.status)) {
      fail(`Asset ${asset.id} has invalid status: ${asset.status}`);
    }
    if (!VALID_MATURITIES.has(asset.maturity)) {
      fail(`Asset ${asset.id} has invalid maturity: ${asset.maturity}`);
    }
    if (!VALID_REUSE_MODES.has(asset.reuseMode)) {
      fail(`Asset ${asset.id} has invalid reuseMode: ${asset.reuseMode}`);
    }
    if (!VALID_TARGET_PROMOTIONS.has(asset.targetPromotion)) {
      fail(`Asset ${asset.id} has invalid targetPromotion: ${asset.targetPromotion}`);
    }
    assertApproval(asset);
    assertFreshness(asset);
    assertArray(asset, 'paths');
    assertArray(asset, 'tests');
    assertArray(asset, 'requiredFoundations');
    assertArray(asset, 'evidenceRefs');
    assertExistingRefs(asset, 'paths');
    assertExistingRefs(asset, 'tests');
    assertExistingRefs(asset, 'evidenceRefs');
    assertCompatibility(asset);
    assertProductUsage(asset);
    if (asset.status === 'production' && asset.maturity !== 'release-ready') {
      fail(`Production asset ${asset.id} must be release-ready`);
    }
  }

  for (const productId of VALID_SOURCE_PRODUCTS) {
    stampReleaseEvidence(productId, targetCommit, now);
  }

  console.log('Product family asset registry validation passed.');
}

main();
