/**
 * @fileoverview Builder Document JSON Schema exports.
 *
 * Exports the canonical JSON Schema (Draft 2020-12) for BuilderDocument v1.0.0.
 * This is the authoritative schema for validating BuilderDocument payloads across
 * all runtimes (TypeScript, Java, CLI tools, CI gates).
 *
 * @doc.type module
 * @doc.purpose JSON Schema re-export for BuilderDocument v1
 * @doc.layer platform
 */

import builderDocumentV1SchemaRaw from './builder-document-v1.schema.json';

/**
 * Canonical JSON Schema (Draft 2020-12) for BuilderDocument v1.0.0.
 *
 * Import this to validate raw documents before deserializing them:
 *
 * @example
 * ```ts
 * import { builderDocumentV1Schema } from '@ghatana/ui-builder';
 * // Use with any JSON-Schema validator (e.g. networknt, ajv)
 * ```
 */
export const builderDocumentV1Schema: Readonly<Record<string, unknown>> =
  builderDocumentV1SchemaRaw as Readonly<Record<string, unknown>>;

/** The schema $id URI for BuilderDocument v1.0.0. */
export const BUILDER_DOCUMENT_V1_SCHEMA_ID =
  'https://schemas.ghatana.dev/ui-builder/builder-document-v1.schema.json';

/** The current schema version string this package targets. */
export const BUILDER_DOCUMENT_V1_VERSION = '1.0.0';
