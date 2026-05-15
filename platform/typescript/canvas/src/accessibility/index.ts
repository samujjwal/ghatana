/**
 * @fileoverview Canvas Accessibility barrel export.
 *
 * @doc.type module
 * @doc.purpose Accessibility utilities public API
 * @doc.layer platform
 */

// Re-export from accessibility.ts
export {
  FocusManager,
  ScreenReaderManager,
  KeyboardNavigationManager,
  ContrastModeManager,
  focusManager,
  screenReaderManager,
  keyboardNavigationManager,
  contrastModeManager,
  isFocusable,
  getFocusableElements,
  trapFocus,
  generateAriaId,
  prefersReducedMotion,
  applyReducedMotion,
  accessibilitySettingsAtom,
  navigationModeAtom,
  focusTrapAtom,
  type FocusDirection,
  type NavigationMode,
  type ContrastMode,
  type FocusableElement,
  type AccessibilitySettings,
} from "./accessibility.js";

// Re-export from keyboard-traversal.ts
export {
  TraversableRegistry,
  TraversalEngine,
  AriaLabelGenerator,
  FocusVisibleManager,
  getNonColorStatusSignal,
  applyNonColorStatus,
  reconcileFocusPath,
  isFocusPathValid,
  NON_COLOR_STATUS_SIGNALS,
  type TraversalDirection,
  type TraversableElement,
  type TraversalResult,
  type FocusVisibleState,
  type NonColorStatus,
  type NonColorPattern,
  type NonColorStatusSignal,
} from "./keyboard-traversal.js";

// Re-export from AccessibilityProvider.tsx
export {
  AccessibilityProvider,
  useAccessibility,
  type AccessibilityContextValue,
  type AccessibilityProviderProps,
} from "./AccessibilityProvider.js";
