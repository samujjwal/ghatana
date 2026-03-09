/**
 * @module selectorStandardization
 * @description Test selector standardization utilities for stable, maintainable test automation.
 * Provides consistent data-testid conventions, selector generation, and validation utilities.
 * 
 * Key Features:
 * - Standardized selector naming conventions
 * - Type-safe selector generation
 * - Selector validation utilities
 * - Component-specific selector builders
 * - Documentation helpers
 * 
 * @example
 * ```typescript
 * // Generate selector for canvas node
 * const nodeSelector = generateNodeSelector('node-123');
 * // => 'canvas-node-node-123'
 * 
 * // Validate selector format
 * const isValid = validateSelector('canvas-node-123'); // true
 * 
 * // Get selector for palette item
 * const paletteSelector = generatePaletteSelector('shape', 'rectangle');
 * // => 'canvas-palette-shape-rectangle'
 * ```
 */

/**
 * Selector component types for consistent naming
 */
export type SelectorComponent =
  | 'canvas'
  | 'node'
  | 'edge'
  | 'palette'
  | 'toolbar'
  | 'panel'
  | 'modal'
  | 'dropdown'
  | 'button'
  | 'input'
  | 'viewport'
  | 'minimap'
  | 'controls';

/**
 * Selector action types for interaction elements
 */
export type SelectorAction =
  | 'add'
  | 'delete'
  | 'edit'
  | 'save'
  | 'cancel'
  | 'close'
  | 'open'
  | 'select'
  | 'drag'
  | 'drop'
  | 'zoom-in'
  | 'zoom-out'
  | 'fit-view'
  | 'reset';

/**
 * Selector state types for dynamic elements
 */
export type SelectorState =
  | 'active'
  | 'inactive'
  | 'selected'
  | 'disabled'
  | 'loading'
  | 'error'
  | 'success';

/**
 * Selector pattern configuration
 */
export interface SelectorPattern {
  prefix: string;
  component: SelectorComponent;
  id?: string;
  action?: SelectorAction;
  state?: SelectorState;
  suffix?: string;
}

/**
 * Selector validation rules
 */
export interface SelectorRules {
  /** Maximum selector length */
  maxLength: number;
  /** Allowed separator character */
  separator: string;
  /** Required prefix */
  requiredPrefix: string;
  /** Disallowed characters regex */
  disallowedChars: RegExp;
  /** Enforce lowercase */
  enforceLowercase: boolean;
}

/**
 * Default selector rules
 */
export const DEFAULT_SELECTOR_RULES: SelectorRules = {
  maxLength: 128,
  separator: '-',
  requiredPrefix: 'canvas',
  disallowedChars: /[^a-z0-9_-]/gi,
  enforceLowercase: true,
};

/**
 * Selector validation result
 */
export interface SelectorValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

/**
 * Create selector management state
 */
export interface SelectorManagerState {
  rules: SelectorRules;
  registeredSelectors: Map<string, SelectorPattern>;
  validationCache: Map<string, SelectorValidationResult>;
}

/**
 * Initialize selector manager with optional custom rules
 */
export function createSelectorManager(
  customRules?: Partial<SelectorRules>
): SelectorManagerState {
  return {
    rules: { ...DEFAULT_SELECTOR_RULES, ...customRules },
    registeredSelectors: new Map(),
    validationCache: new Map(),
  };
}

/**
 * Generate standardized selector from pattern
 */
export function generateSelector(pattern: SelectorPattern): string {
  const parts: string[] = [pattern.prefix, pattern.component];

  if (pattern.id) {
    parts.push(pattern.id);
  }

  if (pattern.action) {
    parts.push(pattern.action);
  }

  if (pattern.state) {
    parts.push(pattern.state);
  }

  if (pattern.suffix) {
    parts.push(pattern.suffix);
  }

  return parts.join('-').toLowerCase();
}

/**
 * Generate canvas node selector
 */
export function generateNodeSelector(nodeId: string, state?: SelectorState): string {
  return generateSelector({
    prefix: 'canvas',
    component: 'node',
    id: nodeId,
    state,
  });
}

/**
 * Generate canvas edge selector
 */
export function generateEdgeSelector(edgeId: string, state?: SelectorState): string {
  return generateSelector({
    prefix: 'canvas',
    component: 'edge',
    id: edgeId,
    state,
  });
}

/**
 * Generate palette item selector
 */
export function generatePaletteSelector(category: string, itemType: string): string {
  return generateSelector({
    prefix: 'canvas',
    component: 'palette',
    id: `${category}-${itemType}`,
  });
}

/**
 * Generate toolbar button selector
 */
export function generateToolbarSelector(action: SelectorAction, state?: SelectorState): string {
  return generateSelector({
    prefix: 'canvas',
    component: 'toolbar',
    action,
    state,
  });
}

/**
 * Generate panel selector
 */
export function generatePanelSelector(panelName: string, section?: string): string {
  return generateSelector({
    prefix: 'canvas',
    component: 'panel',
    id: panelName,
    suffix: section,
  });
}

/**
 * Generate drop zone selector
 */
export function generateDropZoneSelector(zoneId: string): string {
  return generateSelector({
    prefix: 'canvas',
    component: 'viewport',
    id: 'drop-zone',
    suffix: zoneId,
  });
}

/**
 * Generate minimap selector
 */
export function generateMinimapSelector(element?: string): string {
  return generateSelector({
    prefix: 'canvas',
    component: 'minimap',
    suffix: element,
  });
}

/**
 * Generate viewport controls selector
 */
export function generateControlsSelector(action: SelectorAction): string {
  return generateSelector({
    prefix: 'canvas',
    component: 'controls',
    action,
  });
}

/**
 * Validate selector against rules
 */
export function validateSelector(
  selector: string,
  rules: SelectorRules = DEFAULT_SELECTOR_RULES
): SelectorValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  // Check empty
  if (!selector || selector.trim().length === 0) {
    errors.push('Selector cannot be empty');
    return { valid: false, errors, warnings };
  }

  // Check length
  if (selector.length > rules.maxLength) {
    errors.push(`Selector exceeds maximum length of ${rules.maxLength} characters`);
  }

  // Check required prefix
  if (!selector.startsWith(rules.requiredPrefix)) {
    errors.push(`Selector must start with prefix "${rules.requiredPrefix}"`);
  }

  // Check disallowed characters
  if (rules.disallowedChars.test(selector)) {
    errors.push('Selector contains disallowed characters (only a-z, 0-9, -, _ allowed)');
  }

  // Check lowercase enforcement
  if (rules.enforceLowercase && selector !== selector.toLowerCase()) {
    warnings.push('Selector should be lowercase for consistency');
  }

  // Check separator usage
  const separatorCount = (selector.match(new RegExp(`\\${rules.separator}`, 'g')) || []).length;
  if (separatorCount < 1) {
    warnings.push(`Selector should use "${rules.separator}" separator for component parts`);
  }

  // Check double separators
  if (selector.includes(`${rules.separator}${rules.separator}`)) {
    errors.push(`Selector contains consecutive "${rules.separator}" characters`);
  }

  // Check trailing/leading separators
  if (
    selector.startsWith(rules.separator) ||
    selector.endsWith(rules.separator)
  ) {
    errors.push(`Selector should not start or end with "${rules.separator}"`);
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

/**
 * Register selector pattern for documentation and validation
 */
export function registerSelector(
  state: SelectorManagerState,
  selector: string,
  pattern: SelectorPattern
): SelectorManagerState {
  const validation = validateSelector(selector, state.rules);

  if (!validation.valid) {
    throw new Error(
      `Invalid selector "${selector}": ${validation.errors.join(', ')}`
    );
  }

  return {
    ...state,
    registeredSelectors: new Map(state.registeredSelectors).set(selector, pattern),
    validationCache: new Map(state.validationCache).set(selector, validation),
  };
}

/**
 * Get registered selector pattern
 */
export function getRegisteredSelector(
  state: SelectorManagerState,
  selector: string
): SelectorPattern | undefined {
  return state.registeredSelectors.get(selector);
}

/**
 * Get all registered selectors by component type
 */
export function getSelectorsForComponent(
  state: SelectorManagerState,
  component: SelectorComponent
): Map<string, SelectorPattern> {
  const result = new Map<string, SelectorPattern>();

  for (const [selector, pattern] of state.registeredSelectors) {
    if (pattern.component === component) {
      result.set(selector, pattern);
    }
  }

  return result;
}

/**
 * Validate all registered selectors
 */
export function validateAllSelectors(
  state: SelectorManagerState
): Map<string, SelectorValidationResult> {
  const results = new Map<string, SelectorValidationResult>();

  for (const selector of state.registeredSelectors.keys()) {
    const cached = state.validationCache.get(selector);
    if (cached) {
      results.set(selector, cached);
    } else {
      const validation = validateSelector(selector, state.rules);
      results.set(selector, validation);
    }
  }

  return results;
}

/**
 * Get selector statistics
 */
export function getSelectorStatistics(state: SelectorManagerState): {
  totalSelectors: number;
  validSelectors: number;
  invalidSelectors: number;
  selectorsByComponent: Map<SelectorComponent, number>;
  averageLength: number;
} {
  const validationResults = validateAllSelectors(state);
  let totalLength = 0;
  const selectorsByComponent = new Map<SelectorComponent, number>();

  for (const [selector, pattern] of state.registeredSelectors) {
    totalLength += selector.length;

    const count = selectorsByComponent.get(pattern.component) || 0;
    selectorsByComponent.set(pattern.component, count + 1);
  }

  const totalSelectors = state.registeredSelectors.size;
  const validSelectors = Array.from(validationResults.values()).filter((r) => r.valid).length;

  return {
    totalSelectors,
    validSelectors,
    invalidSelectors: totalSelectors - validSelectors,
    selectorsByComponent,
    averageLength: totalSelectors > 0 ? Math.round(totalLength / totalSelectors) : 0,
  };
}

/**
 * Export selector documentation in markdown format
 */
export function exportSelectorDocumentation(state: SelectorManagerState): string {
  const lines: string[] = [];

  lines.push('# Canvas Test Selector Reference');
  lines.push('');
  lines.push('## Selector Conventions');
  lines.push('');
  lines.push(`- **Prefix**: \`${state.rules.requiredPrefix}\``);
  lines.push(`- **Separator**: \`${state.rules.separator}\``);
  lines.push(`- **Max Length**: ${state.rules.maxLength} characters`);
  lines.push(`- **Case**: ${state.rules.enforceLowercase ? 'lowercase' : 'any'}`);
  lines.push('');
  lines.push('## Format');
  lines.push('');
  lines.push('```');
  lines.push(`${state.rules.requiredPrefix}${state.rules.separator}<component>${state.rules.separator}<id>${state.rules.separator}[action]${state.rules.separator}[state]`);
  lines.push('```');
  lines.push('');

  const componentGroups = new Map<SelectorComponent, string[]>();

  for (const [selector, pattern] of state.registeredSelectors) {
    const selectors = componentGroups.get(pattern.component) || [];
    selectors.push(selector);
    componentGroups.set(pattern.component, selectors);
  }

  for (const [component, selectors] of componentGroups) {
    lines.push(`## ${component.charAt(0).toUpperCase() + component.slice(1)} Selectors`);
    lines.push('');

    for (const selector of selectors.sort()) {
      const pattern = state.registeredSelectors.get(selector);
      if (pattern) {
        lines.push(`- \`${selector}\``);
        if (pattern.action) {
          lines.push(`  - Action: ${pattern.action}`);
        }
        if (pattern.state) {
          lines.push(`  - State: ${pattern.state}`);
        }
      }
    }

    lines.push('');
  }

  const stats = getSelectorStatistics(state);
  lines.push('## Statistics');
  lines.push('');
  lines.push(`- Total Selectors: ${stats.totalSelectors}`);
  lines.push(`- Valid: ${stats.validSelectors}`);
  lines.push(`- Invalid: ${stats.invalidSelectors}`);
  lines.push(`- Average Length: ${stats.averageLength} characters`);
  lines.push('');

  return lines.join('\n');
}

/**
 * Parse selector into components
 */
export function parseSelector(
  selector: string,
  separator: string = '-'
): {
  prefix?: string;
  component?: string;
  id?: string;
  parts: string[];
} {
  const parts = selector.split(separator);

  if (parts.length < 2) {
    return { parts };
  }

  return {
    prefix: parts[0],
    component: parts[1],
    id: parts.length > 2 ? parts.slice(2).join(separator) : undefined,
    parts,
  };
}

/**
 * Check if selector follows convention
 */
export function followsConvention(
  selector: string,
  rules: SelectorRules = DEFAULT_SELECTOR_RULES
): boolean {
  const validation = validateSelector(selector, rules);
  return validation.valid && validation.warnings.length === 0;
}

/**
 * Suggest fixes for invalid selector
 */
export function suggestSelectorFix(
  selector: string,
  rules: SelectorRules = DEFAULT_SELECTOR_RULES
): string {
  let fixed = selector.trim();

  // Remove leading/trailing separators first
  fixed = fixed.replace(new RegExp(`^\\${rules.separator}+|\\${rules.separator}+$`, 'g'), '');

  // Convert to lowercase if enforced
  if (rules.enforceLowercase) {
    fixed = fixed.toLowerCase();
  }

  // Remove disallowed characters
  fixed = fixed.replace(rules.disallowedChars, '');

  // Remove consecutive separators
  fixed = fixed.replace(new RegExp(`\\${rules.separator}{2,}`, 'g'), rules.separator);

  // Add prefix if missing
  if (!fixed.startsWith(rules.requiredPrefix)) {
    fixed = `${rules.requiredPrefix}${rules.separator}${fixed}`;
  }

  // Remove leading/trailing separators again (after prefix addition)
  fixed = fixed.replace(new RegExp(`^\\${rules.separator}+|\\${rules.separator}+$`, 'g'), '');

  // Truncate if too long
  if (fixed.length > rules.maxLength) {
    fixed = fixed.substring(0, rules.maxLength);
    // Ensure doesn't end with separator after truncation
    if (fixed.endsWith(rules.separator)) {
      fixed = fixed.substring(0, fixed.length - 1);
    }
  }

  return fixed;
}
