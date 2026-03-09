#!/usr/bin/env node

/**
 * Quick test to verify Prisma client import works
 */

console.log("🧪 Testing Prisma client import...\n");

try {
  console.log("1. Checking generated folder exists...");
  const fs = require('fs');
  const path = require('path');
  
  const generatedPath = path.join(process.cwd(), 'generated', 'prisma');
  if (!fs.existsSync(generatedPath)) {
    console.log("❌ Generated folder not found!");
    process.exit(1);
  }
  console.log(`   ✅ ${generatedPath} exists\n`);

  console.log("2. Checking index.js exists...");
  const indexPath = path.join(generatedPath, 'index.js');
  if (!fs.existsSync(indexPath)) {
    console.log(`❌ ${indexPath} not found!`);
    process.exit(1);
  }
  console.log(`   ✅ ${indexPath} exists\n`);

  console.log("3. Attempting to import PrismaClient...");
  const { PrismaClient } = require('./generated/prisma/index.js');
  console.log("   ✅ Import successful\n");

  console.log("4. Checking PrismaClient type...");
  console.log(`   Type: ${typeof PrismaClient}`);
  console.log(`   Constructor: ${PrismaClient.name}\n`);

  console.log("✅ All tests passed!");
  console.log("\n🚀 Ready to run: npm run dev");

} catch (error) {
  console.error("❌ Test failed:");
  console.error(error.message);
  console.error("\nFull error:");
  console.error(error);
  process.exit(1);
}
