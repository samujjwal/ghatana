/**
 * @fileoverview Deterministic multi-target file emission pipeline.
 *
 * Produces a complete set of design-system artifact files from a single
 * DesignSystemDocument in a single, reproducible call. Callers receive a
 * manifest they can write to disk, cache, or compare deterministically.
 *
 * @doc.type module
 * @doc.purpose DesignSystemDocument → deterministic file manifest emission
 * @doc.layer ds-generator
 * @doc.pattern Pipeline
 */

import type { DesignSystemDocument } from '../model/design-system-document.js';
import { emitCss, type CssTargetOptions } from './css.js';
import { emitJson, type JsonTargetOptions } from './json.js';
import { emitTailwind, type TailwindTargetOptions } from './tailwind.js';
import { emitReactTheme, type ReactThemeTargetOptions } from './react-theme.js';

// ============================================================================
// Types
// ============================================================================

/**
 * A single emitted file — filename relative to the output directory, content
 * as a UTF-8 string, and a deterministic SHA-256-style content checksum.
 */
export interface EmittedFile {
  /** Output filename, relative to the design-system output directory. */
  readonly filename: string;
  /** UTF-8 file content. */
  readonly content: string;
  /**
   * Deterministic 32-character hex digest of the content (djb2 hash, stable
   * across calls for identical input). Used for cache invalidation and diffing.
   */
  readonly checksum: string;
}

/**
 * The complete set of files emitted for a DesignSystemDocument.
 * Keyed by filename for O(1) lookup.
 */
export type EmittedFileManifest = ReadonlyMap<string, EmittedFile>;

/** Options controlling which targets to emit and their per-target settings. */
export interface EmitFilesOptions {
  /**
   * Whether to emit the CSS variables file.
   * Default: true.
   */
  css?: boolean | CssTargetOptions;
  /**
   * Whether to emit the W3C DTCG-inspired JSON tokens file.
   * Default: true.
   */
  json?: boolean | JsonTargetOptions;
  /**
   * Whether to emit the Tailwind CSS config file.
   * Default: true.
   */
  tailwind?: boolean | TailwindTargetOptions;
  /**
   * Whether to emit the React theme context file.
   * Default: true.
   */
  reactTheme?: boolean | ReactThemeTargetOptions;
  /**
   * Base filename without extension for generated files.
   * Default: derived from document name (kebab-case), e.g. "my-design-system".
   */
  basename?: string;
}

// ============================================================================
// Internal helpers
// ============================================================================

/** djb2 non-cryptographic hash — deterministic and fast for content diffing. */
function djb2Checksum(content: string): string {
  let hash = 5381;
  for (let i = 0; i < content.length; i++) {
    // djb2: hash * 33 ^ charCode
    hash = ((hash << 5) + hash) ^ content.charCodeAt(i);
    // Keep within 32-bit signed integer range
    hash |= 0;
  }
  // Convert to unsigned hex, zero-padded to 8 chars
  return (hash >>> 0).toString(16).padStart(8, '0');
}

/** Convert a string to kebab-case for safe filenames. */
function toKebabCase(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function makeFile(filename: string, content: string): EmittedFile {
  return { filename, content, checksum: djb2Checksum(content) };
}

// ============================================================================
// Public API
// ============================================================================

/**
 * Emit all selected design-system artifact files from a DesignSystemDocument
 * in a single deterministic call.
 *
 * The returned manifest maps each filename to its {@link EmittedFile}. The
 * output is fully deterministic: the same document and options always produce
 * the same filenames, content, and checksums.
 *
 * @example
 * ```ts
 * const doc = createDesignSystemDocument({ name: 'Ghatana Default', basePresetId: 'ghatana-default' });
 * const manifest = emitFiles(doc, { reactTheme: false });
 * for (const [filename, file] of manifest) {
 *   await fs.writeFile(path.join(outputDir, filename), file.content, 'utf8');
 * }
 * ```
 */
export function emitFiles(
  doc: DesignSystemDocument,
  options: EmitFilesOptions = {},
): EmittedFileManifest {
  const {
    css = true,
    json = true,
    tailwind = true,
    reactTheme = true,
    basename,
  } = options;

  const base = basename ?? toKebabCase(doc.name || doc.documentId);
  const files = new Map<string, EmittedFile>();

  if (css !== false) {
    const cssOptions: CssTargetOptions = css === true ? {} : css;
    const content = emitCss(doc, cssOptions);
    const filename = `${base}.css`;
    files.set(filename, makeFile(filename, content));
  }

  if (json !== false) {
    const jsonOptions: JsonTargetOptions = json === true ? {} : json;
    const { json: content } = emitJson(doc, jsonOptions);
    const filename = `${base}.tokens.json`;
    files.set(filename, makeFile(filename, content));
  }

  if (tailwind !== false) {
    const tailwindOptions: TailwindTargetOptions = tailwind === true ? {} : tailwind;
    const content = emitTailwind(doc, tailwindOptions);
    const filename = `${base}.tailwind.config.js`;
    files.set(filename, makeFile(filename, content));
  }

  if (reactTheme !== false) {
    const reactThemeOptions: ReactThemeTargetOptions = reactTheme === true ? {} : reactTheme;
    const content = emitReactTheme(doc, reactThemeOptions);
    const filename = `${base}.theme.tsx`;
    files.set(filename, makeFile(filename, content));
  }

  return files;
}
