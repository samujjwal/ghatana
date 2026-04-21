#!/usr/bin/env node
/**
 * Agent Catalog Schema Validation Script
 * Validates all agent YAML files against the catalog schema.
 * 
 * Usage: node scripts/validate-agent-catalog.js
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const Ajv = require('ajv');
const ajvFormats = require('ajv-formats');

const CATALOG_DIR = path.join(__dirname, '../platform/agent-catalog');
const SCHEMA_FILE = path.join(CATALOG_DIR, 'catalog-schema.yaml');

// Load the schema
let schema;
try {
  const schemaContent = fs.readFileSync(SCHEMA_FILE, 'utf8');
  const schemaDoc = yaml.load(schemaContent);
  schema = schemaDoc.schema;
  console.log('✓ Loaded catalog schema v' + schema.version);
} catch (error) {
  console.error('✗ Failed to load schema:', error.message);
  process.exit(1);
}

// Initialize AJV with formats
const ajv = new Ajv({ allErrors: true, strict: false });
ajvFormats(ajv);

// Compile the schema
const validate = ajv.compile(schema);

// Find all agent descriptor YAML files in the catalog
function findYamlFiles(dir, baseDir = dir) {
  const files = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    const relativePath = path.relative(baseDir, fullPath);
    
    if (entry.isDirectory()) {
      // Skip node_modules and hidden directories
      if (!entry.name.startsWith('.') && entry.name !== 'node_modules') {
        files.push(...findYamlFiles(fullPath, baseDir));
      }
    } else if (entry.isFile() && entry.name.endsWith('-agent.yaml')) {
      // Only validate agent descriptor files (ending with -agent.yaml)
      files.push(fullPath);
    }
  }
  
  return files;
}

// Validate a single YAML file
function validateFile(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    const data = yaml.load(content);
    
    // Check for schemaVersion
    if (data.schemaVersion) {
      if (data.schemaVersion !== schema.version) {
        return {
          file: filePath,
          valid: false,
          errors: [`Schema version mismatch: file has v${data.schemaVersion}, schema is v${schema.version}`]
        };
      }
    } else {
      return {
        file: filePath,
        valid: false,
        errors: ['Missing required field: schemaVersion']
      };
    }
    
    // Validate against schema
    const valid = validate(data);
    
    if (!valid) {
      return {
        file: filePath,
        valid: false,
        errors: validate.errors.map(err => `${err.instancePath || 'root'}: ${err.message}`)
      };
    }
    
    return { file: filePath, valid: true, errors: [] };
  } catch (error) {
    return {
      file: filePath,
      valid: false,
      errors: [`Failed to parse YAML: ${error.message}`]
    };
  }
}

// Main validation
console.log('\n=== Agent Catalog Schema Validation ===\n');

const yamlFiles = findYamlFiles(CATALOG_DIR);
console.log(`Found ${yamlFiles.length} YAML files to validate\n`);

let failed = 0;
let passed = 0;

for (const file of yamlFiles) {
  const relativePath = path.relative(CATALOG_DIR, file);
  const result = validateFile(file);
  
  if (result.valid) {
    console.log(`✓ ${relativePath}`);
    passed++;
  } else {
    console.log(`✗ ${relativePath}`);
    for (const error of result.errors) {
      console.log(`  - ${error}`);
    }
    failed++;
  }
}

console.log(`\n=== Summary ===`);
console.log(`Passed: ${passed}`);
console.log(`Failed: ${failed}`);

if (failed > 0) {
  console.error('\n✗ Schema validation failed');
  process.exit(1);
} else {
  console.log('\n✓ All YAML files passed validation');
  process.exit(0);
}
