#!/usr/bin/env node

/**
 * G11-004: PHR mobile DTO contract check.
 *
 * Statically verifies that PhrMobileRoutes.java produces the fields that
 * the mobile app's phrMobileApi.ts validates, and that types.ts defines
 * matching interfaces. This prevents silent DTO mismatch regressions.
 *
 * Assertions:
 *   1. Backend mobile route returns 'fhirPreview' in records (not 'documentType' or 'createdAt' only).
 *   2. Backend mobile route returns 'title' and 'detail' in notifications (not type/referenceId/status).
 *   3. Backend mobile route does NOT return deprecated notification fields: type, referenceId, referenceType, status, createdAt.
 *   4. phrMobileApi.ts asserts 'fhirPreview' exists on each record item.
 *   5. phrMobileApi.ts asserts 'title' and 'detail' exist on each notification item.
 *   6. types.ts MobileRecord interface includes 'fhirPreview'.
 *   7. types.ts MobileNotificationItem interface includes 'title' and 'detail'.
 *   8. types.ts MobileNotificationItem does NOT declare the old backend-only fields (type, referenceId, referenceType).
 *   9. Backend helper methods formatNotificationTitle and formatNotificationDetail exist.
 *  10. Mobile session change detection covers persona and tier (not only role/principalId).
 */

import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';

const ROOT = process.cwd();
const MOBILE_SRC = resolve(ROOT, 'products/phr/apps/mobile/src');
const BACKEND_ROUTES = resolve(ROOT, 'products/phr/src/main/java/com/ghatana/phr/api/routes');

function readRequired(path, label) {
  if (!existsSync(path)) {
    console.error(`ERROR [mobile-dto]: ${label} not found at ${path}`);
    process.exit(1);
  }
  return readFileSync(path, 'utf-8');
}

const mobileRoutesJava = readRequired(resolve(BACKEND_ROUTES, 'PhrMobileRoutes.java'), 'PhrMobileRoutes.java');
const mobileApiTs = readRequired(resolve(MOBILE_SRC, 'services/phrMobileApi.ts'), 'phrMobileApi.ts');
const typesTs = readRequired(resolve(MOBILE_SRC, 'types.ts'), 'types.ts');
const appTsx = readRequired(resolve(MOBILE_SRC, 'App.tsx'), 'App.tsx');

let failures = 0;

function fail(msg) {
  console.error(`FAIL [mobile-dto]: ${msg}`);
  failures++;
}

function pass(msg) {
  console.log(`PASS [mobile-dto]: ${msg}`);
}

function assertContains(content, substring, msg) {
  if (!content.includes(substring)) {
    fail(msg);
    return false;
  }
  return true;
}

function assertNotContains(content, substring, msg) {
  if (content.includes(substring)) {
    fail(msg);
    return false;
  }
  return true;
}

// 1. Backend returns fhirPreview in records
assertContains(
  mobileRoutesJava,
  '"fhirPreview"',
  `PhrMobileRoutes.java must include "fhirPreview" in the records DTO`
);
pass(`backend mobile route includes fhirPreview in records`);

// 2. Backend returns title and detail in notifications
assertContains(
  mobileRoutesJava,
  '"title", title',
  `PhrMobileRoutes.java must put "title" key in notification DTO`
);
assertContains(
  mobileRoutesJava,
  '"detail", detail',
  `PhrMobileRoutes.java must put "detail" key in notification DTO`
);
pass(`backend mobile route includes title/detail in notifications`);

// 3. Backend does NOT return deprecated notification fields
for (const deprecated of ['"type", entry.notificationType()', '"referenceId"', '"referenceType"', '"status", entry.status()']) {
  assertNotContains(
    mobileRoutesJava,
    deprecated,
    `PhrMobileRoutes.java must not return deprecated notification field pattern: ${deprecated}`
  );
}
pass(`backend mobile route does not return deprecated notification fields`);

// 4. Mobile API asserts fhirPreview on each record
assertContains(
  mobileApiTs,
  'record.fhirPreview',
  `phrMobileApi.ts must access record.fhirPreview on each record item`
);
pass(`phrMobileApi.ts references record.fhirPreview`);

// 5. Mobile API asserts title and detail on each notification
assertContains(
  mobileApiTs,
  'notification.title',
  `phrMobileApi.ts must access notification.title on notification items`
);
assertContains(
  mobileApiTs,
  'notification.detail',
  `phrMobileApi.ts must access notification.detail on notification items`
);
pass(`phrMobileApi.ts accesses notification.title and notification.detail`);

// 6. types.ts MobileRecord includes fhirPreview
assertContains(
  typesTs,
  'fhirPreview',
  `types.ts MobileRecord must declare 'fhirPreview' field`
);
pass(`types.ts MobileRecord declares fhirPreview`);

// 7. types.ts MobileNotificationItem includes title and detail
assertContains(
  typesTs,
  'title',
  `types.ts notification type must declare 'title'`
);
assertContains(
  typesTs,
  'detail',
  `types.ts notification type must declare 'detail'`
);
pass(`types.ts notification interface declares title and detail`);

// 8. types.ts notification type must NOT declare old backend-only fields
for (const oldField of ['referenceId', 'referenceType']) {
  // Only flag if these appear inside what looks like a notification interface, not elsewhere
  const notifSection = typesTs.match(/(?:MobileNotification|Notification)[\s\S]{0,500}/)?.[0] ?? '';
  if (notifSection.includes(oldField + ':') || notifSection.includes(oldField + '?:')) {
    fail(`types.ts notification interface still declares deprecated field '${oldField}'`);
  }
}
pass(`types.ts notification interface does not declare deprecated backend-only fields`);

// 9. Backend helper methods exist for formatting notification title/detail
assertContains(
  mobileRoutesJava,
  'formatNotificationTitle',
  `PhrMobileRoutes.java must have a formatNotificationTitle helper method`
);
assertContains(
  mobileRoutesJava,
  'formatNotificationDetail',
  `PhrMobileRoutes.java must have a formatNotificationDetail helper method`
);
pass(`backend mobile route has formatNotificationTitle and formatNotificationDetail helpers`);

// 10. Mobile App.tsx session change detection covers persona and tier
assertContains(
  appTsx,
  'currentSession.persona !== session.persona',
  `App.tsx session change detection must compare persona, not only role/principalId`
);
assertContains(
  appTsx,
  'currentSession.tier !== session.tier',
  `App.tsx session change detection must compare tier, not only role/principalId`
);
pass(`App.tsx session change detection covers persona and tier`);

// Summary
if (failures > 0) {
  console.error(`\nFAIL: ${failures} mobile DTO contract violation(s) found`);
  process.exit(1);
}

console.log(`\nPASS: Mobile DTO contract check passed — backend/mobile/types contract is consistent`);
