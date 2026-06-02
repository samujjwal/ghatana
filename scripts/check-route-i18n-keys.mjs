#!/usr/bin/env node

/**
 * Route i18n Key Check (I18N-01 through I18N-04)
 * Ensures all stable routes have i18n keys defined in locale files
 * Enforces Kernel-level i18n requirements for PHR routes
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');

let violations = [];
let routesChecked = 0;
let i18nKeysChecked = 0;

function loadJsonFile(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    return null;
  }
}

function checkRouteContract(routeContractPath, localeFiles) {
  const relativePath = relative(REPO_ROOT, routeContractPath);
  const contract = loadJsonFile(routeContractPath);
  
  if (!contract || !contract.routes) {
    return;
  }

  contract.routes.forEach((route) => {
    // Only check stable routes
    if (route.stability !== 'stable') {
      return;
    }

    routesChecked++;

    // Check i18nKey
    if (!route.i18nKey) {
      violations.push({
        routeContract: relativePath,
        route: route.path,
        message: 'Stable route missing i18nKey',
        details: 'Route must have i18nKey defined for localization'
      });
    } else {
      i18nKeysChecked++;
      // Check if i18nKey exists in all locale files
      localeFiles.forEach((localeFile) => {
        const locale = loadJsonFile(localeFile.path);
        if (locale && !locale[route.i18nKey]) {
          violations.push({
            routeContract: relativePath,
            route: route.path,
            message: `i18nKey not found in locale file`,
            details: `Key "${route.i18nKey}" missing in ${localeFile.locale}`
          });
        }
      });
    }

    // Check descriptionI18nKey
    if (!route.descriptionI18nKey) {
      violations.push({
        routeContract: relativePath,
        route: route.path,
        message: 'Stable route missing descriptionI18nKey',
        details: 'Route must have descriptionI18nKey defined for localization'
      });
    } else {
      i18nKeysChecked++;
      // Check if descriptionI18nKey exists in all locale files
      localeFiles.forEach((localeFile) => {
        const locale = loadJsonFile(localeFile.path);
        if (locale && !locale[route.descriptionI18nKey]) {
          violations.push({
            routeContract: relativePath,
            route: route.path,
            message: `descriptionI18nKey not found in locale file`,
            details: `Key "${route.descriptionI18nKey}" missing in ${localeFile.locale}`
          });
        }
      });
    }
  });
}

function findLocaleFiles(localesDir) {
  const localeFiles = [];
  const entries = readdirSync(localesDir, { withFileTypes: true });
  
  for (const entry of entries) {
    if (entry.isDirectory()) {
      const commonJsonPath = join(localesDir, entry.name, 'common.json');
      if (statSync(commonJsonPath).isFile()) {
        localeFiles.push({
          locale: entry.name,
          path: commonJsonPath
        });
      }
    }
  }
  
  return localeFiles;
}

function main() {
  console.log('🔍 Checking route i18n keys (I18N-01 through I18N-04)...\n');
  
  const phrConfigDir = join(REPO_ROOT, 'products/phr/config');
  const routeContractPath = join(phrConfigDir, 'phr-route-contract.json');
  const localesDir = join(REPO_ROOT, 'products/phr/apps/web/src/locales');
  
  if (!statSync(routeContractPath).isFile()) {
    console.error('❌ PHR route contract not found');
    process.exit(1);
  }

  if (!statSync(localesDir).isDirectory()) {
    console.error('❌ PHR locales directory not found');
    process.exit(1);
  }

  const localeFiles = findLocaleFiles(localesDir);
  console.log(`📊 Found ${localeFiles.length} locale files\n`);
  
  checkRouteContract(routeContractPath, localeFiles);
  
  console.log(`📊 Checked ${routesChecked} stable routes\n`);
  console.log(`📊 Checked ${i18nKeysChecked} i18n keys\n`);
  
  if (violations.length > 0) {
    console.error(`❌ Found ${violations.length} i18n violations:\n`);
    violations.forEach((v, i) => {
      console.error(`  ${i + 1}. ${v.routeContract}`);
      console.error(`     Route: ${v.route}`);
      console.error(`     ${v.message}`);
      console.error(`     ${v.details}\n`);
    });
    console.error('\n💡 Fix: Add missing i18n keys to locale files and route contract');
    process.exit(1);
  }
  
  console.log('✅ All stable routes have valid i18n keys in all locale files.');
  console.log('✅ i18n enforcement passed.\n');
  process.exit(0);
}

main();
