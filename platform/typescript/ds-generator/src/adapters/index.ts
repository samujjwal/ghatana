/**
 * @fileoverview Target Adapters Export
 *
 * Exports all target adapters for design system token generation.
 *
 * @doc.type module
 * @doc.purpose Target adapters export
 * @doc.layer ds-generator
 */

export { generateCSSVariables } from './css-variables.adapter.js';
export type { CSSVariablesAdapterOptions } from './css-variables.adapter.js';

export { generateTailwindConfig } from './tailwind.adapter.js';
export type { TailwindAdapterOptions } from './tailwind.adapter.js';

export { generateReactTheme } from './react-theme.adapter.js';
export type { ReactThemeAdapterOptions } from './react-theme.adapter.js';

export { generateJSONTokens } from './json.adapter.js';
export type { JSONAdapterOptions } from './json.adapter.js';
