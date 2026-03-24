/**
 * Portal Node Definition
 *
 * Special artifact for drill-down navigation between canvas views.
 * Portals enable infinite canvas with hierarchical navigation.
 *
 * @doc.type definition
 * @doc.purpose Portal node for drill-down navigation
 * @doc.layer core
 * @doc.pattern Data-Driven Definition
 */

import type { ArtifactContract } from '../../model/contracts';

// ============================================================================
// Portal Node
// ============================================================================

export const PortalNodeContract: ArtifactContract = {
    identity: {
        artifactId: 'canvas-portal-001',
        kind: 'canvas:portal',
        version: '1.0.0',
        name: 'Portal',
        category: 'Navigation',
        tags: ['portal', 'navigation', 'drill-down', 'sub-canvas', 'nested'],
        icon: 'door-open',
        description:
            'A portal that links to a sub-canvas for hierarchical navigation and drill-down',
    },
    propsSchema: {
        title: {
            type: 'string',
            default: 'Sub-Canvas',
            description: 'Portal title shown on the node',
        },
        targetViewId: {
            type: 'string',
            description: 'ID of the target view/canvas to navigate to',
        },
        thumbnail: {
            type: 'string',
            description: 'Thumbnail image URL for preview',
        },
        showPreview: {
            type: 'boolean',
            default: true,
            description: 'Show miniature preview of target canvas',
        },
        previewScale: {
            type: 'number',
            default: 0.1,
            min: 0.05,
            max: 0.5,
            step: 0.05,
            description: 'Scale factor for preview rendering',
        },
        navigationMode: {
            type: 'enum',
            values: ['drill-down', 'popup', 'side-panel', 'new-tab'],
            default: 'drill-down',
            description: 'How to navigate when portal is activated',
        },
        breadcrumbLabel: {
            type: 'string',
            description: 'Label to show in breadcrumb navigation',
        },
        description: {
            type: 'string',
            description: 'Optional description of the sub-canvas contents',
        },
        locked: {
            type: 'boolean',
            default: false,
            description: 'Whether the portal destination is locked',
        },
        icon: {
            type: 'string',
            default: 'layers',
            description: 'Icon to display on the portal',
        },
        badge: {
            type: 'string',
            description: 'Optional badge text (e.g., item count)',
        },
        autoSync: {
            type: 'boolean',
            default: true,
            description: 'Auto-sync preview with target canvas changes',
        },
    },
    styleSchema: {
        backgroundColor: {
            type: 'color',
            default: '#f8fafc',
            description: 'Portal background color',
        },
        borderColor: {
            type: 'color',
            default: '#cbd5e1',
            description: 'Border color',
        },
        borderWidth: {
            type: 'number',
            min: 1,
            max: 8,
            default: 2,
            unit: 'px',
            description: 'Border width',
        },
        borderRadius: {
            type: 'number',
            min: 0,
            max: 32,
            default: 12,
            unit: 'px',
            description: 'Border radius',
        },
        borderStyle: {
            type: 'enum',
            values: ['solid', 'dashed', 'dotted'],
            default: 'dashed',
            description: 'Border style',
        },
        shadow: {
            type: 'enum',
            values: ['none', 'sm', 'md', 'lg', 'xl'],
            default: 'md',
            description: 'Box shadow',
        },
        headerColor: {
            type: 'color',
            default: '#e2e8f0',
            description: 'Header background color',
        },
        titleColor: {
            type: 'color',
            default: '#1e293b',
            description: 'Title text color',
        },
        iconColor: {
            type: 'color',
            default: '#6366f1',
            description: 'Icon color',
        },
        hoverBorderColor: {
            type: 'color',
            default: '#6366f1',
            description: 'Border color on hover',
        },
        activeBorderColor: {
            type: 'color',
            default: '#4f46e5',
            description: 'Border color when active',
        },
        previewBgColor: {
            type: 'color',
            default: '#ffffff',
            description: 'Preview area background',
        },
        emptyStateColor: {
            type: 'color',
            default: '#94a3b8',
            description: 'Color for empty state placeholder',
        },
    },
    bindingsSchema: {
        targetViewId: {
            type: 'binding',
            bindingType: 'state',
            valueType: 'string',
            description: 'Dynamic target view binding',
        },
        title: {
            type: 'binding',
            bindingType: 'state',
            valueType: 'string',
            description: 'Dynamic title binding',
        },
        badge: {
            type: 'binding',
            bindingType: 'expression',
            valueType: 'string',
            description: 'Dynamic badge (e.g., child count)',
        },
        onNavigate: {
            type: 'action',
            description: 'Custom navigation action override',
        },
        onPreviewClick: {
            type: 'action',
            description: 'Action when preview is clicked',
        },
    },
    capabilities: {
        resizable: true,
        droppable: true,
        textEditable: true,
        connectable: true,
        styleable: true,
        bindable: true,
        lockable: true,
        copyable: true,
        drillable: true,
    },
    constraints: {
        minWidth: 120,
        maxWidth: 800,
        minHeight: 100,
        maxHeight: 600,
        acceptsChildren: true,
        snapToGrid: 20,
    },
    defaults: {
        props: {
            title: 'Sub-Canvas',
            showPreview: true,
            previewScale: 0.1,
            navigationMode: 'drill-down',
            icon: 'layers',
            autoSync: true,
        },
        style: {
            backgroundColor: '#f8fafc',
            borderColor: '#cbd5e1',
            borderWidth: 2,
            borderRadius: 12,
            borderStyle: 'dashed',
            shadow: 'md',
            headerColor: '#e2e8f0',
            titleColor: '#1e293b',
            iconColor: '#6366f1',
            hoverBorderColor: '#6366f1',
            activeBorderColor: '#4f46e5',
            previewBgColor: '#ffffff',
            emptyStateColor: '#94a3b8',
        },
        width: 280,
        height: 200,
    },
    modality: 'mixed',
    platforms: ['web', 'desktop'],
    platformNotes:
        'Mobile navigation may use different UX patterns (e.g., push navigation instead of drill-down)',
};

// ============================================================================
// Portal Link (Lightweight Alternative)
// ============================================================================

export const PortalLinkContract: ArtifactContract = {
    identity: {
        artifactId: 'canvas-portal-link-001',
        kind: 'canvas:portal-link',
        version: '1.0.0',
        name: 'Portal Link',
        category: 'Navigation',
        tags: ['portal', 'link', 'navigation', 'reference'],
        icon: 'link',
        description: 'A lightweight link to another canvas without preview',
    },
    propsSchema: {
        label: {
            type: 'string',
            default: 'Link to Canvas',
            description: 'Link text',
        },
        targetViewId: {
            type: 'string',
            required: true,
            description: 'ID of the target view/canvas',
        },
        navigationMode: {
            type: 'enum',
            values: ['drill-down', 'popup', 'side-panel', 'new-tab'],
            default: 'drill-down',
            description: 'Navigation mode',
        },
        icon: {
            type: 'string',
            default: 'external-link',
            description: 'Icon to display',
        },
        tooltip: {
            type: 'string',
            description: 'Hover tooltip text',
        },
    },
    styleSchema: {
        backgroundColor: {
            type: 'color',
            default: 'transparent',
            description: 'Background color',
        },
        textColor: {
            type: 'color',
            default: '#6366f1',
            description: 'Text color',
        },
        hoverTextColor: {
            type: 'color',
            default: '#4f46e5',
            description: 'Text color on hover',
        },
        iconColor: {
            type: 'color',
            default: '#6366f1',
            description: 'Icon color',
        },
        underline: {
            type: 'boolean',
            default: true,
            description: 'Show underline',
        },
        fontSize: {
            type: 'number',
            min: 10,
            max: 32,
            default: 14,
            unit: 'px',
            description: 'Font size',
        },
        fontWeight: {
            type: 'enum',
            values: ['normal', 'medium', 'semibold', 'bold'],
            default: 'medium',
            description: 'Font weight',
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
        drillable: true,
    },
    constraints: {
        minWidth: 50,
        maxWidth: 400,
        minHeight: 24,
        maxHeight: 48,
        acceptsChildren: false,
        snapToGrid: 8,
    },
    defaults: {
        props: {
            label: 'Go to Canvas',
            navigationMode: 'drill-down',
            icon: 'external-link',
        },
        style: {
            backgroundColor: 'transparent',
            textColor: '#6366f1',
            hoverTextColor: '#4f46e5',
            iconColor: '#6366f1',
            underline: true,
            fontSize: 14,
            fontWeight: 'medium',
        },
        width: 150,
        height: 32,
    },
    modality: 'visual',
    platforms: ['web', 'desktop', 'mobile'],
};

// ============================================================================
// Breadcrumb (Navigation Aid)
// ============================================================================

export const BreadcrumbContract: ArtifactContract = {
    identity: {
        artifactId: 'canvas-breadcrumb-001',
        kind: 'canvas:breadcrumb',
        version: '1.0.0',
        name: 'Breadcrumb',
        category: 'Navigation',
        tags: ['breadcrumb', 'navigation', 'path', 'hierarchy'],
        icon: 'chevron-right',
        description: 'Shows the navigation path in a portal hierarchy',
    },
    propsSchema: {
        showHome: {
            type: 'boolean',
            default: true,
            description: 'Show home icon at start',
        },
        separator: {
            type: 'enum',
            values: ['chevron', 'slash', 'arrow', 'dot'],
            default: 'chevron',
            description: 'Separator between items',
        },
        maxItems: {
            type: 'number',
            min: 2,
            max: 10,
            default: 5,
            description: 'Maximum items before collapsing',
        },
        collapsible: {
            type: 'boolean',
            default: true,
            description: 'Collapse middle items when too long',
        },
    },
    styleSchema: {
        backgroundColor: {
            type: 'color',
            default: 'transparent',
            description: 'Background color',
        },
        textColor: {
            type: 'color',
            default: '#64748b',
            description: 'Text color',
        },
        activeTextColor: {
            type: 'color',
            default: '#1e293b',
            description: 'Active (last) item color',
        },
        hoverTextColor: {
            type: 'color',
            default: '#6366f1',
            description: 'Hover text color',
        },
        separatorColor: {
            type: 'color',
            default: '#cbd5e1',
            description: 'Separator color',
        },
        fontSize: {
            type: 'number',
            min: 10,
            max: 18,
            default: 14,
            unit: 'px',
            description: 'Font size',
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
        minWidth: 100,
        maxWidth: 600,
        minHeight: 24,
        maxHeight: 48,
        acceptsChildren: false,
        snapToGrid: 8,
    },
    defaults: {
        props: {
            showHome: true,
            separator: 'chevron',
            maxItems: 5,
            collapsible: true,
        },
        style: {
            backgroundColor: 'transparent',
            textColor: '#64748b',
            activeTextColor: '#1e293b',
            hoverTextColor: '#6366f1',
            separatorColor: '#cbd5e1',
            fontSize: 14,
        },
        width: 300,
        height: 32,
    },
    modality: 'visual',
    platforms: ['web', 'desktop', 'mobile'],
};

// ============================================================================
// Export All Portal Components
// ============================================================================

export const PORTAL_NODES: ArtifactContract[] = [
    PortalNodeContract,
    PortalLinkContract,
    BreadcrumbContract,
];

/**
 * Register all portal nodes with the registry
 */
export function registerPortalNodes(
    registry: { register: (c: ArtifactContract, o?: { source: string }) => void }
): void {
    for (const contract of PORTAL_NODES) {
        registry.register(contract, { source: 'core' });
    }
}

/**
 * Default portal node export
 */
export default PortalNodeContract;
