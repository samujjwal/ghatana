/**
 * @fileoverview Token validation utilities for design system generation.
 *
 * Exports WCAG contrast validation and other token-related utilities.
 *
 * @doc.type module
 * @doc.purpose Token validation utilities
 * @doc.layer ds-generator
 */

export {
  hexToRgb,
  calculateRelativeLuminance,
  calculateContrastRatio,
  validateContrast,
  validateContrastBatch,
  passesAA,
  passesAAA,
  passesAALarge,
  passesComponent,
  suggestContrastImprovements,
} from './contrast.js';

export type {
  ContrastRequirements,
  ContrastValidationResult,
} from './contrast.js';
