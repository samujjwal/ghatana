/**
 * @ghatana/platform-testing
 *
 * Shared testing utilities for Ghatana platform.
 * Import specific sub-paths for tree-shaking:
 *   import { scanAccessibility } from '@ghatana/platform-testing/accessibility';
 *
 * @package @ghatana/platform-testing
 * @version 0.1.0
 * @doc.type module
 * @doc.purpose Platform-wide testing helpers — accessibility, performance, WCAG compliance
 * @doc.layer platform
 */

export * from "./accessibility-testing";
export * from './vitest-browser';
export * from './builder-preview';
