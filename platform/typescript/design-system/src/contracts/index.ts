/**
 * @fileoverview Builder-safe component contract metadata for @ghatana/design-system.
 *
 * Re-exports validated ComponentContract types and the subset of design-system
 * components that have registered contracts, so the UI builder can bind
 * directly to validated design-system models without importing the full
 * component implementation bundle.
 *
 * @doc.type subpath
 * @doc.purpose Builder-safe component contract surface for @ghatana/design-system.
 * @doc.layer platform
 *
 * @deprecated-warning Do NOT import component implementations from here.
 *   This subpath exports *contracts and metadata only*, not React components.
 */

export type {
  ComponentContract,
  ComponentProp,
  ComponentSlot,
  ComponentEvent,
  ComponentStyle,
  PropType,
} from '@ghatana/ds-schema';

export {
  ComponentContractSchema,
  ComponentPropSchema,
  ComponentSlotSchema,
  ComponentEventSchema,
  validateComponentContract,
  computeContractHash,
} from '@ghatana/ds-schema';

// ============================================================================
// Builder-safe component registry slice
// ============================================================================

import type { ComponentContract } from '@ghatana/ds-schema';
import { computeContractHash } from '@ghatana/ds-schema';

/**
 * The minimal subset of design-system components that have validated contracts
 * and are safe to expose to the UI builder.
 */
export interface DesignSystemContractManifest {
  readonly version: string;
  readonly contracts: readonly ComponentContract[];
  readonly categories: readonly string[];
}

// ============================================================================
// Component Contracts
// ============================================================================

const BUTTON_CONTRACT: ComponentContract = {
  name: 'Button',
  version: '1.0.0',
  description: 'Trigger an action or navigate.',
  metadata: {
    category: 'input',
    tags: ['action', 'cta', 'interactive'],
    status: 'stable',
    platforms: ['web'],
    a11y: { role: 'button', ariaSupported: true, keyboardNavigation: true, screenReader: 'supported' },
  },
  props: [
    { name: 'children', type: 'node', description: 'Button label or content.', required: true,
      builderMetadata: { control: 'slot', category: 'content', order: 0, bindable: false } },
    { name: 'variant', type: 'enum', typeDetails: ['contained', 'outlined', 'text', 'destructive'],
      description: 'Visual style variant.', defaultValue: 'contained',
      builderMetadata: { control: 'select', category: 'style', order: 1, bindable: false } },
    { name: 'size', type: 'enum', typeDetails: ['sm', 'md', 'lg'],
      description: 'Button size.', defaultValue: 'md',
      builderMetadata: { control: 'select', category: 'style', order: 2, bindable: false } },
    { name: 'disabled', type: 'boolean', description: 'Disables the button.', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'state', order: 3, bindable: true } },
    { name: 'fullWidth', type: 'boolean', description: 'Stretches to fill its container.', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'layout', order: 4, bindable: false } },
    { name: 'loading', type: 'boolean', description: 'Shows a loading spinner.', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'state', order: 5, bindable: true } },
  ],
  slots: [
    { name: 'startIcon', description: 'Icon before the label.',
      builderMetadata: { displayName: 'Start Icon', required: false } },
    { name: 'endIcon', description: 'Icon after the label.',
      builderMetadata: { displayName: 'End Icon', required: false } },
  ],
  events: [
    { name: 'onClick', description: 'Fired when the button is clicked.',
      builderMetadata: { actionable: true, category: 'interaction' } },
  ],
  styles: {
    tokenCategories: ['color', 'spacing', 'typography', 'border'],
  },
  builder: {
    icon: 'square',
    defaultProps: { variant: 'contained', size: 'md', children: 'Button' },
    canvas: { resizable: true, draggable: true, selectable: true, container: false },
    codegen: { importPath: '@ghatana/design-system', componentName: 'Button', namedExport: true },
  },
  examples: [
    { name: 'Primary', props: { variant: 'contained', children: 'Submit' } },
    { name: 'Outlined', props: { variant: 'outlined', children: 'Cancel' } },
  ],
};

const BADGE_CONTRACT: ComponentContract = {
  name: 'Badge',
  version: '1.0.0',
  description: 'Status indicator or label chip.',
  metadata: {
    category: 'display',
    tags: ['status', 'label', 'indicator'],
    status: 'stable',
    platforms: ['web'],
    a11y: { ariaSupported: true, screenReader: 'supported' },
  },
  props: [
    { name: 'label', type: 'string', description: 'Badge text.', required: true,
      builderMetadata: { control: 'text', category: 'content', order: 0, bindable: true } },
    { name: 'tone', type: 'enum', typeDetails: ['neutral', 'positive', 'warning', 'critical', 'info', 'ai'],
      description: 'Colour semantic.', defaultValue: 'neutral',
      builderMetadata: { control: 'select', category: 'style', order: 1, bindable: false } },
    { name: 'size', type: 'enum', typeDetails: ['sm', 'md'],
      description: 'Badge size.', defaultValue: 'md',
      builderMetadata: { control: 'select', category: 'style', order: 2, bindable: false } },
  ],
  slots: [],
  events: [],
  styles: { tokenCategories: ['color', 'spacing', 'typography', 'border'] },
  builder: {
    icon: 'tag',
    defaultProps: { label: 'New', tone: 'neutral' },
    canvas: { resizable: false, draggable: true, selectable: true, container: false },
    codegen: { importPath: '@ghatana/design-system', componentName: 'Badge', namedExport: true },
  },
};

const TEXT_FIELD_CONTRACT: ComponentContract = {
  name: 'TextField',
  version: '1.0.0',
  description: 'Single-line text input.',
  metadata: {
    category: 'input',
    tags: ['form', 'input', 'text'],
    status: 'stable',
    platforms: ['web'],
    a11y: { role: 'textbox', ariaSupported: true, keyboardNavigation: true, screenReader: 'supported' },
  },
  props: [
    { name: 'label', type: 'string', description: 'Field label.',
      builderMetadata: { control: 'text', category: 'content', order: 0, bindable: true } },
    { name: 'value', type: 'string', description: 'Controlled value.',
      builderMetadata: { control: 'text', category: 'data', order: 1, bindable: true } },
    { name: 'placeholder', type: 'string', description: 'Placeholder text.',
      builderMetadata: { control: 'text', category: 'content', order: 2, bindable: false } },
    { name: 'helperText', type: 'string', description: 'Helper or error message.',
      builderMetadata: { control: 'text', category: 'content', order: 3, bindable: true } },
    { name: 'error', type: 'boolean', description: 'Marks the field as invalid.', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'state', order: 4, bindable: true } },
    { name: 'disabled', type: 'boolean', description: 'Disables the field.', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'state', order: 5, bindable: true } },
    { name: 'required', type: 'boolean', description: 'Marks the field as required.', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'validation', order: 6, bindable: false } },
    { name: 'size', type: 'enum', typeDetails: ['sm', 'md', 'lg'], defaultValue: 'md',
      builderMetadata: { control: 'select', category: 'style', order: 7, bindable: false } },
    { name: 'fullWidth', type: 'boolean', defaultValue: true,
      builderMetadata: { control: 'toggle', category: 'layout', order: 8, bindable: false } },
  ],
  slots: [],
  events: [
    { name: 'onChange', description: 'Fired on value change.',
      payloadType: 'React.ChangeEvent<HTMLInputElement>',
      builderMetadata: { actionable: true, category: 'interaction' } },
    { name: 'onBlur', description: 'Fired when the field loses focus.',
      builderMetadata: { actionable: true, category: 'interaction' } },
  ],
  styles: { tokenCategories: ['color', 'spacing', 'typography', 'border'] },
  builder: {
    icon: 'text-cursor-input',
    defaultProps: { label: 'Label', placeholder: 'Enter text…', fullWidth: true },
    canvas: { resizable: true, draggable: true, selectable: true, container: false },
    codegen: { importPath: '@ghatana/design-system', componentName: 'TextField', namedExport: true },
  },
};

const SPINNER_CONTRACT: ComponentContract = {
  name: 'Spinner',
  version: '1.0.0',
  description: 'Indeterminate loading indicator.',
  metadata: {
    category: 'feedback',
    tags: ['loading', 'progress', 'async'],
    status: 'stable',
    platforms: ['web'],
    a11y: { role: 'status', ariaSupported: true, screenReader: 'supported' },
  },
  props: [
    { name: 'size', type: 'enum', typeDetails: ['sm', 'md', 'lg', 'xl'], defaultValue: 'md',
      builderMetadata: { control: 'select', category: 'style', order: 0, bindable: false } },
    { name: 'label', type: 'string', description: 'Screen-reader accessible label.', defaultValue: 'Loading…',
      builderMetadata: { control: 'text', category: 'a11y', order: 1, bindable: true } },
    { name: 'color', type: 'enum', typeDetails: ['primary', 'secondary', 'inherit'], defaultValue: 'primary',
      builderMetadata: { control: 'select', category: 'style', order: 2, bindable: false } },
  ],
  slots: [],
  events: [],
  styles: { tokenCategories: ['color', 'motion'] },
  builder: {
    icon: 'loader-2',
    defaultProps: { size: 'md' },
    canvas: { resizable: false, draggable: true, selectable: true, container: false },
    codegen: { importPath: '@ghatana/design-system', componentName: 'Spinner', namedExport: true },
  },
};

const SELECT_CONTRACT: ComponentContract = {
  name: 'Select',
  version: '1.0.0',
  description: 'Dropdown selection control.',
  metadata: {
    category: 'input',
    tags: ['form', 'dropdown', 'select'],
    status: 'stable',
    platforms: ['web'],
    a11y: { role: 'listbox', ariaSupported: true, keyboardNavigation: true, screenReader: 'supported' },
  },
  props: [
    { name: 'label', type: 'string', description: 'Field label.',
      builderMetadata: { control: 'text', category: 'content', order: 0, bindable: true } },
    { name: 'value', type: 'string', description: 'Selected value.',
      builderMetadata: { control: 'text', category: 'data', order: 1, bindable: true } },
    { name: 'options', type: 'array',
      description: 'Array of { value: string; label: string } option objects.',
      required: true,
      builderMetadata: { control: 'json', category: 'data', order: 2, bindable: true } },
    { name: 'disabled', type: 'boolean', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'state', order: 3, bindable: true } },
    { name: 'error', type: 'boolean', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'state', order: 4, bindable: true } },
    { name: 'helperText', type: 'string',
      builderMetadata: { control: 'text', category: 'content', order: 5, bindable: true } },
    { name: 'fullWidth', type: 'boolean', defaultValue: true,
      builderMetadata: { control: 'toggle', category: 'layout', order: 6, bindable: false } },
  ],
  slots: [],
  events: [
    { name: 'onChange', description: 'Fired when selection changes.',
      payloadType: 'string',
      builderMetadata: { actionable: true, category: 'interaction' } },
  ],
  styles: { tokenCategories: ['color', 'spacing', 'typography', 'border'] },
  builder: {
    icon: 'chevron-down-square',
    defaultProps: { label: 'Select', options: [{ value: 'a', label: 'Option A' }, { value: 'b', label: 'Option B' }], fullWidth: true },
    canvas: { resizable: true, draggable: true, selectable: true, container: false },
    codegen: { importPath: '@ghatana/design-system', componentName: 'Select', namedExport: true },
  },
};

const CHECKBOX_CONTRACT: ComponentContract = {
  name: 'Checkbox',
  version: '1.0.0',
  description: 'Binary on/off toggle with accessible label.',
  metadata: {
    category: 'input',
    tags: ['form', 'toggle', 'boolean'],
    status: 'stable',
    platforms: ['web'],
    a11y: { role: 'checkbox', ariaSupported: true, keyboardNavigation: true, screenReader: 'supported' },
  },
  props: [
    { name: 'label', type: 'string', description: 'Visible label.', required: true,
      builderMetadata: { control: 'text', category: 'content', order: 0, bindable: true } },
    { name: 'checked', type: 'boolean', description: 'Controlled checked state.',
      builderMetadata: { control: 'toggle', category: 'data', order: 1, bindable: true } },
    { name: 'disabled', type: 'boolean', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'state', order: 2, bindable: true } },
    { name: 'indeterminate', type: 'boolean', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'state', order: 3, bindable: false } },
  ],
  slots: [],
  events: [
    { name: 'onChange', description: 'Fired when checked state changes.',
      payloadType: 'boolean',
      builderMetadata: { actionable: true, category: 'interaction' } },
  ],
  styles: { tokenCategories: ['color', 'spacing', 'border'] },
  builder: {
    icon: 'check-square',
    defaultProps: { label: 'Checkbox', checked: false },
    canvas: { resizable: false, draggable: true, selectable: true, container: false },
    codegen: { importPath: '@ghatana/design-system', componentName: 'Checkbox', namedExport: true },
  },
};

const TYPOGRAPHY_CONTRACT: ComponentContract = {
  name: 'Typography',
  version: '1.0.0',
  description: 'Semantic text rendering with token-driven scale.',
  metadata: {
    category: 'display',
    tags: ['text', 'heading', 'body', 'label'],
    status: 'stable',
    platforms: ['web'],
    a11y: { ariaSupported: true, screenReader: 'supported' },
  },
  props: [
    { name: 'children', type: 'node', description: 'Text content.', required: true,
      builderMetadata: { control: 'text', category: 'content', order: 0, bindable: true } },
    { name: 'variant', type: 'enum',
      typeDetails: ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'subtitle1', 'subtitle2', 'body1', 'body2', 'caption', 'overline', 'label'],
      defaultValue: 'body1',
      builderMetadata: { control: 'select', category: 'style', order: 1, bindable: false } },
    { name: 'color', type: 'enum',
      typeDetails: ['inherit', 'primary', 'secondary', 'text.primary', 'text.secondary', 'text.disabled', 'error'],
      defaultValue: 'inherit',
      builderMetadata: { control: 'select', category: 'style', order: 2, bindable: false } },
    { name: 'align', type: 'enum', typeDetails: ['inherit', 'left', 'center', 'right', 'justify'],
      defaultValue: 'inherit',
      builderMetadata: { control: 'select', category: 'style', order: 3, bindable: false } },
    { name: 'noWrap', type: 'boolean', defaultValue: false,
      builderMetadata: { control: 'toggle', category: 'style', order: 4, bindable: false } },
  ],
  slots: [],
  events: [],
  styles: { tokenCategories: ['color', 'typography'] },
  builder: {
    icon: 'type',
    defaultProps: { variant: 'body1', children: 'Text' },
    canvas: { resizable: true, draggable: true, selectable: true, container: false },
    codegen: { importPath: '@ghatana/design-system', componentName: 'Typography', namedExport: true },
  },
};

// ============================================================================
// Contract manifest
// ============================================================================

const CORE_CONTRACTS: ComponentContract[] = [
  BUTTON_CONTRACT,
  BADGE_CONTRACT,
  TEXT_FIELD_CONTRACT,
  SPINNER_CONTRACT,
  SELECT_CONTRACT,
  CHECKBOX_CONTRACT,
  TYPOGRAPHY_CONTRACT,
];

// Pre-compute hashes so the manifest is stable across renders.
const CONTRACTS_WITH_HASHES = CORE_CONTRACTS.map((c) => ({
  ...c,
  _hash: computeContractHash(c),
}));

export const DESIGN_SYSTEM_CONTRACT_MANIFEST: DesignSystemContractManifest = {
  version: '1.0.0',
  contracts: CONTRACTS_WITH_HASHES as unknown as ComponentContract[],
  categories: ['input', 'display', 'layout', 'feedback', 'navigation', 'overlay'],
} as const;

/**
 * Look up a contract by component name.
 */
export function findContract(name: string): ComponentContract | undefined {
  return DESIGN_SYSTEM_CONTRACT_MANIFEST.contracts.find((c) => c.name === name);
}

/**
 * Check whether a named component has a registered contract.
 */
export function hasContract(name: string): boolean {
  return DESIGN_SYSTEM_CONTRACT_MANIFEST.contracts.some((c) => c.name === name);
}
