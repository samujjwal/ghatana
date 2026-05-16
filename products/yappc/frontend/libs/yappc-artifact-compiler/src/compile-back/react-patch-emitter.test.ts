import { mkdir, mkdtemp, writeFile } from 'fs/promises';
import { tmpdir } from 'os';
import { join } from 'path';
import { afterEach, describe, expect, it } from 'vitest';
import { createHash } from 'crypto';
import { ReactPatchEmitter } from './react-patch-emitter';
import type { ChangeOp, PatchContext } from './types';
import type { SemanticModelElement } from '../model/types';

const tempRoots: string[] = [];

async function createTempRoot(): Promise<string> {
  const rootPath = await mkdtemp(join(tmpdir(), 'yappc-react-emitter-'));
  tempRoots.push(rootPath);
  return rootPath;
}

function makeComponentElement(): SemanticModelElement {
  return {
    id: '91c394e7-d45f-4dd6-9f0c-c2a2cc0d18c0',
    kind: 'component',
    name: 'Button',
    confidence: 0.9,
    provenance: {
      extractorId: 'test',
      extractorVersion: '1.0.0',
      sourcePaths: ['src/Button.tsx'],
      kind: 'exact',
      extractedAt: '2026-05-15T00:00:00.000Z',
    },
    securityFlags: [],
    privacyFlags: [],
    tags: [],
    graphNodeIds: [],
    sourceRefs: ['src/Button.tsx'],
    residualIslandIds: [],
    contractName: 'Button',
    props: [],
    slots: [],
    events: [],
    variants: [],
    stateConnections: [],
    dataDependencies: [],
    styleDependencies: [],
    storyIds: [],
    builderCanvasHints: {},
  };
}

describe('ReactPatchEmitter', () => {
  afterEach(async () => {
    await Promise.all(
      tempRoots.splice(0).map(async rootPath => {
        const { rm } = await import('fs/promises');
        await rm(rootPath, { recursive: true, force: true });
      }),
    );
  });

  it('does not emit placeholder sync diffs', () => {
    const emitter = new ReactPatchEmitter();
    const element = makeComponentElement();
    const op: ChangeOp = {
      id: 'op:rename',
      kind: 'rename-component',
      targetElementId: element.id,
      description: 'Rename Button to PrimaryButton',
      before: 'Button',
      after: 'PrimaryButton',
      autoApplyConfidence: 0.9,
    };
    const context: PatchContext = {
      readFile: async () => '',
      fileExists: async () => true,
      residuals: new Map(),
      elementSourcePaths: new Map([[element.id, ['src/Button.tsx']]]),
    };

    expect(emitter.emit(op, element, context)).toEqual([]);
  });

  it('emits a real diff asynchronously for component rename', async () => {
    const rootPath = await createTempRoot();
    const relativePath = 'src/Button.tsx';
    const absolutePath = join(rootPath, relativePath);
    await mkdir(join(rootPath, 'src'), { recursive: true });
    await writeFile(
      absolutePath,
      [
        'export function Button(): JSX.Element {',
        '  return <Button />;',
        '}',
        '',
      ].join('\n'),
    );

    const emitter = new ReactPatchEmitter();
    const element = makeComponentElement();
    const op: ChangeOp = {
      id: 'op:rename',
      kind: 'rename-component',
      targetElementId: element.id,
      description: 'Rename Button to PrimaryButton',
      before: 'Button',
      after: 'PrimaryButton',
      autoApplyConfidence: 0.9,
    };
    const context: PatchContext = {
      readFile: async () => (await import('fs/promises')).readFile(absolutePath, 'utf-8'),
      fileExists: async () => true,
      residuals: new Map(),
      elementSourcePaths: new Map([[element.id, [relativePath]]]),
    };

    const patches = await emitter.emitAsync(op, element, relativePath, context);

    expect(patches).toHaveLength(1);
    expect(patches[0]?.diff).toContain('--- a/src/Button.tsx');
    expect(patches[0]?.diff).toContain('+export function PrimaryButton(): JSX.Element {');
    expect(patches[0]?.diff).not.toContain('YAPPC-RANGE');
    expect(patches[0]?.ranges).toEqual([
      expect.objectContaining({
        startLine: 0,
        startColumn: 0,
        endLine: 0,
        nodeType: 'ComponentDeclaration',
      }),
    ]);
    expect(patches[0]?.baseChecksum).toBe(
      createHash('sha256')
        .update([
          'export function Button(): JSX.Element {',
          '  return <Button />;',
          '}',
          '',
        ].join('\n'))
        .digest('hex'),
    );
    expect(patches[0]?.targetChecksum).toBeDefined();
  });
});