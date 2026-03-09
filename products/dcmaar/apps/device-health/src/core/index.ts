/**
 * @fileoverview Core Module - Public API
 *
 * Exports all core types, interfaces, and utilities.
 *
 * @module core
 */

export type * from './interfaces';
export { DEFAULT_EXTENSION_CONFIG, loadConfig, saveConfig } from './config/ExtensionConfig';
