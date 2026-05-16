/**
 * @fileoverview Unit tests for the compile-back layer:
 *   - buildChangePlan (diff two model element arrays)
 *   - PatchCoordinator (routes ops to emitters)
 *   - preserveResidual (strategy application)
 *   - buildResidualIndex
 */

import { describe, it, expect } from 'vitest';
import { buildChangePlan } from '../compile-back/types';
import { preserveResidual, buildResidualIndex } from '../compile-back/residual-preserver';
import type { SemanticModelElement } from '../model/types';
import type { ResidualIsland } from '../residual/types';

// ============================================================================
// Helpers
// ============================================================================

function makeElement(id: string, name: string, kind: 'component' = 'component'): SemanticModelElement {
  return {
    id,
    kind,
    name,
    confidence: 0.9,
    provenance: {
      extractorId: 'test',
      extractorVersion: '1.0.0',
      sourcePaths: [`src/${name}.tsx`],
      kind: 'exact',
      extractedAt: new Date().toISOString(),
    },
    securityFlags: [],
    privacyFlags: [],
    tags: [],
    contractName: name,
    props: [],
    slots: [],
    events: [],
    variants: [],
    stateConnections: [],
    dataDependencies: [],
    styleDependencies: [],
    storyIds: [],
    builderCanvasHints: {},
  } as SemanticModelElement;
}

function makeResidual(id: string, linkedIds: string[]): ResidualIsland {
  return {
    id,
    kind: 'code',
    originalSource: '// original',
    normalizedSummary: `Residual ${id}`,
    reasonUnmodeled: 'Too complex',
    reviewRequired: false,
    regenerationStrategy: 'verbatim-preserve',
    sourceLocation: { filePath: 'src/test.tsx', startLine: 0, startColumn: 0, endLine: 5, endColumn: 0 },
    extractorId: 'test',
    extractorVersion: '1.0.0',
    extractedAt: new Date().toISOString(),
    confidence: 0.4,
    linkedModelElementIds: linkedIds,
    tags: [],
  };
}

// ============================================================================
// buildChangePlan tests
// ============================================================================

describe('buildChangePlan — additions', () => {
  it('emits add-component ops for new elements', () => {
    const before: SemanticModelElement[] = [];
    const after = [makeElement('e1', 'Button')];

    const ops = buildChangePlan(before, after);

    expect(ops).toHaveLength(1);
    expect(ops[0]!.kind).toBe('add-component');
    expect(ops[0]!.targetElementId).toBe('e1');
    expect(ops[0]!.after).toBeDefined();
    expect(ops[0]!.before).toBeUndefined();
  });

  it('assigns add-component high auto-apply confidence', () => {
    const ops = buildChangePlan([], [makeElement('e1', 'Button')]);
    expect(ops[0]!.autoApplyConfidence).toBeGreaterThanOrEqual(0.8);
  });
});

describe('buildChangePlan — removals', () => {
  it('emits remove-component ops for deleted elements', () => {
    const before = [makeElement('e1', 'Button')];
    const after: SemanticModelElement[] = [];

    const ops = buildChangePlan(before, after);

    expect(ops).toHaveLength(1);
    expect(ops[0]!.kind).toBe('remove-component');
    expect(ops[0]!.before).toBeDefined();
    expect(ops[0]!.after).toBeUndefined();
  });
});

describe('buildChangePlan — renames', () => {
  it('emits rename-component op when element name changes', () => {
    const before = [makeElement('e1', 'OldButton')];
    const after = [makeElement('e1', 'NewButton')];

    const ops = buildChangePlan(before, after);

    expect(ops.some(op => op.kind === 'rename-component')).toBe(true);
    const rename = ops.find(op => op.kind === 'rename-component');
    expect(rename?.before).toBe('OldButton');
    expect(rename?.after).toBe('NewButton');
  });
});

describe('buildChangePlan — no-op', () => {
  it('emits no ops when before and after are identical', () => {
    const element = makeElement('e1', 'Button');
    const ops = buildChangePlan([element], [element]);
    expect(ops).toHaveLength(0);
  });

  it('emits no ops for deep-equal elements', () => {
    const e1 = makeElement('e1', 'Button');
    const e2 = makeElement('e1', 'Button');
    const ops = buildChangePlan([e1], [e2]);
    expect(ops).toHaveLength(0);
  });
});

describe('buildChangePlan — op IDs', () => {
  it('generates stable op IDs for add, remove, and rename', () => {
    const added = buildChangePlan([], [makeElement('e-add', 'A')]);
    expect(added[0]!.id).toBe('op:add:e-add');

    const removed = buildChangePlan([makeElement('e-rm', 'R')], []);
    expect(removed[0]!.id).toBe('op:remove:e-rm');

    const renamed = buildChangePlan([makeElement('e-rn', 'Old')], [makeElement('e-rn', 'New')]);
    expect(renamed.find(op => op.kind === 'rename-component')?.id).toBe('op:rename:e-rn');
  });
});

// ============================================================================
// preserveResidual tests
// ============================================================================

describe('preserveResidual — verbatim-preserve', () => {
  it('returns the original source unchanged', () => {
    const island = makeResidual('r1', []);
    island.originalSource = 'const x = dangerousOp();';

    const result = preserveResidual({ ...island, originalSource: 'const x = dangerousOp();' }, '');
    expect(result.preserved).toBe(true);
    expect(result.content).toBe('const x = dangerousOp();');
    expect(result.warning).toBeUndefined();
  });
});

describe('preserveResidual — best-effort-approximate', () => {
  it('returns original source with a warning', () => {
    const island: ResidualIsland = { ...makeResidual('r2', []), regenerationStrategy: 'best-effort-approximate' };
    const result = preserveResidual(island, '');
    expect(result.preserved).toBe(true);
    expect(result.warning).toBeDefined();
    expect(result.warning).toContain('Best-effort');
  });
});

describe('preserveResidual — emit-warning', () => {
  it('wraps original source in a warning comment', () => {
    const island: ResidualIsland = { ...makeResidual('r3', []), regenerationStrategy: 'emit-warning' };
    const result = preserveResidual(island, '');
    expect(result.preserved).toBe(false);
    expect(result.content).toContain('YAPPC-WARNING');
  });
});

describe('preserveResidual — require-manual-impl', () => {
  it('emits a throw expression and TODO comment', () => {
    const island: ResidualIsland = { ...makeResidual('r4', []), regenerationStrategy: 'require-manual-impl' };
    const result = preserveResidual(island, '');
    expect(result.preserved).toBe(false);
    expect(result.content).toContain('YAPPC-TODO');
    expect(result.content).toContain('throw new Error');
  });
});

describe('preserveResidual — placeholder-stub', () => {
  it('emits a stub with TODO comment', () => {
    const island: ResidualIsland = { ...makeResidual('r5', []), regenerationStrategy: 'placeholder-stub' };
    const result = preserveResidual(island, '');
    expect(result.preserved).toBe(false);
    expect(result.content).toContain('YAPPC-STUB');
    expect(result.content).toContain('TODO');
  });
});

// ============================================================================
// buildResidualIndex tests
// ============================================================================

describe('buildResidualIndex', () => {
  it('indexes residual by its own id when no linkedModelElementIds', () => {
    const island = makeResidual('r-solo', []);
    const index = buildResidualIndex([island]);
    expect(index.has('r-solo')).toBe(true);
    expect(index.get('r-solo')).toBe(island);
  });

  it('indexes residual by each linked element ID', () => {
    const island = makeResidual('r-linked', ['elem1', 'elem2']);
    const index = buildResidualIndex([island]);
    expect(index.has('elem1')).toBe(true);
    expect(index.has('elem2')).toBe(true);
    expect(index.get('elem1')).toBe(island);
  });

  it('handles multiple residuals without collision', () => {
    const a = makeResidual('ra', ['ea1']);
    const b = makeResidual('rb', ['eb1']);
    const index = buildResidualIndex([a, b]);
    expect(index.get('ea1')).toBe(a);
    expect(index.get('eb1')).toBe(b);
  });
});
