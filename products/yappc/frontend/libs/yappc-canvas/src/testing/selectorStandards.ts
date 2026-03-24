/**
 * @module selectorStandards
 * @description Test selector standardization system with data-testid conventions,
 * ESLint enforcement, selector registry, and validation utilities
 *
 * Core Responsibilities:
 * - Enforce data-testid conventions across UI components
 * - Generate standardized test selectors for palette, nodes, edges, drop zones
 * - Validate selector compliance via ESLint rule
 * - Registry management for selector documentation
 * - Build-time enforcement to prevent missing selectors
 *
 * Key Features:
 * - Naming conventions (kebab-case with component prefixes)
 * - Category-based selector generation (canvas-, palette-, node-, edge-, control-)
 * - ESLint rule for detecting missing data-testid attributes
 * - Selector registry for documentation and validation
 * - Component type detection and classification
 *
 * Example Usage:
 * ```typescript
 * const manager = new SelectorStandardsManager();
 *
 * // Generate standardized selectors
 * const selector = manager.generateSelector('node', 'user-profile');
 * // Returns: 'node-user-profile'
 *
 * // Validate selector format
 * const valid = manager.validateSelector('canvas-toolbar');
 * // Returns: { valid: true }
 *
 * // Register component selector
 * manager.registerSelector({
 *   component: 'NodePalette',
 *   selector: 'palette-nodes',
 *   category: 'palette',
 *   description: 'Node palette container'
 * });
 *
 * // ESLint rule check
 * const violations = manager.checkForMissingSelectors(jsxCode);
 * // Returns array of components without data-testid
 * ```
 *
 * Architecture:
 * - SelectorStandardsManager: Central management class
 * - ESLintRuleEngine: AST-based JSX analysis
 * - SelectorRegistry: Documentation and lookup
 * - ValidationEngine: Format and convention checking
 */

/**
 * Selector category classification
 */
export type SelectorCategory =
  | 'canvas' // Canvas-level elements (toolbar, sidebar, viewport)
  | 'palette' // Palette items and categories
  | 'node' // Node components and controls
  | 'edge' // Edge components and handles
  | 'control' // UI controls (buttons, inputs, dropdowns)
  | 'dropzone' // Drop zones for drag-and-drop
  | 'dialog' // Modals and dialogs
  | 'menu' // Context menus and dropdowns
  | 'custom'; // Custom component types

/**
 * Severity level for selector violations
 */
export type ViolationSeverity = 'error' | 'warning' | 'info';

/**
 * Naming convention rule
 */
export interface NamingConvention {
  /**
   * Rule pattern (regex)
   */
  pattern: RegExp;
  /**
   * Human-readable description
   */
  description: string;
  /**
   * Example valid selector
   */
  example: string;
}

/**
 * Selector registration entry
 */
export interface SelectorEntry {
  /**
   * Component name
   */
  component: string;
  /**
   * Generated or assigned selector
   */
  selector: string;
  /**
   * Selector category
   */
  category: SelectorCategory;
  /**
   * Optional description
   */
  description?: string;
  /**
   * File path where used
   */
  filePath?: string;
  /**
   * Registration timestamp
   */
  registeredAt?: Date;
}

/**
 * ESLint violation report
 */
export interface SelectorViolation {
  /**
   * Component or element name
   */
  component: string;
  /**
   * File path
   */
  filePath: string;
  /**
   * Line number
   */
  line: number;
  /**
   * Column number
   */
  column: number;
  /**
   * Violation message
   */
  message: string;
  /**
   * Severity level
   */
  severity: ViolationSeverity;
  /**
   * Suggested fix
   */
  suggestedFix?: string;
}

/**
 * Validation result
 */
export interface ValidationResult {
  /**
   * Whether selector is valid
   */
  valid: boolean;
  /**
   * Error messages if invalid
   */
  errors?: string[];
  /**
   * Warnings (non-blocking)
   */
  warnings?: string[];
  /**
   * Suggested corrections
   */
  suggestions?: string[];
}

/**
 * ESLint rule configuration
 */
export interface ESLintRuleConfig {
  /**
   * Required components (array of patterns)
   */
  requiredComponents: string[];
  /**
   * Ignored components (don't require selectors)
   */
  ignoredComponents: string[];
  /**
   * Severity level for violations
   */
  severity: ViolationSeverity;
  /**
   * Enable auto-fix suggestions
   */
  autoFix: boolean;
}

/**
 * Selector standards configuration
 */
export interface SelectorStandardsConfig {
  /**
   * Naming conventions per category
   */
  namingConventions: Map<SelectorCategory, NamingConvention>;
  /**
   * ESLint rule configuration
   */
  eslintRule: ESLintRuleConfig;
  /**
   * Enable strict mode (fail on warnings)
   */
  strictMode: boolean;
  /**
   * Max selector length
   */
  maxSelectorLength: number;
}

/**
 * Selector statistics
 */
export interface SelectorStatistics {
  /**
   * Total registered selectors
   */
  totalSelectors: number;
  /**
   * Selectors by category
   */
  byCategory: Map<SelectorCategory, number>;
  /**
   * Components with violations
   */
  violationCount: number;
  /**
   * Coverage percentage (registered vs required)
   */
  coveragePercentage: number;
}

/**
 * Default naming conventions
 */
const DEFAULT_NAMING_CONVENTIONS = new Map<SelectorCategory, NamingConvention>([
  [
    'canvas',
    {
      pattern: /^canvas-[a-z][a-z0-9]*(-[a-z0-9]+)*$/,
      description: 'Canvas elements: canvas-{element-name}',
      example: 'canvas-toolbar',
    },
  ],
  [
    'palette',
    {
      pattern: /^palette-[a-z][a-z0-9]*(-[a-z0-9]+)*$/,
      description: 'Palette items: palette-{item-name}',
      example: 'palette-nodes',
    },
  ],
  [
    'node',
    {
      pattern: /^node-[a-z][a-z0-9]*(-[a-z0-9]+)*$/,
      description: 'Node components: node-{node-type}',
      example: 'node-user-profile',
    },
  ],
  [
    'edge',
    {
      pattern: /^edge-[a-z][a-z0-9]*(-[a-z0-9]+)*$/,
      description: 'Edge components: edge-{edge-type}',
      example: 'edge-connection',
    },
  ],
  [
    'control',
    {
      pattern: /^control-[a-z][a-z0-9]*(-[a-z0-9]+)*$/,
      description: 'UI controls: control-{control-name}',
      example: 'control-save-button',
    },
  ],
  [
    'dropzone',
    {
      pattern: /^dropzone-[a-z][a-z0-9]*(-[a-z0-9]+)*$/,
      description: 'Drop zones: dropzone-{zone-name}',
      example: 'dropzone-canvas',
    },
  ],
  [
    'dialog',
    {
      pattern: /^dialog-[a-z][a-z0-9]*(-[a-z0-9]+)*$/,
      description: 'Dialogs: dialog-{dialog-name}',
      example: 'dialog-confirm',
    },
  ],
  [
    'menu',
    {
      pattern: /^menu-[a-z][a-z0-9]*(-[a-z0-9]+)*$/,
      description: 'Menus: menu-{menu-name}',
      example: 'menu-context',
    },
  ],
  [
    'custom',
    {
      pattern: /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/,
      description: 'Custom: {kebab-case-name}',
      example: 'custom-widget',
    },
  ],
]);

/**
 * Default ESLint rule configuration
 */
const DEFAULT_ESLINT_CONFIG: ESLintRuleConfig = {
  requiredComponents: [
    'Canvas*',
    'Palette*',
    'Node*',
    'Edge*',
    '*Button',
    '*Dialog',
    '*Menu',
  ],
  ignoredComponents: ['Fragment', 'React.Fragment', 'div', 'span'],
  severity: 'error',
  autoFix: true,
};

/**
 * Selector Standards Manager
 * Centralized management of test selector conventions and enforcement
 */
export class SelectorStandardsManager {
  private config: SelectorStandardsConfig;
  private registry: Map<string, SelectorEntry>;
  private violations: SelectorViolation[];

  /**
   *
   */
  constructor(partialConfig?: Partial<SelectorStandardsConfig>) {
    this.config = {
      namingConventions: DEFAULT_NAMING_CONVENTIONS,
      eslintRule: DEFAULT_ESLINT_CONFIG,
      strictMode: false,
      maxSelectorLength: 64,
      ...partialConfig,
    };

    this.registry = new Map();
    this.violations = [];
  }

  /**
   * Generate standardized selector for a component
   */
  generateSelector(
    category: SelectorCategory,
    componentName: string
  ): string {
    // Convert component name to kebab-case
    const kebabName = this.toKebabCase(componentName);

    // Apply category prefix
    if (category === 'custom') {
      return kebabName;
    }

    return `${category}-${kebabName}`;
  }

  /**
   * Convert string to kebab-case
   */
  private toKebabCase(str: string): string {
    return str
      .replace(/([a-z])([A-Z])/g, '$1-$2') // lowercase to uppercase
      .replace(/([a-z])([0-9])/g, '$1-$2') // lowercase to number
      .replace(/([0-9])([A-Z])/g, '$1-$2') // number to uppercase
      .replace(/[\s_]+/g, '-')
      .toLowerCase();
  }

  /**
   * Validate selector format against conventions
   */
  validateSelector(selector: string): ValidationResult {
    const result: ValidationResult = {
      valid: true,
      errors: [],
      warnings: [],
      suggestions: [],
    };

    // Check length
    if (selector.length > this.config.maxSelectorLength) {
      result.valid = false;
      result.errors!.push(
        `Selector exceeds max length of ${this.config.maxSelectorLength}`
      );
    }

    // Check for valid characters
    if (!/^[a-z0-9-]+$/.test(selector)) {
      result.valid = false;
      result.errors!.push(
        'Selector must contain only lowercase letters, numbers, and hyphens'
      );
    }

    // Check against category conventions
    // For proper convention checking, we need to match specific patterns
    // (not just the permissive 'custom' pattern)
    let matchesCategory = false;
    let matchesCustomOnly = false;

    for (const [category, convention] of this.config.namingConventions) {
      if (convention.pattern.test(selector)) {
        if (category === 'custom') {
          matchesCustomOnly = true;
        } else {
          matchesCategory = true;
          break;
        }
      }
    }

    // If only matches custom pattern but not a specific category
    if (!matchesCategory && matchesCustomOnly) {
      if (this.config.strictMode) {
        result.valid = false;
        result.errors!.push('Selector does not match any naming convention');
      } else {
        result.warnings!.push(
          'Selector does not match standard conventions'
        );
      }

      // Provide suggestions
      result.suggestions!.push(
        'Example formats:',
        ...Array.from(this.config.namingConventions.values())
          .filter((c) => c.example !== 'custom-widget') // Skip custom in suggestions
          .map((c) => `  ${c.example} (${c.description})`)
      );
    } else if (!matchesCategory && !matchesCustomOnly) {
      // Doesn't match any pattern at all
      result.valid = false;
      result.errors!.push(
        'Selector format is invalid'
      );
    }

    return result;
  }

  /**
   * Register a selector in the documentation registry
   */
  registerSelector(entry: SelectorEntry): void {
    const key = `${entry.component}:${entry.selector}`;

    this.registry.set(key, {
      ...entry,
      registeredAt: new Date(),
    });
  }

  /**
   * Get selector for a component
   */
  getSelector(component: string): SelectorEntry | undefined {
    // Find by component name
    for (const [, entry] of this.registry) {
      if (entry.component === component) {
        return entry;
      }
    }
    return undefined;
  }

  /**
   * Get all registered selectors
   */
  getAllSelectors(): SelectorEntry[] {
    return Array.from(this.registry.values());
  }

  /**
   * Get selectors by category
   */
  getSelectorsByCategory(category: SelectorCategory): SelectorEntry[] {
    return Array.from(this.registry.values()).filter(
      (entry) => entry.category === category
    );
  }

  /**
   * Check JSX code for missing data-testid attributes
   * Simplified AST analysis (in production, use actual ESLint parser)
   */
  checkForMissingSelectors(
    jsxCode: string,
    filePath: string
  ): SelectorViolation[] {
    const violations: SelectorViolation[] = [];
    const lines = jsxCode.split('\n');

    // Simple pattern matching (real implementation would use ESLint AST)
    const componentPattern = /<([A-Z][a-zA-Z0-9]*)/g;

    lines.forEach((line, index) => {
      let match;
      while ((match = componentPattern.exec(line)) !== null) {
        const componentName = match[1];

        // Check if component requires selector
        if (this.requiresSelector(componentName)) {
          // Check if line contains data-testid
          if (!line.includes('data-testid')) {
            // Convert component name directly to kebab-case without category prefix
            // (since component name often already contains the category, e.g., "CanvasToolbar")
            const kebabName = this.toKebabCase(componentName);
            const suggestedSelector = kebabName;

            violations.push({
              component: componentName,
              filePath,
              line: index + 1,
              column: match.index + 1,
              message: `Component "${componentName}" is missing data-testid attribute`,
              severity: this.config.eslintRule.severity,
              suggestedFix: this.config.eslintRule.autoFix
                ? `data-testid="${suggestedSelector}"`
                : undefined,
            });
          }
        }
      }
    });

    // Store violations
    this.violations.push(...violations);

    return violations;
  }

  /**
   * Check if component requires selector based on rules
   */
  private requiresSelector(componentName: string): boolean {
    // Check if explicitly ignored
    for (const pattern of this.config.eslintRule.ignoredComponents) {
      if (this.matchesPattern(componentName, pattern)) {
        return false;
      }
    }

    // Check if required
    for (const pattern of this.config.eslintRule.requiredComponents) {
      if (this.matchesPattern(componentName, pattern)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Match component name against pattern (supports wildcards)
   */
  private matchesPattern(name: string, pattern: string): boolean {
    if (pattern === name) return true;

    // Convert wildcard pattern to regex
    const regexPattern = pattern
      .replace(/\*/g, '.*')
      .replace(/\?/g, '.');

    return new RegExp(`^${regexPattern}$`).test(name);
  }

  /**
   * Detect category from component name
   */
  private detectCategory(componentName: string): SelectorCategory {
    if (componentName.startsWith('Canvas')) return 'canvas';
    if (componentName.startsWith('Palette')) return 'palette';
    if (componentName.startsWith('Node')) return 'node';
    if (componentName.startsWith('Edge')) return 'edge';
    if (componentName.endsWith('Button')) return 'control';
    if (componentName.endsWith('Dialog')) return 'dialog';
    if (componentName.endsWith('Menu')) return 'menu';
    if (componentName.includes('DropZone')) return 'dropzone';

    return 'custom';
  }

  /**
   * Get all violations
   */
  getViolations(filter?: {
    severity?: ViolationSeverity;
    filePath?: string;
    component?: string;
  }): SelectorViolation[] {
    let filtered = [...this.violations];

    if (filter) {
      if (filter.severity) {
        filtered = filtered.filter((v) => v.severity === filter.severity);
      }
      if (filter.filePath) {
        filtered = filtered.filter((v) => v.filePath === filter.filePath);
      }
      if (filter.component) {
        filtered = filtered.filter((v) => v.component === filter.component);
      }
    }

    return filtered;
  }

  /**
   * Clear violations
   */
  clearViolations(filePath?: string): void {
    if (filePath) {
      this.violations = this.violations.filter(
        (v) => v.filePath !== filePath
      );
    } else {
      this.violations = [];
    }
  }

  /**
   * Get naming convention documentation
   */
  getNamingConventions(): Map<SelectorCategory, NamingConvention> {
    return new Map(this.config.namingConventions);
  }

  /**
   * Update naming convention for a category
   */
  updateNamingConvention(
    category: SelectorCategory,
    convention: NamingConvention
  ): void {
    this.config.namingConventions.set(category, convention);
  }

  /**
   * Get ESLint rule configuration
   */
  getESLintConfig(): ESLintRuleConfig {
    return { ...this.config.eslintRule };
  }

  /**
   * Update ESLint rule configuration
   */
  updateESLintConfig(config: Partial<ESLintRuleConfig>): void {
    this.config.eslintRule = {
      ...this.config.eslintRule,
      ...config,
    };
  }

  /**
   * Get selector statistics
   */
  getStatistics(): SelectorStatistics {
    const byCategory = new Map<SelectorCategory, number>();

    for (const entry of this.registry.values()) {
      const count = byCategory.get(entry.category) || 0;
      byCategory.set(entry.category, count + 1);
    }

    return {
      totalSelectors: this.registry.size,
      byCategory,
      violationCount: this.violations.length,
      coveragePercentage:
        this.registry.size > 0
          ? ((this.registry.size /
              (this.registry.size + this.violations.length)) *
              100)
          : 0,
    };
  }

  /**
   * Export documentation in Markdown format
   */
  exportDocumentation(): string {
    const lines: string[] = [
      '# Test Selector Standards',
      '',
      '## Naming Conventions',
      '',
    ];

    for (const [category, convention] of this.config.namingConventions) {
      lines.push(`### ${category}`);
      lines.push(`- **Pattern**: \`${convention.pattern.source}\``);
      lines.push(`- **Description**: ${convention.description}`);
      lines.push(`- **Example**: \`${convention.example}\``);
      lines.push('');
    }

    lines.push('## Registered Selectors', '');

    const byCategory = new Map<SelectorCategory, SelectorEntry[]>();
    for (const entry of this.registry.values()) {
      const list = byCategory.get(entry.category) || [];
      list.push(entry);
      byCategory.set(entry.category, list);
    }

    for (const [category, entries] of byCategory) {
      lines.push(`### ${category}`);
      lines.push('');
      lines.push('| Component | Selector | Description |');
      lines.push('|-----------|----------|-------------|');

      for (const entry of entries) {
        lines.push(
          `| ${entry.component} | \`${entry.selector}\` | ${entry.description || '-'} |`
        );
      }
      lines.push('');
    }

    const stats = this.getStatistics();
    lines.push('## Statistics', '');
    lines.push(`- Total Selectors: ${stats.totalSelectors}`);
    lines.push(`- Coverage: ${stats.coveragePercentage.toFixed(2)}%`);
    lines.push(`- Violations: ${stats.violationCount}`);

    return lines.join('\n');
  }

  /**
   * Reset registry and violations
   */
  reset(): void {
    this.registry.clear();
    this.violations = [];
  }

  /**
   * Get current configuration
   */
  getConfig(): SelectorStandardsConfig {
    return {
      ...this.config,
      namingConventions: new Map(this.config.namingConventions),
    };
  }
}
