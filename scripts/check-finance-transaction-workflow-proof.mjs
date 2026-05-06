#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const checks = [
  {
    name: 'Finance transaction service four-eyes gate',
    file: 'products/finance/src/main/java/com/ghatana/finance/service/TransactionService.java',
    required: [
      'requiresHumanReview',
      'queueForReview(',
      '"approval_required", "four-eyes"',
      '"audit_classification", "TRANSACTION_MUTATION"',
      '"idempotency_key"',
      'processedTransactions.putIfAbsent',
    ],
  },
  {
    name: 'Finance transaction workflow proof',
    file: 'products/finance/src/test/java/com/ghatana/finance/service/TransactionServiceTest.java',
    required: [
      'testProcessTransaction_FourEyesReview_ShouldAuditBeforeAgentExecutionSideEffect',
      'ReviewRequiredAutonomyManager',
      'assertEquals("PENDING_REVIEW", result.getStatus())',
      'assertEquals("four-eyes", result.getMetadata().get("approval_required"))',
      'assertEquals("TRANSACTION_MUTATION", result.getMetadata().get("audit_classification"))',
      'assertEquals("txn-four-eyes", result.getMetadata().get("idempotency_key"))',
      'assertEquals(0, countingOrchestrator.executions.get())',
      'hasNoRecordedDecisions()',
    ],
  },
  {
    name: 'Finance policy compatibility proof',
    file: 'products/finance/src/test/java/com/ghatana/finance/kernel/policy/FinancePolicyCompatibilityTest.java',
    required: [
      'Finance transaction write requires approval',
      'settle',
      'must require approval',
    ],
  },
  {
    name: 'Finance pack contract approval metadata',
    file: 'products/finance/src/test/java/com/ghatana/finance/kernel/FinancePackContractTest.java',
    required: [
      'transaction write requires approval (four-eyes)',
      'transaction writes require approval and audit',
      'approvalPolicy", "four-eyes"',
    ],
  },
];

const violations = [];

for (const check of checks) {
  const filePath = path.join(repoRoot, check.file);
  if (!existsSync(filePath)) {
    violations.push(`${check.name}: missing ${check.file}`);
    continue;
  }

  const source = readFileSync(filePath, 'utf8');
  const missing = check.required.filter((token) => !source.includes(token));
  if (missing.length > 0) {
    violations.push(`${check.name}: missing workflow-proof token(s) ${missing.join(', ')} in ${check.file}`);
  }
}

if (violations.length > 0) {
  console.error('Finance transaction workflow proof check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Finance transaction workflow proof check passed.');
