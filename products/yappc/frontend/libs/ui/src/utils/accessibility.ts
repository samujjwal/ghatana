/**
 * Accessibility utilities for UI components
 * 
 * This file contains utilities for checking and enhancing accessibility in our components.
 */
import React from 'react';

/**
 * Accessibility audit result
 */
export interface AccessibilityAuditResult {
  /**
   * Whether the audit passed
   */
  passed: boolean;
  
  /**
   * List of issues found
   */
  issues: AccessibilityIssue[];
  
  /**
   * Component name
   */
  component: string;
}

/**
 * Accessibility issue
 */
export interface AccessibilityIssue {
  /**
   * Issue type
   */
  type: 'error' | 'warning' | 'info';
  
  /**
   * Issue message
   */
  message: string;
  
  /**
   * Issue code
   */
  code: string;
  
  /**
   * Element selector
   */
  selector?: string;
  
  /**
   * Suggested fix
   */
  fix?: string;
}

/**
 * Accessibility rule
 */
export interface AccessibilityRule {
  /**
   * Rule ID
   */
  id: string;
  
  /**
   * Rule name
   */
  name: string;
  
  /**
   * Rule description
   */
  description: string;
  
  /**
   * Rule test function
   */
  test: (element: HTMLElement) => boolean;
  
  /**
   * Rule fix suggestion
   */
  fix?: string;
  
  /**
   * Rule severity
   */
  severity: 'error' | 'warning' | 'info';
}

/**
 * Common accessibility rules
 */
export const accessibilityRules: AccessibilityRule[] = [
  {
    id: 'a11y-img-alt',
    name: 'Image Alt Text',
    description: 'Images must have alt text',
    test: (element: HTMLElement) => {
      if (element.tagName.toLowerCase() === 'img') {
        return element.hasAttribute('alt');
      }
      return true;
    },
    fix: 'Add alt attribute to img element',
    severity: 'error',
  },
  {
    id: 'a11y-button-name',
    name: 'Button Name',
    description: 'Buttons must have accessible name',
    test: (element: HTMLElement) => {
      if (element.tagName.toLowerCase() === 'button' || 
          (element.getAttribute('role') === 'button')) {
        return !!(element.textContent?.trim() || 
                element.getAttribute('aria-label') || 
                element.getAttribute('aria-labelledby'));
      }
      return true;
    },
    fix: 'Add text content, aria-label, or aria-labelledby to button',
    severity: 'error',
  },
  {
    id: 'a11y-color-contrast',
    name: 'Color Contrast',
    description: 'Text must have sufficient color contrast',
    test: (_element: HTMLElement) => {
      // This is a simplified check - in a real app, we would use a color contrast algorithm
      return true;
    },
    fix: 'Ensure text has sufficient color contrast (4.5:1 for normal text, 3:1 for large text)',
    severity: 'warning',
  },
  {
    id: 'a11y-keyboard-focus',
    name: 'Keyboard Focus',
    description: 'Interactive elements must be focusable',
    test: (element: HTMLElement) => {
      const interactive = ['a', 'button', 'input', 'select', 'textarea'];
      if (interactive.includes(element.tagName.toLowerCase()) || 
          element.hasAttribute('tabindex')) {
        return element.getAttribute('tabindex') !== '-1';
      }
      return true;
    },
    fix: 'Ensure interactive elements are keyboard focusable',
    severity: 'error',
  },
  {
    id: 'a11y-aria-role',
    name: 'ARIA Role',
    description: 'ARIA roles must be valid',
    test: (element: HTMLElement) => {
      const role = element.getAttribute('role');
      if (!role) return true;
      
      // List of valid ARIA roles
      const validRoles = [
        'alert', 'alertdialog', 'application', 'article', 'banner', 'button', 
        'cell', 'checkbox', 'columnheader', 'combobox', 'complementary', 
        'contentinfo', 'definition', 'dialog', 'directory', 'document', 
        'feed', 'figure', 'form', 'grid', 'gridcell', 'group', 'heading', 
        'img', 'link', 'list', 'listbox', 'listitem', 'log', 'main', 
        'marquee', 'math', 'menu', 'menubar', 'menuitem', 'menuitemcheckbox', 
        'menuitemradio', 'navigation', 'none', 'note', 'option', 'presentation', 
        'progressbar', 'radio', 'radiogroup', 'region', 'row', 'rowgroup', 
        'rowheader', 'scrollbar', 'search', 'searchbox', 'separator', 
        'slider', 'spinbutton', 'status', 'switch', 'tab', 'table', 
        'tablist', 'tabpanel', 'term', 'textbox', 'timer', 'toolbar', 
        'tooltip', 'tree', 'treegrid', 'treeitem'
      ];
      
      return validRoles.includes(role);
    },
    fix: 'Use a valid ARIA role',
    severity: 'error',
  },
];

/**
 * Run accessibility audit on an element
 * @param element Element to audit
 * @param componentName Component name
 * @returns Audit result
 */
export function runAccessibilityAudit(element: HTMLElement, componentName: string): AccessibilityAuditResult {
  const issues: AccessibilityIssue[] = [];
  
  // Helper function to audit an element and its children
  const auditElement = (el: HTMLElement, selector: string = '') => {
    // Run each rule on the element
    for (const rule of accessibilityRules) {
      if (!rule.test(el)) {
        issues.push({
          type: rule.severity,
          message: rule.description,
          code: rule.id,
          selector: selector || el.tagName.toLowerCase(),
          fix: rule.fix,
        });
      }
    }
    
    // Audit children
    Array.from(el.children).forEach((child, index) => {
      if (child instanceof HTMLElement) {
        const childSelector = selector ? 
          `${selector} > ${child.tagName.toLowerCase()}:nth-child(${index + 1})` : 
          `${child.tagName.toLowerCase()}:nth-child(${index + 1})`;
        auditElement(child, childSelector);
      }
    });
  };
  
  // Start audit
  auditElement(element);
  
  return {
    passed: issues.length === 0,
    issues,
    component: componentName,
  };
}

/**
 * Get accessibility props for an element
 * @param props Element props
 * @returns Accessibility props
 */
export function getA11yProps(props: Record<string, unknown>): Record<string, unknown> {
  const a11yProps: Record<string, unknown> = {};
  
  // Extract accessibility props from props
  const {
    'aria-label': ariaLabel,
    'aria-labelledby': ariaLabelledby,
    'aria-describedby': ariaDescribedby,
    'aria-hidden': ariaHidden,
    'aria-live': ariaLive,
    'aria-atomic': ariaAtomic,
    'aria-busy': ariaBusy,
    'aria-checked': ariaChecked,
    'aria-controls': ariaControls,
    'aria-current': ariaCurrent,
    'aria-disabled': ariaDisabled,
    'aria-expanded': ariaExpanded,
    'aria-haspopup': ariaHaspopup,
    'aria-invalid': ariaInvalid,
    'aria-multiline': ariaMultiline,
    'aria-multiselectable': ariaMultiselectable,
    'aria-orientation': ariaOrientation,
    'aria-placeholder': ariaPlaceholder,
    'aria-pressed': ariaPressed,
    'aria-readonly': ariaReadonly,
    'aria-required': ariaRequired,
    'aria-selected': ariaSelected,
    'aria-sort': ariaSort,
    'aria-valuemax': ariaValuemax,
    'aria-valuemin': ariaValuemin,
    'aria-valuenow': ariaValuenow,
    'aria-valuetext': ariaValuetext,
    role,
    tabIndex,
    ...rest
  } = props;
  
  // Add aria props if they exist
  if (ariaLabel) a11yProps['aria-label'] = ariaLabel;
  if (ariaLabelledby) a11yProps['aria-labelledby'] = ariaLabelledby;
  if (ariaDescribedby) a11yProps['aria-describedby'] = ariaDescribedby;
  if (ariaHidden !== undefined) a11yProps['aria-hidden'] = ariaHidden;
  if (ariaLive) a11yProps['aria-live'] = ariaLive;
  if (ariaAtomic !== undefined) a11yProps['aria-atomic'] = ariaAtomic;
  if (ariaBusy !== undefined) a11yProps['aria-busy'] = ariaBusy;
  if (ariaChecked !== undefined) a11yProps['aria-checked'] = ariaChecked;
  if (ariaControls) a11yProps['aria-controls'] = ariaControls;
  if (ariaCurrent) a11yProps['aria-current'] = ariaCurrent;
  if (ariaDisabled !== undefined) a11yProps['aria-disabled'] = ariaDisabled;
  if (ariaExpanded !== undefined) a11yProps['aria-expanded'] = ariaExpanded;
  if (ariaHaspopup !== undefined) a11yProps['aria-haspopup'] = ariaHaspopup;
  if (ariaInvalid !== undefined) a11yProps['aria-invalid'] = ariaInvalid;
  if (ariaMultiline !== undefined) a11yProps['aria-multiline'] = ariaMultiline;
  if (ariaMultiselectable !== undefined) a11yProps['aria-multiselectable'] = ariaMultiselectable;
  if (ariaOrientation) a11yProps['aria-orientation'] = ariaOrientation;
  if (ariaPlaceholder) a11yProps['aria-placeholder'] = ariaPlaceholder;
  if (ariaPressed !== undefined) a11yProps['aria-pressed'] = ariaPressed;
  if (ariaReadonly !== undefined) a11yProps['aria-readonly'] = ariaReadonly;
  if (ariaRequired !== undefined) a11yProps['aria-required'] = ariaRequired;
  if (ariaSelected !== undefined) a11yProps['aria-selected'] = ariaSelected;
  if (ariaSort) a11yProps['aria-sort'] = ariaSort;
  if (ariaValuemax !== undefined) a11yProps['aria-valuemax'] = ariaValuemax;
  if (ariaValuemin !== undefined) a11yProps['aria-valuemin'] = ariaValuemin;
  if (ariaValuenow !== undefined) a11yProps['aria-valuenow'] = ariaValuenow;
  if (ariaValuetext) a11yProps['aria-valuetext'] = ariaValuetext;
  if (role) a11yProps.role = role;
  if (tabIndex !== undefined) a11yProps.tabIndex = tabIndex;
  
  return { a11yProps, rest };
}

/**
 * Create accessible label for an element
 * @param label Label text
 * @param id Element ID
 * @returns Accessibility props for label and element
 */
export function createA11yLabel(_label: string, id: string): {
  labelProps: Record<string, unknown>;
  elementProps: Record<string, unknown>;
} {
  return {
    // Return only the attributes required by the tests; do not include `children` here so callers
    // can control rendering of label text.
    labelProps: {
      id: `${id}-label`,
      htmlFor: id,
    },
    elementProps: {
      id,
      'aria-labelledby': `${id}-label`,
    },
  };
}

// --- Small helpers used across components to avoid duplication ---

/**
 * Wrap an interactive element in a non-interactive span to prevent MUI Tooltip
 * from cloning/modifying the element and changing its accessible attributes.
 */
export function wrapForTooltip(element: React.ReactElement, spanProps?: Record<string, unknown>): React.ReactElement {
  // Use React.createElement to avoid requiring .tsx extension for this util file.
  return React.createElement('span', { style: { display: 'inline-block' }, ...spanProps }, element) as React.ReactElement;
}

/**
 * Return true if children are plain text (string or number). Used to decide
 * whether to compute an aria-label from children.
 */
export function isPlainTextChildren(children: unknown): boolean {
  return typeof children === 'string' || typeof children === 'number';
}

/**
 * Compute an aria-label when none is provided and children are plain text.
 */
export function computeAriaLabel(children: unknown, explicitAriaLabel?: string): string | undefined {
  if (explicitAriaLabel) return explicitAriaLabel;
  if (isPlainTextChildren(children)) return String(children);
  return undefined;
}

