/**
 * @fileoverview Capabilities barrel export.
 */

export {
  SupportLevelSchema,
  type SupportLevel,
  CapabilityMetadataSchema,
  type CapabilityMetadata,
  ProviderCapabilitySchema,
  type ProviderCapability,
  ExtractorCapabilitySchema,
  type ExtractorCapability,
  EmitterCapabilitySchema,
  type EmitterCapability,
  ValidatorCapabilitySchema,
  type ValidatorCapability,
  CapabilitySchema,
  type Capability,
} from './capability-registry';

export {
  type CapabilityRegistry,
  type RegistryStats,
  capabilityRegistry,
  registerBuiltinCapabilities,
} from './capability-registry';
