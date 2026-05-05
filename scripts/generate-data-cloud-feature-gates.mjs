#!/usr/bin/env node
/**
 * Generate Data Cloud feature-gate artifacts from CapabilitySchemaGenerator.java.
 *
 * This keeps UI gate identifiers and defaults aligned with the backend capability
 * schema generator and supports CI drift detection.
 *
 * Usage:
 *   node scripts/generate-data-cloud-feature-gates.mjs
 *   node scripts/generate-data-cloud-feature-gates.mjs --check
 *
 * Exit codes:
 *   0 - success or no drift in --check mode
 *   1 - drift detected (in --check mode) or parse/write failure
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const checkOnly = process.argv.includes('--check');

const generatorPath = join(
  repoRoot,
  'products',
  'data-cloud',
  'launcher',
  'src',
  'main',
  'java',
  'com',
  'ghatana',
  'datacloud',
  'launcher',
  'http',
  'handlers',
  'CapabilitySchemaGenerator.java',
);

const outputPath = join(
  repoRoot,
  'products',
  'data-cloud',
  'ui',
  'src',
  'lib',
  'generated',
  'feature-gates.generated.ts',
);

const routeGatesOutputPath = join(
  repoRoot,
  'products',
  'data-cloud',
  'ui',
  'src',
  'lib',
  'generated',
  'route-gates.generated.ts',
);

if (!existsSync(generatorPath)) {
  console.error(`Missing source file: ${generatorPath}`);
  process.exit(1);
}

const javaSource = readFileSync(generatorPath, 'utf8');

function collectConstructorCalls(source, constructorName) {
  const token = `new ${constructorName}(`;
  const calls = [];
  let cursor = 0;

  while (cursor < source.length) {
    const start = source.indexOf(token, cursor);
    if (start < 0) {
      break;
    }

    const argsStart = start + token.length;
    let depth = 1;
    let i = argsStart;
    let inString = false;
    let escaping = false;

    while (i < source.length && depth > 0) {
      const ch = source[i];

      if (inString) {
        if (escaping) {
          escaping = false;
        } else if (ch === '\\') {
          escaping = true;
        } else if (ch === '"') {
          inString = false;
        }
      } else if (ch === '"') {
        inString = true;
      } else if (ch === '(') {
        depth += 1;
      } else if (ch === ')') {
        depth -= 1;
      }

      i += 1;
    }

    if (depth !== 0) {
      throw new Error(`Unbalanced constructor invocation while parsing ${constructorName}`);
    }

    calls.push(source.slice(argsStart, i - 1));
    cursor = i;
  }

  return calls;
}

function splitTopLevelArgs(argumentBlock) {
  const args = [];
  let inString = false;
  let escaping = false;
  let depth = 0;
  let current = '';

  for (const ch of argumentBlock) {
    if (inString) {
      current += ch;
      if (escaping) {
        escaping = false;
      } else if (ch === '\\') {
        escaping = true;
      } else if (ch === '"') {
        inString = false;
      }
      continue;
    }

    if (ch === '"') {
      inString = true;
      current += ch;
      continue;
    }

    if (ch === '(') {
      depth += 1;
      current += ch;
      continue;
    }

    if (ch === ')') {
      depth -= 1;
      current += ch;
      continue;
    }

    if (ch === ',' && depth === 0) {
      args.push(current.trim());
      current = '';
      continue;
    }

    current += ch;
  }

  if (current.trim().length > 0) {
    args.push(current.trim());
  }

  return args;
}

function parseJavaLiteral(rawValue) {
  const value = rawValue.trim();

  if (value === 'null') {
    return null;
  }

  if (value === 'true') {
    return true;
  }

  if (value === 'false') {
    return false;
  }

  if (value.startsWith('"') && value.endsWith('"')) {
    return value.slice(1, -1).replace(/\\"/g, '"').replace(/\\n/g, '\n');
  }

  if (value.startsWith('List.of(') && value.endsWith(')')) {
    const inner = value.slice('List.of('.length, -1).trim();
    if (inner.length === 0) {
      return [];
    }
    return splitTopLevelArgs(inner)
      .map((entry) => parseJavaLiteral(entry))
      .filter((entry) => typeof entry === 'string');
  }

  return value;
}

function parseCapabilitiesFromJava(source) {
  const calls = collectConstructorCalls(source, 'Capability');
  return calls.map((call) => {
    const args = splitTopLevelArgs(call);
    if (args.length < 9) {
      throw new Error(`Expected 9 arguments for Capability(...), got ${args.length}`);
    }

    return {
      id: parseJavaLiteral(args[0]),
      products: parseJavaLiteral(args[4]),
      uiGate: parseJavaLiteral(args[7]),
    };
  });
}

function parseExplicitFeatureGatesFromJava(source) {
  const calls = collectConstructorCalls(source, 'FeatureGate');
  return calls.map((call) => {
    const args = splitTopLevelArgs(call);
    if (args.length < 6) {
      throw new Error(`Expected 6 arguments for FeatureGate(...), got ${args.length}`);
    }

    return {
      id: parseJavaLiteral(args[0]),
      capabilityDependency: parseJavaLiteral(args[3]),
      defaultValue: parseJavaLiteral(args[4]),
      products: parseJavaLiteral(args[5]),
      source: 'explicit',
    };
  });
}

const capabilityEntries = parseCapabilitiesFromJava(javaSource)
  .filter((capability) => typeof capability.id === 'string' && Array.isArray(capability.products));

const derivedFeatureGates = capabilityEntries
  .filter((capability) => typeof capability.uiGate === 'string' && capability.uiGate.length > 0)
  .map((capability) => ({
    id: capability.uiGate,
    capabilityDependency: capability.id,
    defaultValue: true,
    products: capability.products,
    source: 'capability',
  }));

const explicitFeatureGates = parseExplicitFeatureGatesFromJava(javaSource)
  .filter((gate) => typeof gate.id === 'string' && Array.isArray(gate.products));

const byGateId = new Map();
for (const gate of derivedFeatureGates) {
  byGateId.set(gate.id, gate);
}
for (const gate of explicitFeatureGates) {
  byGateId.set(gate.id, gate);
}

const generatedGates = [...byGateId.values()].sort((left, right) => left.id.localeCompare(right.id));

/**
 * Route-to-gate mapping for the Data Cloud UI router.
 *
 * Each entry binds a route pattern (used by TanStack Router or React Router)
 * to the feature-gate ID that must be enabled for the route to be accessible.
 * A null gateId means the route is always available.
 *
 * Routes are derived from the capability schema: capability → uiGate → route.
 * Explicit (non-capability-driven) routes are listed separately at the bottom.
 */
const ROUTE_GATE_ENTRIES = [
  // Capability-driven routes
  { route: '/explore',           gateId: 'enableUnifiedDataExplorer',    label: 'Unified Data Explorer' },
  { route: '/explore/*',         gateId: 'enableUnifiedDataExplorer',    label: 'Unified Data Explorer (sub-pages)' },
  { route: '/workflows',         gateId: 'enableSmartWorkflowBuilder',   label: 'Smart Workflow Builder' },
  { route: '/workflows/*',       gateId: 'enableSmartWorkflowBuilder',   label: 'Smart Workflow Builder (sub-pages)' },
  { route: '/insights',          gateId: 'enableAmbientIntelligence',    label: 'Ambient Intelligence' },
  { route: '/insights/*',        gateId: 'enableAmbientIntelligence',    label: 'Ambient Intelligence (sub-pages)' },
  { route: '/fabric',            gateId: 'enableDataFabricPreview',      label: 'Data Fabric Preview' },
  { route: '/fabric/*',          gateId: 'enableDataFabricPreview',      label: 'Data Fabric Preview (sub-pages)' },
  // Explicit feature-gate routes
  { route: '/hub',               gateId: 'enableIntelligentHub',         label: 'Intelligent Hub' },
  { route: '/hub/*',             gateId: 'enableIntelligentHub',         label: 'Intelligent Hub (sub-pages)' },
  { route: '/legacy',            gateId: 'legacyPagesEnabled',           label: 'Legacy Pages' },
  { route: '/legacy/*',          gateId: 'legacyPagesEnabled',           label: 'Legacy Pages (sub-pages)' },
  // Always-available routes (no gate)
  { route: '/',                  gateId: null,                           label: 'Dashboard' },
  { route: '/settings',          gateId: null,                           label: 'Settings' },
  { route: '/settings/*',        gateId: null,                           label: 'Settings (sub-pages)' },
];

const routeGatesSource = [
  '/**',
  ' * Auto-generated by scripts/generate-data-cloud-feature-gates.mjs.',
  ' * DO NOT EDIT MANUALLY.',
  ' */',
  '',
  'import type { GeneratedFeatureGateId } from \"./feature-gates.generated\";',
  '',
  'export interface GeneratedRouteGateEntry {',
  '  /** Route pattern (e.g., "/explore" or "/explore/*"). */',
  '  readonly route: string;',
  '  /** Feature gate ID that must be enabled to access this route, or null for unrestricted routes. */',
  '  readonly gateId: GeneratedFeatureGateId | null;',
  '  /** Human-readable label for the route. */',
  '  readonly label: string;',
  '}',
  '',
  'export const GENERATED_ROUTE_GATE_MAP = [',
  ...ROUTE_GATE_ENTRIES.map((entry) => {
    const gateId = entry.gateId ? `\"${entry.gateId}\"` : 'null';
    return `  { route: \"${entry.route}\", gateId: ${gateId}, label: \"${entry.label}\" },`;
  }),
  '] as const satisfies readonly GeneratedRouteGateEntry[];',
  '',
  '/** Look up the gate ID for a specific route pattern. Returns undefined when not found. */',
  'export function getRouteGateId(route: string): GeneratedFeatureGateId | null | undefined {',
  '  return GENERATED_ROUTE_GATE_MAP.find((entry) => entry.route === route)?.gateId;',
  '}',
  '',
].join('\n');

const generatedSource = [
  '/**',
  ' * Auto-generated by scripts/generate-data-cloud-feature-gates.mjs.',
  ' * DO NOT EDIT MANUALLY.',
  ' */',
  '',
  'export interface GeneratedFeatureGateConfig {',
  '  id: string;',
  '  capabilityDependency: string | null;',
  '  defaultValue: boolean;',
  '  products: readonly string[];',
  '  source: \"capability\" | \"explicit\";',
  '}',
  '',
  'export const GENERATED_FEATURE_GATE_CONFIG = [',
  ...generatedGates.map((gate) => {
    const products = gate.products.map((p) => `\"${p}\"`).join(', ');
    const dependency = gate.capabilityDependency ? `\"${gate.capabilityDependency}\"` : 'null';
    return `  { id: \"${gate.id}\", capabilityDependency: ${dependency}, defaultValue: ${gate.defaultValue}, products: [${products}], source: \"${gate.source}\" },`;
  }),
  '] as const satisfies readonly GeneratedFeatureGateConfig[];',
  '',
  'export type GeneratedFeatureGateId = (typeof GENERATED_FEATURE_GATE_CONFIG)[number][\"id\"];',
  '',
  'export const GENERATED_FEATURE_GATE_IDS = GENERATED_FEATURE_GATE_CONFIG.map(',
  '  (gate) => gate.id,',
  ') as ReadonlyArray<GeneratedFeatureGateId>;',
  '',
].join('\n');

if (checkOnly) {
  if (!existsSync(outputPath)) {
    console.error(`Drift detected: generated file is missing: ${outputPath}`);
    process.exit(1);
  }

  const existing = readFileSync(outputPath, 'utf8');
  if (existing !== generatedSource) {
    console.error('Drift detected in Data Cloud generated feature-gates artifact.');
    console.error('Run: node scripts/generate-data-cloud-feature-gates.mjs');
    process.exit(1);
  }

  console.log(`Generated feature gates are aligned (${generatedGates.length} gate(s)).`);

  // Also check route-gates drift
  const existingRouteGates = readFileSync(routeGatesOutputPath, 'utf8');
  if (existingRouteGates !== routeGatesSource) {
    console.error('Drift detected in Data Cloud generated route-gates artifact.');
    console.error('Run: node scripts/generate-data-cloud-feature-gates.mjs');
    process.exit(1);
  }

  console.log(`Generated route gates are aligned (${ROUTE_GATE_ENTRIES.length} route(s)).`);
  process.exit(0);
}

writeFileSync(outputPath, generatedSource, 'utf8');
console.log(`Wrote ${generatedGates.length} generated feature gate(s) to ${outputPath}`);

writeFileSync(routeGatesOutputPath, routeGatesSource, 'utf8');
console.log(`Wrote ${ROUTE_GATE_ENTRIES.length} generated route gate(s) to ${routeGatesOutputPath}`);
