/**
 * Utility class for accessibility-related operations.
 * Provides static helper methods for focus management and element queries.
 */
export class AccessibilityUtils {
  /**
   * Check if an element is in production environment.
   *
   * @returns true if running in production, false otherwise
   *
   * @example
   * if (AccessibilityUtils.isProduction()) {
   *   // skip dev-only operations
   * }
   */
  static isProduction(): boolean {
    const nodeEnv =
      (globalThis as unknown as { process?: { env?: { NODE_ENV?: string } } })
        .process?.env?.NODE_ENV ?? '';

    return nodeEnv === 'production';
  }

  /**
   * Get all focusable elements within a container.
   *
   * @param container - The container element to search
   * @param selector - CSS selector for focusable elements
   * @returns Array of focusable HTML elements
   *
   * @example
   * const focusable = AccessibilityUtils.getFocusableElements(
   *   containerRef.current,
   *   'button, input'
   * );
   */
  static getFocusableElements(
    container: HTMLElement | null,
    selector: string
  ): HTMLElement[] {
    if (!container) return [];

    return Array.from(container.querySelectorAll<HTMLElement>(selector));
  }

  /**
   * Restore focus to a previously active element.
   *
   * @param element - The element to focus
   *
   * @example
   * const previousFocus = document.activeElement as HTMLElement;
   * // ... do something ...
   * AccessibilityUtils.restoreFocus(previousFocus);
   */
  static restoreFocus(element: HTMLElement | null): void {
    if (element && typeof element.focus === 'function') {
      element.focus();
    }
  }

  /**
   * Set focus to the first focusable element in a container.
   *
   * @param container - The container element to search
   * @param selector - CSS selector for focusable elements
   * @returns true if focus was set, false otherwise
   *
   * @example
   * const focused = AccessibilityUtils.focusFirstElement(ref.current);
   */
  static focusFirstElement(
    container: HTMLElement | null,
    selector: string
  ): boolean {
    const elements = this.getFocusableElements(container, selector);
    if (elements.length === 0) {
      return false;
    }

    elements[0].focus();
    return true;
  }

  /**
   * Set focus to container as fallback when no focusable elements exist.
   *
   * @param container - The container element to focus
   * @returns true if focus was set, false otherwise
   *
   * @example
   * if (!focusableElements.length) {
   *   AccessibilityUtils.focusContainer(ref.current);
   * }
   */
  static focusContainer(container: HTMLElement | null): boolean {
    if (!container) return false;

    container.setAttribute('tabindex', '-1');
    container.focus();
    return true;
  }

  /**
   * Calculate next focus index with looping support.
   *
   * @param currentIndex - Current focus index
   * @param itemCount - Total number of items
   * @param loop - Whether to enable looping at boundaries
   * @returns Next index to focus
   *
   * @example
   * const nextIdx = AccessibilityUtils.getNextIndex(activeIndex, items.length, loop);
   */
  static getNextIndex(
    currentIndex: number,
    itemCount: number,
    loop: boolean
  ): number {
    const nextIndex = currentIndex + 1;

    if (loop) {
      return nextIndex >= itemCount ? 0 : nextIndex;
    }

    return Math.min(nextIndex, itemCount - 1);
  }

  /**
   * Calculate previous focus index with looping support.
   *
   * @param currentIndex - Current focus index
   * @param itemCount - Total number of items
   * @param loop - Whether to enable looping at boundaries
   * @returns Previous index to focus
   *
   * @example
   * const prevIdx = AccessibilityUtils.getPreviousIndex(activeIndex, items.length, loop);
   */
  static getPreviousIndex(
    currentIndex: number,
    itemCount: number,
    loop: boolean
  ): number {
    const prevIndex = currentIndex - 1;

    if (loop) {
      return prevIndex < 0 ? itemCount - 1 : prevIndex;
    }

    return Math.max(prevIndex, 0);
  }

  /**
   * Get item at specific index from list.
   *
   * @param items - Array of items
   * @param index - Index to retrieve
   * @returns Item at index or undefined
   *
   * @example
   * const item = AccessibilityUtils.getItemAtIndex(focusableElements, targetIndex);
   */
  static getItemAtIndex<T>(items: T[], index: number): T | undefined {
    if (index < 0 || index >= items.length) {
      return undefined;
    }

    // eslint-disable-next-line security/detect-object-injection
    return items[index];
  }
}
