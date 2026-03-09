#!/usr/bin/env node

import { spawn } from "child_process";
import path from "path";
import { fileURLToPath } from "url";
import fs from "fs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log("🔧 Starting Prisma client generation...\n");
console.log(`📍 Current directory: ${process.cwd()}`);
console.log(`📝 Script directory: ${__dirname}\n`);

// First, clean the generated folder
const generatedPath = path.join(__dirname, "generated", "prisma");
console.log(`🗑️  Cleaning ${generatedPath}...`);

if (fs.existsSync(generatedPath)) {
  fs.rmSync(generatedPath, { recursive: true, force: true });
  console.log("✅ Cleaned generated folder\n");
} else {
  console.log("ℹ️  Generated folder doesn't exist yet (first run)\n");
}

// Ensure generated folder exists
const generatedParent = path.join(__dirname, "generated");
if (!fs.existsSync(generatedParent)) {
  fs.mkdirSync(generatedParent, { recursive: true });
  console.log(`📁 Created ${generatedParent}\n`);
}

// Run prisma generate
console.log("⏳ Running Prisma generate...\n");

const schemaPath = path.join(__dirname, "prisma", "schema.prisma");
console.log(`📖 Using schema: ${schemaPath}\n`);

const prismaProcess = spawn("npx", ["prisma", "generate", "--schema", schemaPath], {
  cwd: __dirname,
  stdio: "inherit",
  shell: true,
});

prismaProcess.on("close", (code) => {
  console.log("\n" + "=".repeat(60));
  if (code === 0) {
    console.log("✅ Prisma client generated successfully!");
    console.log(`📂 Location: ${generatedPath}`);

    // Verify
    const indexFile = path.join(generatedPath, "index.js");
    if (fs.existsSync(indexFile)) {
      console.log(`✅ Verified: ${indexFile} exists`);
      console.log("\n🎉 Ready to start the server with: npm run dev");
    } else {
      console.error(`❌ ERROR: ${indexFile} not found!`);
      process.exit(1);
    }
  } else {
    console.error(`❌ Prisma generation failed with exit code ${code}`);
    process.exit(code);
  }
  console.log("=".repeat(60) + "\n");
});
