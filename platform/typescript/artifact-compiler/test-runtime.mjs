/**
 * Runtime smoke test — verifies core modules load and function correctly.
 * Run with: node test-runtime.mjs
 */

import { extractComponentsFromSource } from './dist/extractors/typescript/component-extractor.js';
import { parseCsfSource } from './dist/extractors/storybook/csf-extractor.js';
import { parsePrismaSchema } from './dist/extractors/prisma/schema-extractor.js';
import { scanRepository } from './dist/inventory/scanner.js';

let failures = 0;

function assert(name, condition) {
  if (!condition) {
    console.error(`FAIL: ${name}`);
    failures++;
  } else {
    console.log(`PASS: ${name}`);
  }
}

// Test 1: Component extraction
const tsxSource = `
  export function Button({ label, onClick }: { label: string; onClick: () => void }) {
    return <button onClick={onClick}>{label}</button>;
  }
`;
const components = extractComponentsFromSource(tsxSource, 'Button.tsx');
assert('Extracts Button component', components.length === 1);
assert('Component name is Button', components[0]?.name === 'Button');
assert('Has 2 props', components[0]?.props.length === 2);

// Test 2: CSF extraction
const csfSource = `
  import { Button } from './Button';
  export default { title: 'Components/Button', component: Button };
  export const Primary = { args: { label: 'Click me' } };
`;
const csf = parseCsfSource(csfSource, 'Button.stories.tsx');
assert('CSF meta parsed', csf !== null);
assert('CSF title extracted', csf?.meta.title === 'Components/Button');
assert('CSF has 1 story', csf?.stories.length === 1);

// Test 3: Prisma schema extraction
const prismaSchema = `model User {
  id    Int    @id @default(autoincrement())
  email String @unique
  posts Post[]
}

model Post {
  id     Int  @id @default(autoincrement())
  author User @relation(fields: [authorId], references: [id])
  authorId Int
}`;
const models = parsePrismaSchema(prismaSchema, 'schema.prisma');
assert('Prisma extracts 2 models', models.length === 2);
assert('User has relation to Post', models.find(m => m.name === 'User')?.relations.length === 1);

// Test 4: Scanner runs
const inventory = await scanRepository({ rootPath: process.cwd(), maxFileSizeBytes: 1024 * 1024 });
assert('Scanner returns results', inventory.artifacts.length > 0);
assert('Scanner has summary', inventory.summary.totalFiles > 0);

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
} else {
  console.log('\nAll runtime tests passed');
}
