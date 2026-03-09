/**
 * Domain Content Loader - Main Entry Point
 *
 * @doc.type module
 * @doc.purpose Load domain content (physics, chemistry, etc.) into database
 * @doc.layer product
 * @doc.pattern ETL
 */

export { loadDomainContent, validateDomainContent } from "./loaders/domain-loader";
export { parsePhysicsJSON } from "./parsers/physics-parser";
export { parseChemistryJSON } from "./parsers/chemistry-parser";

// Generators
export { generateModulesFromConcepts } from "./generators/module-generator";
export { generateLearningPaths } from "./generators/learning-path-generator";
export { generateContentBlocks } from "./generators/content-block-generator";
export { generateManifestFromConcept, generateManifestsFromConcepts } from "./generators/manifest-generator";
export { BulkSimulationGenerator } from "./bulk-generator";

export type {
  LoaderOptions,
  LoaderResult,
  ValidationResult,
} from "./types";

export type {
  ModuleGeneratorOptions,
  ModuleGeneratorResult,
} from "./generators/module-generator";

export type {
  LearningPathGeneratorOptions,
  LearningPathGeneratorResult,
} from "./generators/learning-path-generator";

export type {
  ContentBlockGeneratorOptions,
  ContentBlockGeneratorResult,
} from "./generators/content-block-generator";

export type {
  ManifestGeneratorOptions,
  ManifestGeneratorResult,
} from "./generators/manifest-generator";

export type {
  BulkGenerationJob,
  BulkGenerationRequest,
  BulkGenerationResult,
  BulkJobStatus,
} from "./bulk-generator";
