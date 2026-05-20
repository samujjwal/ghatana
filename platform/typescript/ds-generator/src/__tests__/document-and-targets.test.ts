/**
 * @fileoverview Tests for DesignSystemDocument model, token graph, targets,
 * and the document-level contrast audit.
 *
 * @doc.type test
 */

import { describe, it, expect } from 'vitest';
import {
  createDesignSystemDocument,
  DS_DOCUMENT_SCHEMA_VERSION,
  DesignSystemDocumentSchema,
} from '../model/design-system-document.js';
import {
  buildTokenGraph,
  flattenTokenRecord,
  graphToRecord,
} from '../tokens/token-graph.js';
import {
  assertDocumentContrastCompliance,
  auditContrastPairs,
  deriveColorPairs,
} from '../validation/contrast.js';
import { emitCss } from '../targets/css.js';
import { emitJson } from '../targets/json.js';
import { emitTailwind } from '../targets/tailwind.js';
import { emitReactTheme } from '../targets/react-theme.js';
import { emitFiles } from '../targets/emit-files.js';
import type { SemanticTokenAlias } from '../model/design-system-document.js';

// ============================================================================
// Helpers
// ============================================================================

function makeSimpleDoc() {
  return createDesignSystemDocument(
    'doc-1',
    'Test DS',
    'ghatana-default',
    {
      colors: { primary: '#3b82f6', background: '#ffffff', text: '#111827' },
      fontFamily: 'Inter',
      fontSizes: { sm: 14, md: 16, lg: 20 },
      borderRadius: { md: '0.375rem' },
      spacing: { 4: 16, 8: 32 },
      shadow: { md: '0 4px 6px -1px rgb(0 0 0 / 0.1)' },
      motion: { fast: '150ms' },
      zIndex: { modal: 50 },
    },
  );
}

// ============================================================================
// DesignSystemDocument model
// ============================================================================

describe('createDesignSystemDocument', () => {
  it('sets schemaVersion', () => {
    const doc = makeSimpleDoc();
    expect(doc.schemaVersion).toBe(DS_DOCUMENT_SCHEMA_VERSION);
  });

  it('passes Zod parse', () => {
    const doc = makeSimpleDoc();
    expect(() => DesignSystemDocumentSchema.parse(doc)).not.toThrow();
  });

  it('defaults empty arrays for aliases and variants', () => {
    const doc = makeSimpleDoc();
    expect(doc.semanticAliases).toEqual([]);
    expect(doc.componentVariants).toEqual([]);
  });

  it('accepts overrides', () => {
    const doc = createDesignSystemDocument(
      'doc-2',
      'Overridden DS',
      'preset-a',
      {},
      { brandName: 'my-brand', metadata: { team: 'platform' } },
    );
    expect(doc.brandName).toBe('my-brand');
    expect(doc.metadata?.['team']).toBe('platform');
  });
});

// ============================================================================
// Token graph
// ============================================================================

describe('buildTokenGraph', () => {
  it('resolves a direct alias', () => {
    const aliases: SemanticTokenAlias[] = [
      { alias: 'color.brand.primary', tokenKey: 'colors.primary', category: 'color' },
    ];
    const base = new Map<string, unknown>([['colors.primary', '#3b82f6']]);
    const result = buildTokenGraph(aliases, base);
    expect(result.isComplete).toBe(true);
    expect(result.resolved.get('color.brand.primary')?.value).toBe('#3b82f6');
  });

  it('detects cycles', () => {
    const aliases: SemanticTokenAlias[] = [
      { alias: 'a', tokenKey: 'b', category: 'other' },
      { alias: 'b', tokenKey: 'a', category: 'other' },
    ];
    const result = buildTokenGraph(aliases, new Map());
    expect(result.errors.some((e) => e.kind === 'cycle')).toBe(true);
  });

  it('reports missing token', () => {
    const aliases: SemanticTokenAlias[] = [
      { alias: 'x', tokenKey: 'nonexistent', category: 'other' },
    ];
    const result = buildTokenGraph(aliases, new Map());
    expect(result.errors[0]?.kind).toBe('missing-token');
  });
});

describe('flattenTokenRecord', () => {
  it('flattens nested records with dot keys', () => {
    const record = { colors: { primary: '#fff' }, fontFamily: 'Inter' };
    const flat = flattenTokenRecord(record);
    expect(flat.get('colors.primary')).toBe('#fff');
    expect(flat.get('fontFamily')).toBe('Inter');
    // Also stores the sub-object
    expect(flat.has('colors')).toBe(true);
  });
});

describe('graphToRecord', () => {
  it('converts resolved aliases to string record', () => {
    const aliases: SemanticTokenAlias[] = [
      { alias: 'token', tokenKey: 'colors.primary', category: 'color' },
    ];
    const base = new Map<string, unknown>([['colors.primary', '#3b82f6']]);
    const result = buildTokenGraph(aliases, base);
    const record = graphToRecord(result);
    expect(record['token']).toBe('#3b82f6');
  });
});

// ============================================================================
// Contrast audit
// ============================================================================

describe('auditContrastPairs', () => {
  it('marks white on white as failing', () => {
    const result = auditContrastPairs([
      { label: 'white/white', foreground: '#ffffff', background: '#ffffff' },
    ]);
    expect(result.isFullyCompliant).toBe(false);
    expect(result.failingCount).toBe(1);
  });

  it('marks black on white as passing AA', () => {
    const result = auditContrastPairs([
      { label: 'black/white', foreground: '#000000', background: '#ffffff' },
    ]);
    expect(result.isFullyCompliant).toBe(true);
    expect(result.entries[0]?.passesAA).toBe(true);
    expect(result.entries[0]?.passesAAA).toBe(true);
  });
});

describe('assertDocumentContrastCompliance', () => {
  it('passes a document with accessible canonical pairs', () => {
    const doc = makeSimpleDoc();
    expect(() => assertDocumentContrastCompliance(doc)).not.toThrow();
  });

  it('blocks file emission when canonical pairs fail WCAG AA', () => {
    const doc = createDesignSystemDocument(
      'doc-low-contrast',
      'Low Contrast DS',
      'preset',
      {
        colors: {
          background: '#ffffff',
          text: '#ffffff',
        },
      },
    );

    expect(() => emitFiles(doc)).toThrow(/contrast gate failed/);
  });

  it('allows explicit opt-out for exploratory generation', () => {
    const doc = createDesignSystemDocument(
      'doc-low-contrast-optout',
      'Low Contrast DS',
      'preset',
      {
        colors: {
          background: '#ffffff',
          text: '#ffffff',
        },
      },
    );

    expect(() => emitFiles(doc, { enforceContrast: false })).not.toThrow();
  });
});

describe('deriveColorPairs', () => {
  it('derives text/background pairs', () => {
    const colors = {
      background: '#ffffff',
      'text-primary': '#111827',
      'text-secondary': '#6b7280',
    };
    const pairs = deriveColorPairs(colors);
    expect(pairs.length).toBeGreaterThan(0);
    expect(pairs.some((p) => p.label.includes('text-primary'))).toBe(true);
  });
});

// ============================================================================
// CSS target
// ============================================================================

describe('emitCss', () => {
  it('contains :root block', () => {
    const doc = makeSimpleDoc();
    const css = emitCss(doc, { includeComments: false });
    expect(css).toContain(':root {');
    expect(css).toContain('--colors-primary: #3b82f6;');
  });

  it('emits comment header when enabled', () => {
    const doc = makeSimpleDoc();
    const css = emitCss(doc);
    expect(css).toContain('/* Design System: Test DS */');
  });

  it('emits component variant scope', () => {
    const doc = createDesignSystemDocument(
      'doc-v',
      'DS with variants',
      'preset',
      { colors: { primary: '#000' } },
      {
        componentVariants: [
          {
            componentId: 'Button',
            variants: { primary: { '--btn-bg': '#3b82f6' } },
            states: [{ state: 'hover', tokenOverrides: { '--btn-bg': '#2563eb' } }],
          },
        ],
      },
    );
    const css = emitCss(doc);
    expect(css).toContain('[data-variant="Button-primary"]');
    expect(css).toContain('[data-component="Button"]:hover');
  });
});

// ============================================================================
// JSON target
// ============================================================================

describe('emitJson', () => {
  it('produces valid JSON', () => {
    const doc = makeSimpleDoc();
    const { json, data } = emitJson(doc);
    expect(() => JSON.parse(json)).not.toThrow();
    expect(data.documentId).toBe('doc-1');
    expect(data.schemaVersion).toBe(DS_DOCUMENT_SCHEMA_VERSION);
  });

  it('includes metadata when enabled', () => {
    const doc = makeSimpleDoc();
    const { data } = emitJson(doc, { includeMetadata: true });
    expect(data.metadata?.['generator']).toBe('ghatana-ds-generator');
  });
});

// ============================================================================
// Tailwind target
// ============================================================================

describe('emitTailwind', () => {
  it('emits valid JS with extend block', () => {
    const doc = makeSimpleDoc();
    const tw = emitTailwind(doc);
    expect(tw).toContain('export default {');
    expect(tw).toContain('extend: {');
    expect(tw).toContain("'primary': '#3b82f6'");
  });

  it('emits without extend when disabled', () => {
    const doc = makeSimpleDoc();
    const tw = emitTailwind(doc, { extend: false });
    expect(tw).not.toContain('extend:');
  });
});

// ============================================================================
// React theme target
// ============================================================================

describe('emitReactTheme', () => {
  it('emits const export', () => {
    const doc = makeSimpleDoc();
    const source = emitReactTheme(doc);
    expect(source).toContain('export const theme');
    expect(source).toContain("'primary': '#3b82f6'");
  });

  it('respects variableName option', () => {
    const doc = makeSimpleDoc();
    const source = emitReactTheme(doc, { variableName: 'myTheme', includeTypes: false });
    expect(source).toContain('export const myTheme');
    expect(source).not.toContain("import type");
  });
});
