#!/usr/bin/env node
/**
 * Agent Catalog Schema Migration Tool
 * Migrates agent definitions from v1.0.0 to v2.0.0
 * 
 * Usage: node schema-migration.js <agent-file.yaml>
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const SCHEMA_V1 = '1.0.0';
const SCHEMA_V2 = '2.0.0';

class SchemaMigrator {
  constructor() {
    this.errors = [];
    this.warnings = [];
  }

  /**
   * Migrate agent definition from v1 to v2
   */
  migrate(agentDef) {
    const version = agentDef.schemaVersion || SCHEMA_V1;
    
    if (version === SCHEMA_V2) {
      this.warnings.push('Agent already at schema v2.0.0');
      return agentDef;
    }
    
    if (version !== SCHEMA_V1) {
      this.errors.push(`Unknown schema version: ${version}`);
      return null;
    }
    
    console.log(`Migrating ${agentDef.id} from v${SCHEMA_V1} to v${SCHEMA_V2}`);
    
    const migrated = { ...agentDef };
    
    // Update schema version
    migrated.schemaVersion = SCHEMA_V2;
    
    // Migrate generator.type to identity.agentType
    if (migrated.generator && migrated.generator.type) {
      if (!migrated.identity) {
        migrated.identity = {};
      }
      
      migrated.identity.agentType = migrated.generator.type;
      
      this.warnings.push(
        `Migrated generator.type="${migrated.generator.type}" to identity.agentType`
      );
      
      // Mark generator as deprecated but keep for backward compatibility
      migrated.generator._deprecated = true;
      migrated.generator._migratedTo = 'identity.agentType';
    }
    
    // Ensure required v2.0.0 fields exist
    this.ensureRequiredFields(migrated);
    
    // Validate migrated definition
    this.validate(migrated);
    
    return migrated;
  }
  
  /**
   * Ensure all required v2.0.0 fields are present
   */
  ensureRequiredFields(agentDef) {
    const required = {
      id: agentDef.id,
      name: agentDef.name,
      namespace: agentDef.namespace || 'default',
      version: agentDef.version || '1.0.0',
      status: agentDef.status || 'active',
      owners: agentDef.owners || [],
      summary: agentDef.summary || '',
      identity: agentDef.identity || {}
    };
    
    Object.keys(required).forEach(field => {
      if (!agentDef[field]) {
        this.warnings.push(`Missing required field: ${field}, using default`);
        agentDef[field] = required[field];
      }
    });
    
    // Ensure identity has required subfields
    if (!agentDef.identity.agentType) {
      this.errors.push('identity.agentType is required in v2.0.0');
    }
  }
  
  /**
   * Validate migrated agent definition
   */
  validate(agentDef) {
    // Check for deprecated generator usage
    if (agentDef.generator && !agentDef.generator._deprecated) {
      this.warnings.push(
        'generator field should be migrated to identity.agentType'
      );
    }
    
    // Validate agent type
    const validTypes = [
      'DETERMINISTIC',
      'PROBABILISTIC',
      'HYBRID',
      'RULE_BASED',
      'ML_BASED',
      'LLM_BASED'
    ];
    
    if (agentDef.identity.agentType && 
        !validTypes.includes(agentDef.identity.agentType)) {
      this.warnings.push(
        `Unknown agentType: ${agentDef.identity.agentType}`
      );
    }
    
    // Check for legacy type names
    if (agentDef.identity.agentType === 'DETERMINISTIC_LEGACY') {
      this.errors.push(
        'DETERMINISTIC_LEGACY is deprecated, use DETERMINISTIC instead'
      );
    }
    if (agentDef.identity.agentType === 'PROBABILISTIC_LEGACY') {
      this.errors.push(
        'PROBABILISTIC_LEGACY is deprecated, use PROBABILISTIC instead'
      );
    }
  }
  
  /**
   * Migrate all agents in a directory
   */
  migrateDirectory(dirPath) {
    const files = fs.readdirSync(dirPath);
    const results = {
      migrated: [],
      failed: [],
      skipped: []
    };
    
    files.forEach(file => {
      if (!file.endsWith('.yaml') && !file.endsWith('.yml')) {
        return;
      }
      
      const filePath = path.join(dirPath, file);
      console.log(`\nProcessing: ${file}`);
      
      try {
        const content = fs.readFileSync(filePath, 'utf8');
        const agentDef = yaml.load(content);
        
        this.errors = [];
        this.warnings = [];
        
        const migrated = this.migrate(agentDef);
        
        if (this.errors.length > 0) {
          console.error('  ❌ Errors:');
          this.errors.forEach(err => console.error(`     - ${err}`));
          results.failed.push(file);
          return;
        }
        
        if (this.warnings.length > 0) {
          console.warn('  ⚠️  Warnings:');
          this.warnings.forEach(warn => console.warn(`     - ${warn}`));
        }
        
        if (migrated) {
          // Write migrated file
          const backupPath = `${filePath}.v1.backup`;
          fs.copyFileSync(filePath, backupPath);
          
          const migratedYaml = yaml.dump(migrated, {
            indent: 2,
            lineWidth: 100,
            noRefs: true
          });
          
          fs.writeFileSync(filePath, migratedYaml, 'utf8');
          
          console.log(`  ✅ Migrated (backup: ${path.basename(backupPath)})`);
          results.migrated.push(file);
        } else {
          results.skipped.push(file);
        }
      } catch (error) {
        console.error(`  ❌ Error: ${error.message}`);
        results.failed.push(file);
      }
    });
    
    return results;
  }
}

// CLI
if (require.main === module) {
  const args = process.argv.slice(2);
  
  if (args.length === 0) {
    console.error('Usage: node schema-migration.js <agent-file.yaml|directory>');
    console.error('');
    console.error('Examples:');
    console.error('  node schema-migration.js my-agent.yaml');
    console.error('  node schema-migration.js ./core-agents/');
    process.exit(1);
  }
  
  const target = args[0];
  const migrator = new SchemaMigrator();
  
  if (fs.statSync(target).isDirectory()) {
    console.log(`Migrating all agents in: ${target}\n`);
    const results = migrator.migrateDirectory(target);
    
    console.log('\n=== Migration Summary ===');
    console.log(`✅ Migrated: ${results.migrated.length}`);
    console.log(`⚠️  Skipped: ${results.skipped.length}`);
    console.log(`❌ Failed: ${results.failed.length}`);
    
    if (results.failed.length > 0) {
      process.exit(1);
    }
  } else {
    // Single file migration
    const content = fs.readFileSync(target, 'utf8');
    const agentDef = yaml.load(content);
    
    const migrated = migrator.migrate(agentDef);
    
    if (migrator.errors.length > 0) {
      console.error('Errors:');
      migrator.errors.forEach(err => console.error(`  - ${err}`));
      process.exit(1);
    }
    
    if (migrator.warnings.length > 0) {
      console.warn('Warnings:');
      migrator.warnings.forEach(warn => console.warn(`  - ${warn}`));
    }
    
    if (migrated) {
      const migratedYaml = yaml.dump(migrated, {
        indent: 2,
        lineWidth: 100,
        noRefs: true
      });
      
      console.log('\nMigrated YAML:');
      console.log('---');
      console.log(migratedYaml);
    }
  }
}

module.exports = { SchemaMigrator };
