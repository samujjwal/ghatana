import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const rootDir = path.resolve(import.meta.dirname, '..');
const uiDir = path.join(rootDir, 'src', 'components', 'ui');
const designSystemIndex = path.join(rootDir, 'src', 'components', 'design-system', 'index.ts');
const componentsDir = path.join(rootDir, 'src', 'components');
const inventoryPath = path.join(rootDir, 'docs', 'component-inventory.md');

const approvedLocalPrimitives = {
  'alert.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Product wrapper for alert status copy and YAPPC route state compatibility.',
  },
  'badge.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Product wrapper retained for legacy import compatibility while canonical Badge is exported from components/design-system.',
  },
  'Button.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Compatibility wrapper for existing product call sites; new code should import Button from @ghatana/design-system or components/design-system.',
  },
  'Card.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Compatibility wrapper for existing product call sites; new code should import Card from @ghatana/design-system.',
  },
  'CardVariants.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'YAPPC-specific card compositions built on top of canonical card primitives.',
  },
  'Dialog.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Product dialog compositions with YAPPC confirmation semantics.',
  },
  'DraggablePanel.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Product-specific draggable panel behavior for canvas surfaces.',
  },
  'Input.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Compatibility wrapper for existing product form call sites.',
  },
  'Loading.tsx': {
    canonicalSource: 'components/common/LoadingState',
    rationale: 'Product loading compositions retained for route-level fallbacks.',
  },
  'LoadingFallback.tsx': {
    canonicalSource: 'components/route/LoadingSpinner',
    rationale: 'Legacy route fallback wrapper retained for compatibility.',
  },
  'Modal.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Product modal composition retained for existing route flows.',
  },
  'ProgressBar.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Legacy progress wrapper retained for existing product call sites.',
  },
  'Select.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Compatibility wrapper for existing product form call sites.',
  },
  'StatusCard.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Product status-card composition used by YAPPC route summaries.',
  },
  'tabs.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Compatibility wrapper for existing tab call sites.',
  },
  'Textarea.tsx': {
    canonicalSource: '@ghatana/design-system',
    rationale: 'Compatibility wrapper for existing product form call sites.',
  },
};

function listUiPrimitiveFiles() {
  return fs.readdirSync(uiDir, { withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => entry.name)
    .filter((name) => /\.(tsx|ts)$/.test(name) && name !== 'index.ts')
    .sort();
}

function findUnapprovedPrimitives(files) {
  return files.filter((file) => approvedLocalPrimitives[file] == null);
}

function assertDesignSystemIndex() {
  const content = fs.readFileSync(designSystemIndex, 'utf8');
  const violations = [];
  if (content.includes('@ghatana/ui')) {
    violations.push('components/design-system/index.ts must not reference deprecated @ghatana/ui.');
  }
  if (!content.includes("from '@ghatana/design-system'")) {
    violations.push('components/design-system/index.ts must re-export canonical primitives from @ghatana/design-system.');
  }
  return violations;
}

function listComponentFamilies() {
  return fs.readdirSync(componentsDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .filter((entry) => !entry.name.startsWith('__'))
    .map((entry) => {
      const directory = path.join(componentsDir, entry.name);
      const files = fs.readdirSync(directory, { recursive: true, withFileTypes: true })
        .filter((file) => file.isFile())
        .filter((file) => /\.(tsx|ts)$/.test(file.name))
        .length;
      return { name: entry.name, files };
    })
    .sort((left, right) => left.name.localeCompare(right.name));
}

function renderInventoryMarkdown(primitiveFiles, unapprovedPrimitives) {
  const primitiveRows = primitiveFiles
    .map((file) => {
      const approval = approvedLocalPrimitives[file];
      const status = approval ? 'Approved wrapper' : 'Unapproved local primitive';
      const canonicalSource = approval?.canonicalSource ?? 'Needs review';
      const rationale = approval?.rationale ?? 'Move to an approved shared component or document an exception.';
      return `| \`${file}\` | ${status} | \`${canonicalSource}\` | ${rationale} |`;
    })
    .join('\n');

  const familyRows = listComponentFamilies()
    .map((family) => `| \`${family.name}\` | ${family.files} |`)
    .join('\n');

  const unapprovedText = unapprovedPrimitives.length === 0
    ? 'None. The component inventory check fails if a new local primitive appears without an approved mapping.'
    : unapprovedPrimitives.map((file) => `- \`${file}\``).join('\n');

  return `# YAPPC Component Inventory

This inventory is generated by \`products/yappc/frontend/web/scripts/check-design-system-consistency.mjs\`. It keeps local YAPPC UI primitives mapped to shared sources so product components do not duplicate \`@ghatana/design-system\` or \`yappc-ui\` responsibilities.

## Shared Sources

| Source | Boundary |
| --- | --- |
| \`@ghatana/design-system\` | Canonical primitive buttons, cards, inputs, dialogs, tabs, badges, and other reusable UI atoms. |
| \`products/yappc/frontend/libs/yappc-ui\` | Product-local reusable YAPPC UI compositions that are shared across YAPPC apps/libraries. |
| \`products/yappc/frontend/web/src/components/*\` | Web-route and domain-specific compositions. Local primitives are allowed only when listed below as approved wrappers. |

## Approved Local Primitive Wrappers

| File | Status | Canonical source | Rationale |
| --- | --- | --- | --- |
${primitiveRows}

## Unapproved Local Primitives

${unapprovedText}

## Component Families

| Directory | TypeScript files |
| --- | ---: |
${familyRows}
`;
}

function assertInventoryDocument(expectedMarkdown) {
  if (!fs.existsSync(inventoryPath)) {
    return [`Missing component inventory doc: ${path.relative(rootDir, inventoryPath)}`];
  }
  const existing = fs.readFileSync(inventoryPath, 'utf8');
  return existing === expectedMarkdown
    ? []
    : [`Component inventory doc is stale. Run node scripts/check-design-system-consistency.mjs --write.`];
}

const primitiveFiles = listUiPrimitiveFiles();
const unapprovedPrimitives = findUnapprovedPrimitives(primitiveFiles);
const indexViolations = assertDesignSystemIndex();
const expectedInventoryMarkdown = renderInventoryMarkdown(primitiveFiles, unapprovedPrimitives);
const inventoryViolations = process.argv.includes('--write') ? [] : assertInventoryDocument(expectedInventoryMarkdown);
const report = {
  checkedAt: new Date().toISOString(),
  canonicalSource: '@ghatana/design-system',
  localPrimitiveCount: primitiveFiles.length,
  approvedLocalPrimitives,
  unapprovedPrimitives,
  indexViolations,
  inventoryViolations,
};

if (process.argv.includes('--write')) {
  fs.writeFileSync(inventoryPath, expectedInventoryMarkdown, 'utf8');
  console.log(`Wrote ${path.relative(rootDir, inventoryPath)}`);
}

if (process.argv.includes('--report')) {
  console.log(JSON.stringify(report, null, 2));
}

if (unapprovedPrimitives.length > 0 || indexViolations.length > 0 || inventoryViolations.length > 0) {
  console.error('YAPPC design-system consistency check failed.');
  if (unapprovedPrimitives.length > 0) {
    console.error(`Unapproved local primitives: ${unapprovedPrimitives.join(', ')}`);
  }
  for (const violation of indexViolations) {
    console.error(violation);
  }
  for (const violation of inventoryViolations) {
    console.error(violation);
  }
  process.exit(1);
}

console.log(`YAPPC design-system consistency check passed: ${primitiveFiles.length} approved local UI wrappers.`);
