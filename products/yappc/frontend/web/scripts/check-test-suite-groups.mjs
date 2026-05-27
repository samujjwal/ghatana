import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const packageJson = JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8"));

const requiredGroups = {
  "test:unit": [
    "src/components/common/__tests__/common-state-components.test.tsx",
    "src/routes/app/project/__tests__/phase-cockpit-component-packets.test.tsx",
  ],
  "test:integration": [
    "src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx",
    "src/routes/app/project/__tests__/PhaseStatusPanels.test.tsx",
  ],
  "test:contract": [
    "scripts/check-e2e-matrix.mjs",
    "src/lib/api/__tests__/apiContract.test.ts",
    "src/routes/app/project/__tests__/phase-cockpit-i18n.test.ts",
  ],
  "test:a11y": ["e2e/accessibility-contracts.spec.ts"],
  "test:performance": [
    "src/services/performance/__tests__/canvasPerformanceBudgets.test.ts",
    "e2e/performance-memory.spec.ts",
  ],
  "test:e2e": ["playwright.config.ts"],
  "test:e2e:matrix:check": ["docs/e2e-matrix.json", "scripts/check-e2e-matrix.mjs"],
};

const failures = [];

for (const [scriptName, requiredPaths] of Object.entries(requiredGroups)) {
  const command = packageJson.scripts?.[scriptName];
  if (typeof command !== "string" || command.trim().length === 0) {
    failures.push(`${scriptName} is missing or empty in package.json`);
    continue;
  }

  for (const requiredPath of requiredPaths) {
    if (!fs.existsSync(path.join(root, requiredPath))) {
      failures.push(`${scriptName} references missing test target ${requiredPath}`);
    }
  }
}

const regressionCommand = packageJson.scripts?.["test:regression"] ?? "";
for (const scriptName of ["test:unit", "test:integration", "test:contract"]) {
  if (!regressionCommand.includes(`pnpm ${scriptName}`)) {
    failures.push(`test:regression must include ${scriptName}`);
  }
}

if (failures.length > 0) {
  console.error("YAPPC test suite grouping check failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("YAPPC test suite grouping check passed.");
