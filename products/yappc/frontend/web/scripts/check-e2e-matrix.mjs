import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(__dirname, '..');
const matrixPath = path.join(webRoot, 'docs', 'e2e-matrix.json');

const expectedIds = Array.from({ length: 25 }, (_, index) => `E2E-${String(index + 1).padStart(3, '0')}`);

function fail(message) {
  console.error(`E2E matrix check failed: ${message}`);
  process.exitCode = 1;
}

function readMatrix() {
  if (!existsSync(matrixPath)) {
    fail(`missing matrix file: ${path.relative(webRoot, matrixPath)}`);
    return { coverage: [] };
  }
  return JSON.parse(readFileSync(matrixPath, 'utf8'));
}

const matrix = readMatrix();
const coverage = Array.isArray(matrix.coverage) ? matrix.coverage : [];
const byId = new Map(coverage.map((entry) => [entry.id, entry]));

for (const id of expectedIds) {
  const entry = byId.get(id);
  if (!entry) {
    fail(`missing ${id}`);
    continue;
  }
  if (typeof entry.journey !== 'string' || entry.journey.trim().length === 0) {
    fail(`${id} has no journey label`);
  }
  if (typeof entry.spec !== 'string' || entry.spec.trim().length === 0) {
    fail(`${id} has no spec path`);
  } else if (!existsSync(path.join(webRoot, entry.spec))) {
    fail(`${id} spec does not exist: ${entry.spec}`);
  }
  if (!Array.isArray(entry.assertions) || entry.assertions.length === 0) {
    fail(`${id} has no assertions`);
  }
}

for (const entry of coverage) {
  if (!expectedIds.includes(entry.id)) {
    fail(`unexpected matrix id: ${entry.id}`);
  }
}

if (!process.exitCode) {
  console.log(`E2E matrix check passed: ${coverage.length} journeys mapped.`);
}
