#!/usr/bin/env node

/**
 * Generates JSON Schemas from Protocol Buffer definitions
 * 
 * Usage:
 *   node scripts/generate-json-schemas.js
 */

const fs = require('fs');
const path = require('path');
const { promisify } = require('util');
const { compile } = require('json-schema-to-typescript');
const protobuf = require('protobufjs');
const { toJSONSchema } = require('protobufjs-to-json-schema');

const _readFile = promisify(fs.readFile);
const writeFile = promisify(fs.writeFile);
const mkdir = promisify(fs.mkdir);

const PROTO_DIR = path.resolve(__dirname, '../proto');
const OUTPUT_DIR = path.resolve(__dirname, '../schemas');

// Ensure output directory exists
async function ensureDir(dir) {
  try {
    await mkdir(dir, { recursive: true });
  } catch (err) {
    if (err.code !== 'EEXIST') throw err;
  }
}

// Convert proto type to JSON Schema type
function toJsonSchemaType(type) {
  const types = {
    'string': 'string',
    'number': 'number',
    'bool': 'boolean',
    'bytes': 'string',
    'int32': 'integer',
    'int64': 'string', // Use string for 64-bit integers to avoid precision issues
    'uint32': 'integer',
    'uint64': 'string',
    'sint32': 'integer',
    'sint64': 'string',
    'fixed32': 'integer',
    'fixed64': 'string',
    'sfixed32': 'integer',
    'sfixed64': 'string',
    'double': 'number',
    'float': 'number',
  };
  return types[type] || 'object';
}

// Generate JSON Schema from proto message
async function generateSchema(root, messageType) {
  const message = root.lookupType(messageType);
  if (!message) {
    throw new Error(`Message type ${messageType} not found`);
  }

  const schema = {
    $schema: 'http://json-schema.org/draft-07/schema#',
    $id: `https://dcmaar.dev/schemas/${messageType.replace(/\./g, '/')}.json`,
    title: messageType.split('.').pop(),
    version: '0.5.0',
    description: message.comment || '',
    type: 'object',
    properties: {},
    required: [],
  };

  // Add fields to schema
  for (const [fieldName, field] of Object.entries(message.fields)) {
    const _fieldType = field.type;
    const isRepeated = field.repeated;
    
    let fieldSchema = {
      type: toJsonSchemaType(field.type),
      description: field.comment || '',
    };

    // Handle repeated fields
    if (isRepeated) {
      fieldSchema = {
        type: 'array',
        items: fieldSchema,
      };
    }

    // Handle enums
    if (field.resolvedType && field.resolvedType instanceof protobuf.Enum) {
      fieldSchema.enum = Object.values(field.resolvedType.values);
    }

    // Handle nested messages
    if (field.resolvedType && field.resolvedType instanceof protobuf.Type) {
      fieldSchema = await generateSchema(root, field.resolvedType.fullName);
      if (isRepeated) {
        fieldSchema = {
          type: 'array',
          items: fieldSchema,
        };
      }
    }

    schema.properties[fieldName] = fieldSchema;

    // Add to required if field is not optional
    if (field.required && !isRepeated) {
      schema.required.push(fieldName);
    }
  }

  return schema;
}

// Main function
async function main() {
  try {
    await ensureDir(OUTPUT_DIR);
    
    // Load proto files
    const root = new protobuf.Root();
    await root.load([
      'events.proto',
      'ingest.proto',
      'common.proto',
    ], { 
      keepCase: true,
      alternateCommentMode: true,
      includeDirs: [PROTO_DIR],
    });

    // Generate schemas for key messages
    const schemas = [
      { type: 'dcmaar.v1.EventEnvelope', output: 'event-envelope.schema.json' },
      { type: 'dcmaar.v1.EventWithMetadata', output: 'event-with-metadata.schema.json' },
      { type: 'dcmaar.v1.IngestRequest', output: 'ingest-request.schema.json' },
      { type: 'dcmaar.v1.BatchHints', output: 'batch-hints.schema.json' },
    ];

    // Generate and write schemas
    for (const { type, output } of schemas) {
      const schema = await generateSchema(root, type);
      const outputPath = path.join(OUTPUT_DIR, output);
      await writeFile(outputPath, JSON.stringify(schema, null, 2) + '\n');
      console.log(`✅ Generated ${outputPath}`);
    }

    console.log('\n🎉 All schemas generated successfully!');
  } catch (err) {
    console.error('Error generating schemas:', err);
    process.exit(1);
  }
}

main();
