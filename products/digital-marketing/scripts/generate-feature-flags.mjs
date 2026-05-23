#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const MANIFEST_PATH = path.join(__dirname, '..', 'FEATURE_FLAGS_MANIFEST.json');
const JAVA_OUTPUT_PATH = path.join(
  __dirname,
  '..',
  'dm-application',
  'src',
  'main',
  'java',
  'com',
  'ghatana',
  'digitalmarketing',
  'application',
  'DmosFeatureFlags.java',
);
const TS_OUTPUT_PATH = path.join(__dirname, '..', 'ui', 'src', 'lib', 'feature-flags.ts');

function readManifest() {
  const content = fs.readFileSync(MANIFEST_PATH, 'utf-8');
  return JSON.parse(content);
}

function toConstantName(key) {
  return key
    .replace(/^dmos\./, '')
    .replace(/\./g, '_')
    .toUpperCase()
    .replace(/-/g, '_');
}

function generateJavaClass(flags) {
  const constantDeclarations = flags
    .map((flag) => {
      const constantName = toConstantName(flag.key);
      const envVar = flag.environmentVariable;
      const defaultValue = flag.defaultValue !== undefined ? flag.defaultValue : flag.defaultDevelopmentValue;
      const prodDefault =
        flag.productionDefault !== undefined ? flag.productionDefault : flag.defaultProductionValue;

      return `    /**
     * ${flag.description}
     * Env-var fallback: {@code ${envVar}} (default {@code ${prodDefault !== undefined ? prodDefault : defaultValue}}${prodDefault !== undefined ? ' in production' : ''}).
     */
    public static final String ${constantName} = "${flag.key}";`;
    })
    .join('\n\n');

  return `package com.ghatana.digitalmarketing.application;

/**
 * Canonical DMOS feature flag key constants (P2-3: Manifest-based).
 *
 * <p>This class defines the canonical feature flag keys for DMOS. All flags are
 * defined in {@code FEATURE_FLAGS_MANIFEST.json} and should be synchronized with
 * that single source of truth. Do not add new flags here without updating the manifest.</p>
 *
 * <p>Runtime flag values are resolved from environment variables with production-safe
 * defaults (incomplete features default to {@code false} in production).</p>
 *
 * @doc.type class
 * @doc.purpose Canonical DMOS feature flag key constants from manifest
 * @doc.layer product
 * @doc.pattern Config
 */
public final class DmosFeatureFlags {

${constantDeclarations}

    private DmosFeatureFlags() {
        // Utility class - prevent instantiation
    }
}
`;
}

function generateTypeScriptFile(flags) {
  const uiFlags = flags.filter((flag) => {
    const scope = Array.isArray(flag.scope) ? flag.scope : [flag.scope];
    return scope.includes('ui') || scope.includes('both');
  });

  const constantDeclarations = uiFlags
    .map((flag) => {
      const constantName = toConstantName(flag.key);
      return `  ${constantName}: '${flag.key}'`;
    })
    .join(',\n');

  const flagValues = uiFlags
    .map((flag) => {
      const constantName = toConstantName(flag.key);
      const envVar = flag.environmentVariable;
      const defaultValue = flag.defaultValue !== undefined ? flag.defaultValue : flag.defaultDevelopmentValue;
      return `  [FEATURE_FLAGS.${constantName}]: getFlagValue('${envVar}', ${defaultValue}),`;
    })
    .join('\n');

  return `/**
 * DMOS feature flags configuration (P2-3: Canonical manifest-based).
 *
 * This file is generated from the canonical FEATURE_FLAGS_MANIFEST.json.
 * Do not modify this file directly - update the manifest instead.
 *
 * @doc.type config
 * @doc.purpose UI feature flags from canonical manifest
 * @doc.layer frontend
 */

// P2-3: Canonical flag definitions from FEATURE_FLAGS_MANIFEST.json
export const FEATURE_FLAGS = {
${constantDeclarations}
} as const;

export type FeatureFlag = typeof FEATURE_FLAGS[keyof typeof FEATURE_FLAGS];

// P2-3: Flag values from environment variables (canonical source)
const getFlagValue = (key: string, defaultValue: boolean): boolean => {
  const envValue = process.env[key.toUpperCase().replace(/\./g, '_')];
  if (envValue !== undefined) {
    return envValue === 'true' || envValue === '1';
  }
  return defaultValue;
};

export const flagValues = {
${flagValues}
};

export function isFeatureEnabled(flag: FeatureFlag): boolean {
  return flagValues[flag];
}
`;
}

function writeIfChanged(filePath, content) {
  if (fs.existsSync(filePath)) {
    const existing = fs.readFileSync(filePath, 'utf-8');
    if (existing === content) {
      console.log(`No changes to ${filePath}`);
      return false;
    }
  }
  fs.writeFileSync(filePath, content, 'utf-8');
  console.log(`Generated ${filePath}`);
  return true;
}

function main() {
  console.log('Generating feature flags from FEATURE_FLAGS_MANIFEST.json...');
  const manifest = readManifest();
  const javaChanged = writeIfChanged(JAVA_OUTPUT_PATH, generateJavaClass(manifest.flags));
  const tsChanged = writeIfChanged(TS_OUTPUT_PATH, generateTypeScriptFile(manifest.flags));

  if (javaChanged || tsChanged) {
    console.log('Feature flags generated successfully.');
  } else {
    console.log('Feature flags are up to date.');
  }
}

main();
