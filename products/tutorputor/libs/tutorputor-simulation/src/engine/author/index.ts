/**
 * Simulation Author Service - Exports
 * 
 * @doc.type module
 * @doc.purpose Export simulation author service components
 * @doc.layer product
 * @doc.pattern Barrel
 */

export { createSimulationAuthorService } from "./service";
export type { SimAuthorConfig, HealthAwareSimAuthorService } from "./service";
export { getPromptPack, getAllPromptPacks } from "./prompt-packs";
export type { PromptPack } from "./prompt-packs";
export { validateManifest, isValidManifest } from "./validation";
