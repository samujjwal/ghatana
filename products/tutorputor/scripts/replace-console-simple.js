#!/usr/bin/env node

/**
 * Simple Console Statement Replacer
 */

import { readFileSync, writeFileSync } from 'fs';
import { execSync } from 'child_process';

const files = [
  'apps/tutorputor-web/src/main.tsx',
  'apps/tutorputor-web/src/sw.ts',
  'apps/tutorputor-web/src/components/authoring/ReviewPublishWorkflow.tsx',
  'apps/tutorputor-web/src/components/authoring/ContentCreationWorkflow.tsx',
  'services/tutorputor-domain-loader/src/loaders/domain-loader.ts',
  'services/tutorputor-domain-loader/src/generators/learning-path-generator.ts',
  'services/tutorputor-domain-loader/src/generators/simulation-template-generator.ts',
  'services/tutorputor-domain-loader/src/generators/manifest-generator.ts',
  'services/tutorputor-domain-loader/src/generators/module-generator.ts',
  'services/tutorputor-domain-loader/src/generators/content-block-generator.ts',
];

function replaceConsoleStatements(filePath) {
  try {
    let content = readFileSync(filePath, 'utf-8');
    
    // Add logger import at the top if not present
    if (!content.includes('createLogger') && !content.includes('logger')) {
      content = `import { createLogger } from '../utils/logger.js';\nconst logger = createLogger('${filePath.split('/').pop()?.replace(/\.(ts|tsx)$/, '') || 'module'}');\n\n${content}`;
    }
    
    // Replace console.log statements
    content = content.replace(/console\.log\(([^)]+)\)/g, 'logger.info({}, $1)');
    
    // Replace console.error statements  
    content = content.replace(/console\.error\(([^)]+)\)/g, 'logger.error({}, $1)');
    
    // Replace console.warn statements
    content = content.replace(/console\.warn\(([^)]+)\)/g, 'logger.warn({}, $1)');
    
    // Replace console.info statements
    content = content.replace(/console\.info\(([^)]+)\)/g, 'logger.info({}, $1)');
    
    // Replace console.debug statements
    content = content.replace(/console\.debug\(([^)]+)\)/g, 'logger.debug({}, $1)');
    
    writeFileSync(filePath, content, 'utf-8');
    console.log(`✅ Updated ${filePath}`);
  } catch (error) {
    console.error(`❌ Error updating ${filePath}:`, error);
  }
}

console.log('🔄 Starting console statement replacement...\n');

for (const file of files) {
  replaceConsoleStatements(file);
}

console.log('\n✅ Console statement replacement complete!');
