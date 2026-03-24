#!/usr/bin/env node

/**
 * YAPPC Build Scripts Simplification
 * 
 * Reduces package.json scripts from 88 to 20 essential scripts.
 * Creates a backup before modifying.
 * 
 * Usage: node scripts/simplify-build-scripts.js [--dry-run]
 */

import fs from 'fs-extra';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');
const packageJsonPath = path.join(rootDir, 'package.json');

// Essential scripts (20 total)
const ESSENTIAL_SCRIPTS = {
  // Development
  "dev": "pnpm --filter web dev",
  "build": "pnpm --filter web build",
  "preview": "pnpm --filter web preview",
  
  // Testing
  "test": "vitest",
  "test:e2e": "playwright test",
  "test:coverage": "vitest --coverage",
  
  // Code Quality
  "lint": "eslint 'apps/**/src/**/*.{ts,tsx}' 'libs/**/src/**/*.{ts,tsx}' --ignore-pattern '**/*.d.ts' --ignore-pattern '**/generated/**'",
  "lint:fix": "eslint 'apps/**/src/**/*.{ts,tsx}' 'libs/**/src/**/*.{ts,tsx}' --ignore-pattern '**/*.d.ts' --ignore-pattern '**/generated/**' --fix",
  "format": "prettier --write \"**/*.{ts,tsx,js,jsx,json,md,css,scss}\"",
  "format:check": "prettier --check \"**/*.{ts,tsx,js,jsx,json,md,css,scss}\"",
  
  // Type Checking
  "typecheck": "tsc --noEmit",
  "typecheck:build": "tsc -b tsconfig.refs.json",
  
  // Utilities
  "clean": "rimraf dist node_modules/.cache",
  "codegen": "graphql-codegen --config codegen.yml",
  
  // Storybook
  "storybook": "pnpm --filter @yappc/ui storybook",
  
  // Verification
  "verify": "pnpm typecheck && pnpm lint && pnpm test",
  
  // Dependencies
  "deps:update": "pnpm update --latest",
  "deps:audit": "pnpm audit",
  
  // Performance
  "lighthouse": "lhci autorun",
  "analyze": "pnpm build && open apps/web/dist/stats.html"
};

class ScriptSimplifier {
  constructor(dryRun = false) {
    this.dryRun = dryRun;
    this.report = {
      originalCount: 0,
      finalCount: 0,
      removed: [],
      kept: []
    };
  }

  async run() {
    console.log('🚀 Simplifying YAPPC Build Scripts');
    console.log(`Mode: ${this.dryRun ? 'DRY RUN' : 'LIVE'}\n`);

    try {
      // Load current package.json
      const pkg = await fs.readJson(packageJsonPath);
      
      if (!pkg.scripts) {
        console.log('❌ No scripts found in package.json');
        return;
      }

      this.report.originalCount = Object.keys(pkg.scripts).length;
      console.log(`📊 Current scripts: ${this.report.originalCount}`);

      // Create backup
      if (!this.dryRun) {
        const backupPath = path.join(rootDir, 'package.json.backup');
        await fs.copy(packageJsonPath, backupPath);
        console.log(`💾 Backup created: package.json.backup`);
      }

      // Identify scripts to remove
      const currentScripts = Object.keys(pkg.scripts);
      const essentialScriptNames = Object.keys(ESSENTIAL_SCRIPTS);
      
      for (const script of currentScripts) {
        if (essentialScriptNames.includes(script)) {
          this.report.kept.push(script);
        } else {
          this.report.removed.push(script);
        }
      }

      // Update package.json
      if (!this.dryRun) {
        pkg.scripts = ESSENTIAL_SCRIPTS;
        await fs.writeJson(packageJsonPath, pkg, { spaces: 2 });
        console.log('✅ package.json updated');
      }

      this.report.finalCount = Object.keys(ESSENTIAL_SCRIPTS).length;

      // Generate report
      this.generateReport();

      console.log('\n✅ Simplification complete!');
      if (this.dryRun) {
        console.log('⚠️  This was a dry run. No files were modified.');
      }

    } catch (error) {
      console.error('❌ Simplification failed:', error);
      process.exit(1);
    }
  }

  generateReport() {
    console.log('\n📊 Simplification Report');
    console.log('═══════════════════════════════════════════════════');
    console.log(`Original scripts: ${this.report.originalCount}`);
    console.log(`Final scripts: ${this.report.finalCount}`);
    console.log(`Reduction: ${this.report.originalCount - this.report.finalCount} scripts (${Math.round((1 - this.report.finalCount / this.report.originalCount) * 100)}%)`);

    console.log('\n✅ Scripts kept:');
    this.report.kept.forEach(s => console.log(`  - ${s}`));

    console.log('\n❌ Scripts removed:');
    this.report.removed.forEach(s => console.log(`  - ${s}`));

    console.log('\n📋 Script Categories:');
    console.log('  Development: dev, build, preview');
    console.log('  Testing: test, test:e2e, test:coverage');
    console.log('  Code Quality: lint, lint:fix, format, format:check');
    console.log('  Type Checking: typecheck, typecheck:build');
    console.log('  Utilities: clean, codegen, storybook');
    console.log('  Verification: verify');
    console.log('  Dependencies: deps:update, deps:audit');
    console.log('  Performance: lighthouse, analyze');
  }
}

// Run the simplifier
const dryRun = process.argv.includes('--dry-run');
const simplifier = new ScriptSimplifier(dryRun);
simplifier.run().catch(console.error);
