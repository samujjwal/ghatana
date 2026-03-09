/**
 * Prisma Client Generator Script
 * 
 * Workaround for Prisma 7 configuration issues
 * 
 * @doc.type script
 * @doc.purpose Prisma client generation
 * @doc.layer development
 * @doc.pattern Workaround
 */

import { execSync } from 'child_process';
import { writeFileSync, existsSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';

// Create the Prisma client manually
const prismaClientDir = join(process.cwd(), 'src', 'generated', 'prisma');

// Ensure directory exists
if (!existsSync(prismaClientDir)) {
  mkdirSync(prismaClientDir, { recursive: true });
}

// Create a simple index file that imports from the global Prisma client
const indexContent = `
/**
 * Generated Prisma Client
 * 
 * This is a workaround for Prisma 7 configuration issues.
 * The client will be generated when the configuration is fixed.
 * 
 * @doc.type module
 * @doc.purpose Prisma client
 * @doc.layer generated
 */

// For now, export a placeholder that will be replaced when Prisma is fixed
export const PrismaClient = class {
  constructor() {
    throw new Error('Prisma Client not generated yet. Please fix Prisma configuration first.');
  }
};

export default PrismaClient;
`;

// Write the index file
writeFileSync(join(prismaClientDir, 'index.ts'), indexContent);

// Create a runtime library placeholder
const runtimeDir = join(prismaClientDir, 'runtime');
if (!existsSync(runtimeDir)) {
  mkdirSync(runtimeDir, { recursive: true });
}

const libraryContent = `
/**
 * Prisma Runtime Library Placeholder
 * 
 * This is a workaround for Prisma 7 configuration issues.
 * 
 * @doc.type module
 * @doc.purpose Prisma runtime
 * @doc.layer generated
 */

export const empty = {};
`;

writeFileSync(join(runtimeDir, 'library.ts'), libraryContent);
writeFileSync(join(runtimeDir, 'library.d.ts'), libraryContent);

console.log('✅ Prisma client placeholder created');
console.log('📝 To fix this issue:');
console.log('   1. Remove the url property from prisma/schema.prisma');
console.log('   2. Use datasourceUrl in prisma.config.ts');
console.log('   3. Run npx prisma generate');
console.log('');
console.log('🔧 Current workaround allows the API server to start');
console.log('   but database operations will not work until Prisma is fixed.');
`;

// Try to generate with adapter instead
try {
  console.log('🔄 Attempting to generate Prisma client with adapter...');
  
  // Create a temporary schema without url
  const schemaPath = join(process.cwd(), 'prisma', 'schema.prisma');
  const tempSchemaPath = join(process.cwd(), 'prisma', 'schema.temp.prisma');
  
  // Read the original schema
  const fs = require('fs');
  let schemaContent = fs.readFileSync(schemaPath, 'utf-8');
  
  // Remove the url line
  schemaContent = schemaContent.replace(/datasource db \{[\s\S]*?\}/, `datasource db {
  provider = "postgresql"
}`);
  
  // Write temporary schema
  fs.writeFileSync(tempSchemaPath, schemaContent);
  
  // Try to generate
  try {
    execSync('npx prisma generate --schema=./prisma/schema.temp.prisma', { stdio: 'inherit' });
    console.log('✅ Prisma client generated successfully!');
    
    // Clean up temp file
    fs.unlinkSync(tempSchemaPath);
  } catch (error) {
    console.log('❌ Prisma generation failed, keeping placeholder');
    console.log('🔧 Error:', error.message);
    
    // Clean up temp file if it exists
    if (fs.existsSync(tempSchemaPath)) {
      fs.unlinkSync(tempSchemaPath);
    }
  }
} catch (error) {
  console.log('❌ Could not attempt Prisma generation');
  console.log('🔧 Error:', error.message);
}
