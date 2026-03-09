/**
 * @fileoverview Preset type definitions
 */

/**
 * Platform compatibility flags
 */
export interface PlatformCompatibility {
  /** Compatible with agent */
  agent: boolean;
  /** Compatible with desktop */
  desktop: boolean;
  /** Compatible with extension */
  extension: boolean;
}

/**
 * Base preset interface
 */
export interface BasePreset<T = any> {
  /** Unique preset identifier */
  id: string;
  /** Human-readable name */
  name: string;
  /** Preset description */
  description: string;
  /** Configuration object */
  config: T;
  /** Tags for categorization */
  tags: string[];
  /** Platform compatibility */
  compatibility: PlatformCompatibility;
  /** Preset version */
  version?: string;
}

/**
 * Source preset
 */
export interface SourcePreset extends BasePreset {
  config: {
    type: string;
    [key: string]: unknown;
  };
}

/**
 * Sink preset
 */
export interface SinkPreset extends BasePreset {
  config: {
    type: string;
    [key: string]: unknown;
  };
}

/**
 * Processor preset
 */
export interface ProcessorPreset extends BasePreset {
  config: {
    type: string;
    [key: string]: unknown;
  };
}

/**
 * Complete configuration preset
 */
export interface CompletePreset extends BasePreset {
  config: {
    sources: unknown[];
    sinks: unknown[];
    processors?: unknown[];
    monitoring?: unknown;
    security?: unknown;
  };
}
