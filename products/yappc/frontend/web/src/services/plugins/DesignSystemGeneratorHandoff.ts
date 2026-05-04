/**
 * Design System Generator Handoff
 *
 * Handles handoff of custom components to the design-system generator.
 *
 * @doc.type service
 * @doc.purpose Design-system generator handoff
 * @doc.layer product
 */

export interface ComponentMetadata {
  /** Component name */
  name: string;
  /** Component description */
  description?: string;
  /** Component category */
  category?: string;
  /** Tags */
  tags?: string[];
  /** Version */
  version?: string;
}

export interface PropDefinition {
  /** Prop name */
  name: string;
  /** Prop type */
  type: 'string' | 'number' | 'boolean' | 'object' | 'array' | 'function' | 'enum';
  /** Description */
  description?: string;
  /** Default value */
  defaultValue?: unknown;
  /** Required flag */
  required?: boolean;
  /** Enum values (if type is enum) */
  enumValues?: string[];
}

export interface DesignSystemSpec {
  /** Component metadata */
  metadata: ComponentMetadata;
  /** Prop definitions */
  props: PropDefinition[];
  /** Slot definitions */
  slots?: SlotDefinition[];
  /** Style variants */
  variants?: StyleVariant[];
  /** Accessibility requirements */
  accessibility?: AccessibilityRequirements;
}

export interface SlotDefinition {
  /** Slot name */
  name: string;
  /** Slot description */
  description?: string;
  /** Default content */
  defaultContent?: string;
  /** Required flag */
  required?: boolean;
}

export interface StyleVariant {
  /** Variant name */
  name: string;
  /** Variant description */
  description?: string;
  /** CSS properties */
  styles: Record<string, string>;
}

export interface AccessibilityRequirements {
  /** ARIA label required */
  ariaLabelRequired?: boolean;
  /** Keyboard navigable */
  keyboardNavigable?: boolean;
  /** Screen reader compatible */
  screenReaderCompatible?: boolean;
  /** Minimum contrast ratio */
  minContrastRatio?: number;
  /** Focus indicator required */
  focusIndicatorRequired?: boolean;
}

export interface HandoffResult {
  /** Success flag */
  success: boolean;
  /** Generated spec */
  spec?: DesignSystemSpec;
  /** Warnings */
  warnings: string[];
  /** Errors */
  errors: string[];
  /** Generated code */
  generatedCode?: string;
}

/**
 * Generate design-system spec from custom component
 */
export function generateDesignSystemSpec(
  componentCode: string,
  componentName: string,
  options: { description?: string; category?: string; tags?: string[] } = {}
): HandoffResult {
  const warnings: string[] = [];
  const errors: string[] = [];

  try {
    const metadata: ComponentMetadata = {
      name: componentName,
      description: options.description || extractDescription(componentCode),
      category: options.category || 'custom',
      tags: options.tags || ['custom'],
      version: '1.0.0',
    };

    const props = extractProps(componentCode);
    const slots = extractSlots(componentCode);
    const variants = extractVariants(componentCode);
    const accessibility = inferAccessibilityRequirements(componentCode, props);

    const spec: DesignSystemSpec = {
      metadata,
      props,
      slots,
      variants,
      accessibility,
    };

    const generatedCode = generateComponentFromSpec(spec);

    return {
      success: true,
      spec,
      warnings,
      errors,
      generatedCode,
    };
  } catch (error) {
    errors.push(error instanceof Error ? error.message : String(error));
    return {
      success: false,
      warnings,
      errors,
    };
  }
}

/**
 * Extract description from component code
 */
function extractDescription(code: string): string {
  // Look for JSDoc comments
  const jsdocMatch = code.match(/\/\*\*[\s\S]*?\*\//);
  if (jsdocMatch) {
    const descMatch = jsdocMatch[0].match(/@description\s+([^\n*]+)/);
    if (descMatch) {
      return descMatch[1].trim();
    }
  }
  return 'Custom component';
}

/**
 * Extract props from component code
 */
function extractProps(code: string): PropDefinition[] {
  const props: PropDefinition[] = [];

  // Extract interface or type definition
  const interfaceMatch = code.match(/interface\s+(\w+Props)\s*{([\s\S]*?)^}/m);
  const typeMatch = code.match(/type\s+(\w+Props)\s*=\s*{([\s\S]*?)^}/m);

  const match = interfaceMatch || typeMatch;
  if (match) {
    const propsBody = match[2];
    const propLines = propsBody.split('\n').filter(line => line.trim() && !line.trim().startsWith('//'));

    for (const line of propLines) {
      const propMatch = line.match(/(\w+)(\?)?:\s*(\w+)/);
      if (propMatch) {
        const [, name, optional, type] = propMatch;
        props.push({
          name: name.trim(),
          type: mapTypeToPropType(type.trim()),
          required: !optional,
        });
      }
    }
  }

  return props;
}

/**
 * Map TypeScript type to prop type
 */
function mapTypeToPropType(tsType: string): PropDefinition['type'] {
  const typeMap: Record<string, PropDefinition['type']> = {
    'string': 'string',
    'number': 'number',
    'boolean': 'boolean',
    'object': 'object',
    'any': 'object',
    'unknown': 'object',
    'function': 'function',
    'ReactNode': 'object',
    'ReactElement': 'object',
  };

  if (tsType.includes('[]')) return 'array';
  if (tsType.includes('|') && tsType.includes("'")) return 'enum';

  return typeMap[tsType] || 'object';
}

/**
 * Extract slots from component code
 */
function extractSlots(code: string): SlotDefinition[] {
  const slots: SlotDefinition[] = [];

  // Look for children prop or slot patterns
  if (code.includes('children')) {
    slots.push({
      name: 'children',
      description: 'Main content slot',
      required: false,
    });
  }

  // Look for onSlot patterns
  const slotMatches = code.matchAll(/(\w+)Slot/g);
  for (const match of slotMatches) {
    slots.push({
      name: match[1],
      description: `${match[1]} slot`,
      required: false,
    });
  }

  return slots;
}

/**
 * Extract style variants from component code
 */
function extractVariants(code: string): StyleVariant[] {
  const variants: StyleVariant[] = [];

  // Look for variant prop
  const variantMatch = code.match(/variant:\s*{([\s\S]*?)^}/m);
  if (variantMatch) {
    const variantBody = variantMatch[1];
    const variantLines = variantBody.split('\n').filter(line => line.trim() && !line.trim().startsWith('//'));

    for (const line of variantLines) {
      const variantNameMatch = line.match(/(\w+):\s*{([\s\S]*?)}/);
      if (variantNameMatch) {
        const [, name, styles] = variantNameMatch;
        const styleObj: Record<string, string> = {};
        
        // Parse simple CSS properties
        const styleMatches = styles.matchAll(/(\w+):\s*([^,}]+)/g);
        for (const styleMatch of styleMatches) {
          styleObj[styleMatch[1]] = styleMatch[2].trim();
        }

        variants.push({
          name,
          styles: styleObj,
        });
      }
    }
  }

  return variants;
}

/**
 * Infer accessibility requirements from props
 */
function inferAccessibilityRequirements(
  code: string,
  props: PropDefinition[]
): AccessibilityRequirements {
  const requirements: AccessibilityRequirements = {
    ariaLabelRequired: false,
    keyboardNavigable: false,
    screenReaderCompatible: false,
    minContrastRatio: 4.5,
    focusIndicatorRequired: false,
  };

  // Check for onClick
  if (code.includes('onClick')) {
    requirements.keyboardNavigable = true;
    requirements.focusIndicatorRequired = true;
  }

  // Check for ariaLabel prop
  if (props.some(p => p.name === 'ariaLabel')) {
    requirements.ariaLabelRequired = false; // Already has ariaLabel
  } else {
    requirements.ariaLabelRequired = true;
  }

  return requirements;
}

/**
 * Generate component code from spec
 */
function generateComponentFromSpec(spec: DesignSystemSpec): string {
  const { metadata, props, slots, variants, accessibility } = spec;

  let code = `/**
 * ${metadata.name}
 * ${metadata.description || ''}
 *
 * @doc.type component
 * @doc.purpose ${metadata.category || 'custom'} component
 * @doc.layer product
 */

import React from 'react';

interface ${metadata.name}Props {
${props.map(prop => {
  const optional = prop.required ? '' : '?';
  const typeStr = prop.type === 'enum' 
    ? prop.enumValues?.map(v => `'${v}'`).join(' | ') || 'string'
    : prop.type;
  const defaultStr = prop.defaultValue !== undefined ? ` = ${JSON.stringify(prop.defaultValue)}` : '';
  return `  ${prop.name}${optional}: ${typeStr}${defaultStr};`;
}).join('\n')}
}

export const ${metadata.name}: React.FC<${metadata.name}Props> = ({
${props.map(p => `  ${p.name},`).join('\n')}
}) => {
  return (
    <div className="${metadata.name.toLowerCase()}">
      {/* Component implementation */}
    </div>
  );
};

export default ${metadata.name};
`;

  return code;
}

/**
 * Validate design-system spec
 */
export function validateDesignSystemSpec(spec: DesignSystemSpec): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!spec.metadata.name) {
    errors.push('Component name is required');
  }

  if (!spec.props || spec.props.length === 0) {
    errors.push('At least one prop is required');
  }

  for (const prop of spec.props) {
    if (!prop.name) {
      errors.push('Prop name is required');
    }
    if (!prop.type) {
      errors.push(`Prop type is required for ${prop.name}`);
    }
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Export spec as JSON
 */
export function exportSpecAsJSON(spec: DesignSystemSpec): string {
  return JSON.stringify(spec, null, 2);
}

/**
 * Import spec from JSON
 */
export function importSpecFromJSON(json: string): DesignSystemSpec {
  return JSON.parse(json);
}

export default {
  generateDesignSystemSpec,
  validateDesignSystemSpec,
  exportSpecAsJSON,
  importSpecFromJSON,
};
