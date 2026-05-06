#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('..', import.meta.url).pathname);
const auditedProducts = [
  'products/phr',
  'products/finance',
  'products/digital-marketing',
  'products/flashit',
];
const bridgeFiles = execSync(
  "rg --files products | rg '(KernelAdapterImpl|Bridge.*Impl)\\.java$'",
  { cwd: repoRoot, encoding: 'utf8' }
)
  .trim()
  .split('\n')
  .filter(Boolean);
const auditedBridgeFiles = bridgeFiles.filter(file => auditedProducts.some(product => file.startsWith(`${product}/`)));
const expectedAuditedBridgeTests = new Map([
  [
    'products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java',
    [
      {
        file: 'products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java',
        requiredEvidence: [
          'shouldRejectAuthorizationBeforeStart',
          'shouldDelegateAuthorization',
          'shouldDenyConsentVerificationWhenUnauthorized',
          'shouldRejectUnauthorizedApprovalRequests',
          'shouldRecordAuditWithContextAttributes',
          'shouldRejectUnauthorizedAuditRecording',
          'shouldRejectUnauthorizedRiskEvaluation',
          'shouldDelegateFeatureFlagEvaluation',
          'shouldFailClosedForUnauthorizedFeatureFlagChecks',
          'shouldNotifyUser',
        ],
      },
      {
        file: 'products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java',
        requiredEvidence: [
          'transientFailuresTriggerRetryWithBackoff',
          'retryAttemptsPreserveOriginalContext',
          'retryCountRespectsMaximum',
        ],
      },
    ],
  ],
]);

const violations = [];
const exemptMethodNames = new Set(['start', 'stop', 'started']);

for (const file of auditedBridgeFiles) {
  if (!expectedAuditedBridgeTests.has(file)) {
    violations.push(
      `${file}: audited bridge implementation is missing an explicit compliance-test contract entry`
    );
  }
}

for (const [bridgeFile, testContracts] of expectedAuditedBridgeTests.entries()) {
  if (!auditedBridgeFiles.includes(bridgeFile)) {
    violations.push(`${bridgeFile}: expected audited bridge implementation is missing`);
    continue;
  }

  for (const { file, requiredEvidence } of testContracts) {
    const content = readFileSync(resolve(repoRoot, file), 'utf8');
    for (const token of requiredEvidence) {
      if (!content.includes(token)) {
        violations.push(`${file}: missing bridge compliance evidence token '${token}'`);
      }
    }
  }
}

for (const file of bridgeFiles) {
  const content = readFileSync(resolve(repoRoot, file), 'utf8');

  if (!content.includes('redact(')) {
    violations.push(`${file}: bridge adapter must redact logged metadata before logging sensitive context`);
  }

  const methods = [...content.matchAll(/public\s+Promise<[^>]+>\s+(\w+)\s*\(([\s\S]*?)\)\s*\{([\s\S]*?)\n    \}/g)];
  for (const method of methods) {
    const [, name, params, body] = method;
    if (exemptMethodNames.has(name)) {
      continue;
    }

    const hasContextParam = params.includes('BridgeContext') || params.includes('OperationContext');
    if (!hasContextParam) {
      continue;
    }

    if (!body.includes('requireStarted();')) {
      violations.push(`${file}#${name}: missing requireStarted()`);
    }

    if (hasContextParam && !body.includes('toBridgeContext()') && !params.includes('BridgeContext')) {
      violations.push(`${file}#${name}: must pass BridgeContext or convert product context via toBridgeContext()`);
    }

    if (!body.includes('checkAuthorized(')) {
      violations.push(`${file}#${name}: missing authorization check before bridge-sensitive work`);
    }

    if (/(attributes|metadata)/.test(body) && body.includes('LOG.') && !body.includes('redact(')) {
      violations.push(`${file}#${name}: metadata logging must be redacted`);
    }
  }
}

if (violations.length > 0) {
  console.error('Bridge compliance violations:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Bridge compliance validation passed.');
