/**
 * Layer-Specific Actions
 * 
 * Comprehensive action definitions for each semantic layer.
 * Actions are prioritized and categorized for optimal user experience.
 * 
 * @doc.type actions
 * @doc.purpose Layer-specific action definitions
 * @doc.layer core
 */

import { ActionDefinition, ActionContext } from '../core/action-registry';
import { SemanticLayer } from '../chrome';

/**
 * Architecture Layer Actions (0.1x - 0.5x)
 * Focus: System design, high-level flows
 */
export const ARCHITECTURE_ACTIONS: ActionDefinition[] = [
    {
        id: 'arch-add-service',
        label: 'Add Service',
        icon: '🔷',
        shortcut: 'S',
        category: 'layer',
        description: 'Add a service node to the architecture',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Adding service node', context);
            // Handler implementation will be added in Phase 3
        },
        isEnabled: (context) => context.layer === 'architecture',
        isVisible: (context) => context.layer === 'architecture',
    },
    {
        id: 'arch-add-database',
        label: 'Add Database',
        icon: '🗄️',
        shortcut: 'D',
        category: 'layer',
        description: 'Add a database node to the architecture',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding database node', context);
        },
        isEnabled: (context) => context.layer === 'architecture',
        isVisible: (context) => context.layer === 'architecture',
    },
    {
        id: 'arch-add-api-contract',
        label: 'Add API Contract',
        icon: '🔌',
        shortcut: 'A',
        category: 'layer',
        description: 'Define an API contract between services',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Adding API contract', context);
        },
        isEnabled: (context) => context.layer === 'architecture',
        isVisible: (context) => context.layer === 'architecture',
    },
    {
        id: 'arch-connect-services',
        label: 'Connect Services',
        icon: '🔗',
        shortcut: 'C',
        category: 'layer',
        description: 'Create connection between services',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Connecting services', context);
        },
        isEnabled: (context) => context.layer === 'architecture' && context.selection === 'multiple',
        isVisible: (context) => context.layer === 'architecture',
    },
    {
        id: 'arch-define-boundary',
        label: 'Define Boundary',
        icon: '⭕',
        shortcut: 'B',
        category: 'layer',
        description: 'Define system or service boundary',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Defining boundary', context);
        },
        isEnabled: (context) => context.layer === 'architecture',
        isVisible: (context) => context.layer === 'architecture',
    },
];

/**
 * Design Layer Actions (0.5x - 1.0x)
 * Focus: UI/UX design, component layout
 */
export const DESIGN_ACTIONS: ActionDefinition[] = [
    {
        id: 'design-add-component',
        label: 'Add Component',
        icon: '🧩',
        shortcut: 'C',
        category: 'layer',
        description: 'Add a UI component to the design',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Adding component', context);
        },
        isEnabled: (context) => context.layer === 'design',
        isVisible: (context) => context.layer === 'design',
    },
    {
        id: 'design-add-screen',
        label: 'Add Screen',
        icon: '📱',
        shortcut: 'S',
        category: 'layer',
        description: 'Add a screen or page to the design',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding screen', context);
        },
        isEnabled: (context) => context.layer === 'design',
        isVisible: (context) => context.layer === 'design',
    },
    {
        id: 'design-add-user-flow',
        label: 'Add User Flow',
        icon: '🔀',
        shortcut: 'F',
        category: 'layer',
        description: 'Create a user flow diagram',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Adding user flow', context);
        },
        isEnabled: (context) => context.layer === 'design',
        isVisible: (context) => context.layer === 'design',
    },
    {
        id: 'design-add-wireframe',
        label: 'Add Wireframe',
        icon: '📐',
        shortcut: 'W',
        category: 'layer',
        description: 'Create a wireframe mockup',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Adding wireframe', context);
        },
        isEnabled: (context) => context.layer === 'design',
        isVisible: (context) => context.layer === 'design',
    },
    {
        id: 'design-link-screens',
        label: 'Link Screens',
        icon: '🔗',
        shortcut: 'L',
        category: 'layer',
        description: 'Create navigation link between screens',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Linking screens', context);
        },
        isEnabled: (context) => context.layer === 'design' && context.selection === 'multiple',
        isVisible: (context) => context.layer === 'design',
    },
];

/**
 * Component Layer Actions (1.0x - 2.0x)
 * Focus: Detailed component design, interactions
 */
export const COMPONENT_ACTIONS: ActionDefinition[] = [
    {
        id: 'comp-add-component',
        label: 'Add Component',
        icon: '⚙️',
        shortcut: 'C',
        category: 'layer',
        description: 'Add a detailed component specification',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Adding component detail', context);
        },
        isEnabled: (context) => context.layer === 'component',
        isVisible: (context) => context.layer === 'component',
    },
    {
        id: 'comp-add-state',
        label: 'Add State',
        icon: '📊',
        shortcut: 'S',
        category: 'layer',
        description: 'Define component state',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding state', context);
        },
        isEnabled: (context) => context.layer === 'component',
        isVisible: (context) => context.layer === 'component',
    },
    {
        id: 'comp-add-event',
        label: 'Add Event',
        icon: '⚡',
        shortcut: 'E',
        category: 'layer',
        description: 'Define component event handler',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Adding event', context);
        },
        isEnabled: (context) => context.layer === 'component',
        isVisible: (context) => context.layer === 'component',
    },
    {
        id: 'comp-add-props',
        label: 'Add Props',
        icon: '🔧',
        shortcut: 'P',
        category: 'layer',
        description: 'Define component properties',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Adding props', context);
        },
        isEnabled: (context) => context.layer === 'component',
        isVisible: (context) => context.layer === 'component',
    },
    {
        id: 'comp-add-interaction',
        label: 'Add Interaction',
        icon: '🖱️',
        shortcut: 'I',
        category: 'layer',
        description: 'Define user interaction',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Adding interaction', context);
        },
        isEnabled: (context) => context.layer === 'component',
        isVisible: (context) => context.layer === 'component',
    },
];

/**
 * Implementation Layer Actions (2.0x - 5.0x)
 * Focus: Code implementation, logic
 */
export const IMPLEMENTATION_ACTIONS: ActionDefinition[] = [
    {
        id: 'impl-add-code-block',
        label: 'Add Code Block',
        icon: '💻',
        shortcut: 'C',
        category: 'layer',
        description: 'Add a code block',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Adding code block', context);
        },
        isEnabled: (context) => context.layer === 'implementation',
        isVisible: (context) => context.layer === 'implementation',
    },
    {
        id: 'impl-add-function',
        label: 'Add Function',
        icon: 'ƒ',
        shortcut: 'F',
        category: 'layer',
        description: 'Define a function',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding function', context);
        },
        isEnabled: (context) => context.layer === 'implementation',
        isVisible: (context) => context.layer === 'implementation',
    },
    {
        id: 'impl-add-class',
        label: 'Add Class',
        icon: '🏛️',
        shortcut: 'L',
        category: 'layer',
        description: 'Define a class',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Adding class', context);
        },
        isEnabled: (context) => context.layer === 'implementation',
        isVisible: (context) => context.layer === 'implementation',
    },
    {
        id: 'impl-add-data-structure',
        label: 'Add Data Structure',
        icon: '📦',
        shortcut: 'D',
        category: 'layer',
        description: 'Define a data structure',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Adding data structure', context);
        },
        isEnabled: (context) => context.layer === 'implementation',
        isVisible: (context) => context.layer === 'implementation',
    },
    {
        id: 'impl-add-algorithm',
        label: 'Add Algorithm',
        icon: '🔢',
        shortcut: 'A',
        category: 'layer',
        description: 'Define an algorithm flow',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Adding algorithm', context);
        },
        isEnabled: (context) => context.layer === 'implementation',
        isVisible: (context) => context.layer === 'implementation',
    },
];

/**
 * Detail Layer Actions (5.0x+)
 * Focus: Line-by-line code, debugging
 */
export const DETAIL_ACTIONS: ActionDefinition[] = [
    {
        id: 'detail-edit-code',
        label: 'Edit Code',
        icon: '✏️',
        shortcut: 'E',
        category: 'layer',
        description: 'Edit code inline',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Editing code', context);
        },
        isEnabled: (context) => context.layer === 'detail',
        isVisible: (context) => context.layer === 'detail',
    },
    {
        id: 'detail-add-breakpoint',
        label: 'Add Breakpoint',
        icon: '🔴',
        shortcut: 'B',
        category: 'layer',
        description: 'Add debugging breakpoint',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding breakpoint', context);
        },
        isEnabled: (context) => context.layer === 'detail',
        isVisible: (context) => context.layer === 'detail',
    },
    {
        id: 'detail-add-comment',
        label: 'Add Comment',
        icon: '💬',
        shortcut: 'C',
        category: 'layer',
        description: 'Add code comment',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Adding comment', context);
        },
        isEnabled: (context) => context.layer === 'detail',
        isVisible: (context) => context.layer === 'detail',
    },
    {
        id: 'detail-inspect-variable',
        label: 'Inspect Variable',
        icon: '🔍',
        shortcut: 'I',
        category: 'layer',
        description: 'Inspect variable value',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Inspecting variable', context);
        },
        isEnabled: (context) => context.layer === 'detail',
        isVisible: (context) => context.layer === 'detail',
    },
    {
        id: 'detail-add-debug-log',
        label: 'Add Debug Log',
        icon: '📝',
        shortcut: 'L',
        category: 'layer',
        description: 'Add debug logging statement',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Adding debug log', context);
        },
        isEnabled: (context) => context.layer === 'detail',
        isVisible: (context) => context.layer === 'detail',
    },
];

/**
 * Universal Actions (Available in all layers)
 */
export const UNIVERSAL_LAYER_ACTIONS: ActionDefinition[] = [
    {
        id: 'universal-add-shape',
        label: 'Add Shape',
        icon: '⬜',
        shortcut: 'R',
        category: 'universal',
        description: 'Add a shape element',
        priority: 5,
        handler: async (context: ActionContext) => {
            console.log('Adding shape', context);
        },
    },
    {
        id: 'universal-add-text',
        label: 'Add Text',
        icon: '📝',
        shortcut: 'T',
        category: 'universal',
        description: 'Add a text element',
        priority: 5,
        handler: async (context: ActionContext) => {
            console.log('Adding text', context);
        },
    },
    {
        id: 'universal-add-frame',
        label: 'Add Frame',
        icon: '🖼️',
        shortcut: 'F',
        category: 'universal',
        description: 'Add a frame container',
        priority: 5,
        handler: async (context: ActionContext) => {
            console.log('Adding frame', context);
        },
    },
    {
        id: 'universal-draw-annotation',
        label: 'Draw Annotation',
        icon: '✍️',
        shortcut: 'A',
        category: 'universal',
        description: 'Draw freehand annotation',
        priority: 4,
        handler: async (context: ActionContext) => {
            console.log('Drawing annotation', context);
        },
    },
    {
        id: 'universal-insert-image',
        label: 'Insert Image',
        icon: '🖼️',
        shortcut: 'I',
        category: 'universal',
        description: 'Insert an image',
        priority: 4,
        handler: async (context: ActionContext) => {
            console.log('Inserting image', context);
        },
    },
];

/**
 * Get all layer actions
 */
export function getAllLayerActions(): Record<SemanticLayer, ActionDefinition[]> {
    return {
        architecture: ARCHITECTURE_ACTIONS,
        design: DESIGN_ACTIONS,
        component: COMPONENT_ACTIONS,
        implementation: IMPLEMENTATION_ACTIONS,
        detail: DETAIL_ACTIONS,
    };
}

/**
 * Get actions for specific layer
 */
export function getLayerActions(layer: SemanticLayer): ActionDefinition[] {
    const allActions = getAllLayerActions();
    return allActions[layer] || [];
}
