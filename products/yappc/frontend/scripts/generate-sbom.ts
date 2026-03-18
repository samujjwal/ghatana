#!/usr/bin/env node
/**
 * SBOM Generator for Ghatana Monorepo
 *
 * Generates Software Bill of Materials for compliance and security auditing.
 * Supports CycloneDX and SPDX formats.
 *
 * @doc.type tooling
 * @doc.purpose Supply chain security and license compliance
 * @doc.layer infrastructure
 */

import { execSync } from 'child_process';
import { writeFileSync, mkdirSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// ============================================================================
// Configuration
// ============================================================================

const ALLOWED_LICENSES = [
  'MIT',
  'Apache-2.0',
  'BSD-2-Clause',
  'BSD-3-Clause',
  'ISC',
  '0BSD',
  'Python-2.0',
];

const FORBIDDEN_LICENSES = [
  'GPL-2.0',
  'GPL-3.0',
  'AGPL-3.0',
  'LGPL-2.1',
  'LGPL-3.0',
  'SSPL-1.0',
  'EPL-1.0',
  'MPL-2.0',
];

const CRITICAL_PACKAGES = [
  'react',
  'react-dom',
  '@types/react',
  'typescript',
  'vite',
  'tailwindcss',
];

// ============================================================================
// Types
// ============================================================================

interface PackageInfo {
  name: string;
  version: string;
  license: string;
  licenses: string[];
  repository?: string;
  publisher?: string;
  email?: string;
  url?: string;
  path: string;
  dependencyLevel: 'direct' | 'transitive';
  usedBy: string[];
}

interface SbomMetadata {
  timestamp: string;
  tool: string;
  toolVersion: string;
  projectName: string;
  projectVersion: string;
  supplier: string;
}

interface CycloneDxSbom {
  bomFormat: 'CycloneDX';
  specVersion: string;
  serialNumber: string;
  version: number;
  metadata: {
    timestamp: string;
    tools: Array<{ vendor: string; name: string; version: string }>;
    component: {
      type: string;
      name: string;
      version: string;
      supplier: { name: string };
    };
  };
  components: Array<{
    type: string;
    name: string;
    version: string;
    purl: string;
    licenses: Array<{ license: { id?: string; name?: string } }>;
    supplier?: { name: string };
    description?: string;
  }>;
  dependencies: Array<{
    ref: string;
    dependsOn: string[];
  }>;
}

// ============================================================================
// License Detection
// ============================================================================

function normalizeLicense(license: string | string[] | undefined): string {
  if (!license) return 'Unknown';
  
  const licenseStr = Array.isArray(license) ? license.join('; ') : String(license);
  
  const licenseMap: Record<string, string> = {
    'MIT': 'MIT',
    'Apache-2.0': 'Apache-2.0',
    'Apache License 2.0': 'Apache-2.0',
    'BSD-2-Clause': 'BSD-2-Clause',
    'BSD-3-Clause': 'BSD-3-Clause',
    'ISC': 'ISC',
    '0BSD': '0BSD',
    'Python-2.0': 'Python-2.0',
    'GPL-2.0': 'GPL-2.0',
    'GPL-3.0': 'GPL-3.0',
    'AGPL-3.0': 'AGPL-3.0',
    'LGPL-2.1': 'LGPL-2.1',
    'LGPL-3.0': 'LGPL-3.0',
    'UNLICENSED': 'UNLICENSED',
    'SEE LICENSE IN LICENSE': 'Unknown',
  };
  
  return licenseMap[licenseStr] || licenseStr;
}

function checkLicenseCompliance(licenses: string[]): {
  status: 'approved' | 'warning' | 'forbidden';
  issues: string[];
} {
  const issues: string[] = [];
  let hasForbidden = false;
  let hasUnknown = false;
  
  for (const license of licenses) {
    const normalized = normalizeLicense(license);
    
    if (FORBIDDEN_LICENSES.includes(normalized)) {
      issues.push(`Forbidden license detected: ${normalized}`);
      hasForbidden = true;
    } else if (!ALLOWED_LICENSES.includes(normalized)) {
      issues.push(`Unreviewed license: ${normalized}`);
      hasUnknown = true;
    }
  }
  
  if (hasForbidden) return { status: 'forbidden', issues };
  if (hasUnknown) return { status: 'warning', issues };
  return { status: 'approved', issues: [] };
}

// ============================================================================
// Package Discovery
// ============================================================================

function discoverPackages(workspaceRoot: string): PackageInfo[] {
  const packages: PackageInfo[] = [];
  const discovered = new Set<string>();
  
  try {
    // Get pnpm list output
    const output = execSync('pnpm list --json --depth=Infinity', {
      cwd: workspaceRoot,
      encoding: 'utf-8',
      maxBuffer: 50 * 1024 * 1024, // 50MB buffer
    });
    
    const parsed = JSON.parse(output);
    const rootPkg = Array.isArray(parsed) ? parsed[0] : parsed;
    
    function traverseDeps(
      deps: Record<string, any>,
      level: 'direct' | 'transitive',
      parentName: string
    ) {
      if (!deps) return;
      
      for (const [name, info] of Object.entries(deps)) {
        const key = `${name}@${info.version}`;
        
        if (!discovered.has(key)) {
          discovered.add(key);
          
          const license = normalizeLicense(info.license);
          const licenses = info.licenses || [license];
          
          packages.push({
            name,
            version: info.version,
            license,
            licenses,
            repository: info.repository?.url,
            publisher: info.author?.name,
            email: info.author?.email,
            url: info.homepage,
            path: info.path || '',
            dependencyLevel: level,
            usedBy: [parentName],
          });
        } else {
          // Add usage reference
          const pkg = packages.find(p => p.name === name && p.version === info.version);
          if (pkg && !pkg.usedBy.includes(parentName)) {
            pkg.usedBy.push(parentName);
          }
        }
        
        // Recurse into transitive dependencies
        if (info.dependencies) {
          traverseDeps(info.dependencies, 'transitive', name);
        }
      }
    }
    
    if (rootPkg.dependencies) {
      traverseDeps(rootPkg.dependencies, 'direct', rootPkg.name);
    }
    if (rootPkg.devDependencies) {
      traverseDeps(rootPkg.devDependencies, 'direct', rootPkg.name);
    }
    
  } catch (error) {
    console.warn('Warning: Could not discover all packages:', error.message);
  }
  
  return packages;
}

// ============================================================================
// SBOM Generation
// ============================================================================

function generateCycloneDX(
  packages: PackageInfo[],
  metadata: SbomMetadata
): CycloneDxSbom {
  const components = packages.map(pkg => {
    const compliance = checkLicenseCompliance(pkg.licenses);
    
    return {
      type: 'library',
      name: pkg.name,
      version: pkg.version,
      purl: `pkg:npm/${pkg.name}@${pkg.version}`,
      licenses: pkg.licenses.map(lic => ({
        license: { id: normalizeLicense(lic) },
      })),
      supplier: pkg.publisher ? { name: pkg.publisher } : undefined,
      description: `License compliance: ${compliance.status}`,
    };
  });
  
  const dependencies = packages.map(pkg => ({
    ref: `pkg:npm/${pkg.name}@${pkg.version}`,
    dependsOn: pkg.usedBy.map(parent => `pkg:npm/${parent}`),
  }));
  
  return {
    bomFormat: 'CycloneDX',
    specVersion: '1.5',
    serialNumber: `urn:uuid:${crypto.randomUUID()}`,
    version: 1,
    metadata: {
      timestamp: metadata.timestamp,
      tools: [{
        vendor: 'Ghatana',
        name: metadata.tool,
        version: metadata.toolVersion,
      }],
      component: {
        type: 'application',
        name: metadata.projectName,
        version: metadata.projectVersion,
        supplier: { name: metadata.supplier },
      },
    },
    components,
    dependencies,
  };
}

function generateLicenseReport(packages: PackageInfo[]): string {
  const report = {
    summary: {
      total: packages.length,
      direct: packages.filter(p => p.dependencyLevel === 'direct').length,
      transitive: packages.filter(p => p.dependencyLevel === 'transitive').length,
    },
    licenses: {} as Record<string, number>,
    compliance: {
      approved: 0,
      warning: 0,
      forbidden: 0,
    },
    packages: [] as Array<{
      name: string;
      version: string;
      license: string;
      compliance: string;
      issues?: string[];
    }>,
  };
  
  for (const pkg of packages) {
    // Count licenses
    const primaryLicense = pkg.license;
    report.licenses[primaryLicense] = (report.licenses[primaryLicense] || 0) + 1;
    
    // Check compliance
    const compliance = checkLicenseCompliance(pkg.licenses);
    report.compliance[compliance.status]++;
    
    report.packages.push({
      name: pkg.name,
      version: pkg.version,
      license: primaryLicense,
      compliance: compliance.status,
      issues: compliance.issues.length > 0 ? compliance.issues : undefined,
    });
  }
  
  return JSON.stringify(report, null, 2);
}

// ============================================================================
// Main
// ============================================================================

function main() {
  const workspaceRoot = process.cwd();
  const outputDir = join(workspaceRoot, 'sbom');
  
  console.log('🔍 Generating SBOM for Ghatana Monorepo...\n');
  
  // Create output directory
  if (!existsSync(outputDir)) {
    mkdirSync(outputDir, { recursive: true });
  }
  
  // Discover packages
  console.log('📦 Discovering packages...');
  const packages = discoverPackages(workspaceRoot);
  console.log(`   Found ${packages.length} packages\n`);
  
  // Generate metadata
  const metadata: SbomMetadata = {
    timestamp: new Date().toISOString(),
    tool: 'ghatana-sbom-generator',
    toolVersion: '1.0.0',
    projectName: 'ghatana-platform',
    projectVersion: process.env.npm_package_version || '1.0.0',
    supplier: 'Ghatana Inc.',
  };
  
  // Generate CycloneDX SBOM
  console.log('📄 Generating CycloneDX SBOM...');
  const cycloneDx = generateCycloneDX(packages, metadata);
  const cycloneDxPath = join(outputDir, 'sbom.cyclonedx.json');
  writeFileSync(cycloneDxPath, JSON.stringify(cycloneDx, null, 2));
  console.log(`   Written: ${cycloneDxPath}\n`);
  
  // Generate license report
  console.log('📊 Generating license compliance report...');
  const licenseReport = generateLicenseReport(packages);
  const licensePath = join(outputDir, 'license-report.json');
  writeFileSync(licensePath, licenseReport);
  console.log(`   Written: ${licensePath}\n`);
  
  // Summary
  const compliance = {
    approved: packages.filter(p => checkLicenseCompliance(p.licenses).status === 'approved').length,
    warning: packages.filter(p => checkLicenseCompliance(p.licenses).status === 'warning').length,
    forbidden: packages.filter(p => checkLicenseCompliance(p.licenses).status === 'forbidden').length,
  };
  
  console.log('✅ SBOM Generation Complete\n');
  console.log('Summary:');
  console.log(`  Total packages: ${packages.length}`);
  console.log(`  Direct dependencies: ${packages.filter(p => p.dependencyLevel === 'direct').length}`);
  console.log(`  Transitive dependencies: ${packages.filter(p => p.dependencyLevel === 'transitive').length}`);
  console.log(`  License compliance:`);
  console.log(`    ✅ Approved: ${compliance.approved}`);
  console.log(`    ⚠️  Warning: ${compliance.warning}`);
  console.log(`    ❌ Forbidden: ${compliance.forbidden}`);
  
  if (compliance.forbidden > 0) {
    console.log('\n⚠️  WARNING: Forbidden licenses detected!');
    process.exit(1);
  }
  
  if (compliance.warning > 0) {
    console.log('\n⚠️  WARNING: Unreviewed licenses detected. Manual review required.');
    process.exit(0);
  }
  
  console.log('\n✅ All licenses compliant');
}

main();
