import assert from 'node:assert/strict';
import test from 'node:test';

import { findPartialWebSessionContextViolations } from '../check-phr-web-session-context.mjs';

test('detects partial session header objects in web pages', () => {
  const violations = findPartialWebSessionContextViolations(new Map([
    ['products/phr/apps/web/src/pages/RecordsPage.tsx', `
fetchRecords(session.principalId, {
  tenantId: session.tenantId,
  principalId: session.principalId,
  role: session.role,
});
`],
  ]));

  assert.equal(violations.length, 3);
  assert.match(violations[0].message, /toSessionContext\(session\)/);
});

test('allows centralized full session context adapter usage', () => {
  const violations = findPartialWebSessionContextViolations(new Map([
    ['products/phr/apps/web/src/pages/RecordsPage.tsx', `
fetchRecords(session.principalId, toSessionContext(session));
`],
  ]));

  assert.deepEqual(violations, []);
});

test('detects partial access-context API objects in web pages', () => {
  const violations = findPartialWebSessionContextViolations(new Map([
    ['products/phr/apps/web/src/pages/ConsentPage.tsx', `
const apiContext = useMemo(() => ({ tenantId, principalId, role }), [tenantId, principalId, role]);
createConsentGrant(request, apiContext);
`],
  ]));

  assert.equal(violations.length, 1);
  assert.match(violations[0].message, /usePhrRequestContext/);
});

test('allows centralized access request context hook usage', () => {
  const violations = findPartialWebSessionContextViolations(new Map([
    ['products/phr/apps/web/src/pages/ConsentPage.tsx', `
const apiContext = usePhrRequestContext();
createConsentGrant(request, apiContext);
`],
  ]));

  assert.deepEqual(violations, []);
});

test('current PHR web source has no partial session header objects in pages', () => {
  assert.deepEqual(findPartialWebSessionContextViolations(), []);
});
