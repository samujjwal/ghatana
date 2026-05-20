/**
 * @fileoverview Golden tests for DS generator
 *
 * Snapshot tests that compare generated output against expected golden files.
 * Ensures that code generation produces consistent, expected results.
 *
 * @doc.type test
 * @doc.purpose DS generator golden tests
 * @doc.layer ds-generator
 * @doc.pattern SnapshotTesting
 */

import { describe, it, expect } from 'vitest';
import { renderPresetToCss, PRESET_GHATANA_DEFAULT, materializePreset } from '../presets/index.js';
import { createDesignSystemDocument } from '../model/design-system-document.js';
import { emitFiles } from '../targets/emit-files.js';

// ============================================================================
// Fixtures
// ============================================================================

/**
 * Fixed deterministic context so all golden snapshots are stable across runs.
 * The clockFn returns a constant timestamp; idFn returns a predictable counter.
 */
let idCounter = 0;
const DETERMINISTIC_CONTEXT = {
  clockFn: () => '2024-01-01T00:00:00.000Z',
  idFn: () => `golden-id-${String(++idCounter).padStart(4, '0')}`,
};

const BASE_TOKENS = materializePreset(PRESET_GHATANA_DEFAULT) as Record<string, unknown>;

function makeGoldenDoc() {
  idCounter = 0; // Reset counter so each test gets reproducible IDs.
  return createDesignSystemDocument(
    'golden-doc-001',
    'Ghatana Golden Design System',
    PRESET_GHATANA_DEFAULT.id,
    BASE_TOKENS,
    undefined,
    DETERMINISTIC_CONTEXT,
  );
}

// ============================================================================
// Preset-level CSS golden tests (original)
// ============================================================================

describe('DS Generator Golden Tests', () => {
  it('generates preset CSS matching golden file', () => {
    const css = renderPresetToCss(PRESET_GHATANA_DEFAULT);

    expect(css).toMatchSnapshot('preset-css.golden');
  });

  it('renders CSS from preset matching golden file', () => {
    const css = renderPresetToCss(PRESET_GHATANA_DEFAULT);

    expect(css).toMatchSnapshot('rendered-css.golden');
  });
});

// ============================================================================
// emitFiles() multi-target golden snapshot tests
// ============================================================================

describe('emitFiles golden snapshots', () => {
  it('CSS target output matches golden snapshot', () => {
    const doc = makeGoldenDoc();
    const manifest = emitFiles(doc, { json: false, tailwind: false, reactTheme: false });

    expect(manifest.size).toBe(1);
    const [, cssFile] = [...manifest.entries()][0]!;
    expect(cssFile.filename).toMatch(/\.css$/);
    expect(cssFile.content).toMatchSnapshot('emit-files-css-target.golden');
    expect(cssFile.checksum).toMatchSnapshot('emit-files-css-checksum.golden');
  });

  it('JSON target output matches golden snapshot', () => {
    const doc = makeGoldenDoc();
    const manifest = emitFiles(doc, { css: false, tailwind: false, reactTheme: false });

    expect(manifest.size).toBe(1);
    const [, jsonFile] = [...manifest.entries()][0]!;
    expect(jsonFile.filename).toMatch(/\.tokens\.json$/);
    expect(jsonFile.content).toMatchSnapshot('emit-files-json-target.golden');
    expect(jsonFile.checksum).toMatchSnapshot('emit-files-json-checksum.golden');
  });

  it('Tailwind config target output matches golden snapshot', () => {
    const doc = makeGoldenDoc();
    const manifest = emitFiles(doc, { css: false, json: false, reactTheme: false });

    expect(manifest.size).toBe(1);
    const [, tailwindFile] = [...manifest.entries()][0]!;
    expect(tailwindFile.filename).toMatch(/\.tailwind\.config\.js$/);
    expect(tailwindFile.content).toMatchSnapshot('emit-files-tailwind-target.golden');
    expect(tailwindFile.checksum).toMatchSnapshot('emit-files-tailwind-checksum.golden');
  });

  it('React theme target output matches golden snapshot', () => {
    const doc = makeGoldenDoc();
    const manifest = emitFiles(doc, { css: false, json: false, tailwind: false });

    expect(manifest.size).toBe(1);
    const [, themeFile] = [...manifest.entries()][0]!;
    expect(themeFile.filename).toMatch(/\.theme\.tsx$/);
    expect(themeFile.content).toMatchSnapshot('emit-files-react-theme-target.golden');
    expect(themeFile.checksum).toMatchSnapshot('emit-files-react-theme-checksum.golden');
  });

  it('full pipeline (all four targets) is deterministic across two calls', () => {
    const doc = makeGoldenDoc();
    const manifest1 = emitFiles(doc);
    const manifest2 = emitFiles(doc);

    expect(manifest1.size).toBe(4);
    expect(manifest2.size).toBe(4);

    for (const [filename, file1] of manifest1) {
      const file2 = manifest2.get(filename);
      expect(file2).toBeDefined();
      expect(file2!.content).toBe(file1.content);
      expect(file2!.checksum).toBe(file1.checksum);
    }
  });

  it('full pipeline manifest filenames match golden snapshot', () => {
    const doc = makeGoldenDoc();
    const manifest = emitFiles(doc);

    const filenames = [...manifest.keys()].sort();
    expect(filenames).toMatchSnapshot('emit-files-all-filenames.golden');
  });
});
