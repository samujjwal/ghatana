import { readFileSync, writeFileSync } from 'fs';
import { glob } from 'glob';

const routeFiles = glob.sync('products/phr/src/main/java/com/ghatana/phr/api/routes/*.java');

let fixedCount = 0;

for (const file of routeFiles) {
  let content = readFileSync(file, 'utf-8');
  const original = content;
  
  // Fix getMessage(, correlationId) -> getMessage(), correlationId
  content = content.replace(/getMessage\(\s*,\s*correlationId\)/g, 'getMessage(), correlationId');
  
  // Fix .size(, correlationId) -> .size(), correlationId
  content = content.replace(/\.size\(\s*,\s*correlationId\)/g, '.size(), correlationId');
  
  // Fix cases where correlationId is passed as argument in Map.of
  content = content.replace(/("count",\s*events\.size\(\)),\s*correlationId/g, '$1, correlationId');
  
  if (content !== original) {
    writeFileSync(file, content, 'utf-8');
    fixedCount++;
    console.log(`Fixed: ${file}`);
  }
}

console.log(`\nFixed ${fixedCount} files`);
