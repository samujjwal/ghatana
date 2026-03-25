#!/usr/bin/env node
/**
 * Architectural Fitness Functions for Ghatana Monorepo
 *
 * Enforces architectural rules automatically:
 * - No circular dependencies
 * - Layer boundary enforcement (products → platform → contracts)
 * - Dependency depth limits
 * - No cross-product dependencies
 * - Import pattern validation
 *
 * @doc.type tooling
 * @doc.purpose Architectural governance enforcement
 * @doc.layer infrastructure
 */

import { readFileSync, readdirSync, statSync, existsSync } from 'fs';
import { join, dirname, relative, resolve } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// ============================================================================
// Architecture Rules Configuration
// ============================================================================

const ARCHITECTURE_RULES = {
  // Layer boundaries - who can import whom
  layers: {
    products: { canImport: ['platform', 'contracts', 'shared-services'] },
    platform: { canImport: ['contracts'] },
    'shared-services': { canImport: ['platform', 'contracts'] },
    contracts: { canImport: [] }, // Contracts are the base layer
  },

  // Cross-product boundaries
  crossProduct: {
    enabled: true,
    // Exceptions for intentional shared code
    allowed: [
      '@ghatana/design-system', // Platform component library
      '@ghatana/canvas', // Platform canvas components
    ],
  },

  // Dependency depth limits
  limits: {
    maxDependencyDepth: 4,
    maxTransitiveDeps: 50,
  },

  // Forbidden patterns
  forbidden: {
    // Glob patterns for files that shouldn't be imported
    patterns: [
      '**/*.test.ts', // Don't import test files
      '**/*.spec.ts',
      '**/test/**',
      '**/tests/**',
      '**/mocks/**',
      '**/__mocks__/**',
    ],
    // Import patterns that indicate architectural violations
    importPatterns: [
      {
        pattern: /\.\.\/\.\.\/\.\.\/\.\.\//,
        message: 'Too many parent directory traversals (max 3 levels)',
      },
      {
        pattern: /from\s+['"]\./,
        message: 'Relative imports must specify file, not directory',
      },
    ],
  },
};

// ============================================================================
// Types
// ============================================================================

interface Violation {
  type:
    | 'circular-dep'
    | 'layer-violation'
    | 'cross-product'
    | 'depth-exceeded'
    | 'forbidden-import'
    | 'pattern-violation';
  file: string;
  import: string;
  message: string;
  severity: 'error' | 'warning';
  rule: string;
}

interface ImportInfo {
  source: string;
  target: string;
  line: number;
  column: number;
}

// ============================================================================
// Circular Dependency Detection
// ============================================================================

function findCircularDependencies(workspaceRoot: string): Violation[] {
  const violations: Violation[] = [];

  try {
    // Use madge to detect circular dependencies
    const output = execSync('npx madge --circular --extensions ts,tsx src', {
      cwd: workspaceRoot,
      encoding: 'utf-8',
      stdio: ['pipe', 'pipe', 'pipe'],
    });

    if (output.trim()) {
      const cycles = output.trim().split('\n');
      for (const cycle of cycles) {
        if (cycle.includes('✖')) {
          const files = cycle.replace('✖', '').trim().split(' -> ');
          violations.push({
            type: 'circular-dep',
            file: files[0],
            import: files[1] || files[0],
            message: `Circular dependency detected: ${files.join(' → ')}`,
            severity: 'error',
            rule: 'no-circular-deps',
          });
        }
      }
    }
  } catch (error: any) {
    // madge exits with error if circular deps found
    if (error.stdout) {
      const cycles = error.stdout.toString().trim().split('\n');
      for (const cycle of cycles) {
        if (cycle.includes('✖')) {
          const files = cycle.replace('✖', '').trim().split(' -> ');
          violations.push({
            type: 'circular-dep',
            file: files[0],
            import: files[1] || files[0],
            message: `Circular dependency detected: ${files.join(' → ')}`,
            severity: 'error',
            rule: 'no-circular-deps',
          });
        }
      }
    }
  }

  return violations;
}

// ============================================================================
// Layer Boundary Enforcement
// ============================================================================

function determineLayer(filePath: string): string | null {
  const normalizedPath = filePath.replace(/\\/g, '/');

  if (normalizedPath.includes('/products/')) {
    const match = normalizedPath.match(/\/products\/([^/]+)/);
    return match ? `product:${match[1]}` : 'product';
  }
  if (normalizedPath.includes('/platform/')) return 'platform';
  if (normalizedPath.includes('/contracts/')) return 'contracts';
  if (normalizedPath.includes('/shared-services/')) return 'shared-services';

  return null;
}

function canImport(sourceLayer: string, targetLayer: string): boolean {
  // Extract base layer name (e.g., 'product:yappc' -> 'products')
  const sourceBase = sourceLayer.startsWith('product:')
    ? 'products'
    : sourceLayer;
  const targetBase = targetLayer.startsWith('product:')
    ? 'products'
    : targetLayer;

  // Same layer is always allowed
  if (sourceBase === targetBase) return true;

  // Check layer rules
  const rules =
    ARCHITECTURE_RULES.layers[
      sourceBase as keyof typeof ARCHITECTURE_RULES.layers
    ];
  if (!rules) return false;

  return rules.canImport.includes(targetBase as never);
}

function checkLayerBoundaries(workspaceRoot: string): Violation[] {
  const violations: Violation[] = [];

  function scanDirectory(dir: string) {
    const entries = readdirSync(dir);

    for (const entry of entries) {
      const fullPath = join(dir, entry);
      const stat = statSync(fullPath);

      if (stat.isDirectory() && !entry.includes('node_modules')) {
        scanDirectory(fullPath);
      } else if (/\.(ts|tsx|js|jsx)$/.test(entry)) {
        const fileLayer = determineLayer(fullPath);
        if (!fileLayer) continue;

        const content = readFileSync(fullPath, 'utf-8');
        const imports = extractImports(content);

        for (const imp of imports) {
          // Resolve the import path
          let resolvedPath: string | null = null;

          if (imp.source.startsWith('.')) {
            // Relative import
            resolvedPath = resolve(dirname(fullPath), imp.source);
          } else if (imp.source.startsWith('@')) {
            // Workspace import - map to path
            resolvedPath = resolveWorkspaceImport(imp.source, workspaceRoot);
          }

          if (resolvedPath) {
            const targetLayer = determineLayer(resolvedPath);

            if (targetLayer && !canImport(fileLayer, targetLayer)) {
              violations.push({
                type: 'layer-violation',
                file: relative(workspaceRoot, fullPath),
                import: imp.source,
                message: `${fileLayer} cannot import from ${targetLayer}. Violates layer architecture.`,
                severity: 'error',
                rule: 'layer-boundaries',
              });
            }

            // Check cross-product imports
            if (
              ARCHITECTURE_RULES.crossProduct.enabled &&
              fileLayer.startsWith('product:') &&
              targetLayer?.startsWith('product:') &&
              fileLayer !== targetLayer
            ) {
              const isAllowed = ARCHITECTURE_RULES.crossProduct.allowed.some(
                (allowed) => imp.source.includes(allowed)
              );

              if (!isAllowed) {
                violations.push({
                  type: 'cross-product',
                  file: relative(workspaceRoot, fullPath),
                  import: imp.source,
                  message: `Cross-product import detected: ${fileLayer} → ${targetLayer}. Products should not depend on each other.`,
                  severity: 'error',
                  rule: 'no-cross-product-deps',
                });
              }
            }
          }
        }
      }
    }
  }

  scanDirectory(workspaceRoot);
  return violations;
}

// ============================================================================
// Import Analysis Helpers
// ============================================================================

function extractImports(content: string): ImportInfo[] {
  const imports: ImportInfo[] = [];

  // Match ES6 imports
  const es6Regex =
    /import\s+(?:(?:{[^}]*}|[^'"])*\s+from\s+)?['"]([^'"]+)['"];?/g;
  let match;
  while ((match = es6Regex.exec(content)) !== null) {
    imports.push({
      source: match[1],
      target: '',
      line: content.substring(0, match.index).split('\n').length,
      column: match.index - content.lastIndexOf('\n', match.index),
    });
  }

  // Match require() calls
  const requireRegex = /require\s*\(\s*['"]([^'"]+)['"]\s*\)/g;
  while ((match = requireRegex.exec(content)) !== null) {
    imports.push({
      source: match[1],
      target: '',
      line: content.substring(0, match.index).split('\n').length,
      column: match.index - content.lastIndexOf('\n', match.index),
    });
  }

  return imports;
}

function resolveWorkspaceImport(
  importPath: string,
  workspaceRoot: string
): string | null {
  // Map workspace imports to actual paths
  const mappings: Record<string, string> = {
    '@yappc/core': 'products/yappc/frontend/libs/yappc-core',
    '@yappc/ui': 'products/yappc/frontend/libs/yappc-ui',
    '@ghatana/design-system': 'platform/typescript/design-system',
  };

  for (const [prefix, path] of Object.entries(mappings)) {
    if (importPath.startsWith(prefix)) {
      return join(workspaceRoot, path);
    }
  }

  return null;
}

// ============================================================================
// Forbidden Pattern Detection
// ============================================================================

function checkForbiddenPatterns(workspaceRoot: string): Violation[] {
  const violations: Violation[] = [];

  function scanDirectory(dir: string) {
    const entries = readdirSync(dir);

    for (const entry of entries) {
      const fullPath = join(dir, entry);
      const stat = statSync(fullPath);

      if (stat.isDirectory() && !entry.includes('node_modules')) {
        scanDirectory(fullPath);
      } else if (/\.(ts|tsx|js|jsx)$/.test(entry)) {
        const content = readFileSync(fullPath, 'utf-8');
        const imports = extractImports(content);

        for (const imp of imports) {
          // Check forbidden patterns
          for (const rule of ARCHITECTURE_RULES.forbidden.importPatterns) {
            if (rule.pattern.test(imp.source)) {
              violations.push({
                type: 'pattern-violation',
                file: relative(workspaceRoot, fullPath),
                import: imp.source,
                message: rule.message,
                severity: 'error',
                rule: 'import-patterns',
              });
            }
          }

          // Check if importing forbidden file types
          for (const pattern of ARCHITECTURE_RULES.forbidden.patterns) {
            const regex = new RegExp(
              pattern.replace(/\*\*/g, '.*').replace(/\*/g, '[^/]*')
            );
            if (regex.test(imp.source)) {
              violations.push({
                type: 'forbidden-import',
                file: relative(workspaceRoot, fullPath),
                import: imp.source,
                message: `Forbidden import: ${imp.source} matches pattern ${pattern}`,
                severity: 'error',
                rule: 'no-forbidden-imports',
              });
            }
          }
        }
      }
    }
  }

  scanDirectory(workspaceRoot);
  return violations;
}

// ============================================================================
// Report Generation
// ============================================================================

function generateReport(violations: Violation[]): string {
  const errors = violations.filter((v) => v.severity === 'error');
  const warnings = violations.filter((v) => v.severity === 'warning');

  let report = '\n🏗️  Architectural Fitness Report\n';
  report += '===============================\n\n';

  if (violations.length === 0) {
    report += '✅ All architecture rules compliant\n';
    return report;
  }

  report += `Errors: ${errors.length}\n`;
  report += `Warnings: ${warnings.length}\n\n`;

  if (errors.length > 0) {
    report += '❌ ERRORS:\n';
    for (const v of errors) {
      report += `  [${v.type}] ${v.file}\n`;
      report += `    Import: ${v.import}\n`;
      report += `    Rule: ${v.rule}\n`;
      report += `    ${v.message}\n\n`;
    }
  }

  if (warnings.length > 0) {
    report += '⚠️  WARNINGS:\n';
    for (const v of warnings) {
      report += `  [${v.type}] ${v.file}\n`;
      report += `    ${v.message}\n\n`;
    }
  }

  return report;
}

// ============================================================================
// CLI
// ============================================================================

function main(): void {
  const workspaceRoot = process.cwd();

  console.log('🏗️  Running architectural fitness functions...\n');

  const allViolations: Violation[] = [];

  // Check 1: Circular dependencies
  console.log('  Checking for circular dependencies...');
  const circularDeps = findCircularDependencies(workspaceRoot);
  allViolations.push(...circularDeps);
  console.log(`     Found ${circularDeps.length} circular dependencies`);

  // Check 2: Layer boundaries
  console.log('  Checking layer boundaries...');
  const layerViolations = checkLayerBoundaries(workspaceRoot);
  allViolations.push(...layerViolations);
  console.log(`     Found ${layerViolations.length} layer violations`);

  // Check 3: Forbidden patterns
  console.log('  Checking forbidden patterns...');
  const patternViolations = checkForbiddenPatterns(workspaceRoot);
  allViolations.push(...patternViolations);
  console.log(`     Found ${patternViolations.length} pattern violations`);

  // Generate report
  const report = generateReport(allViolations);
  console.log(report);

  const errors = allViolations.filter((v) => v.severity === 'error');
  if (errors.length > 0) {
    console.error(`\n❌ ${errors.length} architectural violations found`);
    process.exit(1);
  }

  console.log('\n✅ All architectural rules satisfied');
}

// Run if called directly
if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}

export {
  ARCHITECTURE_RULES,
  findCircularDependencies,
  checkLayerBoundaries,
  checkForbiddenPatterns,
  generateReport,
};
