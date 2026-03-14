/**
 * Accessibility Testing Utilities
 *
 * Comprehensive testing helpers for WCAG 2.1 AA compliance verification.
 * Designed to work with Jest and React Testing Library.
 *
 * These utilities help automate accessibility testing in CI/CD pipelines
 * and during development to catch issues early.
 *
 * @doc.type utility
 * @doc.purpose Accessibility Testing
 * @doc.layer infrastructure
 * @doc.pattern Testing Utility
 *
 * @example
 * ```tsx
 * import { a11yTestHelpers, checkTouchTargets, checkLabelAssociations } from '@ghatana/ui';
 *
 * describe('MyComponent', () => {
 *   it('meets accessibility requirements', () => {
 *     const { container } = render(<MyComponent />);
 *     expect(a11yTestHelpers.checkLandmarks(container)).toHaveNoViolations();
 *   });
 * });
 * ```
 */

/**
 * WCAG 2.1 AA Requirements
 */
export const WCAG_REQUIREMENTS = {
    /** Minimum touch target size in pixels (WCAG 2.5.5) */
    MIN_TOUCH_TARGET: 44,
    /** Recommended touch target size */
    RECOMMENDED_TOUCH_TARGET: 48,
    /** Minimum contrast ratio for normal text (WCAG 1.4.3) */
    MIN_CONTRAST_NORMAL: 4.5,
    /** Minimum contrast ratio for large text (WCAG 1.4.3) */
    MIN_CONTRAST_LARGE: 3,
    /** Minimum contrast ratio for UI components (WCAG 1.4.11) */
    MIN_CONTRAST_UI: 3,
    /** Large text threshold in pixels */
    LARGE_TEXT_SIZE: 18,
    /** Large text threshold for bold text */
    LARGE_TEXT_SIZE_BOLD: 14,
} as const;

/**
 * Focusable element selector
 */
export const FOCUSABLE_SELECTOR_A11Y = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
    'audio[controls]',
    'video[controls]',
    '[contenteditable]:not([contenteditable="false"])',
].join(', ');

/**
 * Interactive element selector
 */
export const INTERACTIVE_SELECTOR = [
    'a[href]',
    'button',
    'input',
    'select',
    'textarea',
    '[role="button"]',
    '[role="link"]',
    '[role="checkbox"]',
    '[role="radio"]',
    '[role="tab"]',
    '[role="menuitem"]',
    '[role="option"]',
    '[onclick]',
].join(', ');

export interface A11yViolation {
    rule: string;
    impact: 'critical' | 'serious' | 'moderate' | 'minor';
    message: string;
    element: HTMLElement;
    selector: string;
}

export interface A11yTestResult {
    passed: boolean;
    violations: A11yViolation[];
    warnings: A11yViolation[];
}

/**
 * Check touch target sizes for interactive elements
 */
export function checkTouchTargets(container: HTMLElement): A11yTestResult {
    const violations: A11yViolation[] = [];
    const warnings: A11yViolation[] = [];
    const interactiveElements = container.querySelectorAll<HTMLElement>(INTERACTIVE_SELECTOR);

    interactiveElements.forEach((element) => {
        const rect = element.getBoundingClientRect();
        const width = rect.width;
        const height = rect.height;

        // Skip hidden elements
        const style = window.getComputedStyle(element);
        if (style.display === 'none' || style.visibility === 'hidden') {
            return;
        }

        if (width < WCAG_REQUIREMENTS.MIN_TOUCH_TARGET || height < WCAG_REQUIREMENTS.MIN_TOUCH_TARGET) {
            violations.push({
                rule: 'touch-target-size',
                impact: 'serious',
                message: `Touch target size is ${width}x${height}px, minimum required is ${WCAG_REQUIREMENTS.MIN_TOUCH_TARGET}x${WCAG_REQUIREMENTS.MIN_TOUCH_TARGET}px`,
                element,
                selector: getSelector(element),
            });
        } else if (width < WCAG_REQUIREMENTS.RECOMMENDED_TOUCH_TARGET || height < WCAG_REQUIREMENTS.RECOMMENDED_TOUCH_TARGET) {
            warnings.push({
                rule: 'touch-target-size',
                impact: 'minor',
                message: `Touch target size is ${width}x${height}px, recommended is ${WCAG_REQUIREMENTS.RECOMMENDED_TOUCH_TARGET}x${WCAG_REQUIREMENTS.RECOMMENDED_TOUCH_TARGET}px`,
                element,
                selector: getSelector(element),
            });
        }
    });

    return {
        passed: violations.length === 0,
        violations,
        warnings,
    };
}

/**
 * Check form label associations
 */
export function checkLabelAssociations(container: HTMLElement): A11yTestResult {
    const violations: A11yViolation[] = [];
    const warnings: A11yViolation[] = [];

    // Check inputs, selects, textareas
    const formElements = container.querySelectorAll<HTMLElement>('input, select, textarea');

    formElements.forEach((element) => {
        const type = (element as HTMLInputElement).type;

        // Skip hidden inputs and submit/reset buttons
        if (type === 'hidden' || type === 'submit' || type === 'reset' || type === 'button' || type === 'image') {
            return;
        }

        const hasLabel = Boolean(
            element.getAttribute('aria-label') ||
            element.getAttribute('aria-labelledby') ||
            element.getAttribute('title') ||
            element.id && container.querySelector(`label[for="${element.id}"]`) ||
            element.closest('label')
        );

        if (!hasLabel) {
            violations.push({
                rule: 'label-association',
                impact: 'critical',
                message: 'Form element has no associated label',
                element,
                selector: getSelector(element),
            });
        }
    });

    return {
        passed: violations.length === 0,
        violations,
        warnings,
    };
}

/**
 * Check for required ARIA landmarks
 */
export function checkLandmarks(container: HTMLElement): A11yTestResult {
    const violations: A11yViolation[] = [];
    const warnings: A11yViolation[] = [];

    const hasMain = container.querySelector('main, [role="main"]');
    const hasNavigation = container.querySelector('nav, [role="navigation"]');
    const hasBanner = container.querySelector('header, [role="banner"]');

    if (!hasMain) {
        violations.push({
            rule: 'landmark-main',
            impact: 'serious',
            message: 'Page should have a main landmark',
            element: container,
            selector: 'document',
        });
    }

    if (!hasNavigation) {
        warnings.push({
            rule: 'landmark-navigation',
            impact: 'moderate',
            message: 'Page should have a navigation landmark',
            element: container,
            selector: 'document',
        });
    }

    if (!hasBanner) {
        warnings.push({
            rule: 'landmark-banner',
            impact: 'moderate',
            message: 'Page should have a banner/header landmark',
            element: container,
            selector: 'document',
        });
    }

    return {
        passed: violations.length === 0,
        violations,
        warnings,
    };
}

/**
 * Check heading hierarchy
 */
export function checkHeadingHierarchy(container: HTMLElement): A11yTestResult {
    const violations: A11yViolation[] = [];
    const warnings: A11yViolation[] = [];
    const headings = container.querySelectorAll<HTMLHeadingElement>('h1, h2, h3, h4, h5, h6');

    let previousLevel = 0;

    headings.forEach((heading) => {
        const level = parseInt(heading.tagName[1], 10);

        // Check for skipped heading levels
        if (level > previousLevel + 1 && previousLevel !== 0) {
            violations.push({
                rule: 'heading-order',
                impact: 'moderate',
                message: `Heading level ${level} follows level ${previousLevel}, skipping levels`,
                element: heading,
                selector: getSelector(heading),
            });
        }

        previousLevel = level;
    });

    // Check for multiple h1s
    const h1Count = container.querySelectorAll('h1').length;
    if (h1Count > 1) {
        warnings.push({
            rule: 'heading-multiple-h1',
            impact: 'moderate',
            message: `Page has ${h1Count} h1 elements, should typically have only one`,
            element: container,
            selector: 'h1',
        });
    }

    if (h1Count === 0) {
        violations.push({
            rule: 'heading-missing-h1',
            impact: 'serious',
            message: 'Page should have an h1 heading',
            element: container,
            selector: 'document',
        });
    }

    return {
        passed: violations.length === 0,
        violations,
        warnings,
    };
}

/**
 * Check for missing alt text on images
 */
export function checkImageAlt(container: HTMLElement): A11yTestResult {
    const violations: A11yViolation[] = [];
    const warnings: A11yViolation[] = [];
    const images = container.querySelectorAll<HTMLImageElement>('img');

    images.forEach((img) => {
        const hasAlt = img.hasAttribute('alt');
        const altText = img.getAttribute('alt');

        if (!hasAlt) {
            violations.push({
                rule: 'image-alt',
                impact: 'critical',
                message: 'Image is missing alt attribute',
                element: img,
                selector: getSelector(img),
            });
        } else if (altText === '') {
            // Empty alt is valid for decorative images, but warn
            const isDecorative = img.getAttribute('role') === 'presentation' ||
                img.getAttribute('aria-hidden') === 'true';
            if (!isDecorative) {
                warnings.push({
                    rule: 'image-alt-empty',
                    impact: 'minor',
                    message: 'Image has empty alt text. If decorative, add role="presentation"',
                    element: img,
                    selector: getSelector(img),
                });
            }
        }
    });

    return {
        passed: violations.length === 0,
        violations,
        warnings,
    };
}

/**
 * Check focus indicators
 */
export function checkFocusIndicators(container: HTMLElement): A11yTestResult {
    const violations: A11yViolation[] = [];
    const warnings: A11yViolation[] = [];
    const focusableElements = container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR_A11Y);

    focusableElements.forEach((element) => {
        // Focus the element temporarily to check styles
        const previousFocus = document.activeElement;
        element.focus();

        const style = window.getComputedStyle(element);
        const hasOutline = style.outline !== 'none' && style.outline !== '' && style.outlineWidth !== '0px';
        const hasBoxShadow = style.boxShadow !== 'none' && style.boxShadow !== '';
        const hasBorder = style.borderColor !== style.backgroundColor; // Simplified check

        // Blur the element
        if (previousFocus instanceof HTMLElement) {
            previousFocus.focus();
        } else {
            element.blur();
        }

        if (!hasOutline && !hasBoxShadow) {
            // Check if outline is explicitly removed
            if (style.outlineStyle === 'none' || style.outlineWidth === '0px') {
                warnings.push({
                    rule: 'focus-indicator',
                    impact: 'serious',
                    message: 'Element may not have a visible focus indicator',
                    element,
                    selector: getSelector(element),
                });
            }
        }
    });

    return {
        passed: violations.length === 0,
        violations,
        warnings,
    };
}

/**
 * Check for keyboard accessibility
 */
export function checkKeyboardAccessibility(container: HTMLElement): A11yTestResult {
    const violations: A11yViolation[] = [];
    const warnings: A11yViolation[] = [];

    // Check for click handlers without keyboard handlers
    const clickElements = container.querySelectorAll<HTMLElement>('[onclick]');

    clickElements.forEach((element) => {
        const hasKeyHandler = element.hasAttribute('onkeydown') ||
            element.hasAttribute('onkeyup') ||
            element.hasAttribute('onkeypress');

        const isNativeInteractive =
            element.tagName === 'BUTTON' ||
            element.tagName === 'A' ||
            element.tagName === 'INPUT' ||
            element.tagName === 'SELECT' ||
            element.tagName === 'TEXTAREA';

        if (!hasKeyHandler && !isNativeInteractive) {
            const hasTabIndex = element.hasAttribute('tabindex');
            const hasRole = element.getAttribute('role');

            if (!hasTabIndex) {
                violations.push({
                    rule: 'keyboard-accessible',
                    impact: 'critical',
                    message: 'Element has click handler but is not keyboard accessible (missing tabindex)',
                    element,
                    selector: getSelector(element),
                });
            }

            if (!hasRole) {
                warnings.push({
                    rule: 'keyboard-accessible',
                    impact: 'moderate',
                    message: 'Element with click handler should have an appropriate role',
                    element,
                    selector: getSelector(element),
                });
            }
        }
    });

    return {
        passed: violations.length === 0,
        violations,
        warnings,
    };
}

/**
 * Run all accessibility checks
 */
export function runA11yAudit(container: HTMLElement): A11yTestResult {
    const allViolations: A11yViolation[] = [];
    const allWarnings: A11yViolation[] = [];

    const checks = [
        checkTouchTargets,
        checkLabelAssociations,
        checkLandmarks,
        checkHeadingHierarchy,
        checkImageAlt,
        checkKeyboardAccessibility,
    ];

    checks.forEach((check) => {
        const result = check(container);
        allViolations.push(...result.violations);
        allWarnings.push(...result.warnings);
    });

    return {
        passed: allViolations.length === 0,
        violations: allViolations,
        warnings: allWarnings,
    };
}

/**
 * Helper to get a CSS selector for an element
 */
function getSelector(element: HTMLElement): string {
    if (element.id) {
        return `#${element.id}`;
    }

    let selector = element.tagName.toLowerCase();

    if (element.className && typeof element.className === 'string') {
        const classes = element.className.trim().split(/\s+/).slice(0, 2);
        if (classes.length > 0) {
            selector += `.${classes.join('.')}`;
        }
    }

    return selector;
}

/**
 * Jest/Vitest custom matcher for accessibility
 */
export const a11yMatchers = {
    toHaveNoViolations(result: A11yTestResult) {
        const pass = result.passed;
        const message = pass
            ? () => 'Expected accessibility violations but found none'
            : () => {
                const violationMessages = result.violations
                    .map((v) => `  [${v.impact}] ${v.rule}: ${v.message} (${v.selector})`)
                    .join('\n');
                return `Found ${result.violations.length} accessibility violation(s):\n${violationMessages}`;
            };

        return { pass, message };
    },
};

/**
 * Consolidated accessibility test helpers
 */
export const a11yTestHelpers = {
    checkTouchTargets,
    checkLabelAssociations,
    checkLandmarks,
    checkHeadingHierarchy,
    checkImageAlt,
    checkFocusIndicators,
    checkKeyboardAccessibility,
    runA11yAudit,
    WCAG_REQUIREMENTS,
    FOCUSABLE_SELECTOR_A11Y,
    INTERACTIVE_SELECTOR,
};

export default a11yTestHelpers;
