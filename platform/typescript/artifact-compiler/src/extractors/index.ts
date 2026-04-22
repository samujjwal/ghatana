/**
 * @fileoverview Extractor barrel export.
 */

export type {
  ExtractorIdentity,
  ExtractionResult,
  ExtractionError,
  ExtractionWarning,
  ArtifactExtractor,
  ExtractionContext,
} from './types';

// TypeScript component extractor
export {
  EXTRACTOR_ID as TS_COMPONENT_EXTRACTOR_ID,
  EXTRACTOR_VERSION as TS_COMPONENT_EXTRACTOR_VERSION,
  extractComponentsFromSource,
  extractComponentArtifact,
} from './typescript/component-extractor';

export type {
  ExtractedComponent,
} from './typescript/component-extractor';

// Page/route extractor
export {
  EXTRACTOR_ID as PAGE_EXTRACTOR_ID,
  EXTRACTOR_VERSION as PAGE_EXTRACTOR_VERSION,
  extractPageFromSource,
  extractPageArtifact,
} from './typescript/page-extractor';

export type {
  ExtractedPage,
} from './typescript/page-extractor';

// State management extractor
export {
  EXTRACTOR_ID as STATE_EXTRACTOR_ID,
  EXTRACTOR_VERSION as STATE_EXTRACTOR_VERSION,
  extractStateStoresFromSource,
  extractStateStoreArtifact,
} from './typescript/state-extractor';

export type {
  ExtractedStateStore,
} from './typescript/state-extractor';

// Storybook CSF extractor
export {
  EXTRACTOR_ID as CSF_EXTRACTOR_ID,
  EXTRACTOR_VERSION as CSF_EXTRACTOR_VERSION,
  parseCsfSource,
  extractStorybookCsf,
} from './storybook/csf-extractor';

export type {
  ExtractedStory,
  ExtractedMeta,
  ExtractedCsfData,
} from './storybook/csf-extractor';

// Prisma schema extractor
export {
  EXTRACTOR_ID as PRISMA_EXTRACTOR_ID,
  EXTRACTOR_VERSION as PRISMA_EXTRACTOR_VERSION,
  parsePrismaSchema,
  extractPrismaSchemaArtifact,
} from './prisma/schema-extractor';

export type {
  ExtractedPrismaModel,
} from './prisma/schema-extractor';
