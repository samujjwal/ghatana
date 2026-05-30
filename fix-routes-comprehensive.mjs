import { readFileSync, writeFileSync, readdirSync } from 'fs';
import { join } from 'path';

const routesDir = 'products/phr/src/main/java/com/ghatana/phr/api/routes';
const files = readdirSync(routesDir).filter(f => f.endsWith('.java'));

let fixedCount = 0;

for (const file of files) {
  const filePath = join(routesDir, file);
  let content = readFileSync(filePath, 'utf-8');
  const original = content;
  
  // Pattern 1: Fix method(, correlationId) -> method() where correlationId should NOT be inside
  // These are methods that don't take correlationId as a parameter
  const methodsWithoutCorrelationId = [
    'getMessage', 'size', 'role', 'principalId', 'tenantId', 'encounterId', 
    'medicationId', 'registeredBy', 'fchvId', 'messageControlId', 'message',
    'get', 'id', 'documentId', 'patientId', 'studyId', 'now'
  ];
  
  for (const method of methodsWithoutCorrelationId) {
    const regex = new RegExp(`${method}\\(\\s*,\\s*correlationId\\)`, 'g');
    content = content.replace(regex, `${method}()`);
  }
  
  // Pattern 2: Fix .size(, correlationId) inside Map.of -> .size(), and add correlationId to jsonResponse
  // Map.of("count", items.size(, correlationId)) -> Map.of("count", items.size()), correlationId
  content = content.replace(/Map\.of\(([^)]*\.size\(\s*,\s*correlationId\)[^)]*)\)/g, (match, inner) => {
    const fixedInner = inner.replace(/\.size\(\s*,\s*correlationId\)/g, '.size()');
    return `Map.of(${fixedInner}), correlationId`;
  });
  
  // Pattern 3: Fix Map.of(..., key(, correlationId)) -> Map.of(..., key()), and add correlationId to jsonResponse
  content = content.replace(/Map\.of\(([^)]*\w\(\s*,\s*correlationId\)[^)]*)\)/g, (match, inner) => {
    const fixedInner = inner.replace(/(\w+)\(\s*,\s*correlationId\)/g, '$1()');
    return `Map.of(${fixedInner}), correlationId`;
  });
  
  // Pattern 4: Fix trailing comma issues in Map.of
  // Map.of("key", value, correlationId) -> Map.of("key", value), correlationId
  content = content.replace(/Map\.of\(([^)]*),\s*correlationId\)/g, 'Map.of($1), correlationId');
  
  // Pattern 5: Add missing correlationId to jsonResponse calls that end with Map.of(...) without correlationId
  // jsonResponse(200, Map.of(...)) -> jsonResponse(200, Map.of(...), correlationId)
  content = content.replace(/PhrRouteSupport\.jsonResponse\((\d+),\s*Map\.of\([^)]+\)\)(?!\s*,\s*correlationId)/g, 
    (match, statusCode) => {
      // Check if this is inside a method that has correlationId available
      // For now, just add it - we'll fix any false positives manually
      return `PhrRouteSupport.jsonResponse(${statusCode}, $&.match(/Map\.of\([^)]+\)/)[0], correlationId)`;
    });
  
  if (content !== original) {
    writeFileSync(filePath, content, 'utf-8');
    fixedCount++;
    console.log(`Fixed: ${file}`);
  }
}

console.log(`\nFixed ${fixedCount} files`);
