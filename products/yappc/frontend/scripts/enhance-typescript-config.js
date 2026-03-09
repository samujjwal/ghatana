#!/usr/bin/env node

/**
 * TypeScript Configuration Enhancer
 * 
 * This script enhances the TypeScript configuration by:
 * 1. Setting up project references for better build performance
 * 2. Enabling JSDoc type checking for JavaScript files
 */

const fs = require('fs-extra');
const path = require('path');
const chalk = require('chalk');

// Configuration
const ROOT_DIR = process.cwd();
const TSCONFIG_PATH = path.join(ROOT_DIR, 'tsconfig.json');

// Hardcoded workspaces based on the project structure
const WORKSPACES = [
  'apps',
  'libs',
  'packages'
];

/**
 * Get all TypeScript configuration files in workspaces
 */
function getWorkspaceTsConfigs() {
  const configs = [];
  
  for (const workspace of WORKSPACES) {
    const workspacePath = path.join(ROOT_DIR, workspace);
    
    if (fs.existsSync(workspacePath)) {
      const dirs = fs.readdirSync(workspacePath, { withFileTypes: true });
      
      for (const dir of dirs) {
        if (dir.isDirectory()) {
          const tsconfigPath = path.join(workspacePath, dir.name, 'tsconfig.json');
          
          if (fs.existsSync(tsconfigPath)) {
            try {
              const tsconfig = JSON.parse(fs.readFileSync(tsconfigPath, 'utf8'));
              configs.push({
                path: tsconfigPath,
                dir: path.dirname(tsconfigPath),
                config: tsconfig,
                name: `${workspace}/${dir.name}`,
              });
            } catch (error) {
              console.error(chalk.red(`Error reading ${tsconfigPath}:`), error.message);
            }
          }
        }
      }
    }
  }
  
  return configs;
}

/**
 * Update tsconfig.json with project references
 */
function updateProjectReferences() {
  console.log(chalk.blue('\nSetting up project references...'));
  
  const tsconfigs = getWorkspaceTsConfigs();
  const rootTsconfig = JSON.parse(fs.readFileSync(TSCONFIG_PATH, 'utf8'));
  
  // Update root tsconfig with project references
  rootTsconfig.references = tsconfigs.map(tsconfig => ({
    path: path.relative(ROOT_DIR, tsconfig.dir)
  }));
  
  // Save updated root tsconfig
  fs.writeFileSync(
    TSCONFIG_PATH,
    `${JSON.stringify(rootTsconfig, null, 2)  }\n`,
    'utf8'
  );
  
  console.log(chalk.green(`✓ Added ${tsconfigs.length} project references to root tsconfig.json`));
  
  // Update each workspace tsconfig with composite and references
  for (const tsconfig of tsconfigs) {
    const updatedConfig = {
      ...tsconfig.config,
      compilerOptions: {
        ...(tsconfig.config.compilerOptions || {}),
        composite: true,
        declaration: true,
        declarationMap: true,
        outDir: tsconfig.config.compilerOptions?.outDir || './dist',
        rootDir: tsconfig.config.compilerOptions?.rootDir || './src',
      },
      references: [],
    };
    
    // Find dependencies for this project
    const pkgJsonPath = path.join(tsconfig.dir, 'package.json');
    
    if (fs.existsSync(pkgJsonPath)) {
      try {
        const pkg = JSON.parse(fs.readFileSync(pkgJsonPath, 'utf8'));
        const deps = {
          ...(pkg.dependencies || {}),
          ...(pkg.devDependencies || {}),
          ...(pkg.peerDependencies || {}),
        };
        
        // Add references for workspace dependencies
        for (const [dep, version] of Object.entries(deps)) {
          if (version.startsWith('workspace:')) {
            const depTsconfig = tsconfigs.find(t => {
              const depPkgPath = path.join(t.dir, 'package.json');
              if (fs.existsSync(depPkgPath)) {
                const depPkg = JSON.parse(fs.readFileSync(depPkgPath, 'utf8'));
                return depPkg.name === dep;
              }
              return false;
            });
            
            if (depTsconfig) {
              updatedConfig.references.push({
                path: path.relative(tsconfig.dir, depTsconfig.dir),
              });
            }
          }
        }
      } catch (error) {
        console.error(chalk.red(`Error processing ${pkgJsonPath}:`), error.message);
      }
    }
    
    // Save updated tsconfig
    fs.writeFileSync(
      tsconfig.path,
      `${JSON.stringify(updatedConfig, null, 2)  }\n`,
      'utf8'
    );
    
    console.log(chalk.green(`✓ Updated ${tsconfig.name}/tsconfig.json`));
  }
  
  console.log(chalk.green('✓ Project references set up successfully'));
}

/**
 * Enable JSDoc type checking for JavaScript files
 */
function enableJSDocTypeChecking() {
  console.log(chalk.blue('\nEnabling JSDoc type checking...'));
  
  const tsconfigs = [
    { path: TSCONFIG_PATH, config: JSON.parse(fs.readFileSync(TSCONFIG_PATH, 'utf8')) },
    ...getWorkspaceTsConfigs(),
  ];
  
  let updatedCount = 0;
  
  for (const { path: tsconfigPath, config } of tsconfigs) {
    const updatedConfig = {
      ...config,
      compilerOptions: {
        ...(config.compilerOptions || {}),
        // Enable type checking for JS files
        checkJs: true,
        // Better type checking for JS files
        allowJs: true,
        // Better type inference for JS files
        noImplicitAny: true,
        // Better type checking for JS files
        strictNullChecks: true,
        // Better type checking for JS files
        strictFunctionTypes: true,
        // Better type checking for JS files
        strictBindCallApply: true,
        // Better type checking for JS files
        noImplicitThis: true,
        // Better type checking for JS files
        noUnusedLocals: true,
        // Better type checking for JS files
        noUnusedParameters: true,
        // Better type checking for JS files
        noImplicitReturns: true,
        // Better type checking for JS files
        noFallthroughCasesInSwitch: true,
      },
      // Include JS files in type checking
      include: [
        ...(config.include || []),
        '**/*.js',
        '**/*.jsx',
      ].filter((value, index, self) => self.indexOf(value) === index),
    };
    
    // Save updated tsconfig
    fs.writeFileSync(
      tsconfigPath,
      `${JSON.stringify(updatedConfig, null, 2)  }\n`,
      'utf8'
    );
    
    updatedCount++;
  }
  
  console.log(chalk.green(`✓ Updated ${updatedCount} tsconfig files with JSDoc type checking`));
}

/**
 * Configure incremental compilation
 */
function configureIncrementalCompilation() {
  console.log(chalk.blue('\nConfiguring incremental compilation...'));
  
  const tsconfigs = [
    { path: TSCONFIG_PATH, config: JSON.parse(fs.readFileSync(TSCONFIG_PATH, 'utf8')) },
    ...getWorkspaceTsConfigs(),
  ];
  
  let updatedCount = 0;
  
  for (const { path: tsconfigPath, config } of tsconfigs) {
    const updatedConfig = {
      ...config,
      compilerOptions: {
        ...(config.compilerOptions || {}),
        // Enable incremental compilation
        incremental: true,
        // Store incremental compilation info
        tsBuildInfoFile: path.join(
          path.dirname(tsconfigPath),
          'node_modules',
          '.cache',
          'tsbuildinfo',
          path.basename(tsconfigPath).replace('.json', '.tsbuildinfo')
        ),
      },
    };
    
    // Ensure cache directory exists
    const cacheDir = path.join(path.dirname(tsconfigPath), 'node_modules/.cache/tsbuildinfo');
    fs.ensureDirSync(cacheDir);
    
    // Save updated tsconfig
    fs.writeFileSync(
      tsconfigPath,
      `${JSON.stringify(updatedConfig, null, 2)  }\n`,
      'utf8'
    );
    
    updatedCount++;
  }
  
  console.log(chalk.green(`✓ Configured incremental compilation for ${updatedCount} projects`));
}

/**
 * Main function
 */
async function main() {
  console.log(chalk.blue.bold('\n🚀 Enhancing TypeScript Configuration\n'));
  
  try {
    // 1. Set up project references
    updateProjectReferences();
    
    // 2. Enable JSDoc type checking
    enableJSDocTypeChecking();
    
    // 3. Configure incremental compilation
    configureIncrementalCompilation();
    
    console.log(`\n${  '='.repeat(80)}`);
    console.log(chalk.green.bold('\n✅ TypeScript configuration enhanced successfully!\n'));
    console.log(chalk.blue('Next steps:'));
    console.log('1. Run build with project references:');
    console.log(`   ${  chalk.cyan('tsc -b --verbose')}`);
    console.log('2. For faster incremental builds:');
    console.log(`   ${  chalk.cyan('tsc -b --incremental')}`);
    console.log('3. To clean build outputs:');
    console.log(`   ${  chalk.cyan('tsc -b --clean')}`);
    console.log(`\n${  '='.repeat(80)}`);
  } catch (error) {
    console.error(chalk.red('\n❌ Error enhancing TypeScript configuration:'), error);
    process.exit(1);
  }
}

main();
