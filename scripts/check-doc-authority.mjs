#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

function findMarkdownFiles(dir, root) {
  const files = [];
  const entries = readdirSync(dir);
  
  for (const entry of entries) {
    const fullPath = path.join(dir, entry);
    const stat = statSync(fullPath);
    
    if (stat.isDirectory()) {
      // Skip node_modules and hidden directories
      if (!entry.startsWith('.') && entry !== 'node_modules') {
        files.push(...findMarkdownFiles(fullPath, root));
      }
    } else if (entry.endsWith('.md')) {
      files.push(fullPath);
    }
  }
  
  return files;
}

export function validateDocAuthority(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const issues = [];
  
  // Load documentation authority map
  const authorityMapPath = path.join(root, 'config/documentation-authority-map.json');
  let authorityMap = null;
  
  if (!existsSync(authorityMapPath)) {
    issues.push('config/documentation-authority-map.json: missing documentation authority map. Remediation: create the map defining rule-to-authoritative-source relationships.');
  } else {
    try {
      authorityMap = JSON.parse(readFileSync(authorityMapPath, 'utf8'));
    } catch (error) {
      issues.push(`config/documentation-authority-map.json: invalid JSON. Error: ${error.message}`);
    }
  }

  // Check for old package registries that redefine canonical governance
  const oldPackageRegistryPaths = [
    'config/package-registry.json',
    'config/ts-package-registry.json',
    'config/platform-package-registry.json',
    'docs/package-registry.md',
    'docs/platform-package-registry.md'
  ];

  for (const oldPath of oldPackageRegistryPaths) {
    const fullPath = path.join(root, oldPath);
    if (existsSync(fullPath)) {
      const content = readFileSync(fullPath, 'utf8');
      if (content.includes('@ghatana/') || content.includes('package registry')) {
        issues.push(`${oldPath}: old package registry must not redefine platform library governance. Remediation: delete this file and reference platform/typescript/LIBRARY_GOVERNANCE.md instead.`);
      }
    }
  }

  // Validate that documents don't claim target architecture as current executable state
  const docsDir = path.join(root, 'docs');
  const docsToCheck = existsSync(docsDir) ? findMarkdownFiles(docsDir, root) : [];
  for (const docPath of docsToCheck) {
    const content = readFileSync(docPath, 'utf8');
    const relativePath = path.relative(root, docPath);
    
    // Check for target architecture claims without explicit "target-architecture" classification
    if (/target\s+architecture/i.test(content) && !/target-architecture|declared-only/i.test(content)) {
      // Allow if it's clearly marked as target/declared-only
      if (!/target\s+architecture.*declared|target\s+architecture.*not.*executable/i.test(content)) {
        issues.push(`${relativePath}: claims target architecture without explicit classification. Remediation: mark as 'target-architecture' or 'declared-only' classification, or remove executable state claims.`);
      }
    }
  }

  // Validate rule authority if map is loaded
  if (authorityMap) {
    for (const rule of authorityMap.rules) {
      const authoritativePath = path.join(root, rule.authoritativeDocument);
      
      if (!existsSync(authoritativePath)) {
        issues.push(`${rule.authoritativeDocument}: authoritative document for rule '${rule.ruleId}' does not exist. Remediation: create the document or update the authority map.`);
        continue;
      }

      // Check that dependent documents reference the authoritative source
      for (const depDoc of rule.dependentDocuments) {
        const depPath = path.join(root, depDoc);
        if (existsSync(depPath)) {
          const stat = statSync(depPath);
          if (stat.isDirectory()) {
            continue; // Skip directories
          }
          const content = readFileSync(depPath, 'utf8');
          const authoritativeName = path.basename(rule.authoritativeDocument);
          
          // Check if the dependent document references the authoritative source
          if (!content.includes(authoritativeName) && !content.includes(rule.authoritativeDocument)) {
            issues.push(`${depDoc}: depends on '${rule.ruleId}' but does not reference authoritative source '${rule.authoritativeDocument}'. Remediation: add a reference to the authoritative document.`);
          }
        }
      }

      // Check forbidden duplicate patterns
      for (const pattern of rule.forbiddenDuplicatePatterns) {
        // Simple pattern matching - convert glob pattern to regex
        const patternRegex = new RegExp(pattern.replace(/\*/g, '.*').replace(/\?/g, '.'));
        const allFiles = findMarkdownFiles(root, root).concat(findMarkdownFiles(path.join(root, 'config'), root));
        
        for (const filePath of allFiles) {
          const relativePath = path.relative(root, filePath);
          if (patternRegex.test(relativePath)) {
            const content = readFileSync(filePath, 'utf8');
            
            // Check if the file redefines the rule without referencing the authoritative source
            if (content.toLowerCase().includes(rule.name.toLowerCase()) || 
                content.toLowerCase().includes(rule.ruleId.toLowerCase())) {
              if (!content.includes(rule.authoritativeDocument)) {
                issues.push(`${relativePath}: redefines rule '${rule.ruleId}' without referencing authoritative source '${rule.authoritativeDocument}'. Remediation: add reference or remove redefinition.`);
              }
            }
          }
        }
      }
    }

    // Validate duplicate patterns
    if (authorityMap.duplicatePatterns) {
      for (const duplicatePattern of authorityMap.duplicatePatterns) {
        const allFiles = findMarkdownFiles(root, root).concat(findMarkdownFiles(path.join(root, 'config'), root));
        
        for (const filePath of allFiles) {
          const relativePath = path.relative(root, filePath);
          
          // Check if file matches any forbidden pattern
          const matchesForbiddenPattern = Array.isArray(duplicatePattern.forbiddenIn)
            ? duplicatePattern.forbiddenIn.some(pattern => {
                const patternRegex = new RegExp(pattern.replace(/\*/g, '.*').replace(/\?/g, '.'));
                return patternRegex.test(relativePath);
              })
            : false;
          
          if (matchesForbiddenPattern) {
            const content = readFileSync(filePath, 'utf8');
            
            if (content.toLowerCase().includes(duplicatePattern.pattern.toLowerCase())) {
              if (!content.includes(duplicatePattern.mustReference)) {
                issues.push(`${relativePath}: mentions '${duplicatePattern.pattern}' without referencing '${duplicatePattern.mustReference}'. Remediation: add reference to authoritative source.`);
              }
            }
          }
        }
      }
    }
  }

  return issues;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const issues = validateDocAuthority();

  if (issues.length === 0) {
    console.log('OK: documentation authority checks passed.');
    process.exit(0);
  }

  console.error('FAIL: documentation authority checks found issues:');
  for (const issue of issues) {
    console.error(` - ${issue}`);
  }
  process.exit(1);
}