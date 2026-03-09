/**
 * Generators - Public API
 *
 * @doc.type module
 * @doc.purpose Re-export generator functions
 * @doc.layer product
 * @doc.pattern Barrel
 */

export { generateModulesFromConcepts } from "./module-generator";
export type { ModuleGeneratorOptions, ModuleGeneratorResult } from "./module-generator";

export { generateLearningPaths } from "./learning-path-generator";
export type { LearningPathGeneratorOptions, LearningPathGeneratorResult } from "./learning-path-generator";

export { generateContentBlocks } from "./content-block-generator";
export type { ContentBlockGeneratorOptions, ContentBlockGeneratorResult } from "./content-block-generator";

export { generateManifestFromConcept, generateManifestsFromConcepts } from "./manifest-generator";
export type { ManifestGeneratorOptions, ManifestGeneratorResult } from "./manifest-generator";
