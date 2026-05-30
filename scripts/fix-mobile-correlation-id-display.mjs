#!/usr/bin/env node

/**
 * G11-T02: Automated fix to display correlation IDs in mobile error states.
 * 
 * This script adds correlation ID to Alert.alert calls in mobile screens.
 */

import { readFileSync, writeFileSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const SCREENS_DIR = join(__dirname, '..', 'products', 'phr', 'apps', 'mobile', 'src', 'screens');

function fixFile(filePath) {
  let content = readFileSync(filePath, 'utf-8');
  let modified = false;
  const fileName = filePath.split('\\').pop().split('/').pop();

  // Add correlation ID generation at the top of the file if not present
  if (!content.includes('newCorrelationId')) {
    // Find the first import line and add helper function after imports
    const importEndIndex = content.lastIndexOf('import');
    const insertPosition = content.indexOf('\n', importEndIndex) + 1;
    content = content.slice(0, insertPosition) + 
              "\nfunction newCorrelationId(): string {\n  return crypto.randomUUID();\n}\n\n" + 
              content.slice(insertPosition);
    modified = true;
  }

  // Replace Alert.alert calls to include correlation ID in error messages
  // Pattern: Alert.alert(title, message, buttons)
  content = content.replace(
    /Alert\.alert\(\s*([^,]+),\s*([^,]+),\s*\[([^\]]+)\]\s*\)/g,
    (match, title, message, buttons) => {
      // Skip if already has correlation ID
      if (match.includes('correlationId') || match.includes('Correlation')) return match;
      
      // Only add correlation ID to error alerts (not confirmations)
      if (title.includes('Error') || title.includes('Failed') || message.includes('error') || message.includes('failed')) {
        modified = true;
        const correlationVar = 'correlation-' + Math.random().toString(36).substr(2, 9);
        return `const ${correlationVar} = newCorrelationId();\n    Alert.alert(${title}, ${message} + ' (ID: ' + ${correlationVar} + ')', [${buttons}])`;
      }
      return match;
    }
  );

  // Replace throw Error calls to include correlation ID
  content = content.replace(
    /throw new Error\(([^)]+)\)/g,
    (match, message) => {
      if (match.includes('correlationId') || match.includes('Correlation')) return match;
      if (message.includes('error') || message.includes('failed') || message.includes('Error')) {
        modified = true;
        return `throw new Error(${message} + ' (ID: ' + newCorrelationId() + ')')`;
      }
      return match;
    }
  );

  if (modified) {
    writeFileSync(filePath, content, 'utf-8');
    return true;
  }
  return false;
}

function main() {
  try {
    const files = readdirSync(SCREENS_DIR).filter(f => f.endsWith('Screen.tsx') && !f.includes('.test.'));
    let fixedCount = 0;
    
    for (const file of files) {
      if (fixFile(join(SCREENS_DIR, file))) {
        console.log(`Fixed: ${file}`);
        fixedCount++;
      }
    }
    
    console.log(`\nFixed ${fixedCount} files`);
    process.exit(0);
  } catch (error) {
    console.error('Error running fix:', error.message);
    process.exit(1);
  }
}

main();
