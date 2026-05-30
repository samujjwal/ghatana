import { readFileSync, writeFileSync, readdirSync } from 'fs';
import { join } from 'path';

const routesDir = 'products/phr/src/main/java/com/ghatana/phr/api/routes';
const files = readdirSync(routesDir).filter(f => f.endsWith('.java'));

let fixedCount = 0;

for (const file of files) {
  const filePath = join(routesDir, file);
  let content = readFileSync(filePath, 'utf-8');
  const original = content;
  
  // Only fix the specific pattern: method(, correlationId) -> method(), correlationId
  // This is a syntax error where comma is inside the parentheses
  content = content.replace(/(\w+)\(\s*,\s*correlationId\)/g, '$1(), correlationId');
  
  // Fix .size(, correlationId) -> .size(), correlationId
  content = content.replace(/\.size\(\s*,\s*correlationId\)/g, '.size(), correlationId');
  
  if (content !== original) {
    writeFileSync(filePath, content, 'utf-8');
    fixedCount++;
    console.log(`Fixed: ${file}`);
  }
}

console.log(`\nFixed ${fixedCount} files`);
