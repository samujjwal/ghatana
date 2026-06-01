import assert from 'node:assert/strict';
import test from 'node:test';

import { findPhrMobilePrivacyViolations } from '../check-phr-mobile-privacy.mjs';

const privacyPluginPath = 'products/phr/apps/mobile/src/services/mobilePrivacyPlugin.ts';
const sessionStorePath = 'products/phr/apps/mobile/src/services/mobileSessionStore.ts';
const mobileApiPath = 'products/phr/apps/mobile/src/services/phrMobileApi.ts';
const offlineStorePath = 'products/phr/apps/mobile/src/services/offlineStore.ts';
const mobilePhiPolicyPath = 'products/phr/apps/mobile/src/services/mobilePhiPolicy.ts';
const phiEncryptedStoragePath = 'products/phr/apps/mobile/src/services/phiEncryptedStorage.ts';
const emergencyAccessScreenPath = 'products/phr/apps/mobile/src/screens/EmergencyAccessScreen.tsx';
const dashboardPath = 'products/phr/apps/mobile/src/screens/DashboardScreen.tsx';

function validFixture(overrides = {}) {
  return new Map(Object.entries({
    [privacyPluginPath]: `
import { clearKernelMobilePrivacyState } from "@ghatana/kernel-product-contracts/mobile-privacy";
import { phiClearAll } from "./phiEncryptedStorage";
import { clearDashboardOffline } from "./offlineStore";

const defaultMobilePrivacyClearers = [
  { cacheName: "encrypted-phi", clear: phiClearAll },
  { cacheName: "dashboard-offline", clear: clearDashboardOffline },
];

export function clearMobilePrivacyState(reason, clearers = defaultMobilePrivacyClearers) {
  return clearKernelMobilePrivacyState(reason, clearers, console);
}
`,
    [sessionStorePath]: `
import { clearMobilePrivacyState } from "./mobilePrivacyPlugin";

async function clearSessionAndPhi(reason) {
  await clearMobilePrivacyState(reason);
}

export async function clearMobileSession() {
  await clearSessionAndPhi("session-clear");
}
`,
    [mobileApiPath]: `
import { clearMobileSession } from "./mobileSessionStore";
import { clearMobilePrivacyState } from "./mobilePrivacyPlugin";

export async function logoutMobile() {
  await clearMobileSession();
}

export async function revokeConsentGrant() {
  await clearMobilePrivacyState("consent-revoked");
}
`,
    [offlineStorePath]: `
import { shouldRemoveFieldFromMobileCache } from "./mobilePhiPolicy";

export async function saveDashboardOffline(dashboard, ttlMs, sessionIdentity) {
  const envelope = {
    persona: sessionIdentity.persona,
    tier: sessionIdentity.tier,
    facilityId: sessionIdentity.facilityId,
    data: dashboard,
  };
  return envelope;
}

function sessionMatches(envelope, currentSession) {
  return envelope.persona === currentSession.persona &&
    envelope.tier === currentSession.tier &&
    envelope.facilityId === currentSession.facilityId;
}

export function sanitize(value) {
  return shouldRemoveFieldFromMobileCache(value) ? null : value;
}
`,
    [mobilePhiPolicyPath]: `
import {
  createDefaultMobilePhiPolicy,
  evaluateMobilePhiPolicyCheck,
} from "@ghatana/kernel-product-contracts/policy";

const policy = createDefaultMobilePhiPolicy([]);

export function shouldRemoveFieldFromMobileCache(fieldName) {
  return !evaluateMobilePhiPolicyCheck(policy, {
    principalId: "phr-mobile",
    sessionId: "offline-cache-policy",
    action: "cache",
    fieldNames: [fieldName],
  }).allowed;
}
`,
    [phiEncryptedStoragePath]: `
async function requireBiometricPolicyForPhiDecrypt() {
  await enableBiometricPolicy();
  await requireBiometricIfEnabled();
}

async function decrypt(ciphertext) {
  await requireBiometricPolicyForPhiDecrypt();
  const key = await getOrCreateKey({ skipBiometricCheck: true });
  return key.decrypt(ciphertext);
}

export async function phiDisableBiometricPolicy() {
  await enableBiometricPolicy();
  await logSecurityEvent("BIOMETRIC_POLICY_DISABLE_DENIED");
}
`,
    [emergencyAccessScreenPath]: `
import { requestMobileEmergencyAccess } from "../services/phrMobileApi";

export function EmergencyAccessScreen({ onAuthenticate }) {
  async function requestEmergencyAccess() {
    return requestMobileEmergencyAccess();
  }

  async function handleVerify() {
    const granted = await onAuthenticate();
    if (granted) {
      await requestEmergencyAccess();
    }
  }

  if (state === "authorized" && emergencyData) {
    return emergencyData.patientName;
  }

  return handleVerify;
}
`,
    [dashboardPath]: `
import { fetchMobileDashboard, syncOfflineDashboard } from "../services/phrMobileApi";

export async function refresh(session) {
  await fetchMobileDashboard(session);
  await syncOfflineDashboard(session);
}
`,
    ...overrides,
  }));
}

test('accepts the current mobile privacy plugin contract shape', () => {
  assert.deepEqual(findPhrMobilePrivacyViolations(validFixture()), []);
});

test('detects missing required mobile privacy cache clearers', () => {
  const violations = findPhrMobilePrivacyViolations(validFixture({
    [privacyPluginPath]: `
import { clearKernelMobilePrivacyState } from "@ghatana/kernel-product-contracts/mobile-privacy";
import { phiClearAll } from "./phiEncryptedStorage";

const defaultMobilePrivacyClearers = [
  { cacheName: "encrypted-phi", clear: phiClearAll },
];

export function clearMobilePrivacyState(reason, clearers = defaultMobilePrivacyClearers) {
  return clearKernelMobilePrivacyState(reason, clearers, console);
}
`,
  }));

  assert.match(
    violations.map((violation) => violation.message).join('\n'),
    /defaultMobilePrivacyClearers must register dashboard-offline with clearDashboardOffline/,
  );
});

test('detects direct PHI cache clearing in mobile API code', () => {
  const violations = findPhrMobilePrivacyViolations(validFixture({
    [mobileApiPath]: `
import { clearMobileSession } from "./mobileSessionStore";
import { clearMobilePrivacyState } from "./mobilePrivacyPlugin";
import { phiClearAll } from "./phiEncryptedStorage";

export async function logoutMobile() {
  await phiClearAll();
  await clearMobileSession();
}

export async function revokeConsentGrant() {
  await clearMobilePrivacyState("consent-revoked");
}
`,
  }));

  assert.match(
    violations.map((violation) => violation.message).join('\n'),
    /phrMobileApi must not clear individual PHI caches directly/,
  );
});

test('detects mobile dashboard calls without session context', () => {
  const violations = findPhrMobilePrivacyViolations(validFixture({
    [dashboardPath]: `
import { fetchMobileDashboard } from "../services/phrMobileApi";

export async function refresh() {
  await fetchMobileDashboard();
}
`,
  }));

  assert.match(
    violations.map((violation) => violation.message).join('\n'),
    /fetchMobileDashboard\(\) called without session argument/,
  );
});

test('detects offline cache envelopes that omit full session scope binding', () => {
  const violations = findPhrMobilePrivacyViolations(validFixture({
    [offlineStorePath]: `
import { shouldRemoveFieldFromMobileCache } from "./mobilePhiPolicy";

export async function saveDashboardOffline(dashboard, ttlMs, sessionIdentity) {
  return {
    persona: sessionIdentity.persona,
    tier: sessionIdentity.tier,
    data: dashboard,
  };
}

function sessionMatches(envelope, currentSession) {
  return envelope.persona === currentSession.persona &&
    envelope.tier === currentSession.tier;
}

export function sanitize(value) {
  return shouldRemoveFieldFromMobileCache(value) ? null : value;
}
`,
  }));

  assert.match(
    violations.map((violation) => violation.message).join('\n'),
    /offline dashboard cache must bind and validate facilityId/,
  );
});

test('detects offline cache policy code that bypasses Kernel mobile PHI policy contracts', () => {
  const violations = findPhrMobilePrivacyViolations(validFixture({
    [mobilePhiPolicyPath]: `
const localPolicy = new Set(["mentalHealth"]);

export function shouldRemoveFieldFromMobileCache(fieldName) {
  return localPolicy.has(fieldName);
}
`,
  }));

  assert.match(
    violations.map((violation) => violation.message).join('\n'),
    /mobilePhiPolicy must consume the Kernel product-contract policy package/,
  );
});

test('detects PHI decrypt paths that do not enforce biometric policy before key access', () => {
  const violations = findPhrMobilePrivacyViolations(validFixture({
    [phiEncryptedStoragePath]: `
async function decrypt(ciphertext) {
  const key = await getOrCreateKey();
  return key.decrypt(ciphertext);
}

export async function phiDisableBiometricPolicy() {
  await SecureStore.setItemAsync(BIOMETRIC_POLICY_KEY, "false");
}
`,
  }));

  const messages = violations.map((violation) => violation.message).join('\n');
  assert.match(messages, /decrypt must enable\/enforce biometric policy/);
  assert.match(messages, /must not be persisted as disabled/);
});

test('detects emergency screens that request PHI before biometric authentication', () => {
  const violations = findPhrMobilePrivacyViolations(validFixture({
    [emergencyAccessScreenPath]: `
import { requestMobileEmergencyAccess } from "../services/phrMobileApi";

export function EmergencyAccessScreen() {
  async function handleVerify() {
    await requestMobileEmergencyAccess();
  }

  if (emergencyData) {
    return emergencyData.patientName;
  }

  return handleVerify;
}
`,
  }));

  const messages = violations.map((violation) => violation.message).join('\n');
  assert.match(messages, /authenticate locally before requesting server-authorized emergency PHI/);
  assert.match(messages, /render emergency PHI only after authorized state/);
});

test('current mobile source scan has no mobile privacy violations', () => {
  assert.deepEqual(findPhrMobilePrivacyViolations(), []);
});
