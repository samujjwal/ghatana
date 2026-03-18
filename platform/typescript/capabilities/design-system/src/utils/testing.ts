/**
 * Testing utilities for @ghatana/ui components
 */

/**
 * Render component and get accessibility violations
 */
export async function getA11yViolations(_container: HTMLElement) {
  // This would use jest-axe in actual implementation
  // Placeholder for demonstration
  return [];
}

/**
 * Test keyboard navigation
 */
export function testKeyboardNavigation(
  element: HTMLElement,
  expectedOrder: HTMLElement[]
): boolean {
  const focusableElements = Array.from(
    element.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')
  ) as HTMLElement[];

  return focusableElements.length === expectedOrder.length &&
    focusableElements.every((el, i) => el === expectedOrder[i]);
}

/**
 * Test screen reader announcements
 */
export function testScreenReaderAnnouncements(element: HTMLElement): string[] {
  const announcements: string[] = [];

  // Check for aria-label
  const ariaLabel = element.getAttribute('aria-label');
  if (ariaLabel) announcements.push(ariaLabel);

  // Check for aria-labelledby
  const ariaLabelledBy = element.getAttribute('aria-labelledby');
  if (ariaLabelledBy) {
    const labelElement = document.getElementById(ariaLabelledBy);
    if (labelElement) announcements.push(labelElement.textContent || '');
  }

  // Check for aria-describedby
  const ariaDescribedBy = element.getAttribute('aria-describedby');
  if (ariaDescribedBy) {
    const descElement = document.getElementById(ariaDescribedBy);
    if (descElement) announcements.push(descElement.textContent || '');
  }

  return announcements;
}

/**
 * Test color contrast
 */
export function testColorContrast(element: HTMLElement): number {
  const style = window.getComputedStyle(element);
  const bgColor = style.backgroundColor;
  const color = style.color;

  // Parse RGB values
  const parseRGB = (rgb: string): [number, number, number] => {
    const match = rgb.match(/\d+/g);
    return match ? [parseInt(match[0]), parseInt(match[1]), parseInt(match[2])] : [0, 0, 0];
  };

  const bg = parseRGB(bgColor);
  const fg = parseRGB(color);

  // Calculate luminance
  const getLuminance = (rgb: [number, number, number]) => {
    const [r, g, b] = rgb.map((val) => {
      const v = val / 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };

  const l1 = getLuminance(bg);
  const l2 = getLuminance(fg);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);

  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Test responsive behavior
 */
export function testResponsiveBehavior(
  element: HTMLElement,
  breakpoints: Record<string, number>
): Record<string, boolean> {
  const results: Record<string, boolean> = {};

  Object.entries(breakpoints).forEach(([name, width]) => {
    // Simulate viewport width
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: width,
    });

    // Trigger resize event
    window.dispatchEvent(new Event('resize'));

    // Check if element is visible
    const style = window.getComputedStyle(element);
    results[name] = style.display !== 'none';
  });

  return results;
}

/**
 * Performance metrics collector
 */
export class PerformanceMetrics {
  private marks: Map<string, number> = new Map();
  private measures: Map<string, number> = new Map();

  /**
   * Start measuring
   */
  start(label: string): void {
    this.marks.set(label, performance.now());
  }

  /**
   * End measuring
   */
  end(label: string): number {
    const start = this.marks.get(label);
    if (!start) {
      console.warn(`No start mark for ${label}`);
      return 0;
    }

    const duration = performance.now() - start;
    this.measures.set(label, duration);
    this.marks.delete(label);

    return duration;
  }

  /**
   * Get all measures
   */
  getAll(): Record<string, number> {
    return Object.fromEntries(this.measures);
  }

  /**
   * Get specific measure
   */
  get(label: string): number | undefined {
    return this.measures.get(label);
  }

  /**
   * Clear all measures
   */
  clear(): void {
    this.marks.clear();
    this.measures.clear();
  }

  /**
   * Get average
   */
  getAverage(): number {
    const values = Array.from(this.measures.values());
    return values.length > 0 ? values.reduce((a, b) => a + b, 0) / values.length : 0;
  }
}

/**
 * Snapshot testing helper
 */
export function createComponentSnapshot(element: HTMLElement): string {
  return element.outerHTML;
}

/**
 * Visual regression testing helper
 */
export async function captureComponentVisual(element: HTMLElement): Promise<string> {
  // This would use a visual testing library in actual implementation
  return element.outerHTML;
}

/**
 * Interaction testing helper
 */
export class InteractionTester {
  constructor(private element: HTMLElement) {}

  /**
   * Simulate click
   */
  click(): void {
    this.element.click();
  }

  /**
   * Simulate keyboard event
   */
  keyDown(key: string): void {
    const event = new KeyboardEvent('keydown', { key });
    this.element.dispatchEvent(event);
  }

  /**
   * Simulate focus
   */
  focus(): void {
    this.element.focus();
  }

  /**
   * Simulate blur
   */
  blur(): void {
    this.element.blur();
  }

  /**
   * Simulate input
   */
  input(value: string): void {
    if (this.element instanceof HTMLInputElement) {
      this.element.value = value;
      this.element.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }

  /**
   * Simulate change
   */
  change(value: string): void {
    if (this.element instanceof HTMLInputElement) {
      this.element.value = value;
      this.element.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }

  /**
   * Get computed style
   */
  getComputedStyle(property: string): string {
    return window.getComputedStyle(this.element).getPropertyValue(property);
  }

  /**
   * Check if element has class
   */
  hasClass(className: string): boolean {
    return this.element.classList.contains(className);
  }

  /**
   * Get attribute
   */
  getAttribute(name: string): string | null {
    return this.element.getAttribute(name);
  }
}
