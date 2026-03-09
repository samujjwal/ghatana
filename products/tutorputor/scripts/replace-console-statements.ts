#!/usr/bin/env node

/**
 * Console Statement Replacer
 * 
 * Automatically replaces console.log, console.error, console.warn, console.info
 * statements with structured logging calls.
 */

import { readFileSync, writeFileSync } from 'fs';
import { glob } from 'glob';
import { resolve } from 'path';

interface ConsoleStatement {
  line: number;
  column: number;
  type: 'log' | 'error' | 'warn' | 'info' | 'debug';
  content: string;
  fullLine: string;
}

class ConsoleReplacer {
  private loggerImports = new Set<string>();
  private loggerInstances = new Map<string, string>();

  async replaceInDirectory(pattern: string): Promise<void> {
    const files = await glob(pattern, { cwd: process.cwd() });
    
    for (const file of files) {
      await this.replaceInFile(file);
    }
  }

  async replaceInFile(filePath: string): Promise<void> {
    try {
      const content = readFileSync(filePath, 'utf-8');
      const lines = content.split('\n');
      
      const statements = this.findConsoleStatements(lines);
      
      if (statements.length === 0) {
        return;
      }

      console.log(`🔄 Processing ${filePath} (${statements.length} console statements)`);
      
      const modifiedContent = this.replaceStatements(lines, statements);
      
      // Add import if needed
      const finalContent = this.addLoggerImport(modifiedContent, filePath);
      
      writeFileSync(filePath, finalContent, 'utf-8');
      console.log(`✅ Updated ${filePath}`);
    } catch (error) {
      console.error(`❌ Error processing ${filePath}:`, error);
    }
  }

  private findConsoleStatements(lines: string[]): ConsoleStatement[] {
    const statements: ConsoleStatement[] = [];
    
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const matches = line.match(/console\.(log|error|warn|info|debug)\s*\((.*)\)/);
      
      if (matches) {
        const [, type, content] = matches;
        statements.push({
          line: i + 1,
          column: line.indexOf('console.'),
          type: type as ConsoleStatement['type'],
          content: content.trim(),
          fullLine: line.trim(),
        });
      }
    }
    
    return statements;
  }

  private replaceStatements(lines: string[], statements: ConsoleStatement[]): string[] {
    const modifiedLines = [...lines];
    
    // Process in reverse order to avoid line number shifts
    for (let i = statements.length - 1; i >= 0; i--) {
      const statement = statements[i];
      const lineNumber = statement.line - 1;
      
      const replacement = this.generateReplacement(statement);
      modifiedLines[lineNumber] = replacement;
    }
    
    return modifiedLines;
  }

  private generateReplacement(statement: ConsoleStatement): string {
    const { type, content, fullLine } = statement;
    
    // Determine appropriate logger method
    const loggerMethod = this.mapConsoleToLogger(type);
    
    // Generate context object if needed
    let context = '';
    let message = '';
    
    if (content.includes(',')) {
      // Multiple arguments - first is message, rest are context
      const parts = content.split(',').map(part => part.trim());
      message = parts[0];
      
      if (parts.length > 1) {
        context = parts.slice(1).join(', ');
      }
    } else {
      // Single argument - treat as message
      message = content;
    }
    
    // Generate logger instance name
    const loggerName = this.getLoggerInstance(statement.fullLine);
    
    if (context) {
      return `  ${loggerName}.${loggerMethod}({ ${context} }, ${message});`;
    } else {
      return `  ${loggerName}.${loggerMethod}({}, ${message});`;
    }
  }

  private mapConsoleToLogger(consoleType: string): string {
    const mapping = {
      log: 'info',
      error: 'error',
      warn: 'warn',
      info: 'info',
      debug: 'debug',
    };
    
    return mapping[consoleType as keyof typeof mapping] || 'info';
  }

  private getLoggerInstance(line: string): string {
    // Check if there's already a logger in the file context
    const hasLoggerImport = line.includes('createLogger') || line.includes('logger');
    
    if (hasLoggerImport) {
      return 'logger';
    }
    
    // Default logger for different file types
    if (line.includes('test') || line.includes('spec')) {
      return 'testLogger';
    }
    
    return 'logger';
  }

  private addLoggerImport(content: string, filePath: string): string {
    const lines = content.split('\n');
    
    // Check if logger is already imported
    const hasLoggerImport = lines.some(line => 
      line.includes('createLogger') || 
      line.includes('import.*logger') ||
      line.includes('from.*logger')
    );
    
    if (hasLoggerImport) {
      return content;
    }
    
    // Find the last import statement
    const lastImportIndex = lines.reduce((lastIndex, line, index) => {
      if (line.trim().startsWith('import ')) {
        return index;
      }
      return lastIndex;
    }, -1);
    
    if (lastImportIndex === -1) {
      // No imports found, add at the beginning
      lines.unshift('import { createLogger } from \'../utils/logger.js\';');
      lines.unshift('');
    } else {
      // Add after last import
      lines.splice(lastImportIndex + 1, 0, 'import { createLogger } from \'../utils/logger.js\';');
    }
    
    // Add logger instance declaration
    const loggerIndex = lines.findIndex(line => 
      line.trim().startsWith('const ') && 
      line.includes('logger')
    );
    
    if (loggerIndex === -1) {
      // Find a good place to add logger declaration
      const functionIndex = lines.findIndex(line => 
        line.trim().startsWith('function ') ||
        line.trim().startsWith('class ') ||
        line.trim().startsWith('const ') && line.includes('=>')
      );
      
      if (functionIndex !== -1) {
        lines.splice(functionIndex, 0, 'const logger = createLogger(\'module\');');
        lines.splice(functionIndex, 0, '');
      }
    }
    
    return lines.join('\n');
  }
}

// Main execution
async function main() {
  const replacer = new ConsoleReplacer();
  
  const patterns = [
    'apps/tutorputor-web/src/**/*.ts',
    'apps/tutorputor-web/src/**/*.tsx',
    'apps/tutorputor-admin/src/**/*.ts',
    'apps/tutorputor-admin/src/**/*.tsx',
    'services/tutorputor-platform/src/**/*.ts',
    'services/tutorputor-domain-loader/src/**/*.ts',
    'services/tutorputor-domain-loader/src/**/*.js',
    'libs/learning-engine/src/**/*.ts',
    'libs/learning-kernel/src/**/*.ts',
    'libs/assessments/src/**/*.ts',
    'libs/physics-simulation/src/**/*.ts',
    'libs/learning-path/src/**/*.ts',
  ];
  
  console.log('🔍 Starting console statement replacement...\n');
  
  for (const pattern of patterns) {
    console.log(`📁 Processing pattern: ${pattern}`);
    await replacer.replaceInDirectory(pattern);
  }
  
  console.log('\n✅ Console statement replacement complete!');
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch(console.error);
}

export { ConsoleReplacer };
