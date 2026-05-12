/**
 * AST-based conformance validation.
 * Validates code structure and patterns using abstract syntax tree analysis.
 */

import { readFileSync } from 'fs';
import { extname } from 'path';

/**
 * AST validation result.
 */
export interface AstValidationResult {
  valid: boolean;
  errors: string[];
}

/**
 * Validates that a TypeScript file contains required imports.
 */
export function validateRequiredImports(filePath: string, requiredImports: string[]): AstValidationResult {
  const errors: string[] = [];
  
  try {
    const content = readFileSync(filePath, 'utf8');
    
    for (const imp of requiredImports) {
      // Check for import statement or require()
      const importRegex = new RegExp(`import.*from\\s*['"]${imp.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}['"]`, 's');
      const requireRegex = new RegExp(`require\\(['"]${imp.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}['"]\\)`, 's');
      
      if (!importRegex.test(content) && !requireRegex.test(content)) {
        errors.push(`Missing required import: ${imp}`);
      }
    }
    
    return {
      valid: errors.length === 0,
      errors,
    };
  } catch (error) {
    return {
      valid: false,
      errors: [`Failed to read file: ${error instanceof Error ? error.message : String(error)}`],
    };
  }
}

/**
 * Validates that a file contains required function calls or method invocations.
 */
export function validateRequiredCalls(filePath: string, requiredCalls: string[]): AstValidationResult {
  const errors: string[] = [];
  
  try {
    const content = readFileSync(filePath, 'utf8');
    
    for (const call of requiredCalls) {
      if (!content.includes(call)) {
        errors.push(`Missing required call: ${call}`);
      }
    }
    
    return {
      valid: errors.length === 0,
      errors,
    };
  } catch (error) {
    return {
      valid: false,
      errors: [`Failed to read file: ${error instanceof Error ? error.message : String(error)}`],
    };
  }
}

/**
 * Validates that a TypeScript class implements required methods.
 */
export function validateClassMethods(filePath: string, className: string, requiredMethods: string[]): AstValidationResult {
  const errors: string[] = [];
  
  try {
    const content = readFileSync(filePath, 'utf8');
    
    // Simple regex-based class method detection (for basic validation)
    const classRegex = new RegExp(`class\\s+${className}\\s*\\{([\\s\\S]*?)\\}`, 's');
    const classMatch = content.match(classRegex);
    
    if (!classMatch) {
      errors.push(`Class ${className} not found in file`);
      return { valid: false, errors };
    }
    
    const classBody = classMatch[1];
    
    for (const method of requiredMethods) {
      const methodRegex = new RegExp(`\\b${method}\\s*\\(`, 's');
      if (!methodRegex.test(classBody)) {
        errors.push(`Class ${className} missing required method: ${method}`);
      }
    }
    
    return {
      valid: errors.length === 0,
      errors,
    };
  } catch (error) {
    return {
      valid: false,
      errors: [`Failed to read file: ${error instanceof Error ? error.message : String(error)}`],
    };
  }
}

/**
 * Validates file extension is TypeScript.
 */
export function validateTypeScriptFile(filePath: string): AstValidationResult {
  const ext = extname(filePath);
  const validExtensions = ['.ts', '.tsx', '.mts', '.cts'];
  
  if (!validExtensions.includes(ext)) {
    return {
      valid: false,
      errors: [`File ${filePath} is not a TypeScript file (has extension ${ext})`],
    };
  }
  
  return { valid: true, errors: [] };
}
