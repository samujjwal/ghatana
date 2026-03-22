/**
 * Canvas State Configuration Registry
 * 
 * Single source of truth for all 28 canvas states (7 modes × 4 levels).
 * Defines content types, tools, empty states, and AI behaviors per state.
 * 
 * @doc.type configuration
 * @doc.purpose Canvas state definitions
 * @doc.layer product
 * @doc.pattern Registry
 */

import type { CanvasMode, AbstractionLevel } from '../types/canvas';

/**
 * Canvas content type identifiers
 */
export enum CanvasContentType {
    // Brainstorm
    MIND_MAP = 'mind-map',
    STICKY_NOTES = 'sticky-notes',
    ANNOTATIONS = 'annotations',
    PSEUDOCODE = 'pseudocode',

    // Diagram
    ARCHITECTURE_DIAGRAM = 'architecture-diagram',
    COMPONENT_DIAGRAM = 'component-diagram',
    CLASS_DIAGRAM = 'class-diagram',
    SEQUENCE_DIAGRAM = 'sequence-diagram',

    // Design
    DESIGN_SYSTEM = 'design-system',
    PAGE_LAYOUTS = 'page-layouts',
    COMPONENT_SPECS = 'component-specs',
    STYLE_TOKENS = 'style-tokens',

    // Code
    API_TOPOLOGY = 'api-topology',
    MODULE_GRAPH = 'module-graph',
    FILE_EXPLORER = 'file-explorer',
    CODE_EDITOR = 'code-editor',

    // Test
    E2E_COVERAGE = 'e2e-coverage',
    UNIT_COVERAGE = 'unit-coverage',
    TEST_FILE_LIST = 'test-file-list',
    TEST_EDITOR = 'test-editor',

    // Deploy
    INFRASTRUCTURE_DIAGRAM = 'infrastructure-diagram',
    CONTAINER_ORCHESTRATION = 'container-orchestration',
    CONFIG_BROWSER = 'config-browser',
    CONFIG_EDITOR = 'config-editor',

    // Observe
    SYSTEM_DASHBOARD = 'system-dashboard',
    COMPONENT_METRICS = 'component-metrics',
    LOG_VIEWER = 'log-viewer',
    TRACE_EXPLORER = 'trace-explorer',
}

/**
 * Tool identifiers for canvas tool palettes
 */
export enum CanvasTool {
    // Drawing tools
    FREEHAND = 'freehand',
    TEXT = 'text',
    SHAPES = 'shapes',
    STICKY_NOTE = 'sticky-note',
    GROUPING = 'grouping',
    COMMENT = 'comment',

    // Diagram tools
    BOX = 'box',
    ARROW = 'arrow',
    DATABASE = 'database',
    CLOUD = 'cloud',
    MESSAGE_QUEUE = 'message-queue',
    COMPONENT_NODE = 'component-node',
    CLASS_NODE = 'class-node',
    INTERFACE_NODE = 'interface-node',
    INHERITANCE_ARROW = 'inheritance-arrow',
    ACTOR = 'actor',
    LIFELINE = 'lifeline',
    MESSAGE = 'message',

    // Design tools
    THEME_EDITOR = 'theme-editor',
    COLOR_PALETTE = 'color-palette',
    TYPOGRAPHY_SCALE = 'typography-scale',
    PAGE_TEMPLATE = 'page-template',
    GRID_LAYOUT = 'grid-layout',
    COMPONENT_LIBRARY = 'component-library',
    PROPS_EDITOR = 'props-editor',
    VARIANTS = 'variants',
    STATE_MANAGER = 'state-manager',
    CSS_EDITOR = 'css-editor',

    // Code tools
    API_BLOCK = 'api-block',
    ENDPOINT = 'endpoint',
    AUTH_FLOW = 'auth-flow',
    IMPORT_EXPORT = 'import-export',
    FILE_TREE = 'file-tree',
    FILE_PREVIEW = 'file-preview',
    SEARCH = 'search',
    SYNTAX_HIGHLIGHT = 'syntax-highlight',
    INTELLISENSE = 'intellisense',
    FORMATTER = 'formatter',
    LINTER = 'linter',

    // Test tools
    TEST_SUITE_DESIGNER = 'test-suite-designer',
    SCENARIO_BUILDER = 'scenario-builder',
    COVERAGE_HEATMAP = 'coverage-heatmap',
    TEST_STATUS_BADGE = 'test-status-badge',
    TEST_RUNNER = 'test-runner',
    FILTER = 'filter',
    SORT = 'sort',
    ASSERTION_HELPER = 'assertion-helper',
    MOCK_GENERATOR = 'mock-generator',

    // Deploy tools
    CLOUD_RESOURCE = 'cloud-resource',
    VPC = 'vpc',
    LOAD_BALANCER = 'load-balancer',
    CDN = 'cdn',
    POD = 'pod',
    SERVICE = 'service',
    VOLUME = 'volume',
    INGRESS = 'ingress',
    CONFIG_VALIDATOR = 'config-validator',
    YAML_EDITOR = 'yaml-editor',
    JSON_EDITOR = 'json-editor',

    // Observe tools
    STATUS_INDICATOR = 'status-indicator',
    UPTIME_CHART = 'uptime-chart',
    ALERT_LIST = 'alert-list',
    METRIC_GRAPH = 'metric-graph',
    SLO_TRACKER = 'slo-tracker',
    LOG_FILTER = 'log-filter',
    TIME_RANGE = 'time-range',
    SPAN_VIEWER = 'span-viewer',
    TIMING_WATERFALL = 'timing-waterfall',
}

/**
 * AI assistant behavior types
 */
export enum AIBehaviorType {
    IDEATION = 'ideation',
    STRUCTURAL_ANALYSIS = 'structural-analysis',
    GENERATIVE_UI = 'generative-ui',
    CODE_COMPLETION = 'code-completion',
    TEST_GENERATION = 'test-generation',
    CONFIGURATION = 'configuration',
    ROOT_CAUSE_ANALYSIS = 'root-cause-analysis',
}

/**
 * Canvas state configuration interface
 */
export interface CanvasStateConfig {
    mode: CanvasMode;
    level: AbstractionLevel;
    contentType: CanvasContentType;
    canvasContent: string;
    availableTools: CanvasTool[];
    emptyStateMessage: string;
    aiAssistant: {
        name: string;
        behaviorType: AIBehaviorType;
        description: string;
    };
    useCases: string[];
}

/**
 * Complete registry of all 28 canvas states
 */
export const CANVAS_STATE_REGISTRY: Record<string, CanvasStateConfig> = {
    // ============================================
    // 1. BRAINSTORM MODE
    // ============================================
    'brainstorm-system': {
        mode: 'brainstorm',
        level: 'system',
        contentType: CanvasContentType.MIND_MAP,
        canvasContent: 'Mind map of systems',
        availableTools: [CanvasTool.FREEHAND, CanvasTool.TEXT, CanvasTool.SHAPES],
        emptyStateMessage: 'Start with a big idea',
        aiAssistant: {
            name: 'Suggest Analogies',
            behaviorType: AIBehaviorType.IDEATION,
            description: 'Like Uber for X..., Combines Netflix + LinkedIn',
        },
        useCases: [
            'High-level product vision',
            'Multi-system architecture brainstorming',
            'Business model exploration',
        ],
    },

    'brainstorm-component': {
        mode: 'brainstorm',
        level: 'component',
        contentType: CanvasContentType.STICKY_NOTES,
        canvasContent: 'Sticky notes grid',
        availableTools: [CanvasTool.STICKY_NOTE, CanvasTool.GROUPING],
        emptyStateMessage: 'Add components to explore',
        aiAssistant: {
            name: 'Cluster Ideas',
            behaviorType: AIBehaviorType.IDEATION,
            description: 'Group related concepts, identify themes',
        },
        useCases: [
            'Feature brainstorming',
            'Component relationships',
            'User story mapping',
        ],
    },

    'brainstorm-file': {
        mode: 'brainstorm',
        level: 'file',
        contentType: CanvasContentType.ANNOTATIONS,
        canvasContent: 'File-specific notes',
        availableTools: [CanvasTool.COMMENT, CanvasTool.TEXT],
        emptyStateMessage: 'Select a file to annotate',
        aiAssistant: {
            name: 'Contextual Notes',
            behaviorType: AIBehaviorType.IDEATION,
            description: 'Suggest improvements for specific files',
        },
        useCases: [
            'Code review notes',
            'Refactoring ideas',
            'Technical debt tracking',
        ],
    },

    'brainstorm-code': {
        mode: 'brainstorm',
        level: 'code',
        contentType: CanvasContentType.PSEUDOCODE,
        canvasContent: 'Pseudocode blocks',
        availableTools: [CanvasTool.TEXT, CanvasTool.FORMATTER],
        emptyStateMessage: 'Write pseudocode here',
        aiAssistant: {
            name: 'Algorithm Ideas',
            behaviorType: AIBehaviorType.IDEATION,
            description: 'Suggest approaches, pseudocode patterns',
        },
        useCases: [
            'Algorithm design',
            'Logic flow exploration',
            'Quick code sketches',
        ],
    },

    // ============================================
    // 2. DIAGRAM MODE
    // ============================================
    'diagram-system': {
        mode: 'diagram',
        level: 'system',
        contentType: CanvasContentType.ARCHITECTURE_DIAGRAM,
        canvasContent: 'Architecture diagram (C4 Context/Container)',
        availableTools: [
            CanvasTool.BOX,
            CanvasTool.ARROW,
            CanvasTool.DATABASE,
            CanvasTool.CLOUD,
            CanvasTool.MESSAGE_QUEUE,
        ],
        emptyStateMessage: 'Add your first service',
        aiAssistant: {
            name: 'Auto-Connect',
            behaviorType: AIBehaviorType.STRUCTURAL_ANALYSIS,
            description: 'Connect Auth to DB?, Add API Gateway?',
        },
        useCases: [
            'System architecture design',
            'Service topology',
            'Integration planning',
        ],
    },

    'diagram-component': {
        mode: 'diagram',
        level: 'component',
        contentType: CanvasContentType.COMPONENT_DIAGRAM,
        canvasContent: 'Component diagram (C4 Component)',
        availableTools: [
            CanvasTool.COMPONENT_NODE,
            CanvasTool.ARROW,
        ],
        emptyStateMessage: 'Drag components to canvas',
        aiAssistant: {
            name: 'Dependency Analysis',
            behaviorType: AIBehaviorType.STRUCTURAL_ANALYSIS,
            description: 'Detect circular deps, suggest patterns',
        },
        useCases: [
            'Module architecture',
            'Component relationships',
            'Data flow mapping',
        ],
    },

    'diagram-file': {
        mode: 'diagram',
        level: 'file',
        contentType: CanvasContentType.CLASS_DIAGRAM,
        canvasContent: 'Class/module diagram',
        availableTools: [
            CanvasTool.CLASS_NODE,
            CanvasTool.INTERFACE_NODE,
            CanvasTool.INHERITANCE_ARROW,
        ],
        emptyStateMessage: 'Select components to see structure',
        aiAssistant: {
            name: 'UML Suggestions',
            behaviorType: AIBehaviorType.STRUCTURAL_ANALYSIS,
            description: 'Generate class diagrams from code',
        },
        useCases: [
            'Class structure design',
            'Interface definitions',
            'Inheritance hierarchies',
        ],
    },

    'diagram-code': {
        mode: 'diagram',
        level: 'code',
        contentType: CanvasContentType.SEQUENCE_DIAGRAM,
        canvasContent: 'Sequence diagram',
        availableTools: [
            CanvasTool.ACTOR,
            CanvasTool.MESSAGE,
            CanvasTool.LIFELINE,
        ],
        emptyStateMessage: 'Drill into a function to see flow',
        aiAssistant: {
            name: 'Flow Tracing',
            behaviorType: AIBehaviorType.STRUCTURAL_ANALYSIS,
            description: 'Generate sequence diagrams from code',
        },
        useCases: [
            'Function call flows',
            'API interaction sequences',
            'Event-driven flows',
        ],
    },

    // ============================================
    // 3. DESIGN MODE
    // ============================================
    'design-system': {
        mode: 'design',
        level: 'system',
        contentType: CanvasContentType.DESIGN_SYSTEM,
        canvasContent: 'Design system overview',
        availableTools: [
            CanvasTool.THEME_EDITOR,
            CanvasTool.COLOR_PALETTE,
            CanvasTool.TYPOGRAPHY_SCALE,
        ],
        emptyStateMessage: 'Define your design language',
        aiAssistant: {
            name: 'Generative Design System',
            behaviorType: AIBehaviorType.GENERATIVE_UI,
            description: 'Generate tokens from brand colors',
        },
        useCases: [
            'Design system creation',
            'Brand guidelines',
            'Accessibility standards',
        ],
    },

    'design-component': {
        mode: 'design',
        level: 'component',
        contentType: CanvasContentType.PAGE_LAYOUTS,
        canvasContent: 'Page layouts/wireframes',
        availableTools: [
            CanvasTool.PAGE_TEMPLATE,
            CanvasTool.GRID_LAYOUT,
            CanvasTool.COMPONENT_LIBRARY,
        ],
        emptyStateMessage: 'Create your first page',
        aiAssistant: {
            name: 'Generative UI',
            behaviorType: AIBehaviorType.GENERATIVE_UI,
            description: 'Generate Dashboard layout, Create Login page',
        },
        useCases: [
            'Page design',
            'Layout exploration',
            'Component composition',
        ],
    },

    'design-file': {
        mode: 'design',
        level: 'file',
        contentType: CanvasContentType.COMPONENT_SPECS,
        canvasContent: 'Component specifications',
        availableTools: [
            CanvasTool.PROPS_EDITOR,
            CanvasTool.VARIANTS,
            CanvasTool.STATE_MANAGER,
        ],
        emptyStateMessage: 'Design component details',
        aiAssistant: {
            name: 'Component Inspector',
            behaviorType: AIBehaviorType.GENERATIVE_UI,
            description: 'Analyze usage, suggest optimizations',
        },
        useCases: [
            'Component API design',
            'Props/state definition',
            'Variant management',
        ],
    },

    'design-code': {
        mode: 'design',
        level: 'code',
        contentType: CanvasContentType.STYLE_TOKENS,
        canvasContent: 'Style tokens editor',
        availableTools: [CanvasTool.CSS_EDITOR],
        emptyStateMessage: 'Edit component styles',
        aiAssistant: {
            name: 'Style Completion',
            behaviorType: AIBehaviorType.GENERATIVE_UI,
            description: 'Suggest consistent tokens, a11y fixes',
        },
        useCases: [
            'CSS customization',
            'Token refinement',
            'Theme switching logic',
        ],
    },

    // ============================================
    // 4. CODE MODE
    // ============================================
    'code-system': {
        mode: 'code',
        level: 'system',
        contentType: CanvasContentType.API_TOPOLOGY,
        canvasContent: 'Service topology with API endpoints',
        availableTools: [
            CanvasTool.API_BLOCK,
            CanvasTool.ENDPOINT,
            CanvasTool.AUTH_FLOW,
        ],
        emptyStateMessage: 'Map your services',
        aiAssistant: {
            name: 'API Design',
            behaviorType: AIBehaviorType.CODE_COMPLETION,
            description: 'Suggest REST/GraphQL schemas, OpenAPI generation',
        },
        useCases: [
            'API architecture',
            'Microservice contracts',
            'Service mesh design',
        ],
    },

    'code-component': {
        mode: 'code',
        level: 'component',
        contentType: CanvasContentType.MODULE_GRAPH,
        canvasContent: 'Module dependency graph',
        availableTools: [CanvasTool.IMPORT_EXPORT],
        emptyStateMessage: 'View module structure',
        aiAssistant: {
            name: 'Dependency Optimizer',
            behaviorType: AIBehaviorType.CODE_COMPLETION,
            description: 'Suggest tree-shaking, code splitting',
        },
        useCases: [
            'Module structure',
            'Import optimization',
            'Bundle analysis',
        ],
    },

    'code-file': {
        mode: 'code',
        level: 'file',
        contentType: CanvasContentType.FILE_EXPLORER,
        canvasContent: 'File explorer with preview pane',
        availableTools: [
            CanvasTool.FILE_TREE,
            CanvasTool.FILE_PREVIEW,
            CanvasTool.SEARCH,
        ],
        emptyStateMessage: 'Browse project files',
        aiAssistant: {
            name: 'Smart Navigation',
            behaviorType: AIBehaviorType.CODE_COMPLETION,
            description: 'Find related files, Show callers',
        },
        useCases: [
            'File navigation',
            'Quick code review',
            'File organization',
        ],
    },

    'code-code': {
        mode: 'code',
        level: 'code',
        contentType: CanvasContentType.CODE_EDITOR,
        canvasContent: 'Monaco code editor (full IDE features)',
        availableTools: [
            CanvasTool.SYNTAX_HIGHLIGHT,
            CanvasTool.INTELLISENSE,
            CanvasTool.FORMATTER,
            CanvasTool.LINTER,
        ],
        emptyStateMessage: 'Select a file to edit',
        aiAssistant: {
            name: 'Copilot',
            behaviorType: AIBehaviorType.CODE_COMPLETION,
            description: 'Complete functions, refactor, explain code',
        },
        useCases: [
            'Code implementation',
            'Line-by-line editing',
            'Refactoring',
        ],
    },

    // ============================================
    // 5. TEST MODE
    // ============================================
    'test-system': {
        mode: 'test',
        level: 'system',
        contentType: CanvasContentType.E2E_COVERAGE,
        canvasContent: 'E2E test coverage map',
        availableTools: [
            CanvasTool.TEST_SUITE_DESIGNER,
            CanvasTool.SCENARIO_BUILDER,
        ],
        emptyStateMessage: 'Plan integration tests',
        aiAssistant: {
            name: 'Test Strategy',
            behaviorType: AIBehaviorType.TEST_GENERATION,
            description: 'Suggest critical paths, edge cases',
        },
        useCases: [
            'E2E test planning',
            'Integration test design',
            'User journey testing',
        ],
    },

    'test-component': {
        mode: 'test',
        level: 'component',
        contentType: CanvasContentType.UNIT_COVERAGE,
        canvasContent: 'Unit test coverage visualization',
        availableTools: [
            CanvasTool.COVERAGE_HEATMAP,
            CanvasTool.TEST_STATUS_BADGE,
        ],
        emptyStateMessage: 'View test coverage',
        aiAssistant: {
            name: 'Coverage Gaps',
            behaviorType: AIBehaviorType.TEST_GENERATION,
            description: 'Identify untested code, suggest tests',
        },
        useCases: [
            'Coverage analysis',
            'Test prioritization',
            'Quality metrics',
        ],
    },

    'test-file': {
        mode: 'test',
        level: 'file',
        contentType: CanvasContentType.TEST_FILE_LIST,
        canvasContent: 'Test file list with results',
        availableTools: [
            CanvasTool.TEST_RUNNER,
            CanvasTool.FILTER,
            CanvasTool.SORT,
        ],
        emptyStateMessage: 'See test files',
        aiAssistant: {
            name: 'Test Results',
            behaviorType: AIBehaviorType.TEST_GENERATION,
            description: 'Explain failures, suggest fixes',
        },
        useCases: [
            'Test file management',
            'Quick test runs',
            'Failure investigation',
        ],
    },

    'test-code': {
        mode: 'test',
        level: 'code',
        contentType: CanvasContentType.TEST_EDITOR,
        canvasContent: 'Test code editor with runner',
        availableTools: [
            CanvasTool.ASSERTION_HELPER,
            CanvasTool.MOCK_GENERATOR,
            CanvasTool.TEST_RUNNER,
        ],
        emptyStateMessage: 'Write test cases',
        aiAssistant: {
            name: 'Gen-Test',
            behaviorType: AIBehaviorType.TEST_GENERATION,
            description: 'Generate tests from code, suggest assertions',
        },
        useCases: [
            'Test case writing',
            'TDD development',
            'Test refactoring',
        ],
    },

    // ============================================
    // 6. DEPLOY MODE
    // ============================================
    'deploy-system': {
        mode: 'deploy',
        level: 'system',
        contentType: CanvasContentType.INFRASTRUCTURE_DIAGRAM,
        canvasContent: 'Infrastructure diagram (AWS/GCP/Azure)',
        availableTools: [
            CanvasTool.CLOUD_RESOURCE,
            CanvasTool.VPC,
            CanvasTool.LOAD_BALANCER,
            CanvasTool.CDN,
        ],
        emptyStateMessage: 'Design infrastructure',
        aiAssistant: {
            name: 'Auto-Examine',
            behaviorType: AIBehaviorType.CONFIGURATION,
            description: 'Security scan, cost optimization, HA suggestions',
        },
        useCases: [
            'Infrastructure as Code',
            'Cloud architecture',
            'Multi-region deployment',
        ],
    },

    'deploy-component': {
        mode: 'deploy',
        level: 'component',
        contentType: CanvasContentType.CONTAINER_ORCHESTRATION,
        canvasContent: 'Container orchestration (Kubernetes)',
        availableTools: [
            CanvasTool.POD,
            CanvasTool.SERVICE,
            CanvasTool.VOLUME,
            CanvasTool.INGRESS,
        ],
        emptyStateMessage: 'Configure containers',
        aiAssistant: {
            name: 'K8s Helper',
            behaviorType: AIBehaviorType.CONFIGURATION,
            description: 'Generate manifests, suggest resource limits',
        },
        useCases: [
            'Container orchestration',
            'Service mesh',
            'Scaling configuration',
        ],
    },

    'deploy-file': {
        mode: 'deploy',
        level: 'file',
        contentType: CanvasContentType.CONFIG_BROWSER,
        canvasContent: 'Config file browser (Dockerfile, k8s YAML, CI/CD)',
        availableTools: [CanvasTool.CONFIG_VALIDATOR],
        emptyStateMessage: 'View config files',
        aiAssistant: {
            name: 'Config Validator',
            behaviorType: AIBehaviorType.CONFIGURATION,
            description: 'Check syntax, suggest best practices',
        },
        useCases: [
            'Configuration management',
            'Dockerfile optimization',
            'Pipeline definition',
        ],
    },

    'deploy-code': {
        mode: 'deploy',
        level: 'code',
        contentType: CanvasContentType.CONFIG_EDITOR,
        canvasContent: 'Config/script editor',
        availableTools: [
            CanvasTool.YAML_EDITOR,
            CanvasTool.JSON_EDITOR,
            CanvasTool.CONFIG_VALIDATOR,
        ],
        emptyStateMessage: 'Edit deployment config',
        aiAssistant: {
            name: 'Config Copilot',
            behaviorType: AIBehaviorType.CONFIGURATION,
            description: 'Complete configs, explain options',
        },
        useCases: [
            'CI/CD scripting',
            'Environment variables',
            'Secret management',
        ],
    },

    // ============================================
    // 7. OBSERVE MODE
    // ============================================
    'observe-system': {
        mode: 'observe',
        level: 'system',
        contentType: CanvasContentType.SYSTEM_DASHBOARD,
        canvasContent: 'System health dashboard',
        availableTools: [
            CanvasTool.STATUS_INDICATOR,
            CanvasTool.UPTIME_CHART,
            CanvasTool.ALERT_LIST,
        ],
        emptyStateMessage: 'Connect monitoring',
        aiAssistant: {
            name: 'Root Cause',
            behaviorType: AIBehaviorType.ROOT_CAUSE_ANALYSIS,
            description: 'Why 500 error?, correlate incidents',
        },
        useCases: [
            'System monitoring',
            'SLA tracking',
            'Incident response',
        ],
    },

    'observe-component': {
        mode: 'observe',
        level: 'component',
        contentType: CanvasContentType.COMPONENT_METRICS,
        canvasContent: 'Component metrics (latency, throughput, errors)',
        availableTools: [
            CanvasTool.METRIC_GRAPH,
            CanvasTool.SLO_TRACKER,
        ],
        emptyStateMessage: 'View component health',
        aiAssistant: {
            name: 'Anomaly Detection',
            behaviorType: AIBehaviorType.ROOT_CAUSE_ANALYSIS,
            description: 'Identify unusual patterns',
        },
        useCases: [
            'Performance monitoring',
            'Service health',
            'SLO compliance',
        ],
    },

    'observe-file': {
        mode: 'observe',
        level: 'file',
        contentType: CanvasContentType.LOG_VIEWER,
        canvasContent: 'Log stream viewer',
        availableTools: [
            CanvasTool.LOG_FILTER,
            CanvasTool.SEARCH,
            CanvasTool.TIME_RANGE,
        ],
        emptyStateMessage: 'Stream logs',
        aiAssistant: {
            name: 'Log Analysis',
            behaviorType: AIBehaviorType.ROOT_CAUSE_ANALYSIS,
            description: 'Extract insights, find errors',
        },
        useCases: [
            'Real-time logs',
            'Error tracking',
            'Debug sessions',
        ],
    },

    'observe-code': {
        mode: 'observe',
        level: 'code',
        contentType: CanvasContentType.TRACE_EXPLORER,
        canvasContent: 'Distributed trace explorer',
        availableTools: [
            CanvasTool.SPAN_VIEWER,
            CanvasTool.TIMING_WATERFALL,
        ],
        emptyStateMessage: 'Inspect traces',
        aiAssistant: {
            name: 'Trace Insights',
            behaviorType: AIBehaviorType.ROOT_CAUSE_ANALYSIS,
            description: 'Identify bottlenecks, suggest optimizations',
        },
        useCases: [
            'Performance profiling',
            'Request tracing',
            'Latency debugging',
        ],
    },
};

/**
 * Get canvas state configuration by mode and level
 */
export function getCanvasState(mode: CanvasMode, level: AbstractionLevel): CanvasStateConfig {
    const key = `${mode}-${level}`;
    const state = CANVAS_STATE_REGISTRY[key];

    if (!state) {
        throw new Error(`Canvas state not found for mode="${mode}" level="${level}"`);
    }

    return state;
}

/**
 * Get all states for a specific mode
 */
export function getStatesForMode(mode: CanvasMode): CanvasStateConfig[] {
    return Object.values(CANVAS_STATE_REGISTRY).filter(state => state.mode === mode);
}

/**
 * Get all states for a specific level
 */
export function getStatesForLevel(level: AbstractionLevel): CanvasStateConfig[] {
    return Object.values(CANVAS_STATE_REGISTRY).filter(state => state.level === level);
}
