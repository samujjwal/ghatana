/**
 * Canvas Mode Node Registries
 * 
 * Defines available node types, configurations, and factories
 * for each canvas mode. Central registry for mode-specific nodes.
 * 
 * @doc.type service
 * @doc.purpose Node type registration for canvas modes
 * @doc.layer product
 * @doc.pattern Registry Pattern
 */

import type { CanvasMode } from '../../../types/canvasMode';

// ============================================================================
// Types
// ============================================================================

/**
 * Node configuration for a canvas mode
 */
export interface ModeNodeConfig {
    /** Node type identifier */
    type: string;
    /** Display label */
    label: string;
    /** Description */
    description: string;
    /** Icon name (Material Icons) */
    icon: string;
    /** Default width */
    defaultWidth?: number;
    /** Default height */
    defaultHeight?: number;
    /** Color for the node */
    color: string;
    /** Whether this node can have children */
    canHaveChildren?: boolean;
    /** Allowed connection types */
    allowedConnections?: string[];
    /** Default properties */
    defaultProps?: Record<string, unknown>;
}

/**
 * Mode node registry entry
 */
export interface ModeNodeRegistry {
    mode: CanvasMode;
    nodes: ModeNodeConfig[];
    defaultNode: string;
    groupNode?: string;
}

// ============================================================================
// Node Registries by Mode
// ============================================================================

export const brainstormNodes: ModeNodeRegistry = {
    mode: 'brainstorm',
    defaultNode: 'idea',
    groupNode: 'cluster',
    nodes: [
        {
            type: 'idea',
            label: 'Idea',
            description: 'Capture a new idea or concept',
            icon: 'Lightbulb',
            color: 'amber',
            defaultWidth: 200,
            allowedConnections: ['idea', 'note', 'question', 'cluster'],
        },
        {
            type: 'note',
            label: 'Note',
            description: 'Add a note or comment',
            icon: 'StickyNote2',
            color: 'yellow',
            defaultWidth: 180,
            allowedConnections: ['idea', 'note', 'question'],
        },
        {
            type: 'question',
            label: 'Question',
            description: 'Pose a question to explore',
            icon: 'Help',
            color: 'purple',
            defaultWidth: 200,
            allowedConnections: ['idea', 'note'],
        },
        {
            type: 'cluster',
            label: 'Cluster',
            description: 'Group related ideas together',
            icon: 'Folder',
            color: 'grey',
            defaultWidth: 300,
            defaultHeight: 200,
            canHaveChildren: true,
        },
    ],
};

export const diagramNodes: ModeNodeRegistry = {
    mode: 'diagram',
    defaultNode: 'component',
    groupNode: 'group',
    nodes: [
        {
            type: 'component',
            label: 'Component',
            description: 'A software component or module',
            icon: 'Widgets',
            color: 'blue',
            defaultWidth: 180,
            allowedConnections: ['component', 'service', 'database', 'api', 'queue'],
        },
        {
            type: 'service',
            label: 'Service',
            description: 'A microservice or backend service',
            icon: 'Cloud',
            color: 'indigo',
            defaultWidth: 180,
            allowedConnections: ['component', 'service', 'database', 'api', 'queue'],
        },
        {
            type: 'database',
            label: 'Database',
            description: 'A database or data store',
            icon: 'Storage',
            color: 'green',
            defaultWidth: 160,
            allowedConnections: ['service', 'component'],
        },
        {
            type: 'api',
            label: 'API',
            description: 'An API endpoint or gateway',
            icon: 'Api',
            color: 'orange',
            defaultWidth: 160,
            allowedConnections: ['component', 'service'],
        },
        {
            type: 'queue',
            label: 'Queue',
            description: 'A message queue or event bus',
            icon: 'List',
            color: 'purple',
            defaultWidth: 140,
            allowedConnections: ['service', 'component'],
        },
        {
            type: 'flow',
            label: 'Flow',
            description: 'A data or control flow',
            icon: 'Timeline',
            color: 'cyan',
            defaultWidth: 160,
        },
        {
            type: 'decision',
            label: 'Decision',
            description: 'A decision point or condition',
            icon: 'CallSplit',
            color: 'amber',
            defaultWidth: 120,
        },
        {
            type: 'entity',
            label: 'Entity',
            description: 'A data entity or model',
            icon: 'Description',
            color: 'teal',
            defaultWidth: 180,
            allowedConnections: ['entity', 'database'],
        },
    ],
};

export const designNodes: ModeNodeRegistry = {
    mode: 'design',
    defaultNode: 'screen',
    groupNode: 'layout',
    nodes: [
        {
            type: 'screen',
            label: 'Screen',
            description: 'A UI screen or page',
            icon: 'PhoneIphone',
            color: 'purple',
            defaultWidth: 375,
            defaultHeight: 667,
            canHaveChildren: true,
        },
        {
            type: 'component',
            label: 'Component',
            description: 'A reusable UI component',
            icon: 'ViewModule',
            color: 'blue',
            defaultWidth: 200,
            canHaveChildren: true,
        },
        {
            type: 'layout',
            label: 'Layout',
            description: 'A layout container',
            icon: 'GridView',
            color: 'grey',
            defaultWidth: 400,
            defaultHeight: 300,
            canHaveChildren: true,
        },
        {
            type: 'widget',
            label: 'Widget',
            description: 'A UI widget (button, input, etc.)',
            icon: 'SmartButton',
            color: 'green',
            defaultWidth: 120,
        },
        {
            type: 'navigation',
            label: 'Navigation',
            description: 'Navigation component',
            icon: 'Menu',
            color: 'indigo',
            defaultWidth: 200,
        },
        {
            type: 'interaction',
            label: 'Interaction',
            description: 'User interaction point',
            icon: 'TouchApp',
            color: 'orange',
            defaultWidth: 100,
        },
    ],
};

export const codeNodes: ModeNodeRegistry = {
    mode: 'code',
    defaultNode: 'function',
    nodes: [
        {
            type: 'function',
            label: 'Function',
            description: 'A function or method',
            icon: 'Functions',
            color: 'green',
            defaultWidth: 220,
            defaultProps: { language: 'typescript' },
        },
        {
            type: 'class',
            label: 'Class',
            description: 'A class or interface',
            icon: 'Class',
            color: 'blue',
            defaultWidth: 240,
            canHaveChildren: true,
        },
        {
            type: 'module',
            label: 'Module',
            description: 'A module or package',
            icon: 'FolderOpen',
            color: 'amber',
            defaultWidth: 260,
            canHaveChildren: true,
        },
        {
            type: 'endpoint',
            label: 'API Endpoint',
            description: 'A REST or GraphQL endpoint',
            icon: 'Http',
            color: 'orange',
            defaultWidth: 200,
            defaultProps: { method: 'GET' },
        },
        {
            type: 'hook',
            label: 'Hook',
            description: 'A React hook or lifecycle method',
            icon: 'Link',
            color: 'cyan',
            defaultWidth: 180,
        },
        {
            type: 'type',
            label: 'Type',
            description: 'A type definition or interface',
            icon: 'Code',
            color: 'purple',
            defaultWidth: 200,
        },
        {
            type: 'config',
            label: 'Config',
            description: 'A configuration file or constant',
            icon: 'Settings',
            color: 'grey',
            defaultWidth: 180,
        },
    ],
};

export const testNodes: ModeNodeRegistry = {
    mode: 'test',
    defaultNode: 'test-case',
    groupNode: 'test-suite',
    nodes: [
        {
            type: 'test-suite',
            label: 'Test Suite',
            description: 'A collection of test cases',
            icon: 'Folder',
            color: 'teal',
            defaultWidth: 280,
            canHaveChildren: true,
        },
        {
            type: 'test-case',
            label: 'Test Case',
            description: 'An individual test case',
            icon: 'Science',
            color: 'green',
            defaultWidth: 200,
        },
        {
            type: 'fixture',
            label: 'Fixture',
            description: 'Test fixture or setup data',
            icon: 'Construction',
            color: 'amber',
            defaultWidth: 180,
        },
        {
            type: 'mock',
            label: 'Mock',
            description: 'A mock or stub object',
            icon: 'ContentCopy',
            color: 'purple',
            defaultWidth: 160,
        },
        {
            type: 'assertion',
            label: 'Assertion',
            description: 'A test assertion',
            icon: 'CheckCircle',
            color: 'blue',
            defaultWidth: 160,
        },
        {
            type: 'coverage',
            label: 'Coverage',
            description: 'Code coverage indicator',
            icon: 'PieChart',
            color: 'grey',
            defaultWidth: 140,
        },
    ],
};

export const deployNodes: ModeNodeRegistry = {
    mode: 'deploy',
    defaultNode: 'environment',
    groupNode: 'pipeline',
    nodes: [
        {
            type: 'environment',
            label: 'Environment',
            description: 'A deployment environment (dev, staging, prod)',
            icon: 'Layers',
            color: 'indigo',
            defaultWidth: 220,
            canHaveChildren: true,
        },
        {
            type: 'service',
            label: 'Service',
            description: 'A deployed service instance',
            icon: 'Cloud',
            color: 'blue',
            defaultWidth: 180,
        },
        {
            type: 'pipeline',
            label: 'Pipeline',
            description: 'A CI/CD pipeline',
            icon: 'AccountTree',
            color: 'green',
            defaultWidth: 260,
            canHaveChildren: true,
        },
        {
            type: 'stage',
            label: 'Stage',
            description: 'A pipeline stage',
            icon: 'PlayArrow',
            color: 'cyan',
            defaultWidth: 160,
        },
        {
            type: 'artifact',
            label: 'Artifact',
            description: 'A build artifact or container image',
            icon: 'Inventory2',
            color: 'amber',
            defaultWidth: 160,
        },
        {
            type: 'secret',
            label: 'Secret',
            description: 'A secret or credential',
            icon: 'VpnKey',
            color: 'red',
            defaultWidth: 140,
        },
        {
            type: 'resource',
            label: 'Resource',
            description: 'A cloud resource (VM, container, etc.)',
            icon: 'Memory',
            color: 'purple',
            defaultWidth: 180,
        },
    ],
};

export const observeNodes: ModeNodeRegistry = {
    mode: 'observe',
    defaultNode: 'metric',
    groupNode: 'dashboard',
    nodes: [
        {
            type: 'metric',
            label: 'Metric',
            description: 'A performance or business metric',
            icon: 'ShowChart',
            color: 'rose',
            defaultWidth: 180,
        },
        {
            type: 'alert',
            label: 'Alert',
            description: 'An alert or notification rule',
            icon: 'NotificationsActive',
            color: 'red',
            defaultWidth: 180,
        },
        {
            type: 'dashboard',
            label: 'Dashboard',
            description: 'A monitoring dashboard',
            icon: 'Dashboard',
            color: 'blue',
            defaultWidth: 300,
            defaultHeight: 200,
            canHaveChildren: true,
        },
        {
            type: 'log',
            label: 'Log',
            description: 'A log source or stream',
            icon: 'Subject',
            color: 'grey',
            defaultWidth: 180,
        },
        {
            type: 'trace',
            label: 'Trace',
            description: 'A distributed trace',
            icon: 'Timeline',
            color: 'purple',
            defaultWidth: 200,
        },
        {
            type: 'insight',
            label: 'Insight',
            description: 'An AI-generated insight or recommendation',
            icon: 'AutoAwesome',
            color: 'amber',
            defaultWidth: 220,
        },
    ],
};

// ============================================================================
// Registry Map
// ============================================================================

export const NODE_REGISTRIES: Record<CanvasMode, ModeNodeRegistry> = {
    brainstorm: brainstormNodes,
    diagram: diagramNodes,
    design: designNodes,
    code: codeNodes,
    test: testNodes,
    deploy: deployNodes,
    observe: observeNodes,
};

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Get node registry for a canvas mode
 */
export function getNodeRegistry(mode: CanvasMode): ModeNodeRegistry {
    return NODE_REGISTRIES[mode];
}

/**
 * Get all nodes for a canvas mode
 */
export function getNodesForMode(mode: CanvasMode): ModeNodeConfig[] {
    return NODE_REGISTRIES[mode].nodes;
}

/**
 * Get a specific node configuration
 */
export function getNodeConfig(mode: CanvasMode, nodeType: string): ModeNodeConfig | undefined {
    return NODE_REGISTRIES[mode].nodes.find(n => n.type === nodeType);
}

/**
 * Get the default node type for a canvas mode
 */
export function getDefaultNodeType(mode: CanvasMode): string {
    return NODE_REGISTRIES[mode].defaultNode;
}

/**
 * Get the group node type for a canvas mode
 */
export function getGroupNodeType(mode: CanvasMode): string | undefined {
    return NODE_REGISTRIES[mode].groupNode;
}

/**
 * Check if a node type exists in a mode
 */
export function nodeTypeExistsInMode(mode: CanvasMode, nodeType: string): boolean {
    return NODE_REGISTRIES[mode].nodes.some(n => n.type === nodeType);
}
