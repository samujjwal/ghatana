#!/usr/bin/env node
/**
 * @fileoverview Dependency Convergence Analysis Tool
 * Identifies version misalignments across the monorepo
 */

'use strict';

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Target versions for convergence
const TARGET_VERSIONS = {
  react: '^19.2.4',
  'react-dom': '^19.2.4',
  typescript: '^5.9.3',
  jotai: '^2.17.0',
  zod: '^4.3.6',
  '@tanstack/react-query': '^5.90.20',
  tailwindcss: '^4.1.18',
  vite: '^7.3.1',
  vitest: '^4.0.18',
  eslint: '^9.39.2',
  prettier: '^3.8.1',
};

const ALLOWED_RANGES = {
  react: ['^19.0.0', '^19.1.0', '^19.2.0', '^19.2.4'],
  'react-dom': ['^19.0.0', '^19.1.0', '^19.2.0', '^19.2.4'],
  typescript: ['^5.7.0', '^5.8.0', '^5.9.0', '^5.9.3'],
  jotai: ['^2.15.0', '^2.16.0', '^2.17.0'],
  zod: ['^4.0.0', '^4.1.0', '^4.2.0', '^4.3.0', '^4.3.6'],
  '@tanstack/react-query': ['^5.80.0', '^5.90.0', '^5.90.20'],
  tailwindcss: ['^4.0.0', '^4.1.0', '^4.1.18'],
  vite: ['^6.0.0', '^7.0.0', '^7.3.0', '^7.3.1'],
  vitest: ['^3.0.0', '^4.0.0', '^4.0.18'],
  eslint: ['^9.0.0', '^9.30.0', '^9.39.0', '^9.39.2'],
  prettier: ['^3.6.0', '^3.7.0', '^3.8.0', '^3.8.1'],
};

function findPackageJsonFiles() {
  const files = [];
  const workspaceRoot = process.cwd();

  const workspaceYaml = fs.readFileSync(
    path.join(workspaceRoot, 'pnpm-workspace.yaml'),
    'utf-8'
  );

  const patterns = workspaceYaml
    .split('\n')
    .filter(line => line.trim().startsWith('-'))
    .map(line => line.replace(/^\s*-\s*"?([^"]+)"?\s*$/, '$1'));

  for (const pattern of patterns) {
    if (pattern.includes('**')) {
      const baseDir = pattern.split('**')[0];
      walkDirectory(path.join(workspaceRoot, baseDir), files, 5);
    } else if (pattern.includes('*')) {
      const baseDir = pattern.split('*')[0];
      const fullPath = path.join(workspaceRoot, baseDir);
      if (fs.existsSync(fullPath)) {
        const entries = fs.readdirSync(fullPath, { withFileTypes: true });
        for (const entry of entries) {
          if (entry.isDirectory()) {
            const pkgPath = path.join(fullPath, entry.name, 'package.json');
            if (fs.existsSync(pkgPath)) {
              files.push(pkgPath);
            }
          }
        }
      }
    } else {
      const pkgPath = path.join(workspaceRoot, pattern, 'package.json');
      if (fs.existsSync(pkgPath)) {
        files.push(pkgPath);
      }
    }
  }

  return [...new Set(files)];
}

function walkDirectory(dir, files, maxDepth, currentDepth = 0) {
  if (currentDepth > maxDepth) return;

  try {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);

      if (entry.name === 'node_modules') continue;

      if (entry.isDirectory()) {
        walkDirectory(fullPath, files, maxDepth, currentDepth + 1);
      } else if (entry.name === 'package.json') {
        files.push(fullPath);
      }
    }
  } catch (error) {
    // Directory may not exist
  }
}

function analyzeDependencies(packageFiles) {
  const versions = {};
  const misalignments = [];

  for (const pkgPath of packageFiles) {
    try {
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
      const pkgName = pkg.name || path.basename(path.dirname(pkgPath));

      const allDeps = {
        ...pkg.dependencies,
        ...pkg.devDependencies,
        ...pkg.peerDependencies,
      };

      for (const [dep, version] of Object.entries(allDeps)) {
        if (!versions[dep]) {
          versions[dep] = {};
        }
        if (!versions[dep][version]) {
          versions[dep][version] = [];
        }
        versions[dep][version].push({ pkg: pkgName, path: pkgPath });
      }
    } catch (error) {
      console.error(`Error parsing ${pkgPath}:`, error.message);
    }
  }

  // Identify misalignments
  for (const [dep, versionMap] of Object.entries(versions)) {
    if (Object.keys(versionMap).length > 1 && TARGET_VERSIONS[dep]) {
      const target = TARGET_VERSIONS[dep];
      const versionsList = Object.keys(versionMap);

      // Check if any version is within allowed range
      const hasValidVersion = versionsList.some(v =>
        ALLOWED_RANGES[dep]?.includes(v)
      );

      if (!hasValidVersion || versionsList.length > 3) {
        misalignments.push({
          dependency: dep,
          target: target,
          versions: versionMap,
          severity: versionsList.length > 5 ? 'high' : 'medium',
        });
      }
    }
  }

  return { versions, misalignments };
}

function printReport(analysis) {
  console.log('\n📊 DEPENDENCY CONVERGENCE REPORT\n');
  console.log('=' .repeat(70));

  if (analysis.misalignments.length === 0) {
    console.log('\n✅ All key dependencies are aligned!\n');
    return;
  }

  console.log(`\n🚨 Found ${analysis.misalignments.length} misaligned dependencies\n`);

  for (const mis of analysis.misalignments.sort((a, b) =>
    Object.keys(b.versions).length - Object.keys(a.versions).length
  )) {
    const versionCount = Object.keys(mis.versions).length;
    const icon = mis.severity === 'high' ? '🔴' : '🟡';

    console.log(`${icon} ${mis.dependency}`);
    console.log(`   Target: ${mis.target}`);
    console.log(`   Versions found: ${versionCount}`);

    for (const [ver, packages] of Object.entries(mis.versions)) {
      console.log(`     - ${ver}: ${packages.length} packages`);
      if (packages.length <= 3) {
        packages.forEach(p => console.log(`       → ${p.pkg}`));
      }
    }
    console.log('');
  }

  console.log('='.repeat(70));
  console.log('\n📋 RECOMMENDATION:');
  console.log('   Run pnpm update with --recursive to converge versions');
  console.log('   Or use pnpm overrides in root package.json\n');
}

function generateFixScript(analysis) {
  const fixes = [];

  for (const mis of analysis.misalignments) {
    const target = mis.target;

    for (const [version, packages] of Object.entries(mis.versions)) {
      if (version !== target) {
        for (const { path: pkgPath, pkg } of packages) {
          fixes.push({
            package: pkg,
            path: pkgPath,
            dependency: mis.dependency,
            from: version,
            to: target,
          });
        }
      }
    }
  }

  return fixes;
}

function main() {
  console.log('🔍 Analyzing dependency convergence...\n');

  const packageFiles = findPackageJsonFiles();
  console.log(`Found ${packageFiles.length} package.json files\n`);

  const analysis = analyzeDependencies(packageFiles);
  printReport(analysis);

  const fixes = generateFixScript(analysis);

  if (fixes.length > 0) {
    console.log(`\n📦 Generated ${fixes.length} fix recommendations`);
    console.log('\nTop 10 fixes needed:');
    fixes.slice(0, 10).forEach((fix, i) => {
      console.log(`  ${i + 1}. ${fix.package}: ${fix.dependency} ${fix.from} → ${fix.to}`);
    });
  }

  // Save report
  const reportPath = path.join(process.cwd(), 'dependency-convergence-report.json');
  fs.writeFileSync(reportPath, JSON.stringify({
    timestamp: new Date().toISOString(),
    packagesAnalyzed: packageFiles.length,
    misalignments: analysis.misalignments,
    fixes: fixes,
  }, null, 2));

  console.log(`\n📄 Full report saved to: ${reportPath}\n`);
}

if (require.main === module) {
  main();
}

module.exports = { analyzeDependencies, generateFixScript };
