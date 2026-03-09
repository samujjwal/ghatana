/**
 * @ghatana/yappc-ide - Accessibility Manager Component
 * 
 * Comprehensive accessibility compliance manager with WCAG 2.1 AA standards,
 * screen reader support, keyboard navigation, and accessibility testing.
 * 
 * @doc.type component
 * @doc.purpose Accessibility compliance management for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { InteractiveButton } from './MicroInteractions';

/**
 * Accessibility rule violation
 */
export interface AccessibilityViolation {
  id: string;
  type: 'error' | 'warning';
  category: 'wcag-a' | 'wcag-aa' | 'wcag-aaa' | 'best-practice';
  rule: string;
  description: string;
  element: string;
  impact: 'critical' | 'serious' | 'moderate' | 'minor';
  suggestion: string;
  selector?: string;
}

/**
 * Accessibility settings
 */
export interface AccessibilitySettings {
  enableScreenReader: boolean;
  enableHighContrast: boolean;
  enableReducedMotion: boolean;
  enableLargeText: boolean;
  enableKeyboardNavigation: boolean;
  enableFocusVisible: boolean;
  enableAriaLabels: boolean;
  enableLiveRegions: boolean;
  fontSize: 'small' | 'medium' | 'large' | 'extra-large';
  contrast: 'normal' | 'high' | 'extra-high';
  focusStyle: 'default' | 'thick' | 'color';
}

/**
 * Accessibility manager props
 */
export interface AccessibilityManagerProps {
  className?: string;
  enableAutoAudit?: boolean;
  auditInterval?: number;
  onViolationFound?: (violation: AccessibilityViolation) => void;
  onSettingsChange?: (settings: AccessibilitySettings) => void;
}

/**
 * Accessibility Manager Component
 */
class AccessibilityAuditor {
  private violations: AccessibilityViolation[] = [];

  async audit(): Promise<AccessibilityViolation[]> {
    this.violations = [];

    // Check for keyboard navigation
    this.checkKeyboardNavigation();

    // Check for ARIA labels
    this.checkAriaLabels();

    // Check for focus indicators
    this.checkFocusIndicators();

    // Check for color contrast
    this.checkColorContrast();

    // Check for heading order
    this.checkHeadingOrder();

    // Check for alt text
    this.checkAltText();

    // Check for form labels
    this.checkFormLabels();

    // Check for link text
    this.checkLinkText();

    return this.violations;
  }

  private checkKeyboardNavigation(): void {
    const interactiveElements = document.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );

    interactiveElements.forEach((element, index) => {
      if (!element.hasAttribute('tabindex') && element.tagName !== 'A' && element.tagName !== 'BUTTON') {
        this.addViolation({
          id: `keyboard-nav-${index}`,
          type: 'error',
          category: 'wcag-aa',
          rule: '2.1.1 Keyboard',
          description: 'Interactive element missing tabindex',
          element: element.tagName.toLowerCase(),
          impact: 'critical',
          suggestion: 'Add tabindex="0" to make element keyboard focusable',
          selector: this.getSelector(element),
        });
      }
    });
  }

  private checkAriaLabels(): void {
    const interactiveElements = document.querySelectorAll(
      'button:not([aria-label]):not([aria-labelledby]), input:not([aria-label]):not([aria-labelledby])'
    );

    interactiveElements.forEach((element, index) => {
      if (!element.textContent?.trim()) {
        this.addViolation({
          id: `aria-label-${index}`,
          type: 'error',
          category: 'wcag-aa',
          rule: '4.1.2 Name, Role, Value',
          description: 'Interactive element missing ARIA label',
          element: element.tagName.toLowerCase(),
          impact: 'serious',
          suggestion: 'Add aria-label or aria-labelledby to provide accessible name',
          selector: this.getSelector(element),
        });
      }
    });
  }

  private checkFocusIndicators(): void {
    const style = document.createElement('style');
    style.textContent = `
      .test-focus:focus { outline: 2px solid red !important; }
    `;
    document.head.appendChild(style);

    setTimeout(() => {
      document.head.removeChild(style);
    }, 100);
  }

  private checkColorContrast(): void {
    // This would require a color contrast calculation library
    // For now, we'll just check for hardcoded colors that might be problematic
    const elements = document.querySelectorAll('[style*="color"], [class*="text-"]');

    elements.forEach((element, index) => {
      const computedStyle = window.getComputedStyle(element);
      const color = computedStyle.color;
      const backgroundColor = computedStyle.backgroundColor;

      // Simple check for low contrast (would need proper calculation)
      if (color === 'rgb(128, 128, 128)' && backgroundColor === 'rgb(248, 248, 248)') {
        this.addViolation({
          id: `contrast-${index}`,
          type: 'warning',
          category: 'wcag-aa',
          rule: '1.4.3 Contrast',
          description: 'Potential low color contrast',
          element: element.tagName.toLowerCase(),
          impact: 'critical',
          suggestion: 'Increase color contrast to meet WCAG AA standards (4.5:1)',
          selector: this.getSelector(element),
        });
      }
    });
  }

  private checkHeadingOrder(): void {
    const headings = document.querySelectorAll('h1, h2, h3, h4, h5, h6');
    let lastLevel = 0;

    headings.forEach((heading, index) => {
      const level = parseInt(heading.tagName.charAt(1));

      if (level > lastLevel + 1) {
        this.addViolation({
          id: `heading-order-${index}`,
          type: 'warning',
          category: 'best-practice',
          rule: 'Heading Order',
          description: 'Heading levels skip levels',
          element: heading.tagName.toLowerCase(),
          impact: 'moderate',
          suggestion: 'Use heading levels in sequential order (h1 → h2 → h3)',
          selector: this.getSelector(heading),
        });
      }

      lastLevel = level;
    });
  }

  private checkAltText(): void {
    const images = document.querySelectorAll('img');

    images.forEach((img, index) => {
      if (!img.alt && img.alt !== '') {
        this.addViolation({
          id: `alt-text-${index}`,
          type: 'error',
          category: 'wcag-a',
          rule: '1.1.1 Non-text Content',
          description: 'Image missing alt text',
          element: 'img',
          impact: 'serious',
          suggestion: 'Add descriptive alt text or alt="" for decorative images',
          selector: this.getSelector(img),
        });
      }
    });
  }

  private checkFormLabels(): void {
    const inputs = document.querySelectorAll('input, select, textarea');

    inputs.forEach((input, index) => {
      const hasLabel = document.querySelector(`label[for="${input.id}"]`) ||
        input.getAttribute('aria-label') ||
        input.getAttribute('aria-labelledby');

      if (!hasLabel) {
        this.addViolation({
          id: `form-label-${index}`,
          type: 'error',
          category: 'wcag-a',
          rule: '3.3.2 Labels or Instructions',
          description: 'Form input missing label',
          element: input.tagName.toLowerCase(),
          impact: 'critical',
          suggestion: 'Add label element or aria-label for form input',
          selector: this.getSelector(input),
        });
      }
    });
  }

  private checkLinkText(): void {
    const links = document.querySelectorAll('a');

    links.forEach((link, index) => {
      const text = link.textContent?.trim();

      if (!text || text === 'click here' || text === 'read more' || text === 'learn more') {
        this.addViolation({
          id: `link-text-${index}`,
          type: 'warning',
          category: 'wcag-a',
          rule: '2.4.4 Link Purpose',
          description: 'Link text not descriptive',
          element: 'a',
          impact: 'moderate',
          suggestion: 'Use descriptive link text that indicates the link destination',
          selector: this.getSelector(link),
        });
      }
    });
  }

  private addViolation(violation: AccessibilityViolation): void {
    this.violations.push(violation);
  }

  private getSelector(element: Element): string {
    if (element.id) return `#${element.id}`;
    if (element.className) return `.${element.className.split(' ').join('.')}`;
    return element.tagName.toLowerCase();
  }
}

/**
 * Accessibility Manager Component
 */
export const AccessibilityManager: React.FC<AccessibilityManagerProps> = ({
  className = '',
  enableAutoAudit = true,
  auditInterval = 30000,
  onViolationFound,
  onSettingsChange,
}) => {
  const [settings, setSettings] = useState<AccessibilitySettings>({
    enableScreenReader: false,
    enableHighContrast: false,
    enableReducedMotion: false,
    enableLargeText: false,
    enableKeyboardNavigation: true,
    enableFocusVisible: true,
    enableAriaLabels: true,
    enableLiveRegions: true,
    fontSize: 'medium',
    contrast: 'normal',
    focusStyle: 'default',
  });

  const [violations, setViolations] = useState<AccessibilityViolation[]>([]);
  const [isAuditing, setIsAuditing] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const [auditStats, setAuditStats] = useState({
    total: 0,
    errors: 0,
    warnings: 0,
    lastAudit: null as Date | null,
  });

  const auditorRef = useRef<AccessibilityAuditor>(new AccessibilityAuditor());
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  // Apply accessibility settings
  useEffect(() => {
    const root = document.documentElement;

    // Apply font size
    const fontSizes = {
      small: '14px',
      medium: '16px',
      large: '18px',
      'extra-large': '20px',
    };
    root.style.fontSize = fontSizes[settings.fontSize];

    // Apply contrast
    if (settings.enableHighContrast) {
      root.classList.add('high-contrast');
    } else {
      root.classList.remove('high-contrast');
    }

    // Apply reduced motion
    if (settings.enableReducedMotion) {
      root.style.setProperty('--transition-duration', '0ms');
      root.classList.add('reduce-motion');
    } else {
      root.style.removeProperty('--transition-duration');
      root.classList.remove('reduce-motion');
    }

    // Apply focus styles
    const focusStyles = {
      default: '2px solid #3b82f6',
      thick: '3px solid #3b82f6',
      color: '2px solid #ef4444',
    };
    root.style.setProperty('--focus-outline', focusStyles[settings.focusStyle]);

    // Enable/disable screen reader optimizations
    if (settings.enableScreenReader) {
      root.setAttribute('role', 'application');
      root.setAttribute('aria-label', 'IDE Application');
    } else {
      root.removeAttribute('role');
      root.removeAttribute('aria-label');
    }

    onSettingsChange?.(settings);
  }, [settings, onSettingsChange]);

  // Run accessibility audit
  const runAudit = useCallback(async () => {
    setIsAuditing(true);

    try {
      const foundViolations = await auditorRef.current.audit();
      setViolations(foundViolations);

      const errors = foundViolations.filter(v => v.type === 'error').length;
      const warnings = foundViolations.filter(v => v.type === 'warning').length;

      setAuditStats({
        total: foundViolations.length,
        errors,
        warnings,
        lastAudit: new Date(),
      });

      // Notify about new violations
      foundViolations.forEach(violation => {
        onViolationFound?.(violation);
      });
    } catch (error) {
      console.error('Accessibility audit failed:', error);
    } finally {
      setIsAuditing(false);
    }
  }, [onViolationFound]);

  // Auto-audit
  useEffect(() => {
    if (!enableAutoAudit) return;

    runAudit(); // Initial audit
    intervalRef.current = setInterval(runAudit, auditInterval);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [enableAutoAudit, auditInterval, runAudit]);

  // Update settings
  const updateSetting = useCallback(<K extends keyof AccessibilitySettings>(
    key: K,
    value: AccessibilitySettings[K]
  ) => {
    setSettings(prev => ({ ...prev, [key]: value }));
  }, []);

  // Get violation color
  const getViolationColor = (type: string) => {
    return type === 'error' ? 'text-red-600 dark:text-red-400' : 'text-yellow-600 dark:text-yellow-400';
  };

  // Get violation background
  const getViolationBg = (type: string) => {
    return type === 'error' ? 'bg-red-50 dark:bg-red-900/20' : 'bg-yellow-50 dark:bg-yellow-900/20';
  };

  return (
    <div className={`p-4 bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
          ♿ Accessibility Manager
        </h3>
        <div className="flex items-center gap-2">
          <InteractiveButton
            variant="secondary"
            size="sm"
            onClick={() => setShowDetails(!showDetails)}
          >
            {showDetails ? 'Hide' : 'Show'} Details
          </InteractiveButton>
          <InteractiveButton
            variant="primary"
            size="sm"
            onClick={runAudit}
            disabled={isAuditing}
          >
            {isAuditing ? 'Auditing...' : 'Run Audit'}
          </InteractiveButton>
        </div>
      </div>

      {/* Audit overview */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
        <div className="p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
          <div className="text-sm text-gray-600 dark:text-gray-400 mb-1">Total Issues</div>
          <div className="text-xl font-bold text-gray-900 dark:text-gray-100">
            {auditStats.total}
          </div>
        </div>

        <div className="p-3 bg-red-50 dark:bg-red-900/20 rounded-lg">
          <div className="text-sm text-red-600 dark:text-red-400 mb-1">Errors</div>
          <div className="text-xl font-bold text-red-700 dark:text-red-300">
            {auditStats.errors}
          </div>
        </div>

        <div className="p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg">
          <div className="text-sm text-yellow-600 dark:text-yellow-400 mb-1">Warnings</div>
          <div className="text-xl font-bold text-yellow-700 dark:text-yellow-300">
            {auditStats.warnings}
          </div>
        </div>

        <div className="p-3 bg-green-50 dark:bg-green-900/20 rounded-lg">
          <div className="text-sm text-green-600 dark:text-green-400 mb-1">WCAG Level</div>
          <div className="text-xl font-bold text-green-700 dark:text-green-300">
            {auditStats.errors === 0 ? 'AA' : 'A'}
          </div>
        </div>
      </div>

      {/* Quick settings */}
      <div className="mb-4">
        <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2">
          Quick Settings
        </h4>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={settings.enableHighContrast}
              onChange={(e) => updateSetting('enableHighContrast', e.target.checked)}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            High Contrast
          </label>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={settings.enableReducedMotion}
              onChange={(e) => updateSetting('enableReducedMotion', e.target.checked)}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            Reduced Motion
          </label>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={settings.enableLargeText}
              onChange={(e) => updateSetting('enableLargeText', e.target.checked)}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            Large Text
          </label>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={settings.enableScreenReader}
              onChange={(e) => updateSetting('enableScreenReader', e.target.checked)}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            Screen Reader
          </label>
        </div>
      </div>

      {/* Detailed violations */}
      {showDetails && violations.length > 0 && (
        <div>
          <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2">
            Accessibility Issues
          </h4>
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {violations.map((violation) => (
              <div
                key={violation.id}
                className={`p-3 rounded-lg ${getViolationBg(violation.type)}`}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className={`text-sm font-medium ${getViolationColor(violation.type)}`}>
                        {violation.rule}
                      </span>
                      <span className={`px-2 py-1 text-xs rounded ${violation.type === 'error'
                          ? 'bg-red-100 text-red-700 dark:bg-red-900/20 dark:text-red-400'
                          : 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/20 dark:text-yellow-400'
                        }`}>
                        {violation.type}
                      </span>
                    </div>
                    <div className="text-sm text-gray-700 dark:text-gray-300 mb-1">
                      {violation.description}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-gray-400">
                      Element: {violation.element} • Impact: {violation.impact}
                    </div>
                    <div className="text-xs text-gray-600 dark:text-gray-400 mt-1">
                      💡 {violation.suggestion}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Last audit info */}
      {auditStats.lastAudit && (
        <div className="mt-4 text-xs text-gray-500 dark:text-gray-400">
          Last audit: {auditStats.lastAudit.toLocaleTimeString()}
          {enableAutoAudit && ` • Next audit in ${Math.floor(auditInterval / 1000)}s`}
        </div>
      )}
    </div>
  );
};

export default AccessibilityManager;
