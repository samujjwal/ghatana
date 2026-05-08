/**
 * Plugin Services Exports
 *
 * @doc.type module
 * @doc.purpose Plugin service exports
 * @doc.layer product
 */

export {
  DEFAULT_NETWORK_POLICY,
  DEFAULT_STORAGE_POLICY,
  DEFAULT_BROWSER_API_POLICY,
  DEFAULT_TELEMETRY_POLICY,
  createDefaultPluginRuntimePolicy,
  createTrustedPluginRuntimePolicy,
  createPluginSandboxBoundary,
  validatePluginRuntimePolicy,
  enforceNetworkPolicy,
  enforceStoragePolicy,
  enforceBrowserAPIPolicy,
  enforceTelemetryPolicy,
  generateSandboxAttribute,
  createPluginRuntimeEnvironment,
} from './PluginRuntimePolicy';

export type {
  NetworkPolicy,
  StoragePolicy,
  BrowserAPIPolicy,
  TelemetryPolicy,
  PluginRuntimePolicy,
  PluginSandboxBoundary,
  PluginRuntimeEnvironment,
} from './PluginRuntimePolicy';

export {
  COMPONENT_PACKAGE_SIGNATURE_ALGORITHM,
  buildComponentPackageSigningPayload,
  computeComponentPackageIntegrityDigest,
  validateComponentPackageSignature,
} from './ComponentPackageSigning';

export type {
  ComponentPackageSignatureAlgorithm,
  ComponentPackageSignatureSubject,
  ComponentPackageSignature,
  ComponentPackageSigningPayload,
  ComponentPackageSigningValidationResult,
  ComponentPackageSigningInput,
} from './ComponentPackageSigning';

export {
  validateComponentCompatibility,
  applyPropAdapters,
  validatePreviewPolicy,
  createFallbackComponent,
} from './ComponentCompatibilityValidator';

export type {
  RendererContract,
  PropType,
  PropAdapter,
  FallbackConfig,
  PreviewPolicy,
  CompatibilityValidationResult,
} from './ComponentCompatibilityValidator';

export {
  generateDesignSystemSpec,
  validateDesignSystemSpec,
  exportSpecAsJSON,
  importSpecFromJSON,
} from './DesignSystemGeneratorHandoff';

export type {
  ComponentMetadata,
  PropDefinition,
  DesignSystemSpec,
  SlotDefinition,
  StyleVariant,
  AccessibilityRequirements,
  HandoffResult,
} from './DesignSystemGeneratorHandoff';
