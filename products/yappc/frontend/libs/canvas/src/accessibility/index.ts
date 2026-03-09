/**
 * Accessibility Module for Canvas
 *
 * Provides comprehensive accessibility features including keyboard navigation,
 * ARIA support, and reduced-motion compliance.
 *
 * @module canvas/accessibility
 */

// Keyboard shortcuts (Feature 1.12)
export {
  ShortcutRegistry,
  globalShortcutRegistry,
  useKeyboardShortcuts,
  CANVAS_SHORTCUTS,
  type KeyboardShortcut,
  type ModifierKey,
  type ShortcutConflict,
} from './shortcutRegistry';

// ARIA roles and screen reader support (Feature 1.13)
export {
  getNodeAriaProps,
  getEdgeAriaProps,
  getCanvasAriaProps,
  AriaAnnouncer,
  globalAnnouncer,
  useAriaAnnouncer,
  describeNodeRelationships,
  CANVAS_ANNOUNCEMENTS,
  useCanvasAnnouncements,
  type CanvasAriaRole,
  type AriaProperties,
  type AnnouncePolite,
} from './ariaRoles';

// Reduced motion support (Feature 1.13)
export {
  prefersReducedMotion,
  useReducedMotion,
  getAnimationDuration,
  useAnimationConfig,
  getTransition,
  useTransition,
  getSpringConfig,
  CANVAS_ANIMATIONS,
  getCanvasAnimation,
  useCanvasAnimations,
  isZoomLevelSafe,
  clampZoomLevel,
  getResponsiveFontSize,
  isBrowserZoomHigh,
  useZoomResiliency,
  DEFAULT_ANIMATION_CONFIG,
  type AnimationConfig,
  type SpringConfig,
} from './reducedMotion';

// Screen Reader Enhancements (Feature 2.31)
export {
  createScreenReaderEnhancements,
  announceNodeRelationships,
  announceCollaborativeEdit,
  getKeyboardShortcutHelp,
  announceKeyboardShortcuts,
  announceCustom,
  getNextAnnouncement,
  clearAnnouncementQueue,
  setScreenReaderEnabled,
  updateScreenReaderConfig,
  registerKeyboardShortcut,
  unregisterKeyboardShortcut,
  getAnnouncementStatistics,
  describeNodeRelationships as describeRelationships,
  type ScreenReaderConfig,
  type ScreenReaderEnhancementState,
  type NodeRelationships,
  type CollaborativeEditEvent,
  type PolitenessLevel,
  type ShortcutCategory,
  type KeyboardShortcut as SR_KeyboardShortcut,
} from './screenReaderEnhancements';
