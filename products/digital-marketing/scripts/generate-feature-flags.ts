#!/usr/bin/env node
/**
 * P2-003: Feature flag code generator.
 * 
 * Reads FEATURE_FLAGS_MANIFEST.json and generates:
 * - DmosFeatureFlags.java (backend constants)
 * - ui/src/lib/feature-flags.ts (frontend constants)
 * 
 * This ensures a single source of truth for all feature flags.
 */

import fs from 'fs';
import path from 'path';

interface FeatureFlag {
  key: string;
  name: string;
  description: string;
  defaultValue?: boolean;
  defaultDevelopmentValue?: boolean;
  defaultProductionValue?: boolean;
  productionDefault?: boolean;
  environmentVariable: string;
  category: string;
  scope: string | string[];
}

interface Manifest {
  version: string;
  description: string;
  flags: FeatureFlag[];
  categories: Record<string, string>;
  scopes: Record<string, string>;
  notes: string;
}

const MANIFEST_PATH = path.join(__dirname, '..', 'FEATURE_FLAGS_MANIFEST.json');
const JAVA_OUTPUT_PATH = path.join(__dirname, '..', 'dm-application', 'src', 'main', 'java', 'com', 'ghatana', 'digitalmarketing', 'application', 'DmosFeatureFlags.java');
const TS_OUTPUT_PATH = path.join(__dirname, '..', 'ui', 'src', 'lib', 'feature-flags.ts');

function readManifest(): Manifest {
  const content = fs.readFileSync(MANIFEST_PATH, 'utf-8');
  return JSON.parse(content);
}

function generateJavaClass(flags: FeatureFlag[]): string {
  const constantDeclarations = flags.map(flag => {
    const constantName = toConstantName(flag.key);
    const envVar = flag.environmentVariable;
    const defaultValue = flag.defaultValue !== undefined ? flag.defaultValue : flag.defaultDevelopmentValue;
    const prodDefault = flag.productionDefault !== undefined ? flag.productionDefault : flag.defaultProductionValue;
    
    return `    /**
     * ${flag.description}
     * Env-var fallback: {@code ${envVar}} (default {@code ${prodDefault !== undefined ? prodDefault : defaultValue}}${prodDefault !== undefined ? ' in production' : ''}).
     */
    public static final String ${constantName} = "${flag.key}";`;
  }).join('\n\n');

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

function generateTypeScriptFile(flags: FeatureFlag[]): string {
  const uiFlags = flags.filter(f => {
    const scope = Array.isArray(f.scope) ? f.scope : [f.scope];
    return scope.includes('ui') || scope.includes('both');
  });

  const constantDeclarations = uiFlags.map(flag => {
    const constantName = toConstantName(flag.key);
    return `  ${constantName}: '${flag.key}'`;
  }).join(',\n');

  const flagValues = uiFlags.map(flag => {
    const constantName = toConstantName(flag.key);
    const envVar = flag.environmentVariable;
    const defaultValue = flag.defaultValue !== undefined ? flag.defaultValue : flag.defaultDevelopmentValue;
    return `  [FEATURE_FLAGS.${constantName}]: getFlagValue('${envVar}', ${defaultValue}),`;
  }).join('\n');

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

function toConstantName(key: string): string {
  return key
    .replace(/^dmos\./, '')
    .replace(/\./g, '_')
    .toUpperCase()
    .replace(/-/g, '_');
}

function writeIfChanged(filePath: string, content: string): boolean {
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
    process.exit(0);
  } else {
    console.log('Feature flags are up to date.');
    process.exit(0);
  }
}

main();
