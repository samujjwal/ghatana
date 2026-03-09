/**
 * Canvas Examples Barrel Export
 *
 * Provides access to all Canvas library examples for development,
 * testing, and documentation purposes.
 *
 * @warning These examples are for development use only.
 * Do not import in production builds.
 */

// Basic examples - fundamental Canvas usage
export { BasicCanvasExample } from './basic/BasicCanvasExample';
export type { BasicCanvasExampleProps } from './basic/BasicCanvasExample';

// Development guard - warn if imported in production
if (process.env.NODE_ENV === 'production') {
  console.warn(
    '@ghatana/yappc-canvas/examples: Examples should not be imported in production builds. ' +
      'Use conditional imports or build-time exclusions.'
  );
}
