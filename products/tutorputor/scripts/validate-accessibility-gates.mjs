#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(process.cwd(), "products/tutorputor");
const errors = [];

const read = (relativePath) => {
  const fullPath = path.join(root, relativePath);
  if (!fs.existsSync(fullPath)) {
    errors.push(`Missing required accessibility gate file: ${relativePath}`);
    return "";
  }
  return fs.readFileSync(fullPath, "utf8");
};

const webSpec = read("apps/tutorputor-web/e2e/a11y-learner-pages.spec.ts");
const adminSpec = read("apps/tutorputor-admin/e2e/admin-a11y.spec.ts");
const mobileA11y = read("apps/tutorputor-mobile/src/__tests__/dashboard-a11y.test.tsx");
const webPackage = read("apps/tutorputor-web/package.json");
const adminPackage = read("apps/tutorputor-admin/package.json");
const ci = read(".gitea/workflows/tutorputor-ci.yml");

const requiredWebCoverage = ["dashboard", "module", "assessment", "simulation", "AI tutor", "keyboard"];
for (const term of requiredWebCoverage) {
  if (!webSpec.includes(term)) {
    errors.push(`Web accessibility spec does not cover required flow: ${term}`);
  }
}

const requiredAdminCoverage = ["authoring", "keyboard", "AxeBuilder"];
for (const term of requiredAdminCoverage) {
  if (!adminSpec.includes(term)) {
    errors.push(`Admin accessibility spec does not cover required flow/tooling: ${term}`);
  }
}

for (const [name, content] of [
  ["web accessibility spec", webSpec],
  ["admin accessibility spec", adminSpec],
]) {
  if (!content.includes("AxeBuilder")) {
    errors.push(`${name} does not run axe-core.`);
  }
  if (!content.includes('impact === "critical"')) {
    errors.push(`${name} does not fail on critical accessibility violations.`);
  }
  if (!content.includes('keyboard.press("Tab")')) {
    errors.push(`${name} does not include keyboard-only navigation coverage.`);
  }
}

if (!mobileA11y.includes("accessibilityLabel") || !mobileA11y.includes("accessibilityRole")) {
  errors.push("Mobile accessibility guard does not assert native accessible labels.");
}

if (!webPackage.includes("test:a11y") || !webPackage.includes("a11y-learner-pages.spec.ts")) {
  errors.push("Web package does not expose the learner accessibility Playwright gate.");
}

if (!adminPackage.includes("test:a11y") || !adminPackage.includes("admin-a11y.spec.ts")) {
  errors.push("Admin package does not expose the authoring accessibility Playwright gate.");
}

if (!adminPackage.includes("@axe-core/playwright")) {
  errors.push("Admin package does not declare @axe-core/playwright for the accessibility gate.");
}

if (!ci.includes("validate-accessibility-gates.mjs")) {
  errors.push("TutorPutor CI does not run the accessibility gate validator.");
}

if (errors.length > 0) {
  console.error("Accessibility gate validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Accessibility gates cover learner, simulation, assessment, AI tutor, dashboard, authoring, mobile labels, keyboard paths, and CI validation.");
