/**
 * @fileoverview Tests for the SemanticProductModel → BuilderDocument converter.
 *
 * All tests exercise the real production code — no object-literal assertions
 * that bypass the subject under test.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { toBuilderDocument } from '../toBuilderDocument';
import type { SemanticProductModel, ComponentModel, PageModel, TokenModel, ThemeModel } from '../../model/types';

// ============================================================================
// Test fixtures
// ============================================================================

const PROVENANCE = {
  extractorId: 'test-extractor',
  extractorVersion: '1.0.0',
  sourcePaths: ['src/'],
  kind: 'inferred' as const,
  extractedAt: '2026-01-01T00:00:00.000Z',
};

function makeComponent(
  id: string,
  contractName: string,
  overrides: Partial<ComponentModel> = {},
): ComponentModel {
  return {
    kind: 'component',
    id,
    name: contractName,
    contractName,
    description: undefined,
    confidence: 0.9,
    provenance: PROVENANCE,
    reviewRequirement: undefined,
    securityFlags: [],
    privacyFlags: [],
    tags: [],
    props: [],
    slots: [],
    events: [],
    variants: [],
    stateConnections: [],
    dataDependencies: [],
    styleDependencies: [],
    accessibility: undefined,
    storyIds: [],
    builderCanvasHints: {},
    ...overrides,
  };
}

function makePage(
  id: string,
  routePath: string,
  componentIds: string[],
  overrides: Partial<PageModel> = {},
): PageModel {
  return {
    kind: 'page',
    id,
    name: routePath,
    description: undefined,
    confidence: 0.9,
    provenance: PROVENANCE,
    reviewRequirement: undefined,
    securityFlags: [],
    privacyFlags: [],
    tags: [],
    routePath,
    componentIds,
    dataDependencies: [],
    authGuard: undefined,
    seoMetadata: undefined,
    visibility: 'public',
    ...overrides,
  };
}

function makeToken(id: string, name: string): TokenModel {
  return {
    kind: 'token',
    id,
    name,
    description: undefined,
    confidence: 1,
    provenance: PROVENANCE,
    reviewRequirement: undefined,
    securityFlags: [],
    privacyFlags: [],
    tags: [],
    tokenPath: ['color', name],
    value: { value: '#000000', type: 'color' },
    aliases: [],
    platformOverrides: {},
  };
}

function makeTheme(id: string, name: string): ThemeModel {
  return {
    kind: 'theme',
    id,
    name,
    description: undefined,
    confidence: 1,
    provenance: PROVENANCE,
    reviewRequirement: undefined,
    securityFlags: [],
    privacyFlags: [],
    tags: [],
    mode: 'light',
    tokenSetIds: [],
    overrides: {},
  };
}

function makeModel(
  elements: SemanticProductModel['elements'],
  overrides: Partial<SemanticProductModel> = {},
): SemanticProductModel {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    repositoryRoot: '/repo/root',
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-02T00:00:00.000Z',
    version: 1,
    elements,
    elementIndex: {},
    residualIslandIds: [],
    ...overrides,
  };
}

// ============================================================================
// Tests
// ============================================================================

describe('toBuilderDocument', () => {
  describe('empty model', () => {
    it('produces an empty document when there are no elements', () => {
      const model = makeModel([]);
      const { document, additionalPages, roundTripFidelity } = toBuilderDocument(model);

      expect(document.rootNodes).toHaveLength(0);
      expect(document.nodes.size).toBe(0);
      expect(additionalPages).toHaveLength(0);
      // No pages and no orphan components — no loss points
      expect(roundTripFidelity.lossPoints).toHaveLength(0);
      expect(roundTripFidelity.canRoundTrip).toBe(true);
    });

    it('uses the model id as the document id', () => {
      const model = makeModel([]);
      const { document } = toBuilderDocument(model);
      expect(document.id).toBe(model.id);
    });

    it('uses the model version as the document version', () => {
      const model = makeModel([], { version: 42 });
      const { document } = toBuilderDocument(model);
      expect(document.version).toBe('42');
    });
  });

  describe('single page with components', () => {
    const compA = makeComponent('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ButtonComponent');
    const compB = makeComponent('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'CardComponent');
    const page = makePage(
      'cccccccc-cccc-cccc-cccc-cccccccccccc',
      '/home',
      [compA.id, compB.id],
    );

    let model: SemanticProductModel;

    beforeEach(() => {
      model = makeModel([compA, compB, page]);
    });

    it('puts page component ids in rootNodes in order', () => {
      const { document } = toBuilderDocument(model);
      expect(document.rootNodes).toEqual([compA.id, compB.id]);
    });

    it('adds all components to the nodes map', () => {
      const { document } = toBuilderDocument(model);
      expect(document.nodes.size).toBe(2);
      expect(document.nodes.has(compA.id as never)).toBe(true);
      expect(document.nodes.has(compB.id as never)).toBe(true);
    });

    it('sets the contractName on each node', () => {
      const { document } = toBuilderDocument(model);
      const nodeA = document.nodes.get(compA.id as never);
      expect(nodeA?.contractName).toBe('ButtonComponent');
    });

    it('has no additional pages', () => {
      const { additionalPages } = toBuilderDocument(model);
      expect(additionalPages).toHaveLength(0);
    });

    it('reports canRoundTrip = true with no orphans', () => {
      const { roundTripFidelity } = toBuilderDocument(model);
      expect(roundTripFidelity.canRoundTrip).toBe(true);
      expect(roundTripFidelity.lossPoints).toHaveLength(0);
    });

    it('uses page seoMetadata.title as document name when present', () => {
      const pageWithTitle = makePage(
        'cccccccc-cccc-cccc-cccc-cccccccccccc',
        '/home',
        [],
        { seoMetadata: { title: 'My Home Page', description: undefined, ogImage: undefined } },
      );
      const { document } = toBuilderDocument(makeModel([pageWithTitle]));
      expect(document.name).toBe('My Home Page');
    });

    it('falls back to routePath as document name when no seoMetadata', () => {
      const { document } = toBuilderDocument(model);
      expect(document.name).toBe('/home');
    });
  });

  describe('component props', () => {
    it('populates props with default values from PropSchema', () => {
      const comp = makeComponent('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'InputComponent', {
        props: [
          { name: 'label', type: 'string', required: true, defaultValue: 'Enter text', description: undefined, examples: [] },
          { name: 'disabled', type: 'boolean', required: false, defaultValue: false, description: undefined, examples: [] },
          { name: 'count', type: 'number', required: false, defaultValue: undefined, description: undefined, examples: [] },
        ],
      });
      const model = makeModel([comp]);
      const { document } = toBuilderDocument(model);
      const node = document.nodes.get(comp.id as never);

      expect(node?.props['label']).toBe('Enter text');
      expect(node?.props['disabled']).toBe(false);
      // No default → not included in props
      expect(Object.keys(node?.props ?? {})).not.toContain('count');
    });

    it('initialises slots to empty arrays', () => {
      const comp = makeComponent('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ContainerComponent', {
        slots: [
          { name: 'header', multiple: false, required: true, allowedComponents: undefined },
          { name: 'content', multiple: true, required: false, allowedComponents: undefined },
        ],
      });
      const model = makeModel([comp]);
      const { document } = toBuilderDocument(model);
      const node = document.nodes.get(comp.id as never);

      expect(node?.slots['header']).toEqual([]);
      expect(node?.slots['content']).toEqual([]);
    });

    it('initialises bindings as an empty array', () => {
      const comp = makeComponent('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'TextComponent');
      const model = makeModel([comp]);
      const { document } = toBuilderDocument(model);
      const node = document.nodes.get(comp.id as never);

      expect(node?.bindings).toEqual([]);
    });
  });

  describe('multiple pages', () => {
    const comp1 = makeComponent('11111111-2222-3333-4444-555555555555', 'HeaderComponent');
    const comp2 = makeComponent('22222222-2222-3333-4444-555555555555', 'FooterComponent');
    const pageA = makePage('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', '/about', [comp1.id]);
    const pageB = makePage('bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee', '/home', [comp2.id]);

    it('selects the first page by routePath as root', () => {
      const model = makeModel([comp1, comp2, pageA, pageB]);
      const { document } = toBuilderDocument(model);
      // '/about' < '/home' lexicographically
      expect(document.rootNodes).toContain(comp1.id);
    });

    it('puts the remaining pages in additionalPages', () => {
      const model = makeModel([comp1, comp2, pageA, pageB]);
      const { additionalPages } = toBuilderDocument(model);
      expect(additionalPages).toHaveLength(1);
      expect(additionalPages[0]?.routePath).toBe('/home');
    });

    it('reports additional pages in lossPoints', () => {
      const model = makeModel([comp1, comp2, pageA, pageB]);
      const { roundTripFidelity } = toBuilderDocument(model);
      const additionalPageLoss = roundTripFidelity.lossPoints.find(
        (lp) => lp.description.includes('additional page'),
      );
      expect(additionalPageLoss).toBeDefined();
    });
  });

  describe('orphan components', () => {
    it('adds orphan components to nodes map and reports them in lossPoints', () => {
      const orphan = makeComponent('ffffffff-ffff-ffff-ffff-ffffffffffff', 'OrphanWidget');
      const page = makePage(
        'cccccccc-cccc-cccc-cccc-cccccccccccc',
        '/home',
        [], // orphan not referenced
      );
      const model = makeModel([orphan, page]);
      const { document, roundTripFidelity } = toBuilderDocument(model);

      expect(document.nodes.has(orphan.id as never)).toBe(true);

      const orphanLoss = roundTripFidelity.lossPoints.find((lp) =>
        lp.description.includes('orphan'),
      );
      expect(orphanLoss).toBeDefined();
      expect(orphanLoss?.location).toContain(orphan.id);
    });

    it('sets canRoundTrip = false when orphans exist', () => {
      const orphan = makeComponent('ffffffff-ffff-ffff-ffff-ffffffffffff', 'OrphanWidget');
      const model = makeModel([orphan]);
      const { roundTripFidelity } = toBuilderDocument(model);
      expect(roundTripFidelity.canRoundTrip).toBe(false);
    });

    it('reduces confidence below 1 when orphans exist', () => {
      const orphan = makeComponent('ffffffff-ffff-ffff-ffff-ffffffffffff', 'OrphanWidget');
      const model = makeModel([orphan]);
      const { roundTripFidelity } = toBuilderDocument(model);
      expect(roundTripFidelity.confidence).toBeLessThan(1);
    });
  });

  describe('design system', () => {
    it('includes tokenSetIds from token models', () => {
      const token = makeToken('tttttttt-tttt-tttt-tttt-tttttttttttt', 'PrimaryColor');
      const model = makeModel([token]);
      const { document } = toBuilderDocument(model);
      expect(document.designSystem.tokenSetIds).toContain(token.id);
    });

    it('uses primary theme id as themeId', () => {
      const theme = makeTheme('hhhhhhhh-hhhh-hhhh-hhhh-hhhhhhhhhhhh', 'DarkTheme');
      const model = makeModel([theme]);
      const { document } = toBuilderDocument(model);
      expect(document.designSystem.themeId).toBe(theme.id);
    });

    it('falls back to default-theme when no theme exists', () => {
      const model = makeModel([]);
      const { document } = toBuilderDocument(model);
      expect(document.designSystem.themeId).toBe('default-theme');
    });

    it('builds a componentContract for each component', () => {
      const comp = makeComponent('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ButtonComponent', {
        props: [
          { name: 'label', type: 'string', required: true, defaultValue: undefined, description: undefined, examples: [] },
        ],
      });
      const model = makeModel([comp]);
      const { document } = toBuilderDocument(model);
      const contract = document.designSystem.componentContracts.find(
        (c) => c.name === 'ButtonComponent',
      );
      expect(contract).toBeDefined();
      expect(contract?.props).toHaveLength(1);
      expect(contract?.props[0]?.name).toBe('label');
    });

    it('maps prop types to valid PropType enum values', () => {
      const comp = makeComponent('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'MixedPropComponent', {
        props: [
          { name: 'label', type: 'string', required: false, defaultValue: undefined, description: undefined, examples: [] },
          { name: 'count', type: 'int', required: false, defaultValue: undefined, description: undefined, examples: [] },
          { name: 'visible', type: 'bool', required: false, defaultValue: undefined, description: undefined, examples: [] },
          { name: 'items', type: 'string[]', required: false, defaultValue: undefined, description: undefined, examples: [] },
          { name: 'config', type: 'object', required: false, defaultValue: undefined, description: undefined, examples: [] },
          { name: 'unknownCustomType', type: 'VeryCustomType', required: false, defaultValue: undefined, description: undefined, examples: [] },
        ],
      });
      const model = makeModel([comp]);
      const { document } = toBuilderDocument(model);
      const contract = document.designSystem.componentContracts.find(
        (c) => c.name === 'MixedPropComponent',
      );

      const propTypes = Object.fromEntries(
        (contract?.props ?? []).map((p) => [p.name, p.type]),
      );

      expect(propTypes['label']).toBe('string');
      expect(propTypes['count']).toBe('number');
      expect(propTypes['visible']).toBe('boolean');
      expect(propTypes['items']).toBe('array');
      expect(propTypes['config']).toBe('object');
      // Unknown type falls back to string
      expect(propTypes['unknownCustomType']).toBe('string');
    });
  });

  describe('metadata', () => {
    it('preserves createdAt and updatedAt from the model', () => {
      const model = makeModel([], {
        createdAt: '2025-06-01T10:00:00.000Z',
        updatedAt: '2025-06-15T12:30:00.000Z',
      });
      const { document } = toBuilderDocument(model);
      expect(document.metadata.createdAt).toBe('2025-06-01T10:00:00.000Z');
      expect(document.metadata.updatedAt).toBe('2025-06-15T12:30:00.000Z');
    });

    it('includes the model id in the description', () => {
      const model = makeModel([]);
      const { document } = toBuilderDocument(model);
      expect(document.metadata.description).toContain(model.id);
    });

    it('includes version tag in metadata tags', () => {
      const model = makeModel([], { version: 7 });
      const { document } = toBuilderDocument(model);
      expect(document.metadata.tags).toContain('v7');
    });
  });
});
