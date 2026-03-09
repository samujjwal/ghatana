/**
 * Built-in UI Component Definitions
 *
 * Artifact contracts for standard UI components.
 * These are the core building blocks for visual UI design.
 *
 * @doc.type definitions
 * @doc.purpose UI component artifact contracts
 * @doc.layer core
 * @doc.pattern Data-Driven Definition
 */

import type { ArtifactContract } from '../../model/contracts';

// ============================================================================
// Button Component
// ============================================================================

export const ButtonContract: ArtifactContract = {
    identity: {
        artifactId: 'ui-button-001',
        kind: 'ui:button',
        version: '1.0.0',
        name: 'Button',
        category: 'Inputs',
        tags: ['interactive', 'action', 'form'],
        icon: 'hand-pointer',
        description: 'A clickable button for triggering actions',
    },
    propsSchema: {
        label: {
            type: 'string',
            default: 'Click Me',
            required: true,
            description: 'Button text label',
        },
        variant: {
            type: 'enum',
            values: ['primary', 'secondary', 'outline', 'ghost', 'destructive'],
            default: 'primary',
            description: 'Visual style variant',
        },
        size: {
            type: 'enum',
            values: ['sm', 'md', 'lg'],
            default: 'md',
            description: 'Button size',
        },
        disabled: {
            type: 'boolean',
            default: false,
            description: 'Whether the button is disabled',
        },
        loading: {
            type: 'boolean',
            default: false,
            description: 'Show loading spinner',
        },
        icon: {
            type: 'string',
            description: 'Icon name to display',
        },
        iconPosition: {
            type: 'enum',
            values: ['left', 'right'],
            default: 'left',
            description: 'Icon position relative to label',
        },
        fullWidth: {
            type: 'boolean',
            default: false,
            description: 'Expand to full container width',
        },
    },
    styleSchema: {
        backgroundColor: {
            type: 'color',
            token: 'button.primary.bg',
            description: 'Background color',
        },
        textColor: {
            type: 'color',
            token: 'button.primary.text',
            description: 'Text color',
        },
        borderRadius: {
            type: 'spacing',
            tokens: ['none', 'sm', 'md', 'lg', 'full'],
            default: 'md',
            description: 'Border radius',
        },
        borderWidth: {
            type: 'number',
            min: 0,
            max: 4,
            default: 0,
            unit: 'px',
            description: 'Border width',
        },
        borderColor: {
            type: 'color',
            description: 'Border color',
        },
        shadow: {
            type: 'enum',
            values: ['none', 'sm', 'md', 'lg'],
            default: 'none',
            description: 'Box shadow',
        },
    },
    bindingsSchema: {
        onClick: {
            type: 'action',
            description: 'Action to trigger on click',
        },
        label: {
            type: 'binding',
            bindingType: 'state',
            valueType: 'string',
            description: 'Dynamic label binding',
        },
        disabled: {
            type: 'binding',
            bindingType: 'expression',
            valueType: 'boolean',
            description: 'Dynamic disabled state',
        },
    },
    capabilities: {
        resizable: true,
        droppable: false,
        textEditable: true,
        connectable: false,
        styleable: true,
        bindable: true,
        lockable: true,
        copyable: true,
        drillable: false,
    },
    constraints: {
        minWidth: 48,
        maxWidth: 800,
        minHeight: 32,
        maxHeight: 120,
        acceptsChildren: false,
        aspectRatio: undefined,
        snapToGrid: 8,
    },
    defaults: {
        props: {
            label: 'Button',
            variant: 'primary',
            size: 'md',
            disabled: false,
            loading: false,
        },
        style: {
            borderRadius: 'md',
            shadow: 'none',
        },
        width: 120,
        height: 40,
    },
    modality: 'visual',
    platforms: ['web', 'desktop', 'mobile'],
};

// ============================================================================
// Text Input Component
// ============================================================================

export const TextInputContract: ArtifactContract = {
    identity: {
        artifactId: 'ui-textinput-001',
        kind: 'ui:text-input',
        version: '1.0.0',
        name: 'Text Input',
        category: 'Inputs',
        tags: ['interactive', 'form', 'text'],
        icon: 'text-cursor',
        description: 'A text input field for user data entry',
    },
    propsSchema: {
        placeholder: {
            type: 'string',
            default: 'Enter text...',
            description: 'Placeholder text',
        },
        value: {
            type: 'string',
            default: '',
            description: 'Input value',
        },
        type: {
            type: 'enum',
            values: ['text', 'email', 'password', 'number', 'tel', 'url'],
            default: 'text',
            description: 'Input type',
        },
        label: {
            type: 'string',
            description: 'Field label',
        },
        helperText: {
            type: 'string',
            description: 'Helper text below input',
        },
        errorText: {
            type: 'string',
            description: 'Error message',
        },
        disabled: {
            type: 'boolean',
            default: false,
            description: 'Whether the input is disabled',
        },
        required: {
            type: 'boolean',
            default: false,
            description: 'Whether the field is required',
        },
        maxLength: {
            type: 'number',
            description: 'Maximum character length',
        },
    },
    styleSchema: {
        backgroundColor: {
            type: 'color',
            token: 'input.bg',
            description: 'Background color',
        },
        textColor: {
            type: 'color',
            token: 'input.text',
            description: 'Text color',
        },
        borderRadius: {
            type: 'spacing',
            tokens: ['none', 'sm', 'md', 'lg'],
            default: 'md',
            description: 'Border radius',
        },
        borderColor: {
            type: 'color',
            token: 'input.border',
            description: 'Border color',
        },
        focusBorderColor: {
            type: 'color',
            token: 'input.focusBorder',
            description: 'Border color when focused',
        },
    },
    bindingsSchema: {
        value: {
            type: 'binding',
            bindingType: 'state',
            valueType: 'string',
            description: 'Two-way value binding',
        },
        onChange: {
            type: 'action',
            description: 'Action on value change',
        },
        onSubmit: {
            type: 'action',
            description: 'Action on enter key',
        },
    },
    capabilities: {
        resizable: true,
        droppable: false,
        textEditable: false,
        connectable: false,
        styleable: true,
        bindable: true,
        lockable: true,
        copyable: true,
        drillable: false,
    },
    constraints: {
        minWidth: 80,
        maxWidth: 600,
        minHeight: 32,
        maxHeight: 48,
        acceptsChildren: false,
        snapToGrid: 8,
    },
    defaults: {
        props: {
            placeholder: 'Enter text...',
            type: 'text',
            disabled: false,
            required: false,
        },
        style: {
            borderRadius: 'md',
        },
        width: 240,
        height: 40,
    },
    modality: 'visual',
    platforms: ['web', 'desktop', 'mobile'],
};

// ============================================================================
// Container Component
// ============================================================================

export const ContainerContract: ArtifactContract = {
    identity: {
        artifactId: 'ui-container-001',
        kind: 'ui:container',
        version: '1.0.0',
        name: 'Container',
        category: 'Layout',
        tags: ['layout', 'container', 'grouping'],
        icon: 'square',
        description: 'A container for grouping and laying out child elements',
    },
    propsSchema: {
        direction: {
            type: 'enum',
            values: ['row', 'column'],
            default: 'column',
            description: 'Layout direction',
        },
        justify: {
            type: 'enum',
            values: ['start', 'center', 'end', 'between', 'around', 'evenly'],
            default: 'start',
            description: 'Justify content',
        },
        align: {
            type: 'enum',
            values: ['start', 'center', 'end', 'stretch', 'baseline'],
            default: 'stretch',
            description: 'Align items',
        },
        wrap: {
            type: 'boolean',
            default: false,
            description: 'Enable flex wrap',
        },
        gap: {
            type: 'spacing',
            tokens: ['0', '1', '2', '3', '4', '5', '6', '8'],
            default: '2',
            description: 'Gap between children',
        },
        padding: {
            type: 'spacing',
            tokens: ['0', '1', '2', '3', '4', '5', '6', '8'],
            default: '4',
            description: 'Inner padding',
        },
    },
    styleSchema: {
        backgroundColor: {
            type: 'color',
            allowCustom: true,
            description: 'Background color',
        },
        borderRadius: {
            type: 'spacing',
            tokens: ['none', 'sm', 'md', 'lg', 'xl', '2xl'],
            default: 'none',
            description: 'Border radius',
        },
        borderWidth: {
            type: 'number',
            min: 0,
            max: 8,
            default: 0,
            unit: 'px',
            description: 'Border width',
        },
        borderColor: {
            type: 'color',
            description: 'Border color',
        },
        shadow: {
            type: 'enum',
            values: ['none', 'sm', 'md', 'lg', 'xl'],
            default: 'none',
            description: 'Box shadow',
        },
        overflow: {
            type: 'enum',
            values: ['visible', 'hidden', 'scroll', 'auto'],
            default: 'visible',
            description: 'Overflow behavior',
        },
    },
    capabilities: {
        resizable: true,
        droppable: true,
        textEditable: false,
        connectable: false,
        styleable: true,
        bindable: true,
        lockable: true,
        copyable: true,
        drillable: true,
    },
    constraints: {
        minWidth: 40,
        minHeight: 40,
        acceptsChildren: true,
        snapToGrid: 8,
    },
    defaults: {
        props: {
            direction: 'column',
            justify: 'start',
            align: 'stretch',
            wrap: false,
            gap: '2',
            padding: '4',
        },
        style: {
            borderRadius: 'none',
            shadow: 'none',
            overflow: 'visible',
        },
        width: 300,
        height: 200,
    },
    modality: 'visual',
    platforms: ['web', 'desktop', 'mobile'],
};

// ============================================================================
// Card Component
// ============================================================================

export const CardContract: ArtifactContract = {
    identity: {
        artifactId: 'ui-card-001',
        kind: 'ui:card',
        version: '1.0.0',
        name: 'Card',
        category: 'Layout',
        tags: ['layout', 'container', 'surface'],
        icon: 'rectangle-history',
        description: 'A card container with header, body, and footer sections',
    },
    propsSchema: {
        title: {
            type: 'string',
            description: 'Card title',
        },
        subtitle: {
            type: 'string',
            description: 'Card subtitle',
        },
        showHeader: {
            type: 'boolean',
            default: true,
            description: 'Show header section',
        },
        showFooter: {
            type: 'boolean',
            default: false,
            description: 'Show footer section',
        },
        variant: {
            type: 'enum',
            values: ['elevated', 'outlined', 'filled'],
            default: 'elevated',
            description: 'Card visual variant',
        },
    },
    styleSchema: {
        backgroundColor: {
            type: 'color',
            token: 'card.bg',
            description: 'Background color',
        },
        borderRadius: {
            type: 'spacing',
            tokens: ['none', 'sm', 'md', 'lg', 'xl'],
            default: 'lg',
            description: 'Border radius',
        },
        borderColor: {
            type: 'color',
            token: 'card.border',
            description: 'Border color',
        },
        shadow: {
            type: 'enum',
            values: ['none', 'sm', 'md', 'lg', 'xl'],
            default: 'md',
            description: 'Box shadow',
        },
        headerBg: {
            type: 'color',
            description: 'Header background',
        },
        footerBg: {
            type: 'color',
            description: 'Footer background',
        },
    },
    capabilities: {
        resizable: true,
        droppable: true,
        textEditable: true,
        connectable: false,
        styleable: true,
        bindable: true,
        lockable: true,
        copyable: true,
        drillable: true,
    },
    constraints: {
        minWidth: 160,
        minHeight: 100,
        acceptsChildren: true,
        snapToGrid: 8,
    },
    defaults: {
        props: {
            title: 'Card Title',
            showHeader: true,
            showFooter: false,
            variant: 'elevated',
        },
        style: {
            borderRadius: 'lg',
            shadow: 'md',
        },
        width: 320,
        height: 200,
    },
    modality: 'visual',
    platforms: ['web', 'desktop', 'mobile'],
};

// ============================================================================
// Text Component
// ============================================================================

export const TextContract: ArtifactContract = {
    identity: {
        artifactId: 'ui-text-001',
        kind: 'ui:text',
        version: '1.0.0',
        name: 'Text',
        category: 'Typography',
        tags: ['text', 'typography', 'content'],
        icon: 'font',
        description: 'A text element for displaying content',
    },
    propsSchema: {
        content: {
            type: 'string',
            default: 'Text content',
            description: 'Text content to display',
        },
        variant: {
            type: 'enum',
            values: ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'body', 'caption', 'label'],
            default: 'body',
            description: 'Typography variant',
        },
        align: {
            type: 'enum',
            values: ['left', 'center', 'right', 'justify'],
            default: 'left',
            description: 'Text alignment',
        },
        truncate: {
            type: 'boolean',
            default: false,
            description: 'Truncate with ellipsis',
        },
        maxLines: {
            type: 'number',
            min: 1,
            description: 'Maximum lines before truncation',
        },
    },
    styleSchema: {
        color: {
            type: 'color',
            token: 'text.primary',
            description: 'Text color',
        },
        fontSize: {
            type: 'number',
            min: 8,
            max: 120,
            unit: 'px',
            description: 'Font size',
        },
        fontWeight: {
            type: 'enum',
            values: ['normal', 'medium', 'semibold', 'bold'],
            default: 'normal',
            description: 'Font weight',
        },
        fontStyle: {
            type: 'enum',
            values: ['normal', 'italic'],
            default: 'normal',
            description: 'Font style',
        },
        textDecoration: {
            type: 'enum',
            values: ['none', 'underline', 'line-through'],
            default: 'none',
            description: 'Text decoration',
        },
        lineHeight: {
            type: 'number',
            min: 1,
            max: 3,
            step: 0.1,
            default: 1.5,
            description: 'Line height multiplier',
        },
        letterSpacing: {
            type: 'number',
            min: -2,
            max: 10,
            step: 0.1,
            unit: 'px',
            description: 'Letter spacing',
        },
    },
    bindingsSchema: {
        content: {
            type: 'binding',
            bindingType: 'state',
            valueType: 'string',
            description: 'Dynamic text binding',
        },
    },
    capabilities: {
        resizable: true,
        droppable: false,
        textEditable: true,
        connectable: false,
        styleable: true,
        bindable: true,
        lockable: true,
        copyable: true,
        drillable: false,
    },
    constraints: {
        minWidth: 20,
        minHeight: 16,
        acceptsChildren: false,
        snapToGrid: 4,
    },
    defaults: {
        props: {
            content: 'Text',
            variant: 'body',
            align: 'left',
            truncate: false,
        },
        style: {
            fontWeight: 'normal',
            fontStyle: 'normal',
            textDecoration: 'none',
            lineHeight: 1.5,
        },
        width: 200,
        height: 24,
    },
    modality: 'visual',
    platforms: ['web', 'desktop', 'mobile'],
};

// ============================================================================
// Image Component
// ============================================================================

export const ImageContract: ArtifactContract = {
    identity: {
        artifactId: 'ui-image-001',
        kind: 'ui:image',
        version: '1.0.0',
        name: 'Image',
        category: 'Media',
        tags: ['media', 'image', 'content'],
        icon: 'image',
        description: 'An image element for displaying pictures',
    },
    propsSchema: {
        src: {
            type: 'string',
            required: true,
            description: 'Image source URL',
        },
        alt: {
            type: 'string',
            default: '',
            description: 'Alternative text',
        },
        fit: {
            type: 'enum',
            values: ['contain', 'cover', 'fill', 'none', 'scale-down'],
            default: 'cover',
            description: 'Object fit behavior',
        },
        position: {
            type: 'enum',
            values: ['center', 'top', 'bottom', 'left', 'right'],
            default: 'center',
            description: 'Object position',
        },
        loading: {
            type: 'enum',
            values: ['lazy', 'eager'],
            default: 'lazy',
            description: 'Loading strategy',
        },
        fallback: {
            type: 'string',
            description: 'Fallback image URL',
        },
    },
    styleSchema: {
        borderRadius: {
            type: 'spacing',
            tokens: ['none', 'sm', 'md', 'lg', 'xl', 'full'],
            default: 'none',
            description: 'Border radius',
        },
        borderWidth: {
            type: 'number',
            min: 0,
            max: 8,
            default: 0,
            unit: 'px',
            description: 'Border width',
        },
        borderColor: {
            type: 'color',
            description: 'Border color',
        },
        shadow: {
            type: 'enum',
            values: ['none', 'sm', 'md', 'lg', 'xl'],
            default: 'none',
            description: 'Box shadow',
        },
        opacity: {
            type: 'number',
            min: 0,
            max: 1,
            step: 0.1,
            default: 1,
            description: 'Opacity',
        },
    },
    bindingsSchema: {
        src: {
            type: 'binding',
            bindingType: 'state',
            valueType: 'string',
            description: 'Dynamic image source',
        },
    },
    capabilities: {
        resizable: true,
        droppable: false,
        textEditable: false,
        connectable: false,
        styleable: true,
        bindable: true,
        lockable: true,
        copyable: true,
        drillable: false,
    },
    constraints: {
        minWidth: 20,
        minHeight: 20,
        acceptsChildren: false,
        snapToGrid: 8,
    },
    defaults: {
        props: {
            src: 'https://via.placeholder.com/300x200',
            alt: 'Image',
            fit: 'cover',
            position: 'center',
            loading: 'lazy',
        },
        style: {
            borderRadius: 'none',
            shadow: 'none',
            opacity: 1,
        },
        width: 300,
        height: 200,
    },
    modality: 'visual',
    platforms: ['web', 'desktop', 'mobile'],
};

// ============================================================================
// Export All UI Components
// ============================================================================

export const UI_COMPONENTS: ArtifactContract[] = [
    ButtonContract,
    TextInputContract,
    ContainerContract,
    CardContract,
    TextContract,
    ImageContract,
];

/**
 * Register all UI components with the registry
 */
export function registerUIComponents(
    registry: { register: (c: ArtifactContract, o?: { source: string }) => void }
): void {
    for (const contract of UI_COMPONENTS) {
        registry.register(contract, { source: 'core' });
    }
}
