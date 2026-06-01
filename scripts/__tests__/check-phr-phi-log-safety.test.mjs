import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { findPhrPhiLogSafetyViolations } from '../check-phr-phi-log-safety.mjs';

function fixtureRoot() {
  const root = path.join(tmpdir(), `phr-phi-log-safety-${process.pid}-${Date.now()}-${Math.random().toString(16).slice(2)}`);
  mkdirSync(path.join(root, 'products/phr/src/main/java/com/ghatana/phr/kernel/service'), { recursive: true });
  mkdirSync(path.join(root, 'products/phr/apps/web/src/pages'), { recursive: true });
  return root;
}

test('PHR PHI log safety guard rejects raw justification in event metadata', () => {
  const root = fixtureRoot();
  writeFileSync(
    path.join(root, 'products/phr/src/main/java/com/ghatana/phr/kernel/service/UnsafeNotification.java'),
    `
package com.ghatana.phr.kernel.service;

class UnsafeNotification {
  void publish(EmergencyAccessLogService.EmergencyAccessEvent event) {
    java.util.Map.of("justification", event.justification());
  }
}
`,
  );

  const violations = findPhrPhiLogSafetyViolations(root);

  assert.equal(violations.length, 1);
  assert.match(violations[0], /raw justification text/);
});

test('PHR PHI log safety guard allows protected justification references', () => {
  const root = fixtureRoot();
  writeFileSync(
    path.join(root, 'products/phr/src/main/java/com/ghatana/phr/kernel/service/SafeNotification.java'),
    `
package com.ghatana.phr.kernel.service;

class SafeNotification {
  void publish(EmergencyAccessLogService.EmergencyAccessEvent event) {
    java.util.Map.of(
      "justificationCaptured", "true",
      "justificationReference", event.reviewCaseId() + ":" + event.id(),
      "justificationHash", "hash"
    );
  }
}
`,
  );

  assert.deepEqual(findPhrPhiLogSafetyViolations(root), []);
});

test('current PHR source has no PHI log safety violations', () => {
  assert.deepEqual(findPhrPhiLogSafetyViolations(), []);
});
