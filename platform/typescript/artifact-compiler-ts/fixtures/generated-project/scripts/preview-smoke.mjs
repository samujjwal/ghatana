#!/usr/bin/env node
import assert from 'node:assert/strict';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const distEntry = path.resolve(process.cwd(), 'platform/typescript/artifact-compiler-ts/fixtures/generated-project/dist/index.js');
const moduleRef = await import(pathToFileURL(distEntry).href);

const rendered = moduleRef.renderBadgePreview({ label: 'Smoke', tone: 'critical' });
assert.equal(rendered, '<span data-tone="critical">Smoke</span>');

console.log('generated fixture preview smoke passed');
