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
      type: 'enum',
      required: false,
      defaultValue: 'solid',
      description: 'Visual style variant matching the @ghatana/design-system Button variants.',
      builderMetadata: {
        control: 'select',
        category: 'appearance',
        order: 0,
        bindable: false,
      },
      validation: {
        enum: ['solid', 'outline', 'ghost'],
      },
    },
    {
      name: 'color',
      type: 'enum',
      required: false,
      defaultValue: 'primary',
      description: 'Color palette key (tone) matching the design system Button tone prop.',
      builderMetadata: {
        control: 'select',
        category: 'appearance',
        order: 1,
        bindable: false,
      },
      validation: {
        enum: ['primary', 'secondary', 'success', 'warning', 'danger', 'info'],
      },
    },
    {
      name: 'size',
      type: 'enum',
      required: false,
      defaultValue: 'md',
      description: 'Size variant matching the design system Button size prop.',
      builderMetadata: {
        control: 'select',
        category: 'appearance',
        order: 2,
        bindable: false,
      },
      validation: {
        enum: ['sm', 'md', 'lg'],
      },
    },
    {
      name: 'disabled',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether the button is disabled.',
      builderMetadata: {
        control: 'toggle',
        category: 'state',
        order: 0,
        bindable: true,
      },
    },
    {
      name: 'fullWidth',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether to expand to full container width.',
      builderMetadata: {
        control: 'toggle',
        category: 'state',
        order: 1,
        bindable: false,
      },
    },
    {
      name: 'children',
      type: 'string',
      required: true,
      description: 'Button label text.',
      builderMetadata: {
        control: 'text',
        category: 'content',
        order: 0,
        bindable: true,
      },
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
      builderMetadata: {
        control: 'text',
        category: 'label',
        order: 0,
        bindable: true,
      },
    },
    {
      name: 'placeholder',
      type: 'string',
      required: false,
      description: 'Placeholder text shown when the field is empty.',
      builderMetadata: {
        control: 'text',
        category: 'label',
        order: 1,
        bindable: true,
      },
    },
    {
      name: 'size',
      type: 'enum',
      required: false,
      defaultValue: 'medium',
      description: 'Size variant matching the design system TextField size prop.',
      builderMetadata: {
        control: 'select',
        category: 'appearance',
        order: 0,
        bindable: false,
      },
      validation: {
        enum: ['small', 'medium'],
      },
    },
    {
      name: 'required',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Marks the field as required.',
      builderMetadata: {
        control: 'toggle',
        category: 'behavior',
        order: 0,
        bindable: true,
      },
    },
    {
      name: 'disabled',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether the field is disabled.',
      builderMetadata: {
        control: 'toggle',
        category: 'behavior',
        order: 1,
        bindable: true,
      },
    },
    {
      name: 'fullWidth',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether to expand to full container width.',
      builderMetadata: {
        control: 'toggle',
        category: 'appearance',
        order: 1,
        bindable: false,
      },
    },
    {
      name: 'multiline',
      type: 'boolean',
      required: false,
      defaultValue: false,
      description: 'Whether to render as a textarea.',
      builderMetadata: {
        control: 'toggle',
        category: 'behavior',
        order: 2,
        bindable: false,
      },
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
      type: 'enum',
      required: false,
      defaultValue: 'body1',
      description: 'Typography scale variant matching the @ghatana/design-system Typography variant prop.',
      builderMetadata: {
        control: 'select',
        category: 'styling',
        order: 0,
        bindable: false,
      },
      validation: {
        enum: ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'subtitle1', 'subtitle2', 'body1', 'body2', 'caption', 'overline', 'button', 'code'],
      },
    },
    {
      name: 'children',
      type: 'string',
      required: true,
      description: 'The text content.',
      builderMetadata: {
        control: 'text',
        category: 'content',
        order: 0,
        bindable: true,
      },
    },
    {
      name: 'color',
      type: 'enum',
      required: false,
      description: 'Text color token key matching the design system Typography color prop.',
      builderMetadata: {
        control: 'select',
        category: 'styling',
        order: 1,
        bindable: false,
      },
      validation: {
        enum: ['default', 'subtle', 'muted', 'primary', 'secondary', 'success', 'warning', 'danger', 'info'],
      },
    },
    {
      name: 'align',
      type: 'enum',
      required: false,
      defaultValue: 'start',
      description: 'Text alignment.',
      builderMetadata: {
        control: 'select',
        category: 'styling',
        order: 2,
        bindable: false,
      },
      validation: {
        enum: ['start', 'center', 'end', 'justify'],
      },
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
      type: 'enum',
      required: false,
      defaultValue: 'block',
      description: 'CSS display value.',
      builderMetadata: {
        control: 'select',
        category: 'layout',
        order: 0,
        bindable: false,
      },
      validation: {
        enum: ['block', 'flex', 'inline-flex', 'grid', 'inline-block', 'none'],
      },
    },
    {
      name: 'padding',
      type: 'number',
      required: false,
      defaultValue: 2,
      description: 'Spacing scale padding (0–10).',
      builderMetadata: {
        control: 'number',
        category: 'spacing',
        order: 0,
        bindable: false,
      },
      validation: {
        min: 0,
        max: 10,
      },
    },
    {
      name: 'margin',
      type: 'number',
      required: false,
      defaultValue: 0,
      description: 'Spacing scale margin (0–10).',
      builderMetadata: {
        control: 'number',
        category: 'spacing',
        order: 1,
        bindable: false,
      },
      validation: {
        min: 0,
        max: 10,
      },
    },
    {
      name: 'backgroundColor',
      type: 'string',
      required: false,
      description: 'Background color token key.',
      builderMetadata: {
        control: 'text',
        category: 'styling',
        order: 0,
        bindable: false,
      },
    },
    {
      name: 'borderRadius',
      type: 'number',
      required: false,
      defaultValue: 0,
      description: 'Border radius scale value (0–10).',
      builderMetadata: {
        control: 'number',
        category: 'styling',
        order: 1,
        bindable: false,
      },
      validation: {
        min: 0,
        max: 10,
      },
    },
    {
      name: 'flexDirection',
      type: 'enum',
      required: false,
      description: 'Flex direction. Only applies when display is flex or inline-flex.',
      builderMetadata: {
        control: 'select',
        category: 'layout',
        order: 1,
        bindable: false,
      },
      validation: {
        enum: ['row', 'column', 'row-reverse', 'column-reverse'],
      },
    },
    {
      name: 'justifyContent',
      type: 'enum',
      required: false,
      description: 'Flex/grid main-axis alignment.',
      builderMetadata: {
        control: 'select',
        category: 'layout',
        order: 2,
        bindable: false,
      },
      validation: {
        enum: ['flex-start', 'flex-end', 'center', 'space-between', 'space-around', 'space-evenly'],
      },
    },
    {
      name: 'alignItems',
      type: 'enum',
      required: false,
      description: 'Flex/grid cross-axis alignment.',
      builderMetadata: {
        control: 'select',
        category: 'layout',
        order: 3,
        bindable: false,
      },
      validation: {
        enum: ['flex-start', 'flex-end', 'center', 'baseline', 'stretch'],
      },
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
