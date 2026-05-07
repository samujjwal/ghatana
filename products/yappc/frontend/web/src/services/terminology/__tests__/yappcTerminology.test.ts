import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  getYappcTerm,
  getYappcTerminologyEntries,
  YAPPC_TERMINOLOGY,
  type YappcTerminologyKey,
} from '../yappcTerminology';

const REQUIRED_TERMS = [
  'project',
  'product',
  'app',
  'artifact',
  'pageDocument',
  'builderDocument',
  'canvasNode',
  'lifecyclePacket',
] as const satisfies readonly YappcTerminologyKey[];

function readWorkspaceFile(...segments: readonly string[]): string {
  const thisFile = fileURLToPath(import.meta.url);
  const thisDir = path.dirname(thisFile);
  const repositoryRoot = path.resolve(thisDir, '../../../../../../../..');
  return readFileSync(path.join(repositoryRoot, ...segments), 'utf8');
}

describe('YAPPC terminology', () => {
  it('defines every canonical product term from the audit', () => {
    expect(Object.keys(YAPPC_TERMINOLOGY).sort()).toEqual([...REQUIRED_TERMS].sort());

    for (const key of REQUIRED_TERMS) {
      const entry = YAPPC_TERMINOLOGY[key];
      expect(entry.term).toBe(getYappcTerm(key));
      expect(entry.definition.length).toBeGreaterThan(20);
      expect(entry.useWhen.length).toBeGreaterThan(20);
    }
  });

  it('keeps the contributor glossary aligned with the typed glossary', () => {
    const glossary = readWorkspaceFile('products/yappc/docs/guides/terminology-glossary.md');

    for (const entry of getYappcTerminologyEntries()) {
      expect(glossary).toContain(`| ${entry.term} |`);
      expect(glossary).toContain(entry.definition);
      expect(glossary).toContain(entry.useWhen);
    }
  });

  it('keeps API and generated client wording on canonical artifact spelling', () => {
    const openApi = readWorkspaceFile('products/yappc/docs/api/openapi.yaml');
    const generatedClient = readWorkspaceFile(
      'products/yappc/frontend/web/src/clients/generated/openapi.ts',
    );
    const combinedContracts = `${openApi}\n${generatedClient}`;

    const legacyArtifactSpelling = new RegExp(`\\bartefac${'t'}s?\\b`, 'i');

    expect(combinedContracts).not.toMatch(legacyArtifactSpelling);
    expect(openApi).toContain('Generate project artifacts (code, config, CI)');
    expect(openApi).toContain('Artifact manifest');
    expect(generatedClient).toContain('Generate project artifacts (code, config, CI)');
  });

  it('documents the canonical architecture models needed by new contributors', () => {
    const architectureModels = readWorkspaceFile(
      'products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md',
    );
    const requiredHeadings = [
      '## Product Model',
      '## Lifecycle Model',
      '## Artifact Model',
      '## Builder Model',
      '## Preview Trust Model',
      '## Governance Trace',
      '## Contributor Recipes',
    ] as const;

    for (const heading of requiredHeadings) {
      expect(architectureModels).toContain(heading);
    }

    expect(architectureModels).toContain('Add a phase:');
    expect(architectureModels).toContain('Add a component:');
    expect(architectureModels).toContain('Add an import path:');
    expect(architectureModels).toContain('Add a generator:');
  });
});
