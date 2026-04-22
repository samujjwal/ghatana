/**
 * @fileoverview Starter component contracts for the Ghatana design system.
 *
 * These contracts represent the five foundational components used across
 * builder canvases and page designers. They are registered in the global
 * registry via {@link registerStarterContracts}.
 *
 * Import paths resolve to `@ghatana/design-system`, the canonical UI package.
 *
 * @doc.type module
 * @doc.purpose Provides canonical ComponentContract definitions for Button,
 *   Card, TextField, Typography, and Box starter components.
 * @doc.layer platform
 * @doc.pattern Registry
 */

import type { ComponentContract } from '@ghatana/ds-schema';
import type { RegistryStore } from '../registry/store';

// ============================================================================
// Button
// ============================================================================

export const ButtonContract: ComponentContract = {
  name: 'Button',
  version: '1.0.0',
  description: 'A clickable button element for triggering actions.',
  props: [
    {
      name: 'variant',
      type: 'string',
      required: false,
      defaultValue: 'contained',
      description: 'Visual style variant.',
    },
    {
      name: 'color',
      type: 'string',
      required: false,
      defaultValue: 'primary',
      description: 'Color palette key.',
    },
    {
      name: 'size',
      type: 'string',
      required: false,
      defaultValue: 'medium',
      description: 'Size variant.',
    },
    {
      name: 'disabled',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether the button is disabled.',
    },
    {
      name: 'fullWidth',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether to expand to full container width.',
    },
    {
      name: 'children',
      type: 'string',
      required: true,
      description: 'Button label text.',
    },
  ],
  slots: [],
  events: [
    {
      name: 'onClick',
      description: 'Fired when the button is activated via click or keyboard.',
    },
  ],
  styles: {},
  layout: {
    isContainer: false,
    defaultDisplay: 'inline-block',
    draggable: true,
    resizable: false,
    fillsParent: false,
    forbiddenAncestors: [],
    allowedChildTypes: [],
  },
  builder: {
    icon: 'button',
    palette: {
      displayName: 'Button',
      group: 'Inputs',
      searchKeywords: ['click', 'submit', 'action', 'cta'],
      featured: false,
    },
    canvas: {
      resizable: true,
      draggable: true,
      selectable: true,
      container: false,
    },
    codegen: {
      importPath: '@ghatana/design-system',
      componentName: 'Button',
      namedExport: true,
      htmlTagName: 'ghatana-button',
    },
  },
  aiPolicy: {
    allowAutonomousConfiguration: true,
    reviewRequiredProps: [],
    autoApplyConfidenceThreshold: 0.9,
    permittedActions: ['set-prop', 'remove-prop', 'reposition', 'resize'],
  },
  builderA11y: {
    requiredA11yProps: ['aria-label'],
    trapsFocusRequiresClose: false,
    motionRequiresReductionSupport: false,
    wcagLevel: 'AA',
    a11yGuidance:
      'Ensure a descriptive label is provided either as button text or via aria-label.',
  },
  telemetry: {
    emittedEvents: [
      {
        name: 'click',
        description: 'Emitted when the button is clicked',
        containsPii: false,
      },
    ],
    autoTracksInteractions: true,
    requiresConsentForTracking: false,
  },
  observability: {
    requiresTraceContext: true,
    performanceMarks: ['button-click', 'button-render'],
    reportsRenderErrors: true,
  },
  preview: {
    minimumTrustLevel: 'semi-trusted',
    requiresNetwork: false,
    requiresStorage: false,
    requiresConsent: false,
  },
  privacy: {
    requiresConsentFlow: false,
    mayRenderPii: false,
    regulatoryFrameworks: [],
  },
  configurator: {
    groups: [
      {
        id: 'appearance',
        label: 'Appearance',
        collapsed: false,
        propNames: ['variant', 'color', 'size'],
      },
      {
        id: 'state',
        label: 'State',
        collapsed: false,
        propNames: ['disabled', 'fullWidth'],
      },
    ],
    showAdvancedSection: false,
    showLivePreview: true,
    resettableProps: ['variant', 'color', 'size', 'disabled', 'fullWidth'],
  },
  responsive: {
    isResponsive: false,
    breakpoints: [],
    responsiveProps: [],
    supportsContainerQuery: false,
    responsiveScale: 'none',
  },
  metadata: {
    category: 'input',
    status: 'stable',
    platforms: ['web'],
    tags: ['button', 'action', 'interactive'],
  },
};

// ============================================================================
// Card
// ============================================================================

export const CardContract: ComponentContract = {
  name: 'Card',
  version: '1.0.0',
  description: 'A surface element for grouping related content with optional elevation.',
  props: [
    {
      name: 'elevation',
      type: 'number',
      required: false,
      defaultValue: 2,
      description: 'Shadow depth (0–24).',
    },
    {
      name: 'title',
      type: 'string',
      required: false,
      description: 'Card title displayed in the header.',
    },
    {
      name: 'subtitle',
      type: 'string',
      required: false,
      description: 'Secondary heading beneath the title.',
    },
  ],
  slots: [
    {
      name: 'default',
      description: 'Primary card content.',
      allowedComponents: [],
      isDefault: true,
      allowsReorder: true,
      isSingleChild: false,
    },
    {
      name: 'actions',
      description: 'Action buttons displayed in the card footer.',
      allowedComponents: ['Button'],
      isDefault: false,
      allowsReorder: true,
      isSingleChild: false,
    },
  ],
  events: [],
  styles: {},
  layout: {
    isContainer: true,
    defaultDisplay: 'block',
    draggable: true,
    resizable: true,
    fillsParent: false,
    forbiddenAncestors: [],
    allowedChildTypes: [],
  },
  builder: {
    icon: 'card',
    palette: {
      displayName: 'Card',
      group: 'Layout',
      searchKeywords: ['container', 'surface', 'panel', 'tile'],
      featured: false,
    },
    canvas: {
      resizable: true,
      draggable: true,
      selectable: true,
      container: true,
    },
    codegen: {
      importPath: '@ghatana/design-system',
      componentName: 'Card',
      namedExport: true,
      htmlTagName: 'ghatana-card',
    },
  },
  aiPolicy: {
    allowAutonomousConfiguration: true,
    reviewRequiredProps: [],
    autoApplyConfidenceThreshold: 0.85,
    permittedActions: ['set-prop', 'remove-prop', 'add-node', 'remove-node', 'reorder-node', 'resize', 'reposition'],
  },
  builderA11y: {
    requiredA11yProps: [],
    trapsFocusRequiresClose: false,
    motionRequiresReductionSupport: false,
    wcagLevel: 'AA',
  },
  telemetry: {
    emittedEvents: [],
    autoTracksInteractions: false,
    requiresConsentForTracking: false,
  },
  observability: {
    requiresTraceContext: false,
    performanceMarks: ['card-render'],
    reportsRenderErrors: true,
  },
  preview: {
    minimumTrustLevel: 'semi-trusted',
    requiresNetwork: false,
    requiresStorage: false,
    requiresConsent: false,
  },
  privacy: {
    requiresConsentFlow: false,
    mayRenderPii: false,
    regulatoryFrameworks: [],
  },
  configurator: {
    groups: [
      {
        id: 'header',
        label: 'Header',
        collapsed: false,
        propNames: ['title', 'subtitle'],
      },
      {
        id: 'appearance',
        label: 'Appearance',
        collapsed: false,
        propNames: ['elevation'],
      },
    ],
    showAdvancedSection: false,
    showLivePreview: true,
    resettableProps: ['elevation', 'title', 'subtitle'],
  },
  responsive: {
    isResponsive: true,
    breakpoints: [
      {
        breakpoint: 'sm',
        minWidth: 640,
        propOverrides: { elevation: 1 },
        hiddenByDefault: false,
      },
      {
        breakpoint: 'lg',
        minWidth: 1024,
        propOverrides: { elevation: 4 },
        hiddenByDefault: false,
      },
    ],
    responsiveProps: ['elevation'],
    supportsContainerQuery: false,
    responsiveScale: 'spacing',
  },
  metadata: {
    category: 'layout',
    status: 'stable',
    platforms: ['web'],
    tags: ['card', 'container', 'surface'],
  },
};

// ============================================================================
// TextField
// ============================================================================

export const TextFieldContract: ComponentContract = {
  name: 'TextField',
  version: '1.0.0',
  description: 'A text input field for collecting user data.',
  props: [
    {
      name: 'label',
      type: 'string',
      required: true,
      defaultValue: 'Label',
      description: 'Visible label for the input.',
    },
    {
      name: 'placeholder',
      type: 'string',
      required: false,
      description: 'Placeholder text shown when the field is empty.',
    },
    {
      name: 'variant',
      type: 'string',
      required: false,
      defaultValue: 'outlined',
      description: 'Visual style variant.',
    },
    {
      name: 'size',
      type: 'string',
      required: false,
      defaultValue: 'medium',
      description: 'Size variant.',
    },
    {
      name: 'required',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Marks the field as required.',
    },
    {
      name: 'disabled',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether the field is disabled.',
    },
    {
      name: 'fullWidth',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether to expand to full container width.',
    },
    {
      name: 'multiline',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether to render as a textarea.',
    },
  ],
  slots: [],
  events: [
    {
      name: 'onChange',
      description: 'Fired when the input value changes.',
    },
    {
      name: 'onBlur',
      description: 'Fired when the input loses focus.',
    },
  ],
  styles: {},
  layout: {
    isContainer: false,
    defaultDisplay: 'block',
    draggable: true,
    resizable: true,
    fillsParent: false,
    forbiddenAncestors: [],
    allowedChildTypes: [],
  },
  builder: {
    icon: 'textfield',
    palette: {
      displayName: 'Text Field',
      group: 'Inputs',
      searchKeywords: ['input', 'form', 'text', 'field'],
      featured: false,
    },
    canvas: {
      resizable: true,
      draggable: true,
      selectable: true,
      container: false,
    },
    codegen: {
      importPath: '@ghatana/design-system',
      componentName: 'TextField',
      namedExport: true,
      htmlTagName: 'ghatana-text-field',
    },
  },
  aiPolicy: {
    allowAutonomousConfiguration: true,
    reviewRequiredProps: ['label'],
    autoApplyConfidenceThreshold: 0.85,
    permittedActions: ['set-prop', 'remove-prop', 'resize', 'reposition'],
  },
  builderA11y: {
    requiredA11yProps: ['label'],
    trapsFocusRequiresClose: false,
    motionRequiresReductionSupport: false,
    wcagLevel: 'AA',
    a11yGuidance: 'Every TextField must have a visible label prop or an aria-label for screen readers.',
  },
  telemetry: {
    emittedEvents: [
      {
        name: 'onChange',
        description: 'Emitted when the input value changes',
        containsPii: true,
      },
      {
        name: 'onBlur',
        description: 'Emitted when the input loses focus',
        containsPii: false,
      },
    ],
    autoTracksInteractions: true,
    requiresConsentForTracking: true,
  },
  observability: {
    requiresTraceContext: true,
    performanceMarks: ['textfield-render', 'textfield-input'],
    reportsRenderErrors: true,
  },
  preview: {
    minimumTrustLevel: 'trusted-controlled',
    requiresNetwork: false,
    requiresStorage: false,
    requiresConsent: true,
  },
  privacy: {
    requiresConsentFlow: true,
    mayRenderPii: true,
    minimumPreviewTrustLevel: 'trusted-controlled',
    regulatoryFrameworks: ['GDPR', 'CCPA'],
  },
  configurator: {
    groups: [
      {
        id: 'label',
        label: 'Label & Help',
        collapsed: false,
        propNames: ['label', 'placeholder'],
      },
      {
        id: 'appearance',
        label: 'Appearance',
        collapsed: false,
        propNames: ['variant', 'size', 'fullWidth'],
      },
      {
        id: 'behavior',
        label: 'Behavior',
        collapsed: false,
        propNames: ['required', 'disabled', 'multiline'],
      },
    ],
    showAdvancedSection: true,
    showLivePreview: true,
    resettableProps: ['label', 'placeholder', 'variant', 'size', 'required', 'disabled', 'fullWidth', 'multiline'],
  },
  responsive: {
    isResponsive: true,
    breakpoints: [
      {
        breakpoint: 'sm',
        minWidth: 640,
        propOverrides: { fullWidth: true, size: 'small' },
        hiddenByDefault: false,
      },
    ],
    responsiveProps: ['fullWidth', 'size'],
    supportsContainerQuery: false,
    responsiveScale: 'spacing',
  },
  metadata: {
    category: 'input',
    status: 'stable',
    platforms: ['web'],
    tags: ['input', 'form', 'text'],
  },
};

// ============================================================================
// Typography
// ============================================================================

export const TypographyContract: ComponentContract = {
  name: 'Typography',
  version: '1.0.0',
  description: 'A text rendering component for headings, body copy, and captions.',
  props: [
    {
      name: 'variant',
      type: 'string',
      required: false,
      defaultValue: 'body1',
      description: 'Typography scale variant (h1–h6, body1, body2, caption, overline).',
    },
    {
      name: 'children',
      type: 'string',
      required: true,
      description: 'The text content.',
    },
    {
      name: 'color',
      type: 'string',
      required: false,
      description: 'Text color token key.',
    },
    {
      name: 'align',
      type: 'string',
      required: false,
      defaultValue: 'left',
      description: 'Text alignment.',
    },
  ],
  slots: [],
  events: [],
  styles: {},
  layout: {
    isContainer: false,
    defaultDisplay: 'block',
    draggable: true,
    resizable: true,
    fillsParent: false,
    forbiddenAncestors: [],
    allowedChildTypes: [],
  },
  builder: {
    icon: 'text',
    palette: {
      displayName: 'Typography',
      group: 'Content',
      searchKeywords: ['text', 'heading', 'body', 'label', 'caption'],
      featured: false,
    },
    canvas: {
      resizable: true,
      draggable: true,
      selectable: true,
      container: false,
    },
    codegen: {
      importPath: '@ghatana/design-system',
      componentName: 'Typography',
      namedExport: true,
      htmlTagName: 'ghatana-typography',
    },
  },
  aiPolicy: {
    allowAutonomousConfiguration: true,
    reviewRequiredProps: [],
    autoApplyConfidenceThreshold: 0.9,
    permittedActions: ['set-prop', 'remove-prop', 'resize', 'reposition', 'style-update'],
  },
  builderA11y: {
    requiredA11yProps: [],
    trapsFocusRequiresClose: false,
    motionRequiresReductionSupport: false,
    wcagLevel: 'AA',
    a11yGuidance:
      'Use heading variants (h1–h6) for semantic structure. Avoid using heading styles on non-heading content.',
  },
  telemetry: {
    emittedEvents: [],
    autoTracksInteractions: false,
    requiresConsentForTracking: false,
  },
  observability: {
    requiresTraceContext: false,
    performanceMarks: ['typography-render'],
    reportsRenderErrors: false,
  },
  preview: {
    minimumTrustLevel: 'untrusted',
    requiresNetwork: false,
    requiresStorage: false,
    requiresConsent: false,
  },
  privacy: {
    requiresConsentFlow: false,
    mayRenderPii: true,
    minimumPreviewTrustLevel: 'semi-trusted',
    regulatoryFrameworks: [],
  },
  configurator: {
    groups: [
      {
        id: 'content',
        label: 'Content',
        collapsed: false,
        propNames: ['children'],
      },
      {
        id: 'styling',
        label: 'Styling',
        collapsed: false,
        propNames: ['variant', 'color', 'align'],
      },
    ],
    showAdvancedSection: false,
    showLivePreview: true,
    resettableProps: ['variant', 'color', 'align'],
  },
  responsive: {
    isResponsive: true,
    breakpoints: [
      {
        breakpoint: 'sm',
        minWidth: 640,
        propOverrides: { variant: 'body2' },
        hiddenByDefault: false,
        notes: 'Smaller variant on mobile',
      },
    ],
    responsiveProps: ['variant'],
    supportsContainerQuery: true,
    responsiveScale: 'typography',
  },
  metadata: {
    category: 'content',
    status: 'stable',
    platforms: ['web'],
    tags: ['text', 'typography', 'heading', 'content'],
  },
};

// ============================================================================
// Box
// ============================================================================

export const BoxContract: ComponentContract = {
  name: 'Box',
  version: '1.0.0',
  description:
    'A generic layout container with configurable display, flex, and spacing properties.',
  props: [
    {
      name: 'display',
      type: 'string',
      required: false,
      defaultValue: 'block',
      description: 'CSS display value (block, flex, inline-flex, grid).',
    },
    {
      name: 'padding',
      type: 'number',
      required: false,
      defaultValue: 2,
      description: 'Spacing scale padding (0–10).',
    },
    {
      name: 'margin',
      type: 'number',
      required: false,
      defaultValue: 0,
      description: 'Spacing scale margin (0–10).',
    },
    {
      name: 'backgroundColor',
      type: 'string',
      required: false,
      description: 'Background color token key.',
    },
    {
      name: 'borderRadius',
      type: 'number',
      required: false,
      defaultValue: 0,
      description: 'Border radius scale value (0–10).',
    },
    {
      name: 'flexDirection',
      type: 'string',
      required: false,
      description: 'Flex direction (row, column, row-reverse, column-reverse).',
    },
    {
      name: 'justifyContent',
      type: 'string',
      required: false,
      description: 'Flex/grid justification.',
    },
    {
      name: 'alignItems',
      type: 'string',
      required: false,
      description: 'Flex/grid alignment.',
    },
  ],
  slots: [
    {
      name: 'default',
      description: 'Children placed inside the box.',
      allowedComponents: [],
      isDefault: true,
      allowsReorder: true,
      isSingleChild: false,
    },
  ],
  events: [],
  styles: {},
  layout: {
    isContainer: true,
    defaultDisplay: 'block',
    draggable: true,
    resizable: true,
    fillsParent: false,
    forbiddenAncestors: [],
    allowedChildTypes: [],
  },
  builder: {
    icon: 'box',
    palette: {
      displayName: 'Box',
      group: 'Layout',
      searchKeywords: ['container', 'div', 'layout', 'flex', 'grid', 'wrapper'],
      featured: false,
    },
    canvas: {
      resizable: true,
      draggable: true,
      selectable: true,
      container: true,
    },
    codegen: {
      importPath: '@ghatana/design-system',
      componentName: 'Box',
      namedExport: true,
      htmlTagName: 'ghatana-box',
    },
  },
  aiPolicy: {
    allowAutonomousConfiguration: true,
    reviewRequiredProps: [],
    autoApplyConfidenceThreshold: 0.9,
    permittedActions: [
      'set-prop',
      'remove-prop',
      'add-node',
      'remove-node',
      'reorder-node',
      'resize',
      'reposition',
      'style-update',
    ],
  },
  builderA11y: {
    requiredA11yProps: [],
    trapsFocusRequiresClose: false,
    motionRequiresReductionSupport: false,
    wcagLevel: 'AA',
  },
  telemetry: {
    emittedEvents: [],
    autoTracksInteractions: false,
    requiresConsentForTracking: false,
  },
  observability: {
    requiresTraceContext: false,
    performanceMarks: ['box-render', 'box-layout'],
    reportsRenderErrors: true,
  },
  preview: {
    minimumTrustLevel: 'semi-trusted',
    requiresNetwork: false,
    requiresStorage: false,
    requiresConsent: false,
  },
  privacy: {
    requiresConsentFlow: false,
    mayRenderPii: false,
    regulatoryFrameworks: [],
  },
  configurator: {
    groups: [
      {
        id: 'layout',
        label: 'Layout',
        collapsed: false,
        propNames: ['display', 'flexDirection', 'justifyContent', 'alignItems'],
      },
      {
        id: 'spacing',
        label: 'Spacing',
        collapsed: false,
        propNames: ['padding', 'margin'],
      },
      {
        id: 'appearance',
        label: 'Appearance',
        collapsed: false,
        propNames: ['backgroundColor', 'borderRadius'],
      },
    ],
    showAdvancedSection: true,
    showLivePreview: true,
    resettableProps: ['display', 'padding', 'margin', 'backgroundColor', 'borderRadius', 'flexDirection', 'justifyContent', 'alignItems'],
  },
  responsive: {
    isResponsive: true,
    breakpoints: [
      {
        breakpoint: 'sm',
        minWidth: 640,
        propOverrides: { flexDirection: 'column', padding: 1 },
        hiddenByDefault: false,
        notes: 'Stack on mobile',
      },
      {
        breakpoint: 'md',
        minWidth: 768,
        propOverrides: { flexDirection: 'row', padding: 2 },
        hiddenByDefault: false,
      },
      {
        breakpoint: 'lg',
        minWidth: 1024,
        propOverrides: { padding: 3 },
        hiddenByDefault: false,
      },
    ],
    responsiveProps: ['display', 'flexDirection', 'padding', 'margin'],
    supportsContainerQuery: true,
    responsiveScale: 'both',
  },
  metadata: {
    category: 'layout',
    status: 'stable',
    platforms: ['web'],
    tags: ['layout', 'container', 'box', 'flex', 'grid'],
  },
};

// ============================================================================
// Starter contract list and registration helper
// ============================================================================

/**
 * All five starter contracts as a readonly array.
 * Suitable for bulk registration or inspection.
 */
export const starterContracts: readonly ComponentContract[] = [
  ButtonContract,
  CardContract,
  TextFieldContract,
  TypographyContract,
  BoxContract,
];

/**
 * Registers all starter contracts into the given {@link RegistryStore}.
 *
 * Each contract is registered with its canonical `@ghatana/design-system`
 * import path and a deterministic ID (`starter-{name.toLowerCase()}`).
 * Calling this function when a contract is already registered (by id) will
 * silently no-op because {@link RegistryStore.registerComponent} stores by id.
 *
 * @param store - The registry store to register contracts into.
 */
export function registerStarterContracts(store: RegistryStore): void {
  for (const contract of starterContracts) {
    store.registerComponent({
      id: `starter-${contract.name.toLowerCase()}`,
      contract,
      hash: contract.name,
      source: '@ghatana/design-system',
      version: contract.version,
    });
  }
}
