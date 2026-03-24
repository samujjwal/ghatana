/**
 * @doc.type configuration
 * @doc.purpose Predefined persona canvas configurations for YAPPC journeys
 * @doc.layer product
 * @doc.pattern Configuration
 */

import type {
    PersonaCanvasConfig,
    PersonaType,
} from '../types/persona';

/**
 * PM Persona Configuration (Journey 1.1: Requirements Handoff)
 * Focus: Roadmap view with timeline, requirement tracking, stakeholder management
 */
export const PM_CANVAS_CONFIG: PersonaCanvasConfig = {
    type: 'PM',
    name: 'Product Manager',
    viewMode: 'roadmap',
    layout: {
        algorithm: 'timeline',
        direction: 'LR', // Left-to-right for timeline
        spacing: { x: 200, y: 100 },
        autoLayout: true,
    },
    toolbar: {
        sections: {
            grouping: true,
            testGeneration: false,
            deployment: false,
            documentation: true,
            codeGen: false,
        },
        customActions: [
            {
                id: 'create-epic',
                label: 'Create Epic',
                icon: 'flag',
                action: 'createEpic',
            },
            {
                id: 'handoff-dev',
                label: 'Handoff to Dev',
                icon: 'send',
                action: 'handoffToDev',
            },
            {
                id: 'view-roadmap',
                label: 'View Roadmap',
                icon: 'timeline',
                action: 'viewRoadmap',
            },
        ],
    },
    nodeStyle: {
        colors: {
            default: '#E3F2FD', // Light blue
            selected: '#2196F3', // Blue
            grouped: '#FFA726', // Orange (pending)
            testing: '#FFEB3B', // Yellow
            deployed: '#66BB6A', // Green (ready)
        },
        shape: 'rounded',
        showIcons: true,
        showBadges: true,
    },
    panels: {
        left: [
            {
                id: 'requirements',
                title: 'Requirements',
                component: 'RequirementsPanel',
                defaultOpen: true,
            },
            {
                id: 'stakeholders',
                title: 'Stakeholders',
                component: 'StakeholdersPanel',
                defaultOpen: false,
            },
        ],
        right: [
            {
                id: 'properties',
                title: 'Properties',
                component: 'PropertiesPanel',
                defaultOpen: true,
            },
            {
                id: 'timeline',
                title: 'Timeline',
                component: 'TimelinePanel',
                defaultOpen: false,
            },
        ],
        bottom: {
            id: 'activity',
            title: 'Activity Feed',
            component: 'ActivityPanel',
            defaultHeight: 200,
        },
    },
    features: {
        grouping: true,
        testGeneration: false,
        codeGeneration: false,
        deployment: false,
        versioning: true,
        collaboration: true,
    },
};

/**
 * Architect Persona Configuration (Journey 2.1: System Design)
 * Focus: Layered architecture view, component design, tech stack decisions
 */
export const ARCHITECT_CANVAS_CONFIG: PersonaCanvasConfig = {
    type: 'Architect',
    name: 'Solution Architect',
    viewMode: 'system-design',
    layout: {
        algorithm: 'hierarchical',
        direction: 'TB', // Top-to-bottom for layers
        spacing: { x: 150, y: 120 },
        autoLayout: true,
    },
    toolbar: {
        sections: {
            grouping: true,
            testGeneration: false,
            deployment: false,
            documentation: true,
            codeGen: true,
        },
        customActions: [
            {
                id: 'add-layer',
                label: 'Add Layer',
                icon: 'layers',
                action: 'addArchitectureLayer',
            },
            {
                id: 'generate-adr',
                label: 'Generate ADR',
                icon: 'description',
                action: 'generateADR',
            },
            {
                id: 'export-diagram',
                label: 'Export C4',
                icon: 'download',
                action: 'exportC4Diagram',
            },
        ],
    },
    nodeStyle: {
        colors: {
            default: '#F3E5F5', // Light purple
            selected: '#9C27B0', // Purple
            grouped: '#BA68C8', // Medium purple
            testing: '#FFB74D', // Orange
            deployed: '#81C784', // Light green
        },
        shape: 'rectangle',
        showIcons: true,
        showBadges: true,
    },
    panels: {
        left: [
            {
                id: 'components',
                title: 'Components',
                component: 'ComponentsPanel',
                defaultOpen: true,
            },
            {
                id: 'tech-stack',
                title: 'Tech Stack',
                component: 'TechStackPanel',
                defaultOpen: false,
            },
        ],
        right: [
            {
                id: 'properties',
                title: 'Properties',
                component: 'PropertiesPanel',
                defaultOpen: true,
            },
            {
                id: 'dependencies',
                title: 'Dependencies',
                component: 'DependenciesPanel',
                defaultOpen: true,
            },
        ],
        bottom: {
            id: 'adrs',
            title: 'Architecture Decisions',
            component: 'ADRPanel',
            defaultHeight: 250,
        },
    },
    features: {
        grouping: true,
        testGeneration: false,
        codeGeneration: true,
        deployment: false,
        versioning: true,
        collaboration: true,
    },
};

/**
 * Developer Persona Configuration (Journey 3.1: Code Implementation)
 * Focus: Code-centric view, file tree, implementation status
 */
export const DEVELOPER_CANVAS_CONFIG: PersonaCanvasConfig = {
    type: 'Developer',
    name: 'Software Developer',
    viewMode: 'code-tree',
    layout: {
        algorithm: 'dagre',
        direction: 'TB',
        spacing: { x: 120, y: 80 },
        autoLayout: false, // Developers prefer manual control
    },
    toolbar: {
        sections: {
            grouping: true,
            testGeneration: false,
            deployment: false,
            documentation: false,
            codeGen: true,
        },
        customActions: [
            {
                id: 'scaffold-code',
                label: 'Scaffold Code',
                icon: 'code',
                action: 'scaffoldCode',
            },
            {
                id: 'open-in-vscode',
                label: 'Open in VS Code',
                icon: 'launch',
                action: 'openInVSCode',
            },
            {
                id: 'run-locally',
                label: 'Run Locally',
                icon: 'play_arrow',
                action: 'runLocally',
            },
        ],
    },
    nodeStyle: {
        colors: {
            default: '#E8F5E9', // Light green
            selected: '#4CAF50', // Green
            grouped: '#66BB6A', // Medium green
            testing: '#FDD835', // Yellow
            deployed: '#1976D2', // Blue
        },
        shape: 'rounded',
        showIcons: true,
        showBadges: true,
    },
    panels: {
        left: [
            {
                id: 'file-tree',
                title: 'File Explorer',
                component: 'FileTreePanel',
                defaultOpen: true,
            },
            {
                id: 'git-status',
                title: 'Git Status',
                component: 'GitStatusPanel',
                defaultOpen: false,
            },
        ],
        right: [
            {
                id: 'code-editor',
                title: 'Code Preview',
                component: 'CodeEditorPanel',
                defaultOpen: true,
            },
            {
                id: 'properties',
                title: 'Properties',
                component: 'PropertiesPanel',
                defaultOpen: false,
            },
        ],
        bottom: {
            id: 'terminal',
            title: 'Terminal Output',
            component: 'TerminalPanel',
            defaultHeight: 200,
        },
    },
    features: {
        grouping: true,
        testGeneration: false,
        codeGeneration: true,
        deployment: false,
        versioning: true,
        collaboration: true,
    },
};

/**
 * QA Persona Configuration (Journey 4.1: Test Generation & Execution)
 * Focus: Test coverage view, execution status, quality metrics
 */
export const QA_CANVAS_CONFIG: PersonaCanvasConfig = {
    type: 'QA',
    name: 'Quality Assurance Engineer',
    viewMode: 'test-coverage',
    layout: {
        algorithm: 'grid',
        direction: 'TB',
        spacing: { x: 140, y: 100 },
        autoLayout: true,
    },
    toolbar: {
        sections: {
            grouping: true,
            testGeneration: true, // Primary feature for QA
            deployment: false,
            documentation: false,
            codeGen: false,
        },
        customActions: [
            {
                id: 'generate-all-tests',
                label: 'Generate All Tests',
                icon: 'auto_fix_high',
                action: 'generateAllTests',
            },
            {
                id: 'run-all-tests',
                label: 'Run All Tests',
                icon: 'play_circle',
                action: 'runAllTests',
            },
            {
                id: 'export-report',
                label: 'Export Report',
                icon: 'assessment',
                action: 'exportTestReport',
            },
        ],
    },
    nodeStyle: {
        colors: {
            default: '#FFF3E0', // Light orange
            selected: '#FF9800', // Orange
            grouped: '#FFB74D', // Medium orange
            testing: '#42A5F5', // Blue (running)
            deployed: '#66BB6A', // Green (passed)
        },
        shape: 'rounded',
        showIcons: true,
        showBadges: true, // Show coverage badges
    },
    panels: {
        left: [
            {
                id: 'test-suites',
                title: 'Test Suites',
                component: 'TestSuitesPanel',
                defaultOpen: true,
            },
            {
                id: 'coverage',
                title: 'Coverage',
                component: 'CoveragePanel',
                defaultOpen: true,
            },
        ],
        right: [
            {
                id: 'test-results',
                title: 'Test Results',
                component: 'TestResultsPanel',
                defaultOpen: true,
            },
            {
                id: 'properties',
                title: 'Properties',
                component: 'PropertiesPanel',
                defaultOpen: false,
            },
        ],
        bottom: {
            id: 'test-output',
            title: 'Test Output',
            component: 'TestOutputPanel',
            defaultHeight: 250,
        },
    },
    features: {
        grouping: true,
        testGeneration: true, // Core feature
        codeGeneration: false,
        deployment: false,
        versioning: true,
        collaboration: true,
    },
};

/**
 * Registry of all persona configurations
 */
export const PERSONA_CONFIGS: Record<PersonaType, PersonaCanvasConfig> = {
    PM: PM_CANVAS_CONFIG,
    Architect: ARCHITECT_CANVAS_CONFIG,
    Developer: DEVELOPER_CANVAS_CONFIG,
    QA: QA_CANVAS_CONFIG,
};

/**
 * Get configuration for a specific persona
 */
export function getPersonaConfig(type: PersonaType): PersonaCanvasConfig {
    return PERSONA_CONFIGS[type];
}

/**
 * Get all available persona types
 */
export function getAvailablePersonas(): PersonaType[] {
    return Object.keys(PERSONA_CONFIGS) as PersonaType[];
}
