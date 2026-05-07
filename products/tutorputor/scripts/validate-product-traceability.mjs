import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const productRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const matrixPath = path.join(productRoot, "docs", "TRACEABILITY_MATRIX.md");
const routeOwnersPath = path.join(
  productRoot,
  "docs",
  "architecture",
  "API_ROUTE_OWNERS.json",
);
const openApiPath = path.join(productRoot, "api", "tutorputor-api.openapi.yaml");

const requiredCapabilityIds = [
  "visual-first-learning",
  "simulation-first-pedagogy",
  "ai-tutor",
  "adaptive-pathway",
  "cbm",
  "micro-viva",
  "telemetry",
  "cms-review-workflow",
  "privacy-center",
  "lti",
  "credentials",
  "offline-learning",
  "accessibility",
];

const ownerColumns = [
  "Contract / API Owner",
  "Backend / Domain Owner",
  "Frontend Route / Component Owner",
  "Database / Schema Owner",
  "Verification / Test Owner",
];

function fail(message) {
  console.error(`traceability: ${message}`);
  process.exitCode = 1;
}

function assertFileExists(relativePath, context) {
  const normalized = relativePath.trim();
  if (!normalized || normalized === "N/A") {
    fail(`${context} is empty`);
    return;
  }

  const resolved = path.join(productRoot, normalized);
  if (!fs.existsSync(resolved)) {
    fail(`${context} points to missing file: ${normalized}`);
  }
}

function splitCellReferences(cell) {
  return cell
    .split(";")
    .map((value) => value.trim())
    .filter(Boolean);
}

function parseMarkdownMatrix(markdown) {
  const rows = markdown
    .split(/\r?\n/)
    .filter((line) => line.startsWith("|") && !line.includes("---"));

  if (rows.length < 2) {
    fail("TRACEABILITY_MATRIX.md does not contain a markdown table");
    return [];
  }

  const headers = rows[0]
    .split("|")
    .slice(1, -1)
    .map((header) => header.trim());

  return rows.slice(1).map((row) => {
    const cells = row
      .split("|")
      .slice(1, -1)
      .map((cell) => cell.trim());
    return Object.fromEntries(headers.map((header, index) => [header, cells[index] ?? ""]));
  });
}

function validateMatrix() {
  const markdown = fs.readFileSync(matrixPath, "utf8");
  const rows = parseMarkdownMatrix(markdown);
  const byId = new Map(rows.map((row) => [row["Capability ID"], row]));

  for (const capabilityId of requiredCapabilityIds) {
    const row = byId.get(capabilityId);
    if (!row) {
      fail(`required capability is missing from TRACEABILITY_MATRIX.md: ${capabilityId}`);
      continue;
    }

    for (const column of ownerColumns) {
      const references = splitCellReferences(row[column] ?? "");
      if (references.length === 0) {
        fail(`${capabilityId} has no ${column}`);
        continue;
      }

      references.forEach((reference) => {
        assertFileExists(reference, `${capabilityId} ${column}`);
      });
    }

    const testReferences = splitCellReferences(row["Verification / Test Owner"] ?? "");
    if (
      !testReferences.some((reference) =>
        /\.(test|spec)\.(ts|tsx|js|jsx|java)$/.test(reference),
      )
    ) {
      fail(`${capabilityId} must reference at least one executable test/spec file`);
    }
  }
}

function parseOpenApiPaths(openApiText) {
  const paths = [];
  let inPaths = false;

  for (const line of openApiText.split(/\r?\n/)) {
    if (/^paths:\s*$/.test(line)) {
      inPaths = true;
      continue;
    }
    if (inPaths && /^[a-zA-Z0-9_-]+:\s*$/.test(line)) {
      break;
    }

    const match = /^  (\/[^:]+):\s*$/.exec(line);
    if (inPaths && match) {
      paths.push(match[1]);
    }
  }

  return paths;
}

function validateRouteOwners() {
  const openApiPaths = parseOpenApiPaths(fs.readFileSync(openApiPath, "utf8"));
  const routeOwners = JSON.parse(fs.readFileSync(routeOwnersPath, "utf8"));

  if (routeOwners.canonicalBackend !== "services/tutorputor-platform") {
    fail("API_ROUTE_OWNERS.json must declare services/tutorputor-platform as canonicalBackend");
  }

  const routes = routeOwners.routes ?? {};
  for (const publicPath of openApiPaths) {
    const owner = routes[publicPath];
    if (!owner) {
      fail(`OpenAPI path has no canonical route owner: ${publicPath}`);
      continue;
    }

    for (const field of ["backendOwner", "apiContract", "typedClient", "test"]) {
      assertFileExists(owner[field] ?? "", `${publicPath} ${field}`);
    }
  }

  for (const declaredPath of Object.keys(routes)) {
    if (!openApiPaths.includes(declaredPath)) {
      fail(`API_ROUTE_OWNERS.json declares a route that is not in OpenAPI: ${declaredPath}`);
    }
  }
}

validateMatrix();
validateRouteOwners();

if (process.exitCode) {
  process.exit(process.exitCode);
}

console.log("TutorPutor traceability and route-owner conformance passed.");
