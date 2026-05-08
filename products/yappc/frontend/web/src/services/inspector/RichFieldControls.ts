/**
 * Rich Field Controls
 *
 * Provides rich field control definitions for the property inspector.
 * Supports text, number, boolean, enum, tokens, component/slot/action/data binding,
 * object/array editors, responsive, and state variants.
 *
 * @doc.type service
 * @doc.purpose Rich field controls
 * @doc.layer product
 */

export type FieldType = 
  | 'text' 
  | 'number' 
  | 'boolean' 
  | 'enum' 
  | 'tokens' 
  | 'component' 
  | 'slot' 
  | 'action' 
  | 'data-binding' 
  | 'object' 
  | 'array' 
  | 'color' 
  | 'date' 
  | 'file';

export interface FieldControl {
  /** Field name */
  name: string;
  /** Field type */
  type: FieldType;
  /** Label */
  label?: string;
  /** Description */
  description?: string;
  /** Default value */
  defaultValue?: unknown;
  /** Required flag */
  required?: boolean;
  /** Disabled flag */
  disabled?: boolean;
  /** Readonly flag */
  readonly?: boolean;
  /** Placeholder */
  placeholder?: string;
  /** Validation rules */
  validation?: ValidationRule[];
  /** Options for enum/select fields */
  options?: FieldOption[];
  /** Minimum value (for number) */
  min?: number;
  /** Maximum value (for number) */
  max?: number;
  /** Step (for number) */
  step?: number;
  /** Multiple values allowed */
  multiple?: boolean;
  /** Responsive configuration */
  responsive?: ResponsiveConfig;
  /** State variants */
  stateVariants?: StateVariant[];
  /** Custom renderer */
  customRenderer?: string;
}

export interface ValidationRule {
  /** Rule type */
  type: 'required' | 'min' | 'max' | 'pattern' | 'custom';
  /** Rule value */
  value?: unknown;
  /** Error message */
  message?: string;
  /** Custom validator function name */
  validator?: string;
}

export interface FieldOption {
  /** Option value */
  value: string | number;
  /** Option label */
  label: string;
  /** Option icon */
  icon?: string;
  /** Disabled flag */
  disabled?: boolean;
}

export interface ResponsiveConfig {
  /** Breakpoint-specific configurations */
  breakpoints: Record<string, Partial<FieldControl>>;
  /** Default breakpoint */
  defaultBreakpoint?: string;
}

export interface StateVariant {
  /** State name */
  state: string;
  /** State-specific configuration */
  config: Partial<FieldControl>;
}

export interface FieldControlGroup {
  /** Group ID */
  id: string;
  /** Group name */
  name: string;
  /** Group description */
  description?: string;
  /** Field controls */
  controls: FieldControl[];
  /** Collapsed flag */
  collapsed?: boolean;
}

function createFieldControl(type: FieldType, config: Partial<FieldControl> = {}): FieldControl {
  return {
    ...config,
    name: config.name ?? '',
    type,
  };
}

/**
 * Create text field control
 */
export function createTextField(config: Partial<FieldControl> = {}): FieldControl {
  return createFieldControl('text', config);
}

/**
 * Create number field control
 */
export function createNumberField(config: Partial<FieldControl> = {}): FieldControl {
  return {
    step: 1,
    ...createFieldControl('number', config),
  };
}

/**
 * Create boolean field control
 */
export function createBooleanField(config: Partial<FieldControl> = {}): FieldControl {
  return {
    defaultValue: false,
    ...createFieldControl('boolean', config),
  };
}

/**
 * Create enum/select field control
 */
export function createEnumField(options: FieldOption[], config: Partial<FieldControl> = {}): FieldControl {
  return {
    options,
    ...createFieldControl('enum', config),
  };
}

/**
 * Create tokens field control
 */
export function createTokensField(config: Partial<FieldControl> = {}): FieldControl {
  return {
    multiple: true,
    placeholder: 'Add token...',
    ...createFieldControl('tokens', config),
  };
}

/**
 * Create component selector field control
 */
export function createComponentField(config: Partial<FieldControl> = {}): FieldControl {
  return {
    placeholder: 'Select component...',
    ...createFieldControl('component', config),
  };
}

/**
 * Create slot selector field control
 */
export function createSlotField(config: Partial<FieldControl> = {}): FieldControl {
  return {
    placeholder: 'Select slot...',
    ...createFieldControl('slot', config),
  };
}

/**
 * Create action handler field control
 */
export function createActionField(config: Partial<FieldControl> = {}): FieldControl {
  return {
    placeholder: 'Configure action...',
    ...createFieldControl('action', config),
  };
}

/**
 * Create data binding field control
 */
export function createDataBindingField(config: Partial<FieldControl> = {}): FieldControl {
  return {
    placeholder: 'Select data source...',
    ...createFieldControl('data-binding', config),
  };
}

/**
 * Create object editor field control
 */
export function createObjectField(config: Partial<FieldControl> = {}): FieldControl {
  return createFieldControl('object', config);
}

/**
 * Create array editor field control
 */
export function createArrayField(config: Partial<FieldControl> = {}): FieldControl {
  return {
    multiple: true,
    ...createFieldControl('array', config),
  };
}

/**
 * Create color picker field control
 */
export function createColorField(config: Partial<FieldControl> = {}): FieldControl {
  return createFieldControl('color', config);
}

/**
 * Create date picker field control
 */
export function createDateField(config: Partial<FieldControl> = {}): FieldControl {
  return createFieldControl('date', config);
}

/**
 * Create file upload field control
 */
export function createFileField(config: Partial<FieldControl> = {}): FieldControl {
  return createFieldControl('file', config);
}

/**
 * Add responsive configuration to field control
 */
export function withResponsive(
  field: FieldControl,
  breakpoints: ResponsiveConfig['breakpoints']
): FieldControl {
  return {
    ...field,
    responsive: {
      breakpoints,
      defaultBreakpoint: 'md',
    },
  };
}

/**
 * Add state variants to field control
 */
export function withStateVariants(
  field: FieldControl,
  variants: StateVariant[]
): FieldControl {
  return {
    ...field,
    stateVariants: variants,
  };
}

/**
 * Add validation rules to field control
 */
export function withValidation(
  field: FieldControl,
  rules: ValidationRule[]
): FieldControl {
  return {
    ...field,
    validation: [...(field.validation || []), ...rules],
  };
}

/**
 * Validate field value
 */
export function validateFieldValue(
  field: FieldControl,
  value: unknown
): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!field.validation) {
    return { valid: true, errors: [] };
  }

  for (const rule of field.validation) {
    switch (rule.type) {
      case 'required':
        if (value === undefined || value === null || value === '') {
          errors.push(rule.message || `${field.name} is required`);
        }
        break;
      case 'min':
        if (typeof value === 'number' && value < (rule.value as number)) {
          errors.push(rule.message || `${field.name} must be at least ${rule.value}`);
        }
        break;
      case 'max':
        if (typeof value === 'number' && value > (rule.value as number)) {
          errors.push(rule.message || `${field.name} must be at most ${rule.value}`);
        }
        break;
      case 'pattern':
        if (typeof value === 'string' && !new RegExp(rule.value as string).test(value)) {
          errors.push(rule.message || `${field.name} format is invalid`);
        }
        break;
      case 'custom':
        // Custom validators would be called by name
        // This is a placeholder for the actual implementation
        break;
    }
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Get field control for prop metadata
 */
export function getFieldControlForProp(prop: {
  name: string;
  type: string;
  description?: string;
  defaultValue?: unknown;
  required?: boolean;
  enumValues?: string[];
}): FieldControl {
  const baseConfig = {
    name: prop.name,
    label: formatLabel(prop.name),
    description: prop.description,
    defaultValue: prop.defaultValue,
    required: prop.required,
  };

  switch (prop.type) {
    case 'string':
      if (prop.enumValues) {
        return createEnumField(
          prop.enumValues.map(v => ({ value: v, label: v })),
          baseConfig
        );
      }
      return createTextField(baseConfig);
    case 'number':
      return createNumberField(baseConfig);
    case 'boolean':
      return createBooleanField(baseConfig);
    case 'component':
      return createComponentField(baseConfig);
    case 'slot':
      return createSlotField(baseConfig);
    case 'action':
      return createActionField(baseConfig);
    case 'data-binding':
      return createDataBindingField(baseConfig);
    case 'object':
      return createObjectField(baseConfig);
    case 'array':
      return createArrayField(baseConfig);
    default:
      return createTextField(baseConfig);
  }
}

/**
 * Format label from prop name
 */
function formatLabel(name: string): string {
  return name
    .split(/(?=[A-Z])/)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

/**
 * Create field control group
 */
export function createFieldControlGroup(
  id: string,
  name: string,
  controls: FieldControl[],
  description?: string
): FieldControlGroup {
  return {
    id,
    name,
    description,
    controls,
    collapsed: false,
  };
}

/**
 * Get responsive value for field
 */
export function getResponsiveValue(
  field: FieldControl,
  breakpoint: string,
  currentValue: unknown
): unknown {
  if (!field.responsive) {
    return currentValue;
  }

  const breakpointConfig = field.responsive.breakpoints[breakpoint];
  if (breakpointConfig && breakpointConfig.defaultValue !== undefined) {
    return breakpointConfig.defaultValue;
  }

  return currentValue;
}

/**
 * Get state variant value for field
 */
export function getStateVariantValue(
  field: FieldControl,
  state: string,
  currentValue: unknown
): unknown {
  if (!field.stateVariants) {
    return currentValue;
  }

  const variant = field.stateVariants.find(v => v.state === state);
  if (variant && variant.config.defaultValue !== undefined) {
    return variant.config.defaultValue;
  }

  return currentValue;
}

export default {
  createTextField,
  createNumberField,
  createBooleanField,
  createEnumField,
  createTokensField,
  createComponentField,
  createSlotField,
  createActionField,
  createDataBindingField,
  createObjectField,
  createArrayField,
  createColorField,
  createDateField,
  createFileField,
  withResponsive,
  withStateVariants,
  withValidation,
  validateFieldValue,
  getFieldControlForProp,
  createFieldControlGroup,
  getResponsiveValue,
  getStateVariantValue,
};
