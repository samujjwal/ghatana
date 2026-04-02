/**
 * @doc.type module
 * @doc.purpose Public API exports for Accessibility (WCAG 2.1 AA) utilities
 * @doc.layer platform
 */

export type { A11yConfig, AccessibleCanvasElementProps } from './canvas-a11y';
export {
  generateAriaProps,
  useCanvasFocus,
  useAnnouncer,
  ScreenReaderAnnouncer,
  AccessibleCanvasElement,
} from './canvas-a11y';
