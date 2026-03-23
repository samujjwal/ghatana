/**
 * Canvas Accessibility Utilities
 *
 * Comprehensive accessibility utilities for WCAG 2.1 AA compliance.
 * Provides focus management, screen reader support, keyboard navigation,
 * and high contrast mode detection.
 *
 * @doc.type utilities
 * @doc.purpose Accessibility compliance and utilities
 * @doc.layer application
 */

import { atom } from "jotai";
import { atomWithStorage } from "jotai/utils";

// ============================================================================
// ACCESSIBILITY TYPES
// ============================================================================

export type FocusDirection = "horizontal" | "vertical" | "grid" | "custom";
export type NavigationMode = "normal" | "screen-reader" | "keyboard-only";
export type ContrastMode = "normal" | "high-contrast" | "reduced-motion";

export interface FocusableElement {
  element: HTMLElement;
  id: string;
  group?: string;
  priority?: number;
  disabled?: boolean;
}

export interface AccessibilitySettings {
  reducedMotion: boolean;
  highContrast: boolean;
  screenReader: boolean;
  keyboardOnly: boolean;
  fontSize: "small" | "medium" | "large" | "extra-large";
  focusVisible: boolean;
}

// ============================================================================
// ACCESSIBILITY ATOMS
// ============================================================================

/**
 * Accessibility settings atom with localStorage persistence
 */
export const accessibilitySettingsAtom = atomWithStorage<AccessibilitySettings>(
  "canvas-accessibility-settings",
  {
    reducedMotion: false,
    highContrast: false,
    screenReader: false,
    keyboardOnly: false,
    fontSize: "medium",
    focusVisible: true,
  },
);

/**
 * Current navigation mode atom
 */
export const navigationModeAtom = atom<NavigationMode>("normal");

/**
 * Focus trap state atom
 */
export const focusTrapAtom = atom<string | null>(null);

// ============================================================================
// FOCUS MANAGEMENT
// ============================================================================

/**
 * Focus management utilities
 */
export class FocusManager {
  private focusableElements: Map<string, FocusableElement> = new Map();
  private currentFocus: string | null = null;
  private focusHistory: string[] = [];

  /**
   * Register a focusable element
   */
  register(
    element: HTMLElement,
    options: Partial<FocusableElement> = {},
  ): string {
    const id =
      options.id ||
      `focusable-${Date.now()}-${Math.random().toString(36).substring(2)}`;

    this.focusableElements.set(id, {
      element,
      id,
      group: options.group,
      priority: options.priority || 0,
      disabled: options.disabled || false,
    });

    return id;
  }

  /**
   * Unregister a focusable element
   */
  unregister(id: string): void {
    this.focusableElements.delete(id);
    if (this.currentFocus === id) {
      this.currentFocus = null;
    }
    this.focusHistory = this.focusHistory.filter((h) => h !== id);
  }

  /**
   * Focus an element by ID
   */
  focus(id: string, saveToHistory: boolean = true): boolean {
    const focusable = this.focusableElements.get(id);
    if (!focusable || focusable.disabled) return false;

    // Save current focus to history
    if (saveToHistory && this.currentFocus) {
      this.focusHistory.push(this.currentFocus);
    }

    // Focus the element
    focusable.element.focus();
    this.currentFocus = id;

    return true;
  }

  /**
   * Focus next element in direction
   */
  focusNext(direction: FocusDirection = "horizontal"): boolean {
    const elements = this.getSortedElements();
    const currentIndex = elements.findIndex((e) => e.id === this.currentFocus);

    let nextIndex = -1;

    switch (direction) {
      case "horizontal":
        nextIndex = currentIndex < elements.length - 1 ? currentIndex + 1 : 0;
        break;
      case "vertical":
        nextIndex = currentIndex < elements.length - 1 ? currentIndex + 1 : 0;
        break;
      case "grid":
        // Grid navigation would need more complex logic
        nextIndex = currentIndex < elements.length - 1 ? currentIndex + 1 : 0;
        break;
      case "custom":
        // Custom navigation logic would be provided by caller
        nextIndex = currentIndex < elements.length - 1 ? currentIndex + 1 : 0;
        break;
    }

    if (nextIndex >= 0 && nextIndex < elements.length) {
      return this.focus(elements[nextIndex].id);
    }

    return false;
  }

  /**
   * Focus previous element in direction
   */
  focusPrevious(direction: FocusDirection = "horizontal"): boolean {
    const elements = this.getSortedElements();
    const currentIndex = elements.findIndex((e) => e.id === this.currentFocus);

    let prevIndex = -1;

    switch (direction) {
      case "horizontal":
        prevIndex = currentIndex > 0 ? currentIndex - 1 : elements.length - 1;
        break;
      case "vertical":
        prevIndex = currentIndex > 0 ? currentIndex - 1 : elements.length - 1;
        break;
      case "grid":
        prevIndex = currentIndex > 0 ? currentIndex - 1 : elements.length - 1;
        break;
      case "custom":
        prevIndex = currentIndex > 0 ? currentIndex - 1 : elements.length - 1;
        break;
    }

    if (prevIndex >= 0 && prevIndex < elements.length) {
      return this.focus(elements[prevIndex].id);
    }

    return false;
  }

  /**
   * Go back to previous focus
   */
  goBack(): boolean {
    if (this.focusHistory.length === 0) return false;

    const previousId = this.focusHistory.pop();
    if (previousId) {
      return this.focus(previousId, false);
    }

    return false;
  }

  /**
   * Get all focusable elements sorted by priority and DOM order
   */
  private getSortedElements(): FocusableElement[] {
    return Array.from(this.focusableElements.values())
      .filter((e) => !e.disabled)
      .sort((a, b) => {
        // First by priority (higher priority first)
        if (a.priority !== b.priority) {
          return (b.priority || 0) - (a.priority || 0);
        }
        // Then by DOM order
        return a.element.compareDocumentPosition(b.element) &
          Node.DOCUMENT_POSITION_FOLLOWING
          ? -1
          : 1;
      });
  }

  /**
   * Get elements in a specific group
   */
  getGroupElements(group: string): FocusableElement[] {
    return Array.from(this.focusableElements.values()).filter(
      (e) => e.group === group && !e.disabled,
    );
  }

  /**
   * Get current focused element
   */
  getCurrentFocus(): FocusableElement | null {
    if (!this.currentFocus) return null;
    return this.focusableElements.get(this.currentFocus) || null;
  }

  /**
   * Clear all focusable elements
   */
  clear(): void {
    this.focusableElements.clear();
    this.currentFocus = null;
    this.focusHistory = [];
  }
}

// ============================================================================
// SCREEN READER UTILITIES
// ============================================================================

/**
 * Screen reader utilities
 */
export class ScreenReaderManager {
  private announcements: HTMLElement | null = null;

  constructor() {
    this.createAnnouncementRegion();
  }

  /**
   * Create screen reader announcement region
   */
  private createAnnouncementRegion(): void {
    if (typeof document === "undefined") return;

    this.announcements = document.createElement("div");
    this.announcements.setAttribute("aria-live", "polite");
    this.announcements.setAttribute("aria-atomic", "true");
    this.announcements.setAttribute("aria-relevant", "additions text");
    this.announcements.style.position = "absolute";
    this.announcements.style.left = "-10000px";
    this.announcements.style.width = "1px";
    this.announcements.style.height = "1px";
    this.announcements.style.overflow = "hidden";
    document.body.appendChild(this.announcements);
  }

  /**
   * Announce message to screen readers
   */
  announce(message: string, priority: "polite" | "assertive" = "polite"): void {
    if (!this.announcements) return;

    // Update aria-live based on priority
    this.announcements.setAttribute("aria-live", priority);

    // Clear previous content
    this.announcements.textContent = "";

    // Add new message
    setTimeout(() => {
      if (this.announcements) {
        this.announcements.textContent = message;
      }
    }, 100);
  }

  /**
   * Announce state change
   */
  announceStateChange(
    element: string,
    oldState: string,
    newState: string,
  ): void {
    this.announce(`${element} changed from ${oldState} to ${newState}`);
  }

  /**
   * Announce navigation
   */
  announceNavigation(location: string): void {
    this.announce(`Navigated to ${location}`);
  }

  /**
   * Announce error
   */
  announceError(error: string): void {
    this.announce(`Error: ${error}`, "assertive");
  }

  /**
   * Announce success
   */
  announceSuccess(message: string): void {
    this.announce(`Success: ${message}`);
  }

  /**
   * Cleanup
   */
  destroy(): void {
    if (this.announcements && this.announcements.parentNode) {
      this.announcements.parentNode.removeChild(this.announcements);
      this.announcements = null;
    }
  }
}

// ============================================================================
// KEYBOARD NAVIGATION
// ============================================================================

/**
 * Keyboard navigation utilities
 */
export class KeyboardNavigationManager {
  private handlers: Map<string, (event: KeyboardEvent) => boolean> = new Map();
  private active: boolean = true;

  /**
   * Register keyboard handler
   */
  register(key: string, handler: (event: KeyboardEvent) => boolean): void {
    this.handlers.set(key, handler);
  }

  /**
   * Unregister keyboard handler
   */
  unregister(key: string): void {
    this.handlers.delete(key);
  }

  /**
   * Handle keyboard event
   */
  handle(event: KeyboardEvent): boolean {
    if (!this.active) return false;

    const key = this.getKeyString(event);
    const handler = this.handlers.get(key);

    if (handler) {
      return handler(event);
    }

    return false;
  }

  /**
   * Get normalized key string
   */
  private getKeyString(event: KeyboardEvent): string {
    const parts: string[] = [];

    if (event.ctrlKey) parts.push("ctrl");
    if (event.altKey) parts.push("alt");
    if (event.shiftKey) parts.push("shift");
    if (event.metaKey) parts.push("meta");

    parts.push(event.key.toLowerCase());

    return parts.join("+");
  }

  /**
   * Activate/deactivate keyboard navigation
   */
  setActive(active: boolean): void {
    this.active = active;
  }

  /**
   * Clear all handlers
   */
  clear(): void {
    this.handlers.clear();
  }
}

// ============================================================================
// HIGH CONTRAST MODE
// ============================================================================

/**
 * High contrast mode utilities
 */
export class ContrastModeManager {
  private mediaQuery: MediaQueryList | null = null;
  private listeners: Set<(mode: ContrastMode) => void> = new Set();

  constructor() {
    this.initialize();
  }

  /**
   * Initialize contrast mode detection
   */
  private initialize(): void {
    if (typeof window === "undefined" || !window.matchMedia) return;

    // Detect high contrast mode
    this.mediaQuery = window.matchMedia("(prefers-contrast: high)");

    const handleChange = () => {
      const mode = this.getCurrentMode();
      this.notifyListeners(mode);
    };

    if (this.mediaQuery.addEventListener) {
      this.mediaQuery.addEventListener("change", handleChange);
    } else {
      // Fallback for older browsers
      this.mediaQuery.addListener(handleChange);
    }
  }

  /**
   * Get current contrast mode
   */
  getCurrentMode(): ContrastMode {
    if (this.mediaQuery?.matches) {
      return "high-contrast";
    }

    // Check for reduced motion
    if (typeof window !== "undefined" && window.matchMedia) {
      const reducedMotion = window.matchMedia(
        "(prefers-reduced-motion: reduce)",
      );
      if (reducedMotion.matches) {
        return "reduced-motion";
      }
    }

    return "normal";
  }

  /**
   * Subscribe to contrast mode changes
   */
  subscribe(listener: (mode: ContrastMode) => void): () => void {
    this.listeners.add(listener);

    // Immediately call with current mode
    listener(this.getCurrentMode());

    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Notify all listeners
   */
  private notifyListeners(mode: ContrastMode): void {
    this.listeners.forEach((listener) => listener(mode));
  }

  /**
   * Apply high contrast CSS variables
   */
  applyHighContrastVariables(): void {
    if (typeof document === "undefined") return;

    const root = document.documentElement;

    if (this.getCurrentMode() === "high-contrast") {
      root.style.setProperty("--canvas-bg-primary", "#000000");
      root.style.setProperty("--canvas-bg-secondary", "#1a1a1a");
      root.style.setProperty("--canvas-text-primary", "#ffffff");
      root.style.setProperty("--canvas-text-secondary", "#cccccc");
      root.style.setProperty("--canvas-border-primary", "#ffffff");
      root.style.setProperty("--canvas-border-focus", "#ffff00");
      root.style.setProperty("--canvas-interactive-primary", "#ffffff");
      root.style.setProperty("--canvas-interactive-primary-hover", "#ffff00");
      root.setAttribute("data-high-contrast", "true");
    } else {
      root.removeAttribute("data-high-contrast");
    }
  }

  /**
   * Cleanup
   */
  destroy(): void {
    if (this.mediaQuery) {
      if (this.mediaQuery.removeEventListener) {
        this.mediaQuery.removeEventListener("change", () => {});
      } else {
        // Fallback for older browsers
        this.mediaQuery.removeListener(() => {});
      }
    }
    this.listeners.clear();
  }
}

// ============================================================================
// GLOBAL INSTANCES
// ============================================================================

export const focusManager = new FocusManager();
export const screenReaderManager = new ScreenReaderManager();
export const keyboardNavigationManager = new KeyboardNavigationManager();
export const contrastModeManager = new ContrastModeManager();

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Check if element is focusable
 */
export function isFocusable(element: HTMLElement): boolean {
  if (!element) return false;

  // Check if element is disabled (only applicable to certain elements)
  if ("disabled" in element && element.disabled) return false;

  const tagName = element.tagName.toLowerCase();
  const focusableTags = [
    "a",
    "button",
    "input",
    "select",
    "textarea",
    "details",
  ];
  const focusableRoles = [
    "button",
    "link",
    "menuitem",
    "option",
    "radio",
    "checkbox",
    "tab",
  ];

  // Check if it's a focusable tag
  if (focusableTags.includes(tagName)) return true;

  // Check if it has a focusable role
  const role = element.getAttribute("role");
  if (role && focusableRoles.includes(role)) return true;

  // Check if it has tabindex
  if (
    element.hasAttribute("tabindex") &&
    element.getAttribute("tabindex") !== "-1"
  )
    return true;

  return false;
}

/**
 * Get all focusable elements in a container
 */
export function getFocusableElements(container: HTMLElement): HTMLElement[] {
  const focusableElements: HTMLElement[] = [];

  const walker = document.createTreeWalker(container, NodeFilter.SHOW_ELEMENT, {
    acceptNode: (node) => {
      const element = node as HTMLElement;
      return isFocusable(element)
        ? NodeFilter.FILTER_ACCEPT
        : NodeFilter.FILTER_SKIP;
    },
  });

  let node;
  while ((node = walker.nextNode())) {
    focusableElements.push(node as HTMLElement);
  }

  return focusableElements;
}

/**
 * Trap focus within a container
 */
export function trapFocus(container: HTMLElement): () => void {
  const focusableElements = getFocusableElements(container);

  if (focusableElements.length === 0) return () => {};

  const firstElement = focusableElements[0];
  const lastElement = focusableElements[focusableElements.length - 1];

  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key !== "Tab") return;

    if (event.shiftKey) {
      // Shift + Tab
      if (document.activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      }
    } else {
      // Tab
      if (document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    }
  };

  container.addEventListener("keydown", handleKeyDown);

  // Focus first element
  firstElement.focus();

  // Return cleanup function
  return () => {
    container.removeEventListener("keydown", handleKeyDown);
  };
}

/**
 * Generate unique ID for accessibility
 */
export function generateAriaId(prefix: string = "canvas"): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).substring(2)}`;
}

/**
 * Check if user prefers reduced motion
 */
export function prefersReducedMotion(): boolean {
  if (typeof window === "undefined" || !window.matchMedia) return false;
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/**
 * Apply reduced motion styles
 */
export function applyReducedMotion(element: HTMLElement): void {
  if (prefersReducedMotion()) {
    element.style.setProperty("--transition-duration", "0.01ms");
    element.style.setProperty("--animation-duration", "0.01ms");
    element.setAttribute("data-reduced-motion", "true");
  }
}
