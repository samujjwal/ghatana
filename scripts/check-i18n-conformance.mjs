#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const violations = [];

function requireFile(relativePath) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    violations.push(`Missing i18n artifact: ${relativePath}`);
    return '';
  }
  return readFileSync(absolutePath, 'utf8');
}

const dataCloudI18n = requireFile('products/data-cloud/delivery/ui/src/i18n/config.ts');
if (dataCloudI18n) {
  for (const token of [
    "import i18n from 'i18next'",
    'fallbackLng',
    'supportedLngs',
    "'en-XA'",
    'pseudoLocalize',
  ]) {
    if (!dataCloudI18n.includes(token)) {
      violations.push(`Data Cloud i18n config missing token ${JSON.stringify(token)}`);
    }
  }
}

const dataCloudI18nTest = requireFile('products/data-cloud/delivery/ui/src/i18n/__tests__/config.test.ts');
if (dataCloudI18nTest) {
  for (const token of ['supports pseudo-locale coverage', 'en-XA']) {
    if (!dataCloudI18nTest.includes(token)) {
      violations.push(`Data Cloud i18n test missing token ${JSON.stringify(token)}`);
    }
  }
}

const digitalMarketingI18n = requireFile('products/digital-marketing/ui/src/lib/i18n/format.ts');
if (digitalMarketingI18n) {
  for (const token of ['Intl.NumberFormat', 'Intl.DateTimeFormat']) {
    if (!digitalMarketingI18n.includes(token)) {
      violations.push(`Digital Marketing i18n formatter missing token ${JSON.stringify(token)}`);
    }
  }
}

const digitalMarketingI18nTests = requireFile('products/digital-marketing/ui/src/lib/i18n/__tests__/format.test.ts');
if (digitalMarketingI18nTests && !digitalMarketingI18nTests.includes("describe('i18n formatting helpers'")) {
  violations.push('Digital Marketing i18n tests are missing the formatting helper suite');
}

// P0-05: PHR i18n conformance check
const phrI18n = requireFile('products/phr/apps/web/src/i18n/phrI18n.ts');
if (phrI18n) {
  for (const token of [
    'resolvePhrLocale',
    'formatPhrDate',
    'formatPhrDateTime',
    'formatPhrPercent',
    'pseudoLocalize',
    "'en-XA'",
  ]) {
    if (!phrI18n.includes(token)) {
      violations.push(`PHR i18n config missing token ${JSON.stringify(token)}`);
    }
  }
}

const phrI18nTest = requireFile('products/phr/apps/web/src/i18n/__tests__/phrI18n.test.ts');
if (phrI18nTest) {
  for (const token of ['resolvePhrLocale', 'formatPhrDate', 'pseudoLocalize']) {
    if (!phrI18nTest.includes(token)) {
      violations.push(`PHR i18n test missing token ${JSON.stringify(token)}`);
    }
  }
}

const phrEnLocale = requireFile('products/phr/apps/web/src/locales/en/common.json');
if (phrEnLocale) {
  for (const token of ['dashboard.', 'login.', 'consents.', 'appointments.', 'labs.', 'medications.', 'records.', 'emergency.', 'settings.', 'validation.', 'error.', 'route.', 'role.', 'shell.', 'audit.']) {
    if (!phrEnLocale.includes(token)) {
      violations.push(`PHR English locale missing namespace ${JSON.stringify(token)}`);
    }
  }
}

const phrNeLocale = requireFile('products/phr/apps/web/src/locales/ne/common.json');
if (phrNeLocale) {
  for (const token of ['dashboard.', 'login.', 'consents.', 'appointments.', 'labs.', 'medications.', 'records.', 'emergency.', 'settings.', 'validation.', 'error.', 'route.', 'role.', 'shell.', 'audit.']) {
    if (!phrNeLocale.includes(token)) {
      violations.push(`PHR Nepali locale missing namespace ${JSON.stringify(token)}`);
    }
  }
}

const releaseWorkflow = requireFile('.github/workflows/data-cloud-release.yml');
if (releaseWorkflow && !releaseWorkflow.includes('pnpm check:i18n-conformance')) {
  violations.push('Data Cloud release workflow must execute check:i18n-conformance');
}

if (violations.length > 0) {
  console.error('i18n conformance failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('i18n conformance passed.');
