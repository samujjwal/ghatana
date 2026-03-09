/**
 * Simulation SDK - Module exports.
 *
 * @doc.type module
 * @doc.purpose Barrel exports for simulation SDK
 * @doc.layer product
 * @doc.pattern Module
 */

// Base kernel
export { BaseKernel } from './base-kernel';
export type { KernelState, KernelAnalytics, KernelHooks } from './base-kernel';

// Kernel builder
export { KernelBuilder, createKernel } from './kernel-builder';

// Plugin system
export {
  pluginRegistry,
  defineKernelPlugin,
  definePromptPackPlugin,
  defineVisualizerPlugin,
  isKernelPlugin,
  isPromptPackPlugin,
  isVisualizerPlugin,
} from './plugin-system';
export type {
  PluginMetadata,
  KernelPlugin,
  PromptPackPlugin,
  VisualizerPlugin,
  SimulationPlugin,
} from './plugin-system';

// Manifest schema
export {
  ManifestSchema,
  EntitySchema,
  StepSchema,
  KeyframeSchema,
  VisualStyleSchema,
  PositionSchema,
  validateManifest,
  validateEntity,
  validateStep,
} from './manifest-schema';
export type {
  Position,
  VisualStyle,
  Entity,
  StepAction,
  Annotation,
  Step,
  Keyframe,
  Manifest,
} from './manifest-schema';
