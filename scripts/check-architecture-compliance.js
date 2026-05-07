#!/usr/bin/env node
/**
 * @fileoverview Architecture compliance check script for CI
 * Validates monorepo structure, dependencies, and naming conventions
 */

'use strict';

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const EXIT_CODES = {
  SUCCESS: 0,
  VIOLATIONS_FOUND: 1,
  ERROR: 2,
};

const POLICY = require('../eslint-rules/dependency-policy.json');

class ArchitectureChecker {
  constructor() {
    this.violations = [];
    this.warnings = [];
    this.allowlist = this.loadAllowlist();
    this.allowlistHits = new Set();
    this.stats = {
      packagesChecked: 0,
      importsChecked: 0,
      violations: 0,
      warnings: 0,
    };
  }

  addViolation(type, message, file, details = {}) {
    this.violations.push({ type, message, file, details });
    this.stats.violations++;
    console.error(`❌ [${type}] ${message}`);
    if (file) console.error(`   File: ${file}`);
  }

  addPolicyViolation(type, message, file, details = {}) {
    const exception = this.findAllowlistException(type, file, details);
    if (exception) {
      this.allowlistHits.add(exception.__key);
      this.addWarning(
        `ALLOWLISTED_${type}`,
        `${message} (temporary exception: ${exception.reason}; owner: ${exception.owner}; remove by: ${exception.targetRemovalDate})`,
        file
      );
      return;
    }

    this.addViolation(type, message, file, details);
  }

  addWarning(type, message, file) {
    this.warnings.push({ type, message, file });
    this.stats.warnings++;
    console.warn(`⚠️  [${type}] ${message}`);
  }

  async run() {
    console.log('🔍 Ghatana Architecture Compliance Check\n');

    const startTime = Date.now();

    try {
      await this.checkPackageNaming();
      await this.checkDependencyPolicy();
      await this.checkGradleDependencyDirection();
      await this.checkBannedLibraries();
      await this.checkDeprecatedPackages();
      await this.checkDuplicatePackageNames();
      await this.checkLicensePolicy();
      this.checkAllowlistFreshness();

      const duration = ((Date.now() - startTime) / 1000).toFixed(2);

      this.printSummary(duration);

      return this.violations.length === 0 ? EXIT_CODES.SUCCESS : EXIT_CODES.VIOLATIONS_FOUND;
    } catch (error) {
      console.error('💥 Fatal error during architecture check:', error.message);
      return EXIT_CODES.ERROR;
    }
  }

  async checkPackageNaming() {
    console.log('📦 Checking package naming conventions...');

    const packageFiles = this.findPackageJsonFiles();

    for (const pkgPath of packageFiles) {
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
      const pkgName = pkg.name;

      if (!pkgName) {
        this.addViolation('MISSING_NAME', 'package.json missing name field', pkgPath);
        continue;
      }

      // Check deprecated naming patterns
      if (pkgName.startsWith('@ghatana/yappc-')) {
        const newName = pkgName.replace('@ghatana/yappc-', '@yappc/');
        this.addPolicyViolation(
          'DEPRECATED_NAMING',
          `Package uses deprecated naming: ${pkgName} → ${newName}`,
          pkgPath,
          { packageName: pkgName, replacement: newName }
        );
      }

      // Check scope alignment with location
      const locationScope = this.getScopeFromLocation(pkgPath);
      const nameScope = pkgName.startsWith('@') ? pkgName.split('/')[0] : null;

      if (locationScope && nameScope && locationScope !== nameScope) {
        if (nameScope !== '@ghatana' && pkgName !== '@ghatana/event-cloud') {
          this.addViolation(
            'SCOPE_MISMATCH',
            `Package scope ${nameScope} doesn't match location ${locationScope}`,
            pkgPath,
            { expected: locationScope, actual: nameScope }
          );
        }
      }

      this.stats.packagesChecked++;
    }

    console.log(`   Checked ${this.stats.packagesChecked} packages\n`);
  }

  async checkDependencyPolicy() {
    console.log('🔗 Checking dependency policy...');

    const packageFiles = this.findPackageJsonFiles();

    for (const pkgPath of packageFiles) {
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
      const product = this.getProductFromPath(pkgPath);

      if (!product) continue;

      const policy = POLICY.productBoundaries[product];
      if (!policy) continue;

      // Check all dependency types
      const allDeps = {
        ...pkg.dependencies,
        ...pkg.devDependencies,
        ...pkg.peerDependencies,
      };

      for (const dep of Object.keys(allDeps)) {
        if (!dep.startsWith('@')) continue;

        const depProduct = this.getProductFromPackage(dep);
        if (!depProduct || depProduct === product) continue;

        if (policy.disallowedImports.includes(depProduct)) {
          this.addViolation(
            'CROSS_PRODUCT_DEPENDENCY',
            `Cross-product dependency detected: ${dep}`,
            pkgPath,
            { from: product, to: depProduct }
          );
        }

        this.stats.importsChecked++;
      }
    }

    console.log(`   Checked ${this.stats.importsChecked} imports\n`);
  }

  async checkBannedLibraries() {
    console.log('🚫 Checking for banned libraries...');

    const banned = Object.keys(POLICY.bannedLibraries);
    const packageFiles = this.findPackageJsonFiles();

    for (const pkgPath of packageFiles) {
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));

      const allDeps = {
        ...pkg.dependencies,
        ...pkg.devDependencies,
      };

      for (const dep of Object.keys(allDeps)) {
        const baseDep = dep.split('/')[0];
        if (banned.includes(baseDep)) {
          const policy = POLICY.bannedLibraries[baseDep];
          this.addPolicyViolation(
            'BANNED_LIBRARY',
            `Banned library ${dep}: ${policy.reason} (use ${policy.alternative})`,
            pkgPath,
            { banned: baseDep, alternative: policy.alternative }
          );
        }
      }
    }

    console.log(`   Checked for ${banned.length} banned libraries\n`);
  }

  loadAllowlist() {
    const allowlistPath = path.join(process.cwd(), 'config/architecture-compliance-allowlist.json');
    if (!fs.existsSync(allowlistPath)) {
      return [];
    }

    const parsed = JSON.parse(fs.readFileSync(allowlistPath, 'utf-8'));
    return (parsed.exceptions || []).map((entry, index) => ({
      ...entry,
      __key: `${entry.type}:${entry.file}:${entry.packageName || entry.dependency || index}`,
    }));
  }

  findAllowlistException(type, file, details) {
    const relativeFile = path.relative(process.cwd(), path.resolve(file)).replace(/\\/g, '/');

    return this.allowlist.find((entry) => {
      if (entry.type !== type || entry.file !== relativeFile) {
        return false;
      }

      if (entry.packageName && entry.packageName !== details.packageName) {
        return false;
      }

      if (entry.dependency && entry.dependency !== details.banned) {
        return false;
      }

      return true;
    });
  }

  checkAllowlistFreshness() {
    for (const entry of this.allowlist) {
      if (!entry.reason || !entry.owner || !entry.targetRemovalDate) {
        this.addViolation(
          'INVALID_ALLOWLIST_ENTRY',
          `Architecture allowlist entry must declare reason, owner, and targetRemovalDate: ${entry.__key}`,
          'config/architecture-compliance-allowlist.json'
        );
      }

      if (!this.allowlistHits.has(entry.__key)) {
        this.addViolation(
          'STALE_ALLOWLIST_ENTRY',
          `Architecture allowlist entry no longer matches an active violation: ${entry.__key}`,
          'config/architecture-compliance-allowlist.json'
        );
      }
    }
  }

  async checkGradleDependencyDirection() {
    console.log('🏗️  Checking Gradle dependency direction...');

    const buildFiles = [];
    this.walkDirectory(process.cwd(), buildFiles, 6);
    const gradleFiles = buildFiles.filter((file) => file.endsWith('build.gradle.kts'));

    for (const gradleFile of gradleFiles) {
      const relativeFile = path.relative(process.cwd(), gradleFile);
      const content = fs.readFileSync(gradleFile, 'utf-8');
      const currentProduct = this.getProductFromPath(relativeFile);
      const projectDependencies = [...content.matchAll(/project\("(:[^"]+)"\)/g)].map((match) => match[1]);

      for (const dependency of projectDependencies) {
        if (relativeFile.startsWith('platform-kernel/') && dependency.startsWith(':products:')) {
          this.addViolation(
            'KERNEL_TO_PRODUCT_DEP',
            `Kernel module must not depend on product module ${dependency}`,
            relativeFile
          );
        }

        if (relativeFile.startsWith('platform-plugins/') && dependency.startsWith(':products:')) {
          this.addViolation(
            'PLUGIN_TO_PRODUCT_DEP',
            `Platform plugin must not depend on product module ${dependency}`,
            relativeFile
          );
        }

        if (currentProduct && dependency.startsWith(':products:')) {
          const depProduct = dependency.split(':')[2];
          if (depProduct && depProduct !== currentProduct) {
            this.addViolation(
              'PRODUCT_TO_PRODUCT_GRADLE_DEP',
              `Product ${currentProduct} must not depend directly on product ${depProduct} (${dependency})`,
              relativeFile
            );
          }
        }
      }
    }

    console.log(`   Checked ${gradleFiles.length} Gradle build files\n`);
  }

  async checkDeprecatedPackages() {
    console.log('⚠️  Checking for deprecated packages...');

    const deprecated = Object.keys(POLICY.deprecatedPackages);
    const packageFiles = this.findPackageJsonFiles();

    for (const pkgPath of packageFiles) {
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));

      const allDeps = {
        ...pkg.dependencies,
        ...pkg.devDependencies,
        ...pkg.peerDependencies,
      };

      for (const dep of Object.keys(allDeps)) {
        if (deprecated.includes(dep)) {
          const policy = POLICY.deprecatedPackages[dep];
          this.addViolation(
            'DEPRECATED_PACKAGE',
            `Deprecated package ${dep}: Use ${policy.replacement} (sunset: ${policy.sunsetDate})`,
            pkgPath,
            { deprecated: dep, replacement: policy.replacement }
          );
        }
      }
    }

    console.log(`   Checked for ${deprecated.length} deprecated packages\n`);
  }

  async checkDuplicatePackageNames() {
    console.log('📋 Checking for duplicate package names...');

    const packageFiles = this.findPackageJsonFiles();
    const names = new Map();

    for (const pkgPath of packageFiles) {
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
      const name = pkg.name;

      if (!name) continue;

      if (names.has(name)) {
        this.addViolation(
          'DUPLICATE_PACKAGE_NAME',
          `Duplicate package name "${name}" in ${pkgPath} (also in ${names.get(name)})`,
          pkgPath,
          { name, otherLocation: names.get(name) }
        );
      } else {
        names.set(name, pkgPath);
      }
    }

    console.log(`   Checked ${names.size} unique package names\n`);
  }

  async checkLicensePolicy() {
    console.log('📜 Checking license policy...');

    try {
      // This would run the license check script
      const result = execSync('pnpm licenses list --json', {
        encoding: 'utf-8',
        cwd: process.cwd(),
      });

      const licenses = JSON.parse(result);
      const denied = POLICY.licensePolicy.denied;

      for (const pkg of Object.keys(licenses)) {
        const license = licenses[pkg].license;
        if (denied.some(d => this.matchLicense(license, d))) {
          this.addViolation(
            'DENIED_LICENSE',
            `Package ${pkg} uses denied license: ${license}`,
            'pnpm-lock.yaml'
          );
        }
      }
    } catch (error) {
      this.addWarning('LICENSE_CHECK', 'Could not run license check: ' + error.message);
    }

    console.log('   License check complete\n');
  }

  findPackageJsonFiles() {
    const files = [];
    const workspaceRoot = process.cwd();

    // Read pnpm-workspace.yaml for package locations
    const workspaceYaml = fs.readFileSync(
      path.join(workspaceRoot, 'pnpm-workspace.yaml'),
      'utf-8'
    );

    const patterns = workspaceYaml
      .split('\n')
      .filter(line => line.trim().startsWith('-'))
      .map(line => line.replace(/^\s*-\s*"?([^"]+)"?\s*$/, '$1'));

    for (const pattern of patterns) {
      const glob = pattern.replace(/\*\*/g, '%%RECURSIVE%%').replace(/\*/g, '%%WILDCARD%%');

      // Simple glob matching
      if (pattern.includes('**')) {
        const baseDir = pattern.split('**')[0];
        const searchRoot = path.join(workspaceRoot, baseDir);
        if (fs.existsSync(searchRoot)) {
          this.walkDirectory(searchRoot, files, 5);
        }
      } else if (pattern.includes('*')) {
        const baseDir = pattern.split('*')[0];
        const searchRoot = path.join(workspaceRoot, baseDir);
        if (!fs.existsSync(searchRoot)) {
          continue;
        }
        const entries = fs.readdirSync(searchRoot, { withFileTypes: true });
        for (const entry of entries) {
          if (entry.isDirectory()) {
            const pkgPath = path.join(workspaceRoot, baseDir, entry.name, 'package.json');
            if (fs.existsSync(pkgPath)) {
              files.push(pkgPath);
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

  walkDirectory(dir, files, maxDepth, currentDepth = 0) {
    if (currentDepth > maxDepth) return;
    if (!fs.existsSync(dir)) return;

    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);

      // Phase 0.4: Exclude generated and build artifacts from purity scans
      if (this.isGeneratedArtifactDir(entry.name)) {
        continue;
      }

      if (entry.name === 'node_modules') continue;

      if (entry.isDirectory()) {
        this.walkDirectory(fullPath, files, maxDepth, currentDepth + 1);
      } else if (entry.name === 'package.json') {
        files.push(fullPath);
      }
    }
  }

  isGeneratedArtifactDir(dirName) {
    // Phase 0.4: Exclude stale generated and compiled artifacts from purity scans
    const generatedDirs = [
      'build',
      'out',
      'bin',
      'target',
      'generated',
      '.gradle',
      'classes',
      'test-classes',
      'node_modules',
      '.cache',
      'dist',
      '.next',
      '.nuxt',
    ];
    return generatedDirs.includes(dirName);
  }

  getScopeFromLocation(pkgPath) {
    const relative = path.relative(process.cwd(), pkgPath);

    if (relative.startsWith('platform/')) return '@ghatana';
    if (relative.startsWith('products/yappc/')) return '@yappc';
    if (relative.startsWith('products/flashit/')) return '@flashit';
    if (relative.startsWith('products/tutorputor/')) return '@tutorputor';
    if (relative.startsWith('products/data-cloud/')) return '@data-cloud';
    if (relative.startsWith('products/dcmaar/')) return '@dcmaar';
    if (relative.startsWith('products/audio-video/')) return '@audio-video';
    if (relative.startsWith('products/data-cloud/planes/action/')) return '@aep';
    if (relative.startsWith('shared-services/')) return '@shared';

    return null;
  }

  getProductFromPath(pkgPath) {
    const relative = path.relative(process.cwd(), pkgPath);
    const match = relative.match(/products\/([^/]+)/);
    return match ? match[1] : null;
  }

  getProductFromPackage(packageName) {
    if (packageName.startsWith('@yappc/')) return 'yappc';
    if (packageName.startsWith('@flashit/')) return 'flashit';
    if (packageName.startsWith('@tutorputor/')) return 'tutorputor';
    if (packageName.startsWith('@data-cloud/')) return 'data-cloud';
    if (packageName.startsWith('@dcmaar/')) return 'dcmaar';
    if (packageName.startsWith('@audio-video/')) return 'audio-video';
    if (packageName.startsWith('@aep/')) return 'aep';
    return null;
  }

  matchLicense(actual, pattern) {
    // Simple glob matching for licenses
    const regex = new RegExp(pattern.replace(/\*/g, '.*'));
    return regex.test(actual);
  }

  printSummary(duration) {
    console.log('='.repeat(60));
    console.log('📊 ARCHITECTURE COMPLIANCE SUMMARY');
    console.log('='.repeat(60));
    console.log(`   Duration:        ${duration}s`);
    console.log(`   Packages:        ${this.stats.packagesChecked}`);
    console.log(`   Imports:         ${this.stats.importsChecked}`);
    console.log(`   Violations:      ${this.stats.violations} ❌`);
    console.log(`   Warnings:        ${this.stats.warnings} ⚠️`);
    console.log('='.repeat(60));

    if (this.violations.length > 0) {
      console.log('\n🚨 VIOLATIONS DETAILED:');
      const grouped = this.groupByType(this.violations);
      for (const [type, items] of Object.entries(grouped)) {
        console.log(`\n   ${type} (${items.length}):`);
        items.slice(0, 5).forEach(v => console.log(`     - ${v.message}`));
        if (items.length > 5) {
          console.log(`     ... and ${items.length - 5} more`);
        }
      }
    }

    console.log('');
    if (this.violations.length === 0) {
      console.log('✅ All architecture compliance checks passed!');
    } else {
      console.log('❌ Architecture compliance violations detected. Please fix before merging.');
    }
  }

  groupByType(items) {
    return items.reduce((acc, item) => {
      acc[item.type] = acc[item.type] || [];
      acc[item.type].push(item);
      return acc;
    }, {});
  }
}

// Run if called directly
if (require.main === module) {
  const checker = new ArchitectureChecker();
  checker.run().then(code => process.exit(code));
}

module.exports = ArchitectureChecker;
