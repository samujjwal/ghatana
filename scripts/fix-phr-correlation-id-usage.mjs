#!/usr/bin/env node

/**
 * Automated fix for G11-T01: Add correlation ID to all backend responses.
 * 
 * This script fixes:
 * 1. PhrRouteSupport.errorResponse(statusCode, code, message) -> errorResponse(statusCode, code, message, correlationId)
 * 2. PhrRouteSupport.jsonResponse(statusCode, body) -> jsonResponse(statusCode, body, correlationId)
 * 3. Adds correlationId extraction at the start of handler methods if missing
 */

import { readFileSync, writeFileSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const ROUTES_DIR = join(__dirname, '..', 'products', 'phr', 'src', 'main', 'java', 'com', 'ghatana', 'phr', 'api', 'routes');

function fixFile(filePath) {
  let content = readFileSync(filePath, 'utf-8');
  let modified = false;
  
  // Fix errorResponse calls with 3 args to 4 args
  content = content.replace(
    /PhrRouteSupport\.errorResponse\((\d+),\s*"([^"]+)",\s*([^)]+)\)(\s*)(?!;)/g,
    (match, statusCode, code, message, trailing) => {
      // Check if the closing paren is followed by semicolon or end of statement
      const fullMatch = match + trailing;
      if (fullMatch.includes('correlationId')) {
        return match; // Already has correlationId
      }
      modified = true;
      return `PhrRouteSupport.errorResponse(${statusCode}, "${code}", ${message}, correlationId)${trailing}`;
    }
  );
  
  // Fix jsonResponse calls with 2 args to 3 args
  content = content.replace(
    /PhrRouteSupport\.jsonResponse\((\d+),\s*([^)]+)\)(\s*)(?!;)/g,
    (match, statusCode, body, trailing) => {
      if (match.includes('correlationId')) {
        return match; // Already has correlationId
      }
      modified = true;
      return `PhrRouteSupport.jsonResponse(${statusCode}, ${body}, correlationId)${trailing}`;
    }
  );
  
  // Add correlationId extraction at the start of handler methods if missing
  // Pattern: private Promise<HttpResponse> handleXxx(HttpRequest request) {
  content = content.replace(
    /(private Promise<HttpResponse> handle\w+\(HttpRequest request\)\s*\{)/g,
    (match) => {
      // Check if the next few lines already have correlationId extraction
      const methodStart = content.indexOf(match);
      const methodContent = content.substring(methodStart, Math.min(methodStart + 500, content.length));
      
      if (methodContent.includes('extractCorrelationId') || methodContent.includes('correlationId =')) {
        return match; // Already has correlationId extraction
      }
      
      modified = true;
      return match + '\n        String correlationId = PhrRouteSupport.extractCorrelationId(request);';
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
    const files = readdirSync(ROUTES_DIR).filter(f => f.endsWith('Routes.java'));
    let fixedCount = 0;
    
    for (const file of files) {
      if (fixFile(join(ROUTES_DIR, file))) {
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
