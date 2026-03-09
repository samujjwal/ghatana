/**
 * Built-in Diagram Node Definitions
 *
 * Artifact contracts for diagram and flowchart nodes.
 * Supports flowcharts, state diagrams, ER diagrams, etc.
 *
 * @doc.type definitions
 * @doc.purpose Diagram node artifact contracts
 * @doc.layer core
 * @doc.pattern Data-Driven Definition
 */

import type { ArtifactContract } from '../../model/contracts';

// ============================================================================
// Flowchart Start/End Node
// ============================================================================

export const FlowchartTerminalContract: ArtifactContract = {
    identity: {
        artifactId: 'diagram-terminal-001',
        kind: 'diagram:terminal',
        version: '1.0.0',
        name: 'Terminal',
        category: 'Flowchart',
        tags: ['flowchart', 'start', 'end', 'terminal'],
        icon: 'circle',
        description: 'Start or end point of a flowchart',
    },
    propsSchema: {
        label: {
            type: 'string',
            default: 'Start',
            description: 'Terminal label',
        },
        terminalType: {
            type: 'enum',
            values: ['start', 'end'],
            default: 'start',
            description: 'Terminal type',
        },
    },
    styleSchema: {
        fillColor: {
            type: 'color',
            default: '#10b981',
            description: 'Fill color',
        },
        strokeColor: {
            type: 'color',
            default: '#059669',
            description: 'Stroke color',
        },
        strokeWidth: {
            type: 'number',
            min: 1,
            max: 8,
            default: 2,
            unit: 'px',
            description: 'Stroke width',
        },
        textColor: {
            type: 'color',
            default: '#ffffff',
            description: 'Text color',
        },
    },
    capabilities: {
        resizable: true,
        droppable: false,
        textEditable: true,
        connectable: true,
        styleable: true,
        bindable: false,
        lockable: true,
        copyable: true,
        drillable: false,
    },
    constraints: {
        minWidth: 60,
        maxWidth: 200,
        minHeight: 40,
        maxHeight: 80,
        acceptsChildren: false,
        aspectRatio: undefined,
        snapToGrid: 20,
    },
    defaults: {
        props: {
            label: 'Start',
            terminalType: 'start',
        },
        style: {
            fillColor: '#10b981',
            strokeColor: '#059669',
            strokeWidth: 2,
            textColor: '#ffffff',
        },
        width: 100,
        height: 50,
    },
    modality: 'diagram',
    platforms: ['web', 'desktop'],
};

// ============================================================================
// Flowchart Process Node
// ============================================================================

export const FlowchartProcessContract: ArtifactContract = {
    identity: {
        artifactId: 'diagram-process-001',
        kind: 'diagram:process',
        version: '1.0.0',
        name: 'Process',
        category: 'Flowchart',
        tags: ['flowchart', 'process', 'action', 'step'],
        icon: 'square',
        description: 'A process or action step in a flowchart',
    },
    propsSchema: {
        label: {
            type: 'string',
            default: 'Process',
            description: 'Process label',
        },
        description: {
            type: 'string',
            description: 'Optional description',
        },
    },
    styleSchema: {
        fillColor: {
            type: 'color',
            default: '#3b82f6',
            description: 'Fill color',
        },
        strokeColor: {
            type: 'color',
            default: '#2563eb',
            description: 'Stroke color',
        },
        strokeWidth: {
            type: 'number',
            min: 1,
            max: 8,
            default: 2,
            unit: 'px',
            description: 'Stroke width',
        },
        textColor: {
            type: 'color',
            default: '#ffffff',
            description: 'Text color',
        },
        cornerRadius: {
            type: 'number',
            min: 0,
            max: 20,
            default: 4,
            unit: 'px',
            description: 'Corner radius',
        },
    },
    capabilities: {
        resizable: true,
        droppable: false,
        textEditable: true,
        connectable: true,
        styleable: true,
        bindable: false,
        lockable: true,
        copyable: true,
        drillable: true,
    },
    constraints: {
        minWidth: 80,
        maxWidth: 400,
        minHeight: 40,
        maxHeight: 200,
        acceptsChildren: false,
        snapToGrid: 20,
    },
    defaults: {
        props: {
            label: 'Process',
        },
        style: {
            fillColor: '#3b82f6',
            strokeColor: '#2563eb',
            strokeWidth: 2,
            textColor: '#ffffff',
            cornerRadius: 4,
        },
        width: 140,
        height: 60,
    },
    modality: 'diagram',
    platforms: ['web', 'desktop'],
};

// ============================================================================
// Flowchart Decision Node
// ============================================================================

export const FlowchartDecisionContract: ArtifactContract = {
    identity: {
        artifactId: 'diagram-decision-001',
        kind: 'diagram:decision',
        version: '1.0.0',
        name: 'Decision',
        category: 'Flowchart',
        tags: ['flowchart', 'decision', 'condition', 'branch'],
        icon: 'diamond',
        description: 'A decision point with yes/no branches',
    },
    propsSchema: {
        condition: {
            type: 'string',
            default: 'Condition?',
            description: 'Decision condition',
        },
        yesLabel: {
            type: 'string',
            default: 'Yes',
            description: 'Label for yes branch',
        },
        noLabel: {
            type: 'string',
            default: 'No',
            description: 'Label for no branch',
        },
    },
    styleSchema: {
        fillColor: {
            type: 'color',
            default: '#f59e0b',
            description: 'Fill color',
        },
        strokeColor: {
            type: 'color',
            default: '#d97706',
            description: 'Stroke color',
        },
        strokeWidth: {
            type: 'number',
            min: 1,
            max: 8,
            default: 2,
            unit: 'px',
            description: 'Stroke width',
        },
        textColor: {
            type: 'color',
            default: '#000000',
            description: 'Text color',
        },
    },
    capabilities: {
        resizable: true,
        droppable: false,
        textEditable: true,
        connectable: true,
        styleable: true,
        bindable: false,
        lockable: true,
        copyable: true,
        drillable: false,
    },
    constraints: {
        minWidth: 80,
        maxWidth: 300,
        minHeight: 60,
        maxHeight: 200,
        acceptsChildren: false,
        aspectRatio: 1.5,
        snapToGrid: 20,
    },
    defaults: {
        props: {
            condition: 'Condition?',
            yesLabel: 'Yes',
            noLabel: 'No',
        },
        style: {
            fillColor: '#f59e0b',
            strokeColor: '#d97706',
            strokeWidth: 2,
            textColor: '#000000',
        },
        width: 120,
        height: 80,
    },
    modality: 'diagram',
    platforms: ['web', 'desktop'],
};

// ============================================================================
// Flowchart IO Node
// ============================================================================

export const FlowchartIOContract: ArtifactContract = {
    identity: {
        artifactId: 'diagram-io-001',
        kind: 'diagram:io',
        version: '1.0.0',
        name: 'Input/Output',
        category: 'Flowchart',
        tags: ['flowchart', 'input', 'output', 'data'],
        icon: 'parallelogram',
        description: 'Data input or output operation',
    },
    propsSchema: {
        label: {
            type: 'string',
            default: 'Data',
            description: 'IO label',
        },
        ioType: {
            type: 'enum',
            values: ['input', 'output'],
            default: 'input',
            description: 'Input or output type',
        },
        dataType: {
            type: 'string',
            description: 'Data type annotation',
        },
    },
    styleSchema: {
        fillColor: {
            type: 'color',
            default: '#8b5cf6',
            description: 'Fill color',
        },
        strokeColor: {
            type: 'color',
            default: '#7c3aed',
            description: 'Stroke color',
        },
        strokeWidth: {
            type: 'number',
            min: 1,
            max: 8,
            default: 2,
            unit: 'px',
            description: 'Stroke width',
        },
        textColor: {
            type: 'color',
            default: '#ffffff',
            description: 'Text color',
        },
        skew: {
            type: 'number',
            min: -30,
            max: 30,
            default: 15,
            unit: 'deg',
            description: 'Parallelogram skew angle',
        },
    },
    capabilities: {
        resizable: true,
        droppable: false,
        textEditable: true,
        connectable: true,
        styleable: true,
        bindable: false,
        lockable: true,
        copyable: true,
        drillable: false,
    },
    constraints: {
        minWidth: 80,
        maxWidth: 300,
        minHeight: 40,
        maxHeight: 120,
        acceptsChildren: false,
        snapToGrid: 20,
    },
    defaults: {
        props: {
            label: 'Input',
            ioType: 'input',
        },
        style: {
            fillColor: '#8b5cf6',
            strokeColor: '#7c3aed',
            strokeWidth: 2,
            textColor: '#ffffff',
            skew: 15,
        },
        width: 120,
        height: 50,
    },
    modality: 'diagram',
    platforms: ['web', 'desktop'],
};

// ============================================================================
// State Machine State
// ============================================================================

export const StateMachineStateContract: ArtifactContract = {
    identity: {
        artifactId: 'diagram-state-001',
        kind: 'diagram:state',
        version: '1.0.0',
        name: 'State',
        category: 'State Machine',
        tags: ['state-machine', 'state', 'node'],
        icon: 'rounded-square',
        description: 'A state in a state machine diagram',
    },
    propsSchema: {
        name: {
            type: 'string',
            default: 'State',
            description: 'State name',
        },
        entry: {
            type: 'string',
            description: 'Entry action',
        },
        exit: {
            type: 'string',
            description: 'Exit action',
        },
        do: {
            type: 'string',
            description: 'Do activity',
        },
        isInitial: {
            type: 'boolean',
            default: false,
            description: 'Is initial state',
        },
        isFinal: {
            type: 'boolean',
            default: false,
            description: 'Is final state',
        },
    },
    styleSchema: {
        fillColor: {
            type: 'color',
            default: '#e2e8f0',
            description: 'Fill color',
        },
        strokeColor: {
            type: 'color',
            default: '#64748b',
            description: 'Stroke color',
        },
        strokeWidth: {
            type: 'number',
            min: 1,
            max: 8,
            default: 2,
            unit: 'px',
            description: 'Stroke width',
        },
        textColor: {
            type: 'color',
            default: '#1e293b',
            description: 'Text color',
        },
        headerColor: {
            type: 'color',
            default: '#cbd5e1',
            description: 'Header background',
        },
        cornerRadius: {
            type: 'number',
            min: 0,
            max: 30,
            default: 12,
            unit: 'px',
            description: 'Corner radius',
        },
    },
    capabilities: {
        resizable: true,
        droppable: true,
        textEditable: true,
        connectable: true,
        styleable: true,
        bindable: false,
        lockable: true,
        copyable: true,
        drillable: true,
    },
    constraints: {
        minWidth: 100,
        maxWidth: 400,
        minHeight: 60,
        maxHeight: 300,
        acceptsChildren: ['diagram:state'],
        snapToGrid: 20,
    },
    defaults: {
        props: {
            name: 'State',
            isInitial: false,
            isFinal: false,
        },
        style: {
            fillColor: '#e2e8f0',
            strokeColor: '#64748b',
            strokeWidth: 2,
            textColor: '#1e293b',
            headerColor: '#cbd5e1',
            cornerRadius: 12,
        },
        width: 140,
        height: 80,
    },
    modality: 'diagram',
    platforms: ['web', 'desktop'],
};

// ============================================================================
// ER Entity
// ============================================================================

export const EREntityContract: ArtifactContract = {
    identity: {
        artifactId: 'diagram-entity-001',
        kind: 'diagram:entity',
        version: '1.0.0',
        name: 'Entity',
        category: 'ER Diagram',
        tags: ['er-diagram', 'entity', 'table', 'database'],
        icon: 'table',
        description: 'An entity in an ER diagram',
    },
    propsSchema: {
        name: {
            type: 'string',
            default: 'Entity',
            required: true,
            description: 'Entity name',
        },
        attributes: {
            type: 'array',
            items: {
                type: 'object',
                properties: {
                    name: { type: 'string', required: true },
                    type: { type: 'string', default: 'string' },
                    isPrimary: { type: 'boolean', default: false },
                    isForeign: { type: 'boolean', default: false },
                    isNullable: { type: 'boolean', default: true },
                },
            },
            description: 'Entity attributes',
        },
        stereotypes: {
            type: 'array',
            items: { type: 'string' },
            description: 'UML stereotypes',
        },
    },
    styleSchema: {
        headerColor: {
            type: 'color',
            default: '#6366f1',
            description: 'Header background',
        },
        bodyColor: {
            type: 'color',
            default: '#ffffff',
            description: 'Body background',
        },
        strokeColor: {
            type: 'color',
            default: '#4f46e5',
            description: 'Border color',
        },
        strokeWidth: {
            type: 'number',
            min: 1,
            max: 4,
            default: 1,
            unit: 'px',
            description: 'Border width',
        },
        headerTextColor: {
            type: 'color',
            default: '#ffffff',
            description: 'Header text color',
        },
        bodyTextColor: {
            type: 'color',
            default: '#1f2937',
            description: 'Body text color',
        },
    },
    capabilities: {
        resizable: true,
        droppable: false,
        textEditable: true,
        connectable: true,
        styleable: true,
        bindable: false,
        lockable: true,
        copyable: true,
        drillable: false,
    },
    constraints: {
        minWidth: 120,
        maxWidth: 400,
        minHeight: 80,
        acceptsChildren: false,
        snapToGrid: 20,
    },
    defaults: {
        props: {
            name: 'Entity',
            attributes: [
                { name: 'id', type: 'uuid', isPrimary: true, isNullable: false },
                { name: 'name', type: 'string', isNullable: false },
                { name: 'created_at', type: 'timestamp', isNullable: false },
            ],
        },
        style: {
            headerColor: '#6366f1',
            bodyColor: '#ffffff',
            strokeColor: '#4f46e5',
            strokeWidth: 1,
            headerTextColor: '#ffffff',
            bodyTextColor: '#1f2937',
        },
        width: 180,
        height: 120,
    },
    modality: 'diagram',
    platforms: ['web', 'desktop'],
};

// ============================================================================
// Connector (Edge)
// ============================================================================

export const ConnectorContract: ArtifactContract = {
    identity: {
        artifactId: 'diagram-connector-001',
        kind: 'diagram:connector',
        version: '1.0.0',
        name: 'Connector',
        category: 'Connectors',
        tags: ['connector', 'edge', 'arrow', 'line'],
        icon: 'arrow-right',
        description: 'A connector between diagram nodes',
    },
    propsSchema: {
        label: {
            type: 'string',
            description: 'Connector label',
        },
        connectorType: {
            type: 'enum',
            values: ['straight', 'orthogonal', 'curved', 'bezier'],
            default: 'orthogonal',
            description: 'Connector routing type',
        },
        sourceArrow: {
            type: 'enum',
            values: ['none', 'arrow', 'diamond', 'circle', 'square'],
            default: 'none',
            description: 'Arrow at source end',
        },
        targetArrow: {
            type: 'enum',
            values: ['none', 'arrow', 'diamond', 'circle', 'square', 'open-arrow'],
            default: 'arrow',
            description: 'Arrow at target end',
        },
        lineStyle: {
            type: 'enum',
            values: ['solid', 'dashed', 'dotted'],
            default: 'solid',
            description: 'Line style',
        },
    },
    styleSchema: {
        strokeColor: {
            type: 'color',
            default: '#64748b',
            description: 'Line color',
        },
        strokeWidth: {
            type: 'number',
            min: 1,
            max: 8,
            default: 2,
            unit: 'px',
            description: 'Line width',
        },
        labelColor: {
            type: 'color',
            default: '#374151',
            description: 'Label text color',
        },
        labelBgColor: {
            type: 'color',
            default: '#ffffff',
            description: 'Label background color',
        },
    },
    capabilities: {
        resizable: false,
        droppable: false,
        textEditable: true,
        connectable: false,
        styleable: true,
        bindable: false,
        lockable: true,
        copyable: true,
        drillable: false,
    },
    constraints: {
        acceptsChildren: false,
    },
    defaults: {
        props: {
            connectorType: 'orthogonal',
            sourceArrow: 'none',
            targetArrow: 'arrow',
            lineStyle: 'solid',
        },
        style: {
            strokeColor: '#64748b',
            strokeWidth: 2,
            labelColor: '#374151',
            labelBgColor: '#ffffff',
        },
        width: 0,
        height: 0,
    },
    modality: 'diagram',
    platforms: ['web', 'desktop'],
};

// ============================================================================
// Swimlane
// ============================================================================

export const SwimlaneContract: ArtifactContract = {
    identity: {
        artifactId: 'diagram-swimlane-001',
        kind: 'diagram:swimlane',
        version: '1.0.0',
        name: 'Swimlane',
        category: 'Layout',
        tags: ['swimlane', 'lane', 'partition', 'layout'],
        icon: 'columns',
        description: 'A swimlane for organizing diagram content',
    },
    propsSchema: {
        title: {
            type: 'string',
            default: 'Lane',
            description: 'Swimlane title',
        },
        orientation: {
            type: 'enum',
            values: ['horizontal', 'vertical'],
            default: 'vertical',
            description: 'Swimlane orientation',
        },
        collapsed: {
            type: 'boolean',
            default: false,
            description: 'Whether collapsed',
        },
    },
    styleSchema: {
        headerColor: {
            type: 'color',
            default: '#f1f5f9',
            description: 'Header background',
        },
        bodyColor: {
            type: 'color',
            default: '#fafafa',
            description: 'Body background',
        },
        strokeColor: {
            type: 'color',
            default: '#e2e8f0',
            description: 'Border color',
        },
        strokeWidth: {
            type: 'number',
            min: 1,
            max: 4,
            default: 1,
            unit: 'px',
            description: 'Border width',
        },
        titleColor: {
            type: 'color',
            default: '#475569',
            description: 'Title text color',
        },
    },
    capabilities: {
        resizable: true,
        droppable: true,
        textEditable: true,
        connectable: false,
        styleable: true,
        bindable: false,
        lockable: true,
        copyable: true,
        drillable: true,
    },
    constraints: {
        minWidth: 150,
        minHeight: 200,
        acceptsChildren: true,
        snapToGrid: 20,
    },
    defaults: {
        props: {
            title: 'Swimlane',
            orientation: 'vertical',
            collapsed: false,
        },
        style: {
            headerColor: '#f1f5f9',
            bodyColor: '#fafafa',
            strokeColor: '#e2e8f0',
            strokeWidth: 1,
            titleColor: '#475569',
        },
        width: 250,
        height: 400,
    },
    modality: 'diagram',
    platforms: ['web', 'desktop'],
};

// ============================================================================
// Export All Diagram Components
// ============================================================================

export const DIAGRAM_NODES: ArtifactContract[] = [
    FlowchartTerminalContract,
    FlowchartProcessContract,
    FlowchartDecisionContract,
    FlowchartIOContract,
    StateMachineStateContract,
    EREntityContract,
    ConnectorContract,
    SwimlaneContract,
];

/**
 * Register all diagram nodes with the registry
 */
export function registerDiagramNodes(
    registry: { register: (c: ArtifactContract, o?: { source: string }) => void }
): void {
    for (const contract of DIAGRAM_NODES) {
        registry.register(contract, { source: 'core' });
    }
}
