/**
 * Z-index tokens for the design system
 * 
 * These tokens define the stacking order for layered elements,
 * ensuring consistent z-index values across the application.
 */

/**
 * Z-index scale
 * 
 * Use these values to maintain a consistent stacking order.
 * Values are organized by common UI layers.
 */
export const zIndex = {
  /**
   * Base level - default content
   */
  base: 0,
  
  /**
   * Dropdown menus, popovers
   * @default 1000
   */
  dropdown: 1000,
  
  /**
   * Sticky positioned elements (headers, sidebars)
   * @default 1100
   */
  sticky: 1100,
  
  /**
   * Fixed positioned elements (toolbars, FABs)
   * @default 1200
   */
  fixed: 1200,
  
  /**
   * Backdrop/overlay for modals and drawers
   * @default 1300
   */
  backdrop: 1300,
  
  /**
   * Modals, dialogs, drawers
   * @default 1400
   */
  modal: 1400,
  
  /**
   * Popovers, tooltips (need to be above modals)
   * @default 1500
   */
  popover: 1500,
  
  /**
   * Toast notifications, snackbars
   * @default 1600
   */
  toast: 1600,
  
  /**
   * Tooltips (highest priority for user guidance)
   * @default 1700
   */
  tooltip: 1700,
  
  /**
   * Loading spinners, progress indicators (global)
   * @default 9998
   */
  loader: 9998,
  
  /**
   * Debug overlays, development tools
   * @default 9999
   */
  debug: 9999,
};

/**
 * Semantic z-index aliases
 */
export const semanticZIndex = {
  /**
   * Content that should appear above regular content but below interactive elements
   */
  elevated: zIndex.dropdown,
  
  /**
   * Interactive overlays (dropdowns, menus)
   */
  overlay: zIndex.dropdown,
  
  /**
   * Navigation elements that need to stay above content
   */
  navigation: zIndex.sticky,
  
  /**
   * Modal dialogs and their backdrops
   */
  dialog: zIndex.modal,
  dialogBackdrop: zIndex.backdrop,
  
  /**
   * Contextual information (tooltips, popovers)
   */
  contextual: zIndex.popover,
  
  /**
   * Temporary notifications
   */
  notification: zIndex.toast,
  
  /**
   * Critical system-level UI
   */
  system: zIndex.loader,
};

/**
 * Component-specific z-index values
 * 
 * Use these for common component patterns
 */
export const componentZIndex = {
  /**
   * AppBar / Header
   */
  appBar: zIndex.sticky,
  
  /**
   * Drawer / Sidebar
   */
  drawer: zIndex.modal,
  drawerBackdrop: zIndex.backdrop,
  
  /**
   * Modal / Dialog
   */
  modal: zIndex.modal,
  modalBackdrop: zIndex.backdrop,
  
  /**
   * Menu / Dropdown
   */
  menu: zIndex.dropdown,
  
  /**
   * Popover
   */
  popover: zIndex.popover,
  
  /**
   * Tooltip
   */
  tooltip: zIndex.tooltip,
  
  /**
   * Snackbar / Toast
   */
  snackbar: zIndex.toast,
  
  /**
   * Floating Action Button (FAB)
   */
  fab: zIndex.fixed,
  
  /**
   * Speed dial (group of FABs)
   */
  speedDial: zIndex.fixed + 1,
  
  /**
   * Loading overlay
   */
  loadingOverlay: zIndex.modal + 1,
  
  /**
   * Mobile bottom navigation
   */
  bottomNavigation: zIndex.sticky,
};

/**
 * Helper function to get z-index value
 * 
 * @param layer - The layer name
 * @returns The z-index value
 * 
 * @example
 * ```typescript
 * const modalZIndex = getZIndex('modal'); // 1400
 * ```
 */
export function getZIndex(layer: keyof typeof zIndex): number {
  return zIndex[layer];
}

/**
 * Helper function to get relative z-index
 * 
 * @param layer - The base layer name
 * @param offset - The offset from the base layer
 * @returns The calculated z-index value
 * 
 * @example
 * ```typescript
 * const aboveModal = getRelativeZIndex('modal', 1); // 1401
 * const belowModal = getRelativeZIndex('modal', -1); // 1399
 * ```
 */
export function getRelativeZIndex(
  layer: keyof typeof zIndex,
  offset: number
): number {
  return zIndex[layer] + offset;
}
