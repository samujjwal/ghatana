import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const productRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

const canonicalFiles = [
  "libs/content-studio-agents/src/main/java/com/ghatana/tutorputor/agent/ContentGenerationRequest.java",
  "libs/content-studio-agents/src/main/java/com/ghatana/tutorputor/agent/ContentGenerationOutputGenerator.java",
];

const forbiddenDuplicates = [
  "libs/tutorputor-ai/src/agents/main/java/com/ghatana/tutorputor/agent/ContentGenerationRequest.java",
  "libs/tutorputor-ai/src/agents/main/java/com/ghatana/tutorputor/agent/ContentGenerationOutputGenerator.java",
];

let failed = false;

for (const relativePath of canonicalFiles) {
  if (!fs.existsSync(path.join(productRoot, relativePath))) {
    console.error(`content-generation canonical file is missing: ${relativePath}`);
    failed = true;
  }
}

for (const relativePath of forbiddenDuplicates) {
  if (fs.existsSync(path.join(productRoot, relativePath))) {
    console.error(`duplicate content-generation Java class is forbidden: ${relativePath}`);
    failed = true;
  }
}

if (failed) {
  process.exit(1);
}

console.log("Content-generation Java contract/generator canonical ownership passed.");
