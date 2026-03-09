#!/usr/bin/env node

/**
 * Error Handling Fixer
 * 
 * Replaces empty catch blocks and console.warn in catch blocks
 * with proper error handling and structured logging.
 */

import { readFileSync, writeFileSync } from 'fs';

const filesToFix = [
  'apps/api-gateway/src/routes/admin-content.ts',
  'apps/tutorputor-web/src/features/simulation-authoring/components/NLAuthorPanel.ts',
  'apps/tutorputor-web/src/features/simulation-authoring/hooks/useNLAuthoring.ts',
  'apps/tutorputor-web/src/components/OmnipresentAITutor.tsx',
  'apps/tutorputor-web/src/pages/AITutorPage.tsx',
  'services/tutorputor-platform/src/modules/compliance/exporter.ts',
];

function fixErrorHandling(filePath) {
  try {
    let content = readFileSync(filePath, 'utf-8');
    let modified = false;
    
    // Add logger import if not present
    if (!content.includes('createLogger') && !content.includes('logger')) {
      const importIndex = content.lastIndexOf('import');
      const importEndIndex = content.indexOf('\n', importIndex);
      
      if (importIndex !== -1 && importEndIndex !== -1) {
        content = content.slice(0, importEndIndex + 1) + 
                  'import { createLogger } from \'../utils/logger.js\';\n' +
                  'const logger = createLogger(\'' + filePath.split('/').pop()?.replace(/\.(ts|tsx)$/, '') + '\');\n' +
                  content.slice(importEndIndex + 1);
        modified = true;
      }
    }
    
    // Fix console.warn in catch blocks
    const consoleWarnPattern = /} catch \(([^)]+)\) \{ console\.warn\('([^']+)',?\s*([^)]*)\)?; \}/g;
    content = content.replace(consoleWarnPattern, (match, errorVar, message, extraArg) => {
      modified = true;
      if (extraArg && extraArg.trim()) {
        return `} catch (${errorVar}) { logger.error({ error: ${errorVar}, ${extraArg.replace(/['"]/g, '').trim()} }, '${message}'); }`;
      } else {
        return `} catch (${errorVar}) { logger.error({ error: ${errorVar} }, '${message}'); }`;
      }
    });
    
    // Fix empty catch blocks
    const emptyCatchPattern = /} catch \(([^)]+)\) \{\s*\}/g;
    content = content.replace(emptyCatchPattern, (match, errorVar) => {
      modified = true;
      return `} catch (${errorVar}) { logger.error({ error: ${errorVar} }, 'Unexpected error in catch block'); throw ${errorVar}; }`;
    });
    
    // Fix catch blocks with only comments
    const commentCatchPattern = /} catch \(([^)]+)\) \{\s*\/\/.*\s*\}/g;
    content = content.replace(commentCatchPattern, (match, errorVar) => {
      modified = true;
      return `} catch (${errorVar}) { logger.error({ error: ${errorVar} }, 'Error occurred'); throw ${errorVar}; }`;
    });
    
    // Fix catch blocks that just set empty arrays or defaults
    const defaultCatchPattern = /} catch \(([^)]+)\) \{\s*[^}]+\s*=\s*\[\s*\];?\s*\}/g;
    content = content.replace(defaultCatchPattern, (match, errorVar) => {
      modified = true;
      return `} catch (${errorVar}) { logger.warn({ error: ${errorVar} }, 'Failed to parse data, using default'); ${match.split('{')[1].split('}')[0].trim()} }`;
    });
    
    if (modified) {
      writeFileSync(filePath, content, 'utf-8');
      console.log(`✅ Fixed error handling in ${filePath}`);
    } else {
      console.log(`ℹ️  No error handling fixes needed in ${filePath}`);
    }
  } catch (error) {
    console.error(`❌ Error fixing ${filePath}:`, error);
  }
}

console.log('🔧 Starting error handling fixes...\n');

for (const file of filesToFix) {
  fixErrorHandling(file);
}

console.log('\n✅ Error handling fixes complete!');
