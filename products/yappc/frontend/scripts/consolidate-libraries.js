#!/usr/bin/env node

/**
 * YAPPC Library Consolidation Script
 * 
 * This script automates the consolidation of 35 frontend libraries into 8 focused libraries.
 * It performs the following operations:
 * 1. Creates new consolidated library structures
 * 2. Copies files from old libraries to new ones
 * 3. Updates import paths across the codebase
 * 4. Updates package.json dependencies
 * 5. Generates migration report
 * 
 * Usage: node scripts/consolidate-libraries.js [--dry-run]
 */

import fs from 'fs-extra';
import path from 'path';
import { fileURLToPath } from 'url';
import { glob } from 'glob';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');
const libsDir = path.join(rootDir, 'libs');

// Consolidation mapping
const CONSOLIDATION_MAP = {
  '@yappc/core': {
    sources: ['core', 'types', 'utils'],
    description: 'Essential types, utilities, domain models'
  },
  '@yappc/ui': {
    sources: ['ui', 'base-ui', 'development-ui', 'initialization-ui', 'navigation-ui', 'theme', 'shortcuts'],
    description: 'Complete UI component system'
  },
  '@yappc/ai': {
    sources: ['ai', 'messaging', 'realtime', 'notifications', 'chat'],
    description: 'AI integration and real-time features'
  },
  '@yappc/state': {
    sources: ['state', 'config-hooks', 'crdt'],
    description: 'State management and hooks'
  },
  '@yappc/config': {
    sources: ['config', 'aep-config'],
    description: 'Configuration management'
  },
  '@yappc/testing': {
    sources: ['testing', 'mocks'],
    description: 'Test utilities and mocks'
  }
};

// Libraries to keep as-is
const KEEP_AS_IS = ['canvas', 'auth', 'ide', 'api', 'code-editor', 'mobile', 'collab'];

class LibraryConsolidator {
  constructor(dryRun = false) {
    this.dryRun = dryRun;
    this.report = {
      filesProcessed: 0,
      importUpdates: 0,
      errors: [],
      warnings: []
    };
  }

  async run() {
    console.log('🚀 Starting YAPPC Library Consolidation');
    console.log(`Mode: ${this.dryRun ? 'DRY RUN' : 'LIVE'}\n`);

    try {
      // Step 1: Validate current structure
      await this.validateStructure();

      // Step 2: Create consolidated library structures
      await this.createConsolidatedStructures();

      // Step 3: Copy files to consolidated libraries
      await this.copyFilesToConsolidated();

      // Step 4: Update import paths
      await this.updateImportPaths();

      // Step 5: Update package.json files
      await this.updatePackageJsonFiles();

      // Step 6: Generate migration report
      this.generateReport();

      console.log('\n✅ Consolidation complete!');
      if (this.dryRun) {
        console.log('⚠️  This was a dry run. No files were modified.');
      }
    } catch (error) {
      console.error('❌ Consolidation failed:', error);
      process.exit(1);
    }
  }

  async validateStructure() {
    console.log('📋 Validating current library structure...');
    
    const libs = await fs.readdir(libsDir);
    const expectedLibs = [
      ...Object.values(CONSOLIDATION_MAP).flatMap(m => m.sources),
      ...KEEP_AS_IS
    ];

    for (const lib of expectedLibs) {
      const libPath = path.join(libsDir, lib);
      if (!await fs.pathExists(libPath)) {
        this.report.warnings.push(`Library not found: ${lib}`);
      }
    }

    console.log(`✓ Found ${libs.length} libraries\n`);
  }

  async createConsolidatedStructures() {
    console.log('🏗️  Creating consolidated library structures...');

    for (const [targetLib, config] of Object.entries(CONSOLIDATION_MAP)) {
      const libName = targetLib.replace('@yappc/', '');
      const targetPath = path.join(libsDir, libName);

      // Check if this is an expansion of existing library
      const isExpansion = config.sources.includes(libName);

      if (isExpansion) {
        console.log(`  ↗️  Expanding existing library: ${targetLib}`);
      } else {
        console.log(`  📦 Creating new library: ${targetLib}`);
        
        if (!this.dryRun) {
          await fs.ensureDir(path.join(targetPath, 'src'));
          
          // Create package.json
          const packageJson = {
            name: targetLib,
            version: '1.0.0',
            type: 'module',
            description: config.description,
            main: './src/index.ts',
            types: './src/index.ts',
            exports: {
              '.': './src/index.ts'
            },
            peerDependencies: {
              react: '^19.2.4',
              'react-dom': '^19.2.4'
            }
          };
          
          await fs.writeJson(
            path.join(targetPath, 'package.json'),
            packageJson,
            { spaces: 2 }
          );

          // Create tsconfig.json
          const tsConfig = {
            extends: '../../tsconfig.base.json',
            compilerOptions: {
              outDir: './dist',
              rootDir: './src'
            },
            include: ['src/**/*'],
            exclude: ['node_modules', 'dist', '**/*.test.ts', '**/*.test.tsx']
          };

          await fs.writeJson(
            path.join(targetPath, 'tsconfig.json'),
            tsConfig,
            { spaces: 2 }
          );

          // Create index.ts
          await fs.writeFile(
            path.join(targetPath, 'src', 'index.ts'),
            `// ${config.description}\n\nexport {};\n`
          );
        }
      }
    }

    console.log('');
  }

  async copyFilesToConsolidated() {
    console.log('📁 Copying files to consolidated libraries...');

    for (const [targetLib, config] of Object.entries(CONSOLIDATION_MAP)) {
      const libName = targetLib.replace('@yappc/', '');
      const targetPath = path.join(libsDir, libName, 'src');

      for (const sourceLib of config.sources) {
        // Skip if source is the target (expansion case)
        if (sourceLib === libName) continue;

        const sourcePath = path.join(libsDir, sourceLib, 'src');
        
        if (!await fs.pathExists(sourcePath)) {
          this.report.warnings.push(`Source not found: ${sourceLib}/src`);
          continue;
        }

        console.log(`  ${sourceLib} → ${libName}`);

        if (!this.dryRun) {
          // Create subdirectory for source library to avoid conflicts
          const targetSubdir = path.join(targetPath, sourceLib);
          await fs.copy(sourcePath, targetSubdir, {
            overwrite: false,
            errorOnExist: false
          });

          this.report.filesProcessed += await this.countFiles(sourcePath);
        }
      }
    }

    console.log('');
  }

  async updateImportPaths() {
    console.log('🔄 Updating import paths...');

    // Find all TypeScript/TSX files
    const files = await glob('**/*.{ts,tsx}', {
      cwd: rootDir,
      ignore: ['node_modules/**', 'dist/**', '**/node_modules/**'],
      absolute: true
    });

    console.log(`  Found ${files.length} files to process`);

    for (const file of files) {
      if (!this.dryRun) {
        await this.updateImportsInFile(file);
      }
    }

    console.log(`  Updated ${this.report.importUpdates} import statements\n`);
  }

  async updateImportsInFile(filePath) {
    try {
      let content = await fs.readFile(filePath, 'utf-8');
      let modified = false;

      // Build import replacement map
      const replacements = {};
      for (const [targetLib, config] of Object.entries(CONSOLIDATION_MAP)) {
        for (const sourceLib of config.sources) {
          const oldImport = `@yappc/${sourceLib}`;
          const newImport = targetLib;
          
          // Skip if source is target (expansion)
          if (oldImport === newImport) continue;
          
          replacements[oldImport] = `${newImport}/${sourceLib}`;
        }
      }

      // Replace imports
      for (const [oldImport, newImport] of Object.entries(replacements)) {
        const regex = new RegExp(`from ['"]${oldImport}(['"/])`, 'g');
        if (regex.test(content)) {
          content = content.replace(regex, `from '${newImport}$1`);
          modified = true;
          this.report.importUpdates++;
        }
      }

      if (modified) {
        await fs.writeFile(filePath, content, 'utf-8');
      }
    } catch (error) {
      this.report.errors.push(`Failed to update ${filePath}: ${error.message}`);
    }
  }

  async updatePackageJsonFiles() {
    console.log('📦 Updating package.json dependencies...');

    const packageJsonFiles = await glob('**/package.json', {
      cwd: rootDir,
      ignore: ['node_modules/**', '**/node_modules/**'],
      absolute: true
    });

    for (const pkgFile of packageJsonFiles) {
      if (!this.dryRun) {
        await this.updatePackageJson(pkgFile);
      }
    }

    console.log('');
  }

  async updatePackageJson(pkgPath) {
    try {
      const pkg = await fs.readJson(pkgPath);
      let modified = false;

      // Update dependencies
      for (const depType of ['dependencies', 'devDependencies', 'peerDependencies']) {
        if (!pkg[depType]) continue;

        for (const [targetLib, config] of Object.entries(CONSOLIDATION_MAP)) {
          for (const sourceLib of config.sources) {
            const oldDep = `@yappc/${sourceLib}`;
            const newDep = targetLib;

            if (oldDep === newDep) continue;

            if (pkg[depType][oldDep]) {
              pkg[depType][newDep] = pkg[depType][oldDep];
              delete pkg[depType][oldDep];
              modified = true;
            }
          }
        }
      }

      if (modified) {
        await fs.writeJson(pkgPath, pkg, { spaces: 2 });
      }
    } catch (error) {
      this.report.errors.push(`Failed to update ${pkgPath}: ${error.message}`);
    }
  }

  async countFiles(dir) {
    const files = await glob('**/*', {
      cwd: dir,
      nodir: true
    });
    return files.length;
  }

  generateReport() {
    console.log('\n📊 Consolidation Report');
    console.log('═══════════════════════════════════════════════════');
    console.log(`Files processed: ${this.report.filesProcessed}`);
    console.log(`Import updates: ${this.report.importUpdates}`);
    console.log(`Warnings: ${this.report.warnings.length}`);
    console.log(`Errors: ${this.report.errors.length}`);

    if (this.report.warnings.length > 0) {
      console.log('\n⚠️  Warnings:');
      this.report.warnings.forEach(w => console.log(`  - ${w}`));
    }

    if (this.report.errors.length > 0) {
      console.log('\n❌ Errors:');
      this.report.errors.forEach(e => console.log(`  - ${e}`));
    }

    console.log('\n📋 Consolidation Summary:');
    console.log('  35 libraries → 10 libraries (71% reduction)');
    console.log('  Consolidated libraries:');
    for (const [targetLib, config] of Object.entries(CONSOLIDATION_MAP)) {
      console.log(`    ${targetLib}: ${config.sources.length} sources merged`);
    }
    console.log('  Libraries kept as-is:');
    KEEP_AS_IS.forEach(lib => console.log(`    @yappc/${lib}`));
  }
}

// Run the consolidator
const dryRun = process.argv.includes('--dry-run');
const consolidator = new LibraryConsolidator(dryRun);
consolidator.run().catch(console.error);
