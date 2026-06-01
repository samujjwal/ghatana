#!/usr/bin/env node
/**
 * PHR Mobile Privacy Gate
 * -----------------------
 * Static analysis check that enforces mobile PHI handling conventions:
 *
 * 1. No direct `AsyncStorage.setItem` calls in source files (all PHI must go
 *    through `phiSet` from `phiEncryptedStorage`).
 * 2. Every `fetchMobileDashboard` call must receive a `session` argument
 *    (no bare zero-argument calls that would omit session context headers).
 * 3. Error boundary `componentDidCatch` must not reference `error.message`
 *    or `errorInfo.componentStack` in event emissions.
 * 4. Every file that imports `fetchMobileDashboard` must also import from
 *    the session store (session context must flow through the call chain).
 *
 * Exit code 0 = all checks pass.
 * Exit code 1 = one or more violations detected.
 *
 * Usage:
 *   node scripts/check-phr-mobile-privacy.mjs
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const MOBILE_SRC = resolve(__dirname, '../products/phr/apps/mobile/src');
const REPO_ROOT = resolve(__dirname, '..');
const NON_PHI_ASYNC_STORAGE_SETITEM_FILES = new Set([
  'products/phr/apps/mobile/src/i18n/phrMobileI18n.ts',
]);
const REQUIRED_MOBILE_PRIVACY_CLEARERS = [
  {
    cacheName: 'encrypted-phi',
    clearFunction: 'phiClearAll',
  },
  {
    cacheName: 'dashboard-offline',
    clearFunction: 'clearDashboardOffline',
  },
];

// ─── File walker ─────────────────────────────────────────────────────────────

function walkTs(dir) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory() && entry !== 'node_modules' && entry !== '__mocks__') {
      results.push(...walkTs(full));
    } else if (stat.isFile() && (entry.endsWith('.ts') || entry.endsWith('.tsx'))) {
      results.push(full);
    }
  }
  return results;
}

// ─── Checks ──────────────────────────────────────────────────────────────────

function formatViolation(violation) {
  return `  ✗  ${violation.file}:${violation.line}  ${violation.message}`;
}

function lineOf(content, pattern) {
  const index = content.search(pattern);
  if (index < 0) {
    return 1;
  }
  return content.slice(0, index).split('\n').length;
}

function createViolation(file, line, message) {
  return { file, line, message };
}

function scanMobileSourceFile(file, content) {
  const violations = [];
  const isTestFile = file.includes('__tests__') || file.endsWith('.test.ts') || file.endsWith('.test.tsx');
  const lines = content.split('\n');

  // ── Check 1: No bare AsyncStorage.setItem outside phiEncryptedStorage ──────
  if (!file.endsWith('phiEncryptedStorage.ts') && !isTestFile && !NON_PHI_ASYNC_STORAGE_SETITEM_FILES.has(file)) {
    lines.forEach((line, index) => {
      if (/AsyncStorage\.setItem\s*\(/.test(line)) {
        violations.push(createViolation(file, index + 1, 'Direct AsyncStorage.setItem with PHI is forbidden. Use phiSet() from phiEncryptedStorage instead.'));
      }
    });
  }

  // ── Check 2: fetchMobileDashboard must be called with session argument ──────
  const fetchCallRe = /fetchMobileDashboard\s*\(\s*\)/;
  lines.forEach((line, i) => {
    if (fetchCallRe.test(line) && !isTestFile) {
      violations.push(createViolation(file, i + 1, 'fetchMobileDashboard() called without session argument. Pass the MobileSession to include required PHI access headers.'));
    }
  });

  // ── Check 3: componentDidCatch must not emit error.message or componentStack ─
  if (content.includes('componentDidCatch') && !isTestFile) {
    const catchIdx = lines.findIndex((l) => l.includes('componentDidCatch'));
    if (catchIdx >= 0) {
      // Check the next 20 lines for PHI-risky patterns.
      const window = lines.slice(catchIdx, catchIdx + 20).join('\n');
      if (/error\.message/.test(window) || /errorInfo\.componentStack/.test(window)) {
        violations.push(createViolation(
          file,
          catchIdx + 1,
          'componentDidCatch emits error.message or componentStack which may contain PHI. Emit only a sanitized error code.',
        ));
      }
    }
  }

  // ── Check 4: syncOfflineDashboard must be called with session argument ───────
  const syncCallRe = /syncOfflineDashboard\s*\(\s*\)/;
  lines.forEach((line, i) => {
    if (syncCallRe.test(line) && !isTestFile) {
      violations.push(createViolation(file, i + 1, 'syncOfflineDashboard() called without session argument.'));
    }
  });

  return violations;
}

function requirePattern(violations, files, file, pattern, message) {
  const content = files.get(file);
  if (content === undefined) {
    violations.push(createViolation(file, 1, `Required mobile privacy contract file is missing: ${file}.`));
    return;
  }
  if (!pattern.test(content)) {
    violations.push(createViolation(file, lineOf(content, pattern), message));
  }
}

function findMobilePrivacyContractViolations(files) {
  const violations = [];
  const privacyPlugin = 'products/phr/apps/mobile/src/services/mobilePrivacyPlugin.ts';
  const sessionStore = 'products/phr/apps/mobile/src/services/mobileSessionStore.ts';
  const mobileApi = 'products/phr/apps/mobile/src/services/phrMobileApi.ts';
  const offlineStore = 'products/phr/apps/mobile/src/services/offlineStore.ts';
  const mobilePhiPolicy = 'products/phr/apps/mobile/src/services/mobilePhiPolicy.ts';
  const phiEncryptedStorage = 'products/phr/apps/mobile/src/services/phiEncryptedStorage.ts';
  const emergencyAccessScreen = 'products/phr/apps/mobile/src/screens/EmergencyAccessScreen.tsx';
  const privacyPluginContent = files.get(privacyPlugin) ?? '';
  const sessionStoreContent = files.get(sessionStore) ?? '';
  const mobileApiContent = files.get(mobileApi) ?? '';
  const offlineStoreContent = files.get(offlineStore) ?? '';
  const phiEncryptedStorageContent = files.get(phiEncryptedStorage) ?? '';
  const emergencyAccessScreenContent = files.get(emergencyAccessScreen) ?? '';

  requirePattern(
    violations,
    files,
    privacyPlugin,
    /clearKernelMobilePrivacyState/,
    'mobilePrivacyPlugin must delegate cache clearing to the Kernel mobile privacy contract.',
  );
  requirePattern(
    violations,
    files,
    privacyPlugin,
    /clearKernelMobilePrivacyState\s*\(\s*reason\s*,\s*clearers\s*,\s*console\s*\)/,
    'clearMobilePrivacyState must call clearKernelMobilePrivacyState(reason, clearers, console).',
  );

  for (const clearer of REQUIRED_MOBILE_PRIVACY_CLEARERS) {
    if (!privacyPluginContent.includes(`cacheName: "${clearer.cacheName}"`) || !privacyPluginContent.includes(`clear: ${clearer.clearFunction}`)) {
      violations.push(createViolation(
        privacyPlugin,
        lineOf(privacyPluginContent, /defaultMobilePrivacyClearers/),
        `defaultMobilePrivacyClearers must register ${clearer.cacheName} with ${clearer.clearFunction}.`,
      ));
    }
  }

  requirePattern(
    violations,
    files,
    sessionStore,
    /import\s*\{\s*clearMobilePrivacyState\s*\}\s*from\s*["']\.\/mobilePrivacyPlugin["']/,
    'mobileSessionStore must import clearMobilePrivacyState from the mobile privacy plugin.',
  );
  requirePattern(
    violations,
    files,
    sessionStore,
    /clearMobilePrivacyState\s*\(\s*reason\s*\)/,
    'mobileSessionStore must clear local PHI through clearMobilePrivacyState(reason).',
  );
  requirePattern(
    violations,
    files,
    sessionStore,
    /clearSessionAndPhi\s*\(\s*["']session-clear["']\s*\)/,
    'clearMobileSession must clear through the shared session-clear privacy path.',
  );
  if (/\b(phiClearAll|clearDashboardOffline)\b/.test(sessionStoreContent)) {
    violations.push(createViolation(
      sessionStore,
      lineOf(sessionStoreContent, /\b(phiClearAll|clearDashboardOffline)\b/),
      'mobileSessionStore must not clear individual PHI caches directly; use clearMobilePrivacyState.',
    ));
  }

  requirePattern(
    violations,
    files,
    mobileApi,
    /import\s*\{\s*clearMobilePrivacyState\s*\}\s*from\s*["']\.\/mobilePrivacyPlugin["']/,
    'phrMobileApi must import clearMobilePrivacyState for consent revocation cleanup.',
  );
  requirePattern(
    violations,
    files,
    mobileApi,
    /import\s*\{\s*clearMobileSession\s*\}\s*from\s*["']\.\/mobileSessionStore["']/,
    'phrMobileApi logout must clear through clearMobileSession.',
  );
  requirePattern(
    violations,
    files,
    mobileApi,
    /clearMobilePrivacyState\s*\(\s*["']consent-revoked["']\s*\)/,
    'revokeConsentGrant must clear all mobile privacy caches with the consent-revoked reason.',
  );
  requirePattern(
    violations,
    files,
    mobileApi,
    /clearMobileSession\s*\(\s*\)/,
    'logoutMobile must clear the secure session through clearMobileSession().',
  );
  if (/\b(phiClearAll|clearDashboardOffline)\b/.test(mobileApiContent)) {
    violations.push(createViolation(
      mobileApi,
      lineOf(mobileApiContent, /\b(phiClearAll|clearDashboardOffline)\b/),
      'phrMobileApi must not clear individual PHI caches directly; use the mobile privacy plugin/session store.',
    ));
  }

  requirePattern(
    violations,
    files,
    offlineStore,
    /import\s*\{\s*shouldRemoveFieldFromMobileCache\s*\}\s*from\s*["']\.\/mobilePhiPolicy["']/,
    'offlineStore must use the Kernel-backed mobile PHI policy adapter for restricted cache fields.',
  );
  for (const identityField of ['persona', 'tier', 'facilityId']) {
    if (
      !new RegExp(`${identityField}:\\s*sessionIdentity\\.${identityField}`).test(offlineStoreContent) ||
      !new RegExp(`envelope\\.${identityField}\\s*===\\s*currentSession\\.${identityField}`).test(offlineStoreContent)
    ) {
      violations.push(createViolation(
        offlineStore,
        lineOf(offlineStoreContent, new RegExp(identityField)),
        `offline dashboard cache must bind and validate ${identityField} in the session envelope.`,
      ));
    }
  }
  if (/\b(RESTRICTED_FIELDS|restrictedFields|restrictedFieldSet)\b/.test(offlineStoreContent)) {
    violations.push(createViolation(
      offlineStore,
      lineOf(offlineStoreContent, /\b(RESTRICTED_FIELDS|restrictedFields|restrictedFieldSet)\b/),
      'offlineStore must not own a local restricted-field list; use mobilePhiPolicy.',
    ));
  }

  requirePattern(
    violations,
    files,
    mobilePhiPolicy,
    /from\s*["']@ghatana\/kernel-product-contracts\/policy["']/,
    'mobilePhiPolicy must consume the Kernel product-contract policy package.',
  );
  requirePattern(
    violations,
    files,
    mobilePhiPolicy,
    /createDefaultMobilePhiPolicy/,
    'mobilePhiPolicy must create its cache policy through the Kernel mobile PHI policy contract.',
  );
  requirePattern(
    violations,
    files,
    mobilePhiPolicy,
    /evaluateMobilePhiPolicyCheck/,
    'mobilePhiPolicy must evaluate restricted cache fields through the Kernel mobile PHI policy contract.',
  );

  requirePattern(
    violations,
    files,
    phiEncryptedStorage,
    /async\s+function\s+decrypt[\s\S]*await\s+requireBiometricPolicyForPhiDecrypt\s*\(\s*\)[\s\S]*getOrCreateKey\s*\(\s*\{\s*skipBiometricCheck:\s*true\s*\}\s*\)/,
    'phiEncryptedStorage decrypt must enable/enforce biometric policy before PHI key access.',
  );
  requirePattern(
    violations,
    files,
    phiEncryptedStorage,
    /async\s+function\s+requireBiometricPolicyForPhiDecrypt[\s\S]*enableBiometricPolicy\s*\(\s*\)[\s\S]*requireBiometricIfEnabled\s*\(\s*\)/,
    'phiEncryptedStorage must fail closed by enabling a missing biometric policy before decrypting PHI.',
  );
  requirePattern(
    violations,
    files,
    phiEncryptedStorage,
    /export\s+async\s+function\s+phiDisableBiometricPolicy[\s\S]*enableBiometricPolicy\s*\(\s*\)[\s\S]*BIOMETRIC_POLICY_DISABLE_DENIED/,
    'phiDisableBiometricPolicy must deny opt-out and preserve the biometric PHI gate.',
  );
  if (/BIOMETRIC_POLICY_KEY\s*,\s*["']false["']/.test(phiEncryptedStorageContent)) {
    violations.push(createViolation(
      phiEncryptedStorage,
      lineOf(phiEncryptedStorageContent, /BIOMETRIC_POLICY_KEY\s*,\s*["']false["']/),
      'PHI biometric policy must not be persisted as disabled.',
    ));
  }

  requirePattern(
    violations,
    files,
    emergencyAccessScreen,
    /const\s+granted\s*=\s*await\s+onAuthenticate\s*\(\s*\)[\s\S]*if\s*\(\s*granted\s*\)[\s\S]*requestEmergencyAccess\s*\(\s*\)/,
    'EmergencyAccessScreen must authenticate locally before requesting server-authorized emergency PHI.',
  );
  requirePattern(
    violations,
    files,
    emergencyAccessScreen,
    /if\s*\(\s*state\s*===\s*["']authorized["']\s*&&\s*emergencyData\s*\)/,
    'EmergencyAccessScreen must render emergency PHI only after authorized state and server data are present.',
  );
  if (/requestMobileEmergencyAccess\s*\(/.test(emergencyAccessScreenContent) && !/onAuthenticate\s*\(\s*\)/.test(emergencyAccessScreenContent)) {
    violations.push(createViolation(
      emergencyAccessScreen,
      lineOf(emergencyAccessScreenContent, /requestMobileEmergencyAccess\s*\(/),
      'EmergencyAccessScreen must not request emergency PHI without biometric/device authentication.',
    ));
  }

  return violations;
}

function loadMobileSourceFiles(mobileSrc = MOBILE_SRC) {
  const files = new Map();
  for (const file of walkTs(mobileSrc)) {
    const repoRelativePath = relative(REPO_ROOT, file).replaceAll('\\', '/');
    files.set(repoRelativePath, readFileSync(file, 'utf8'));
  }
  return files;
}

export function findPhrMobilePrivacyViolations(files = loadMobileSourceFiles()) {
  const violations = [];

  for (const [file, content] of files.entries()) {
    violations.push(...scanMobileSourceFile(file, content));
  }

  violations.push(...findMobilePrivacyContractViolations(files));
  return violations;
}

// ─── Report ───────────────────────────────────────────────────────────────────

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const violations = findPhrMobilePrivacyViolations();

  if (violations.length > 0) {
    console.error('\n[phr-mobile-privacy] FAIL: Privacy policy violations detected:\n');
    for (const v of violations) {
      console.error(formatViolation(v));
    }
    console.error(`\n${violations.length} violation(s) found. Fix before merging to main.\n`);
    process.exit(1);
  }

  console.log('[phr-mobile-privacy] PASS: No mobile privacy policy violations found.');
}
