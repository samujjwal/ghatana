#!/usr/bin/env node

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..');

// Verify all TypeScript projects build successfully
function verifyTypeScriptBuild() {
  console.log('🔍 Verifying TypeScript project references...');
  
  try {
    // Clean first to ensure a fresh build
    console.log('🧹 Cleaning previous builds...');
    execSync('pnpm typecheck:refs:clean', { stdio: 'inherit' });
    
    // Build all projects with references
    console.log('🏗️  Building all TypeScript projects...');
    execSync('pnpm typecheck:refs', { stdio: 'inherit' });
    
    console.log('✅ All TypeScript projects built successfully!');
    return true;
  } catch (error) {
    console.error('❌ TypeScript build failed:', error.message);
    return false;
  }
}

// Verify all tsconfig.json files exist and are valid
function verifyTsConfigs() {
  console.log('🔍 Verifying tsconfig.json files...');
  
  const projects = [
    'apps/web',
    'apps/mobile-cap',
    'libs/types',
    'libs/store',
    'libs/graphql',
    'libs/mocks',
    'libs/ui',
    'libs/diagram',
    'packages/eslint-config-custom'
  ];
  
  let allValid = true;
  
  for (const project of projects) {
    const tsConfigPath = path.join(ROOT_DIR, project, 'tsconfig.json');
    
    try {
      const content = fs.readFileSync(tsConfigPath, 'utf8');
      JSON.parse(content);
      console.log(`✅ Valid tsconfig.json: ${project}`);
    } catch (error) {
      console.error(`❌ Invalid or missing tsconfig.json in ${project}:`, error.message);
      allValid = false;
    }
  }
  
  return allValid;
}

// Main function
function main() {
  console.log('🚀 Starting TypeScript project references verification...');
  
  const tsConfigsValid = verifyTsConfigs();
  if (!tsConfigsValid) {
    console.error('❌ Some tsconfig.json files are invalid or missing');
    process.exit(1);
  }
  
  const buildSucceeded = verifyTypeScriptBuild();
  if (!buildSucceeded) {
    process.exit(1);
  }
  
  console.log('🎉 All TypeScript project references verified successfully!');
}

main();
