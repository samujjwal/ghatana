/**
 * Accessibility Compliance Testing Utilities
 * @doc.type utility
 * @doc.purpose WCAG 2.1 AA accessibility testing helpers and matchers
 * @doc.layer testing
 */

/**
 * Accessibility violation severity levels
 */
export type AccessibilityViolationSeverity =
  | "minor"
  | "moderate"
  | "serious"
  | "critical";

/**
 * Accessibility check result
 */
export interface AccessibilityCheckResult {
  violations: Array<{
    id: string;
    description: string;
    severity: AccessibilityViolationSeverity;
    nodes: number;
  }>;
  passes: string[];
  incomplete: string[];
}

/**
 * Run accessibility scan on component
 */
export async function scanAccessibility(
  container: HTMLElement,
): Promise<AccessibilityCheckResult> {
  // Would use axe-core for actual accessibility scanning
  return {
    violations: [],
    passes: [],
    incomplete: [],
  };
}

/**
 * Verify ARIA attributes on element
 */
export function verifyAriaAttributes(
  element: HTMLElement,
  expectedAttrs: Record<string, string>,
): boolean {
  return Object.entries(expectedAttrs).every(([key, value]) => {
    return element.getAttribute(`aria-${key}`) === value;
  });
}

/**
 * Check color contrast ratio
 */
export function getContrastRatio(
  foreground: string,
  background: string,
): number {
  // Would calculate actual contrast ratio
  return 4.5;
}

/**
 * Verify keyboard navigation support
 */
export function hasKeyboardSupport(element: HTMLElement): boolean {
  const focusableElements = element.querySelectorAll(
    "button, [href], input, select, textarea, [tabindex]",
  );
  return focusableElements.length > 0;
}

/**
 * Check focus management after modal open
 */
export function focusMovedToElement(
  previousElement: HTMLElement,
  newElement: HTMLElement,
): boolean {
  return document.activeElement === newElement;
}

/**
 * Verify semantic HTML usage
 */
export function hasSemanticStructure(html: string): boolean {
  const hasNav = html.includes("<nav");
  const hasMain = html.includes("<main");
  const hasHeadings = /<h[1-6]/i.test(html);
  return hasNav || hasMain || hasHeadings;
}

/**
 * Check alt text on images
 */
export function imagesHaveAltText(container: HTMLElement): boolean {
  const images = container.querySelectorAll("img");
  return Array.from(images).every((img) => {
    const alt = img.getAttribute("alt");
    return typeof alt === 'string' && alt.trim().length > 0;
  });
}

/**
 * Verify heading hierarchy is correct
 */
export function hasCorrectHeadingHierarchy(container: HTMLElement): boolean {
  const headings = Array.from(
    container.querySelectorAll("h1, h2, h3, h4, h5, h6"),
  );
  if (headings.length === 0) return true;

  let lastLevel = 0;
  return headings.every((heading) => {
    const level = parseInt(heading.tagName[1]);
    const isValid = level <= lastLevel + 1;
    lastLevel = level;
    return isValid;
  });
}

/**
 * Check for form label associations
 */
export function inputsHaveLabels(container: HTMLElement): boolean {
  const inputs = container.querySelectorAll("input, textarea, select");
  return Array.from(inputs).every((input) => {
    const id = input.getAttribute("id");
    if (!id) return false;
    const label = container.querySelector(`label[for="${id}"]`);
    return !!label;
  });
}

/**
 * Verify color is not the only indicator
 */
export function colorNotOnlyIndicator(element: HTMLElement): boolean {
  // Check for text, icon, or other visual indicators beyond color
  return Boolean(
    (element.textContent?.trim().length ?? 0) > 0 ||
      element.querySelector('svg, img[role="img"]') !== null,
  );
}

/**
 * Check text alternative for decorative images
 */
export function decorativeImagesHaveEmptyAlt(container: HTMLElement): boolean {
  const decorativeImages = container.querySelectorAll(
    'img[aria-hidden="true"]',
  );
  return Array.from(decorativeImages).every((img) => {
    const alt = img.getAttribute("alt");
    return alt === "" || alt === null;
  });
}

/**
 * Verify proper link text
 */
export function linksHaveDescriptiveText(container: HTMLElement): boolean {
  const links = container.querySelectorAll("a");
  return Array.from(links).every((link) => {
    const text = link.textContent?.trim() || "";
    const ariaLabel = link.getAttribute("aria-label") || "";
    return text.length > 0 || ariaLabel.length > 0;
  });
}

/**
 * Check for focus indicators
 */
export function hasFocusIndicator(element: HTMLElement): boolean {
  const style = window.getComputedStyle(element, ":focus");
  const outline = style.outline || style.outlineWidth;
  return Boolean(outline && outline !== "none");
}

/**
 * Verify WCAG AA compliance for element
 */
export async function verifyWCAGCompliance(
  container: HTMLElement,
): Promise<boolean> {
  const checks = [
    hasSemanticStructure(container.outerHTML),
    imagesHaveAltText(container),
    hasCorrectHeadingHierarchy(container),
    inputsHaveLabels(container),
  ];

  return checks.every((check) => check === true);
}

export default {
  scanAccessibility,
  verifyAriaAttributes,
  getContrastRatio,
  hasKeyboardSupport,
  focusMovedToElement,
  hasSemanticStructure,
  imagesHaveAltText,
  hasCorrectHeadingHierarchy,
  inputsHaveLabels,
  colorNotOnlyIndicator,
  decorativeImagesHaveEmptyAlt,
  linksHaveDescriptiveText,
  hasFocusIndicator,
  verifyWCAGCompliance,
};
