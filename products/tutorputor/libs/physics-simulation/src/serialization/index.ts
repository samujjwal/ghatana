/**
 * @doc.type module
 * @doc.purpose Serialization exports for import/export
 * @doc.layer core
 * @doc.pattern Barrel
 */

export {
  type PhysicsSimulationManifest,
  MANIFEST_VERSION,
  physicsManifestSchema,
  createManifest,
  exportManifestToJSON,
  downloadManifest,
  parseManifest,
  migrateManifest,
  readManifestFromFile,
} from "./manifest";
