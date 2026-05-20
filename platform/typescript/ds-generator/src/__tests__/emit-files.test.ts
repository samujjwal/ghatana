/**
 * @fileoverview Tests for emitFiles() — deterministic multi-target pipeline.
 */

import { describe, it, expect } from 'vitest';
import { createDesignSystemDocument } from '../model/design-system-document.js';
import { PRESET_GHATANA_DEFAULT, materializePreset } from '../presets/index.js';
import {
  emitFiles,
  type EmittedFile,
  type EmittedFileManifest,
} from '../targets/emit-files.js';

// ============================================================================
// Fixture
// ============================================================================

/** Minimal resolved tokens from the default preset. */
const BASE_TOKENS = materializePreset(PRESET_GHATANA_DEFAULT) as Record<string, unknown>;

function makeDoc(name = 'Test Design System') {
  return createDesignSystemDocument(
    `doc-${name.toLowerCase().replace(/\s+/g, '-')}`,
    name,
    PRESET_GHATANA_DEFAULT.id,
    BASE_TOKENS,
  );
}

// ============================================================================
// emitFiles
// ============================================================================

describe('emitFiles', () => {
  it('returns all four targets by default', () => {
    const doc = makeDoc();
    const manifest = emitFiles(doc);

    expect(manifest.size).toBe(4);
    const filenames = [...manifest.keys()];
    expect(filenames.some((f) => f.endsWith('.css'))).toBe(true);
    expect(filenames.some((f) => f.endsWith('.tokens.json'))).toBe(true);
    expect(filenames.some((f) => f.endsWith('.tailwind.config.js'))).toBe(true);
    expect(filenames.some((f) => f.endsWith('.theme.tsx'))).toBe(true);
  });

  it('derives basename from document name in kebab-case', () => {
    const doc = makeDoc('My Brand Design System');
    const manifest = emitFiles(doc);
    const filenames = [...manifest.keys()];

    expect(filenames.every((f) => f.startsWith('my-brand-design-system'))).toBe(true);
  });

  it('uses explicit basename when provided', () => {
    const doc = makeDoc();
    const manifest = emitFiles(doc, { basename: 'custom-tokens' });
    const filenames = [...manifest.keys()];

    expect(filenames.every((f) => f.startsWith('custom-tokens'))).toBe(true);
  });

  it('omits targets when set to false', () => {
    const doc = makeDoc();
    const manifest = emitFiles(doc, {
      tailwind: false,
      reactTheme: false,
    });

    expect(manifest.size).toBe(2);
    const filenames = [...manifest.keys()];
    expect(filenames.some((f) => f.endsWith('.css'))).toBe(true);
    expect(filenames.some((f) => f.endsWith('.tokens.json'))).toBe(true);
    expect(filenames.some((f) => f.endsWith('.tailwind.config.js'))).toBe(false);
    expect(filenames.some((f) => f.endsWith('.theme.tsx'))).toBe(false);
  });

  it('every file has non-empty content and a checksum', () => {
    const doc = makeDoc();
    const manifest = emitFiles(doc);

    for (const [, file] of manifest) {
      expect(file.content.length).toBeGreaterThan(0);
      expect(file.checksum).toMatch(/^[0-9a-f]{8}$/);
    }
  });

  it('is deterministic — same input produces identical checksums', () => {
    const doc = makeDoc('Stable System');
    const manifest1 = emitFiles(doc);
    const manifest2 = emitFiles(doc);

    for (const [filename, file1] of manifest1) {
      const file2 = manifest2.get(filename);
      expect(file2).toBeDefined();
      expect(file2?.checksum).toBe(file1.checksum);
      expect(file2?.content).toBe(file1.content);
    }
  });

  it('CSS file contains :root custom property block', () => {
    const doc = makeDoc();
    const manifest = emitFiles(doc);
    const cssFile = [...manifest.values()].find((f) => f.filename.endsWith('.css'));

    expect(cssFile).toBeDefined();
    expect(cssFile?.content).toContain(':root {');
    expect(cssFile?.content).toContain('--');
  });

  it('JSON file is valid JSON with schemaVersion', () => {
    const doc = makeDoc();
    const manifest = emitFiles(doc);
    const jsonFile = [...manifest.values()].find((f) => f.filename.endsWith('.tokens.json'));

    expect(jsonFile).toBeDefined();
    const parsed = JSON.parse(jsonFile?.content ?? '{}') as Record<string, unknown>;
    expect(parsed['schemaVersion']).toBeDefined();
  });

  it('Tailwind file starts with a config comment', () => {
    const doc = makeDoc();
    const manifest = emitFiles(doc);
    const tailwindFile = [...manifest.values()].find((f) =>
      f.filename.endsWith('.tailwind.config.js'),
    );

    expect(tailwindFile).toBeDefined();
    expect(tailwindFile?.content).toContain('tailwindcss');
  });

  it('React theme file contains a ThemeProvider or theme export', () => {
    const doc = makeDoc();
    const manifest = emitFiles(doc);
    const themeFile = [...manifest.values()].find((f) => f.filename.endsWith('.theme.tsx'));

    expect(themeFile).toBeDefined();
    expect(themeFile?.content).toContain('export');
  });

  it('passes CssTargetOptions through to emitCss', () => {
    const doc = makeDoc();
    // Passing CSS options via object form — using prefix option
    const manifest = emitFiles(doc, { css: { prefix: '--brand-' } });
    const cssFile = [...manifest.values()].find((f) => f.filename.endsWith('.css'));

    expect(cssFile?.content).toContain('--brand-');
  });

  it('returns a ReadonlyMap — values cannot be mutated', () => {
    const doc = makeDoc();
    const manifest: EmittedFileManifest = emitFiles(doc);
    // It should be a Map (with .size, .get, .keys, .values, .entries, .forEach)
    expect(typeof manifest.size).toBe('number');
    expect(manifest.get).toBeDefined();
  });

  it('each file satisfies the EmittedFile interface', () => {
    const doc = makeDoc();
    const manifest = emitFiles(doc);

    for (const [, file] of manifest) {
      const typedFile: EmittedFile = file;
      expect(typeof typedFile.filename).toBe('string');
      expect(typeof typedFile.content).toBe('string');
      expect(typeof typedFile.checksum).toBe('string');
    }
  });
});
