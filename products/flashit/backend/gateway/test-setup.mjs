#!/usr/bin/env node

/**
 * Quick test to verify Prisma client setup
 */

console.log("Testing Prisma client setup...\n");

try {
  console.log("1. Testing generated/prisma/index.js import...");
  const { PrismaClient } = require('./generated/prisma/index.js');
  console.log("   ✅ Successfully imported PrismaClient\n");

  console.log("2. Checking PrismaClient type...");
  console.log(`   Type: ${typeof PrismaClient}`);
  console.log(`   Name: ${PrismaClient.name}\n`);

  console.log("3. Checking schema.prisma...");
  const fs = require('fs');
  const schema = fs.readFileSync('./generated/prisma/schema.prisma', 'utf-8');
  
  if (schema.includes('provider = "postgresql"')) {
    console.log("   ✅ Schema uses PostgreSQL\n");
  } else {
    console.log("   ❌ Schema does NOT use PostgreSQL!\n");
    process.exit(1);
  }

  console.log("4. Checking DATABASE_URL...");
  require('dotenv').config();
  if (process.env.DATABASE_URL) {
    console.log(`   ✅ DATABASE_URL set\n`);
  } else {
    console.log("   ❌ DATABASE_URL not set!\n");
    process.exit(1);
  }

  console.log("✅ All checks passed!");
  console.log("\nReady to start: npm run dev");

} catch (error) {
  console.error("❌ Test failed:");
  console.error(error.message);
  console.error("\nFull error:");
  console.error(error);
  process.exit(1);
}
