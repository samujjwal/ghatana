import { readFileSync, writeFileSync, readdirSync } from 'fs';
import { join } from 'path';

const routesDir = 'products/phr/src/main/java/com/ghatana/phr/api/routes';
const files = readdirSync(routesDir).filter(f => f.endsWith('.java'));

let fixedCount = 0;

for (const file of files) {
  const filePath = join(routesDir, file);
  let content = readFileSync(filePath, 'utf-8');
  const original = content;
  
  // Fix ONLY the syntax error: method(, correlationId) -> method()
  // Remove the correlationId from inside the parentheses entirely
  const methodsThatShouldNotTakeCorrelationId = [
    'getMessage', 'size', 'role', 'principalId', 'tenantId', 'encounterId', 
    'medicationId', 'registeredBy', 'fchvId', 'messageControlId', 'message',
    'get', 'id', 'documentId', 'patientId', 'studyId', 'now'
  ];
  
  for (const method of methodsThatShouldNotTakeCorrelationId) {
    const regex = new RegExp(`${method}\\(\\s*,\\s*correlationId\\)`, 'g');
    content = content.replace(regex, `${method}()`);
  }
  
  if (content !== original) {
    writeFileSync(filePath, content, 'utf-8');
    fixedCount++;
    console.log(`Fixed: ${file}`);
  }
}

console.log(`\nFixed ${fixedCount} files`);
