/**
 * @fileoverview @ghatana/ui-builder - UI Builder foundation.
 *
 * @doc.type package
 * @doc.purpose Provides BuilderDocument model, component bindings, actions,
 *   validation, React code generation, and preview host protocol.
 * @doc.layer platform
 * @doc.subpaths core, react, web, preview, testing
 */

export * from './core/index';
export * from './ai/index';
export {
  attachBuilderDocumentCompatibility,
  normalizeBuilderDocument,
} from './core/legacy-builder-document-adapter';
export {
  builderDocumentV1Schema,
  BUILDER_DOCUMENT_V1_SCHEMA_ID,
  BUILDER_DOCUMENT_V1_VERSION,
} from './schema/index';
