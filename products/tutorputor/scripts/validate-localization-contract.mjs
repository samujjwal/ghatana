#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(process.cwd(), "products/tutorputor");
const errors = [];

const read = (relativePath) => {
  const fullPath = path.join(root, relativePath);
  if (!fs.existsSync(fullPath)) {
    errors.push(`Missing required localization contract file: ${relativePath}`);
    return "";
  }
  return fs.readFileSync(fullPath, "utf8");
};

const source = read("contracts/v1/learning-unit.ts");
const declarations = read("contracts/v1/learning-unit.d.ts");
const schema = read("contracts/v1/learning-unit.schema.json");
const test = read("contracts/tests/learning-unit-localization.test.ts");
const ci = read(".gitea/workflows/tutorputor-ci.yml");

for (const term of [
  "LocalizationConfig",
  "LocalizedTextResource",
  "contentBlocks",
  "captions",
  "transcripts",
  "prompts",
  "feedback",
  "rubrics",
  "accessibilityAlternatives",
  "publishRequiresCompleteCoverage",
]) {
  if (!source.includes(term)) {
    errors.push(`Source learning-unit contract missing ${term}.`);
  }
  if (!declarations.includes(term)) {
    errors.push(`Declaration learning-unit contract missing ${term}.`);
  }
  if (!schema.includes(term)) {
    errors.push(`JSON schema learning-unit contract missing ${term}.`);
  }
}

for (const fixtureMarker of [
  "localizedPrompt",
  "localizedFeedback",
  "localizedRubric",
  "captions",
  "transcript",
  "accessibilityAlternative",
  "assessment.localization",
]) {
  if (!test.includes(fixtureMarker)) {
    errors.push(`Localization fixture test missing ${fixtureMarker}.`);
  }
}

if (!schema.includes('"localization"') || !schema.includes('"required"')) {
  errors.push("JSON schema does not require root localization.");
}

if (!ci.includes("validate-localization-contract.mjs")) {
  errors.push("TutorPutor CI does not run the localization contract validator.");
}

if (errors.length > 0) {
  console.error("Localization contract validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Localization contract covers learning content, captions, transcripts, prompts, feedback, rubrics, accessibility alternatives, fixtures, and CI validation.");
