/**
 * @fileoverview @ghatana/ds-registry - Design system registry.
 *
 * @doc.type package
 * @doc.purpose Provides component, token, theme, and pattern registration
 *   with compatibility checking and lookup queries.
 * @doc.layer platform
 * @doc.dependency @ghatana/ds-schema, zod
 */

export {
  getRegistryStore,
  createRegistryStore,
  resetRegistryStore,
} from './registry/store';

export type {
  ComponentEntry,
  TokenSetEntry,
  ThemeEntry,
  PatternEntry,
  RegistryStore,
} from './registry/store';
