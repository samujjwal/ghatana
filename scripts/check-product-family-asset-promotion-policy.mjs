#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const registryPath = path.join(root, 'config/product-family-asset-registry.json');
const policyPath = path.join(root, 'config/product-family-asset-promotion-policy.json');
const evidenceDir = path.join(root, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'product-family-asset-promotion-policy.json');

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function normalizePromotionTarget(value) {
  return String(value ?? '').trim().toLowerCase();
}

function main() {
  if (!existsSync(registryPath)) {
    throw new Error('Missing config/product-family-asset-registry.json');
  }
  if (!existsSync(policyPath)) {
    throw new Error('Missing config/product-family-asset-promotion-policy.json');
  }

  const registry = readJson(registryPath);
  const policy = readJson(policyPath);

  const violations = [];
  const warnings = [];

  const allowedStates = new Set(policy.states ?? []);
  const transitions = policy.allowedTransitions ?? {};
  const requirements = policy.requirements ?? {};

  for (const asset of registry.assets ?? []) {
    const assetId = asset.id ?? '<missing-id>';
    const status = String(asset.status ?? '').trim();

    if (!allowedStates.has(status)) {
      violations.push(`Asset ${assetId} has unsupported status ${JSON.stringify(status)}`);
      continue;
    }

    const requirement = requirements[status] ?? {};
    const minimumEvidenceRefs = Number(requirement.minimumEvidenceRefs ?? 0);
    const evidenceRefs = Array.isArray(asset.evidenceRefs) ? asset.evidenceRefs : [];

    if (evidenceRefs.length < minimumEvidenceRefs) {
      violations.push(
        `Asset ${assetId} status ${status} requires at least ${minimumEvidenceRefs} evidence refs, found ${evidenceRefs.length}`,
      );
    }

    for (const evidenceRef of evidenceRefs) {
      const normalizedRef = String(evidenceRef ?? '').trim();
      if (!normalizedRef) {
        violations.push(`Asset ${assetId} contains blank evidence ref`);
        continue;
      }
      const absoluteRef = path.join(root, normalizedRef);
      if (!existsSync(absoluteRef)) {
        violations.push(`Asset ${assetId} references missing evidence path ${JSON.stringify(normalizedRef)}`);
      }
    }

    const targetPromotion = normalizePromotionTarget(asset.targetPromotion);
    const allowedTargets = new Set((requirement.allowedTargetPromotions ?? []).map((value) => normalizePromotionTarget(value)));
    if (targetPromotion && !allowedTargets.has(targetPromotion)) {
      violations.push(
        `Asset ${assetId} status ${status} has disallowed targetPromotion ${JSON.stringify(asset.targetPromotion)}`,
      );
    }

    const nextStates = Array.isArray(transitions[status]) ? transitions[status] : [];
    if (status !== 'shared' && nextStates.length === 0) {
      warnings.push(`Asset ${assetId} status ${status} has no defined forward transition in policy`);
    }
    if (status === 'shared' && targetPromotion === 'none') {
      warnings.push(`Asset ${assetId} is shared but targetPromotion is none`);
    }
  }

  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(
    evidencePath,
    `${JSON.stringify({
      generatedAt: new Date().toISOString(),
      status: violations.length === 0 ? 'passed' : 'failed',
      violations,
      warnings,
      policyVersion: policy.version ?? 'unknown',
      assetCount: Array.isArray(registry.assets) ? registry.assets.length : 0,
    }, null, 2)}\n`,
    'utf8',
  );

  if (violations.length > 0) {
    console.error('Product-family asset promotion policy check failed:\n');
    for (const violation of violations) {
      console.error(`- ${violation}`);
    }
    console.error(`\nEvidence written to ${path.relative(root, evidencePath)}`);
    process.exit(1);
  }

  if (warnings.length > 0) {
    for (const warning of warnings) {
      console.warn(`Warning: ${warning}`);
    }
  }

  console.log(`Product-family asset promotion policy check passed. Evidence: ${path.relative(root, evidencePath)}`);
}

main();
