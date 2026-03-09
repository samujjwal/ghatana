#!/usr/bin/env node

/**
 * Workspace Dependency Verifier
 * 
 * This script verifies workspace dependencies and checks for common issues:
 * 1. Ensures all workspace dependencies resolve correctly
 * 2. Identifies duplicate dependencies
 * 3. Verifies cross-workspace imports
 * 4. Checks for production build issues
 */

const fs = require('fs-extra');
const path = require('path');
const { execSync } = require('child_process');
const chalk = require('chalk');

// Configuration
const ROOT_DIR = process.cwd();
const WORKSPACES = require('../pnpm-workspace.yaml').packages.map(pkg => 
  pkg.replace('/*', '')
);

// Track issues
const issues = {
  unresolvedDeps: [],
  duplicateDeps: {},
  crossWorkspaceIssues: [],
  prodBuildIssues: [],
};

/**
 * Get all package.json files in workspaces
 */
function getWorkspacePackages() {
  const packages = [];
  
  for (const workspace of WORKSPACES) {
    const workspacePath = path.join(ROOT_DIR, workspace);
    
    if (fs.existsSync(workspacePath)) {
      const dirs = fs.readdirSync(workspacePath, { withFileTypes: true });
      
      for (const dir of dirs) {
        if (dir.isDirectory()) {
          const pkgPath = path.join(workspacePath, dir.name, 'package.json');
          
          if (fs.existsSync(pkgPath)) {
            try {
              const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
              packages.push({
                name: pkg.name,
                path: path.dirname(pkgPath),
                pkg,
                relativePath: path.relative(ROOT_DIR, path.dirname(pkgPath)),
              });
            } catch (error) {
              console.error(chalk.red(`Error reading ${pkgPath}:`), error.message);
            }
          }
        }
      }
    }
  }
  
  return packages;
}

/**
 * Check for unresolved dependencies
 */
function checkUnresolvedDependencies(packages) {
  console.log(chalk.blue('\nChecking for unresolved dependencies...'));
  
  for (const pkg of packages) {
    const deps = {
      ...(pkg.pkg.dependencies || {}),
      ...(pkg.pkg.devDependencies || {}),
      ...(pkg.pkg.peerDependencies || {}),
    };
    
    for (const [dep, version] of Object.entries(deps)) {
      if (version.startsWith('workspace:')) {
        const workspaceDep = packages.find(p => p.name === dep);
        
        if (!workspaceDep) {
          issues.unresolvedDeps.push({
            package: pkg.name,
            dependency: dep,
            version,
            error: 'Workspace dependency not found',
          });
        }
      }
    }
  }
  
  if (issues.unresolvedDeps.length > 0) {
    console.error(chalk.red(`✗ Found ${issues.unresolvedDeps.length} unresolved workspace dependencies`));
    issues.unresolvedDeps.forEach(issue => {
      console.error(`  - ${chalk.bold(issue.package)} depends on ${chalk.bold(issue.dependency)} (${issue.version})`);
    });
  } else {
    console.log(chalk.green('✓ All workspace dependencies resolve correctly'));
  }
}

/**
 * Check for duplicate dependencies
 */
function checkDuplicateDependencies(packages) {
  console.log(chalk.blue('\nChecking for duplicate dependencies...'));
  
  const allDeps = new Map();
  
  // Collect all dependencies
  for (const pkg of packages) {
    const deps = {
      ...(pkg.pkg.dependencies || {}),
      ...(pkg.pkg.devDependencies || {}),
    };
    
    for (const [dep, version] of Object.entries(deps)) {
      if (!allDeps.has(dep)) {
        allDeps.set(dep, new Set());
      }
      allDeps.get(dep).add(version);
    }
  }
  
  // Find duplicates
  for (const [dep, versions] of allDeps.entries()) {
    if (versions.size > 1) {
      issues.duplicateDeps[dep] = Array.from(versions);
    }
  }
  
  if (Object.keys(issues.duplicateDeps).length > 0) {
    console.error(chalk.yellow(`⚠ Found ${Object.keys(issues.duplicateDeps).length} dependencies with multiple versions:`));
    for (const [dep, versions] of Object.entries(issues.duplicateDeps)) {
      console.error(`  - ${chalk.bold(dep)}: ${versions.join(', ')}`);
    }
  } else {
    console.log(chalk.green('✓ No duplicate dependencies found'));
  }
}

/**
 * Verify cross-workspace imports
 */
function checkCrossWorkspaceImports(packages) {
  console.log(chalk.blue('\nVerifying cross-workspace imports...'));
  
  // This is a simplified check - in a real project, you'd want to use a proper TypeScript parser
  for (const pkg of packages) {
    const srcDir = path.join(pkg.path, 'src');
    
    if (fs.existsSync(srcDir)) {
      const files = findFiles(srcDir, ['.ts', '.tsx', '.js', '.jsx']);
      
      for (const file of files) {
        const content = fs.readFileSync(file, 'utf8');
        const importRegex = /from\s+['"](@[^'"]+)['"]/g;
        let match;
        
        while ((match = importRegex.exec(content)) !== null) {
          const importPath = match[1];
          
          // Check if it's a workspace import
          if (importPath.startsWith('@ghatana/yappc-')) {
            const importedPkg = packages.find(p => p.name === importPath.split('/')[0]);
            
            if (!importedPkg) {
              issues.crossWorkspaceIssues.push({
                file: path.relative(ROOT_DIR, file),
                import: importPath,
                error: 'Imported workspace package not found',
              });
            }
          }
        }
      }
    }
  }
  
  if (issues.crossWorkspaceIssues.length > 0) {
    console.error(chalk.red(`✗ Found ${issues.crossWorkspaceIssues.length} cross-workspace import issues`));
    issues.crossWorkspaceIssues.forEach(issue => {
      console.error(`  - ${chalk.bold(issue.file)} imports ${chalk.bold(issue.import)}: ${issue.error}`);
    });
  } else {
    console.log(chalk.green('✓ All cross-workspace imports are valid'));
  }
}

/**
 * Check production build issues
 */
function checkProductionBuilds(packages) {
  console.log(chalk.blue('\nChecking for production build issues...'));
  
  for (const pkg of packages) {
    if (pkg.pkg.private) continue;
    
    const distPath = path.join(pkg.path, 'dist');
    
    if (fs.existsSync(distPath)) {
      // Check for devDependencies in production build
      const pkgJson = JSON.parse(fs.readFileSync(path.join(distPath, 'package.json'), 'utf8'));
      
      if (pkgJson.devDependencies) {
        issues.prodBuildIssues.push({
          package: pkg.name,
          issue: 'devDependencies should not be included in production build',
        });
      }
      
      // Check for source maps in production
      const files = findFiles(distPath, ['.js']);
      const hasSourceMaps = files.some(file => file.endsWith('.map'));
      
      if (hasSourceMaps) {
        issues.prodBuildIssues.push({
          package: pkg.name,
          issue: 'Source maps should not be included in production build',
        });
      }
    }
  }
  
  if (issues.prodBuildIssues.length > 0) {
    console.error(chalk.yellow(`⚠ Found ${issues.prodBuildIssues.length} production build issues`));
    issues.prodBuildIssues.forEach(issue => {
      console.error(`  - ${chalk.bold(issue.package)}: ${issue.issue}`);
    });
  } else {
    console.log(chalk.green('✓ No production build issues found'));
  }
}

/**
 * Helper to find files with specific extensions
 */
function findFiles(dir, extensions) {
  let results = [];
  const files = fs.readdirSync(dir, { withFileTypes: true });
  
  for (const file of files) {
    const fullPath = path.join(dir, file.name);
    
    if (file.isDirectory()) {
      results = [...results, ...findFiles(fullPath, extensions)];
    } else if (extensions.some(ext => file.name.endsWith(ext))) {
      results.push(fullPath);
    }
  }
  
  return results;
}

/**
 * Generate a report
 */
function generateReport() {
  const hasIssues = 
    issues.unresolvedDeps.length > 0 ||
    Object.keys(issues.duplicateDeps).length > 0 ||
    issues.crossWorkspaceIssues.length > 0 ||
    issues.prodBuildIssues.length > 0;
  
  console.log(`\n${  '='.repeat(80)}`);
  console.log(chalk.bold('WORKSPACE DEPENDENCY VERIFICATION REPORT'));
  console.log('='.repeat(80));
  
  // Unresolved Dependencies
  console.log(`\n${  chalk.underline('UNRESOLVED DEPENDENCIES')}`);
  if (issues.unresolvedDeps.length > 0) {
    issues.unresolvedDeps.forEach(issue => {
      console.log(`❌ ${chalk.red(issue.package)} -> ${chalk.bold(issue.dependency)} (${issue.version})`);
      console.log(`   ${issue.error}`);
    });
  } else {
    console.log('✅ No unresolved dependencies found');
  }
  
  // Duplicate Dependencies
  console.log(`\n${  chalk.underline('DUPLICATE DEPENDENCIES')}`);
  if (Object.keys(issues.duplicateDeps).length > 0) {
    for (const [dep, versions] of Object.entries(issues.duplicateDeps)) {
      console.log(`⚠️  ${chalk.yellow(dep)}: ${versions.join(', ')}`);
    }
  } else {
    console.log('✅ No duplicate dependencies found');
  }
  
  // Cross-Workspace Issues
  console.log(`\n${  chalk.underline('CROSS-WORKSPACE IMPORTS')}`);
  if (issues.crossWorkspaceIssues.length > 0) {
    issues.crossWorkspaceIssues.forEach(issue => {
      console.log(`❌ ${chalk.red(issue.file)} -> ${chalk.bold(issue.import)}`);
      console.log(`   ${issue.error}`);
    });
  } else {
    console.log('✅ All cross-workspace imports are valid');
  }
  
  // Production Build Issues
  console.log(`\n${  chalk.underline('PRODUCTION BUILD ISSUES')}`);
  if (issues.prodBuildIssues.length > 0) {
    issues.prodBuildIssues.forEach(issue => {
      console.log(`⚠️  ${chalk.yellow(issue.package)}: ${issue.issue}`);
    });
  } else {
    console.log('✅ No production build issues found');
  }
  
  console.log(`\n${  '='.repeat(80)}`);
  
  if (hasIssues) {
    console.error(chalk.red.bold('\nISSUES DETECTED: Please fix the above issues before proceeding.'));
    process.exit(1);
  } else {
    console.log(chalk.green.bold('\nALL CHECKS PASSED! Your workspace dependencies look good. 🎉'));
  }
}

/**
 * Main function
 */
async function main() {
  console.log(chalk.blue.bold('\n🔍 Verifying Workspace Dependencies\n'));
  
  try {
    // Get all workspace packages
    const packages = getWorkspacePackages();
    console.log(chalk.green(`✓ Found ${packages.length} workspace packages`));
    
    // Run all checks
    checkUnresolvedDependencies(packages);
    checkDuplicateDependencies(packages);
    checkCrossWorkspaceImports(packages);
    checkProductionBuilds(packages);
    
    // Generate report
    generateReport();
  } catch (error) {
    console.error(chalk.red('\n❌ Error during verification:'), error);
    process.exit(1);
  }
}

main();
