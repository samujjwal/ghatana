/**
 * YAPPC Config Loader
 *
 * Loads task definitions, workflows, and lifecycle configurations from YAML files.
 * Provides validation and type-safe access to configuration data.
 *
 * @module config/loader
 */

import type {
    TaskDomainId,
    TaskDomain,
    TaskDefinition,
    TaskDomainConfig,
    WorkflowDefinition,
    WorkflowPhase,
    LifecycleStage,
    LifecycleStageConfig,
    StageTransition,
    TaskRegistryState,
    Persona,
    AutomationLevel,
    AuditArtifactType,
    TaskUIConfig,
} from '@ghatana/types/tasks';

// ============================================================================
// YAML Type Definitions (Raw)
// ============================================================================

interface RawTaskYAML {
    id: string;
    name: string;
    description: string;
    personas?: string[];
    lifecycle_stages?: string[];
    automation_level?: string;
    required_capabilities?: string[];
    input_schema?: string;
    output_schema?: string;
    audit_artifacts?: string[];
    ui?: {
        icon?: string;
        color?: string;
        tags?: string[];
        input_hints?: string[];
        output_hints?: string[];
    };
}

interface RawDomainYAML {
    domain: {
        id: string;
        name: string;
        description: string;
        order: number;
        icon: string;
        color: string;
        lifecycle_stages?: string[];
        agent_personas?: string[];
        audit_artifacts?: string[];
        inputs?: string[];
        outputs?: string[];
    };
    tasks: RawTaskYAML[];
}

interface RawStageYAML {
    id: string;
    name: string;
    description: string;
    order: number;
    icon: string;
    color: string;
    required: boolean;
    entry_criteria: string[];
    exit_criteria: string[];
    typical_activities: string[];
    artifacts: string[];
}

interface RawTransitionYAML {
    from: string;
    to: string;
    type: string;
    description: string;
    required_artifacts?: string[];
    conditions?: string[];
    reason?: string;
    triggers: string[];
}

interface RawWorkflowPhaseYAML {
    id: string;
    name: string;
    stages: string[];
    tasks: string[];
}

interface RawWorkflowYAML {
    id: string;
    name: string;
    description: string;
    category: string;
    icon: string;
    color: string;
    estimated_duration: string;
    lifecycle_stages: string[];
    phases: RawWorkflowPhaseYAML[];
}

// ============================================================================
// Validation Utilities
// ============================================================================

const VALID_LIFECYCLE_STAGES: LifecycleStage[] = [
    'intent',
    'context',
    'plan',
    'execute',
    'verify',
    'observe',
    'learn',
    'institutionalize',
];

const VALID_PERSONAS: Persona[] = [
    'Developer',
    'Tech Lead',
    'PM',
    'Security',
    'DevOps',
    'QA',
];

const VALID_AUTOMATION_LEVELS: AutomationLevel[] = ['manual', 'assisted', 'automated'];

function validateLifecycleStages(stages: string[]): LifecycleStage[] {
    return stages.filter((s): s is LifecycleStage =>
        VALID_LIFECYCLE_STAGES.includes(s as LifecycleStage)
    );
}

function validatePersonas(personas: string[]): Persona[] {
    return personas.filter((p): p is Persona =>
        VALID_PERSONAS.includes(p as Persona)
    );
}

function validateAutomationLevel(level: string): AutomationLevel {
    if (VALID_AUTOMATION_LEVELS.includes(level as AutomationLevel)) {
        return level as AutomationLevel;
    }
    return 'manual';
}

// ============================================================================
// Transform Functions
// ============================================================================

/**
 * Transform raw YAML task to typed TaskDefinition
 */
function transformTask(raw: RawTaskYAML): TaskDefinition {
    return {
        id: raw.id,
        name: raw.name,
        description: raw.description,
        personas: validatePersonas(raw.personas || []),
        lifecycleStages: validateLifecycleStages(raw.lifecycle_stages || []),
        automationLevel: validateAutomationLevel(raw.automation_level || 'manual'),
        requiredCapabilities: raw.required_capabilities || [],
        inputSchema: raw.input_schema,
        outputSchema: raw.output_schema,
        auditArtifacts: (raw.audit_artifacts || []) as AuditArtifactType[],
        ui: {
            icon: raw.ui?.icon || 'task',
            color: raw.ui?.color || '#666666',
            tags: raw.ui?.tags || [],
            inputHints: raw.ui?.input_hints,
            outputHints: raw.ui?.output_hints,
        },
    };
}

/**
 * Transform raw YAML domain to typed TaskDomain
 */
function transformDomain(raw: RawDomainYAML): TaskDomain {
    const domainConfig: TaskDomainConfig = {
        id: raw.domain.id as TaskDomainId,
        name: raw.domain.name,
        description: raw.domain.description,
        order: raw.domain.order,
        icon: raw.domain.icon,
        color: raw.domain.color,
        lifecycleStages: validateLifecycleStages(raw.domain.lifecycle_stages || []),
        agentPersonas: raw.domain.agent_personas || [],
        auditArtifacts: raw.domain.audit_artifacts || [],
        inputs: raw.domain.inputs || [],
        outputs: raw.domain.outputs || [],
    };

    return {
        ...domainConfig,
        tasks: raw.tasks.map(transformTask),
    };
}

/**
 * Transform raw YAML stage to typed LifecycleStageConfig
 */
function transformStage(raw: RawStageYAML): LifecycleStageConfig {
    return {
        id: raw.id as LifecycleStage,
        name: raw.name,
        description: raw.description,
        order: raw.order,
        icon: raw.icon,
        color: raw.color,
        required: raw.required,
        entryCriteria: raw.entry_criteria || [],
        exitCriteria: raw.exit_criteria || [],
        typicalActivities: raw.typical_activities || [],
        artifacts: raw.artifacts || [],
    };
}

/**
 * Transform raw YAML transition to typed StageTransition
 */
function transformTransition(raw: RawTransitionYAML): StageTransition {
    return {
        from: raw.from as LifecycleStage,
        to: raw.to as LifecycleStage,
        type: raw.type as 'forward' | 'backward' | 'skip',
        description: raw.description,
        requiredArtifacts: raw.required_artifacts,
        conditions: raw.conditions,
        reason: raw.reason,
        triggers: raw.triggers as StageTransition['triggers'],
    };
}

/**
 * Transform raw YAML workflow to typed WorkflowDefinition
 */
function transformWorkflow(raw: RawWorkflowYAML): WorkflowDefinition {
    return {
        id: raw.id,
        name: raw.name,
        description: raw.description,
        category: raw.category as WorkflowDefinition['category'],
        icon: raw.icon,
        color: raw.color,
        estimatedDuration: raw.estimated_duration,
        lifecycleStages: validateLifecycleStages(raw.lifecycle_stages),
        phases: raw.phases.map((p) => ({
            id: p.id,
            name: p.name,
            stages: validateLifecycleStages(p.stages),
            tasks: p.tasks,
        })),
    };
}

// ============================================================================
// Config Loader Class
// ============================================================================

/**
 * Configuration loader options
 */
export interface ConfigLoaderOptions {
    basePath: string;
    yamlParser: (content: string) => unknown;
    fileReader: (path: string) => Promise<string>;
}

/**
 * Task configuration loader
 */
export class TaskConfigLoader {
    private options: ConfigLoaderOptions;
    private cache: {
        domains: Map<TaskDomainId, TaskDomain>;
        stages: Map<LifecycleStage, LifecycleStageConfig>;
        transitions: StageTransition[];
        workflows: Map<string, WorkflowDefinition>;
    };

    constructor(options: ConfigLoaderOptions) {
        this.options = options;
        this.cache = {
            domains: new Map(),
            stages: new Map(),
            transitions: [],
            workflows: new Map(),
        };
    }

    /**
     * Load all configurations and return registry state
     */
    async loadAll(): Promise<TaskRegistryState> {
        const [domains, stages, transitions, workflows] = await Promise.all([
            this.loadAllDomains(),
            this.loadStages(),
            this.loadTransitions(),
            this.loadWorkflows(),
        ]);

        const tasks = new Map<string, TaskDefinition>();
        for (const domain of domains.values()) {
            for (const task of domain.tasks) {
                tasks.set(task.id, task);
            }
        }

        return {
            domains,
            tasks,
            workflows,
            stages,
            transitions,
            isLoaded: true,
            lastUpdated: new Date(),
        };
    }

    /**
     * Load all domain configurations
     */
    async loadAllDomains(): Promise<Map<TaskDomainId, TaskDomain>> {
        const indexPath = `${this.options.basePath}/tasks/domains/index.yaml`;
        const indexContent = await this.options.fileReader(indexPath);
        const indexData = this.options.yamlParser(indexContent) as {
            domains: Array<{ id: string; file: string }>;
        };

        const domainPromises = indexData.domains.map(async (d) => {
            const domainPath = `${this.options.basePath}/tasks/domains/${d.file}`;
            return this.loadDomain(domainPath);
        });

        const loadedDomains = await Promise.all(domainPromises);

        const domainMap = new Map<TaskDomainId, TaskDomain>();
        for (const domain of loadedDomains) {
            domainMap.set(domain.id, domain);
            this.cache.domains.set(domain.id, domain);
        }

        return domainMap;
    }

    /**
     * Load a single domain configuration
     */
    async loadDomain(path: string): Promise<TaskDomain> {
        const content = await this.options.fileReader(path);
        const rawData = this.options.yamlParser(content) as RawDomainYAML;
        return transformDomain(rawData);
    }

    /**
     * Load lifecycle stages configuration
     */
    async loadStages(): Promise<Map<LifecycleStage, LifecycleStageConfig>> {
        const path = `${this.options.basePath}/lifecycle/stages.yaml`;
        const content = await this.options.fileReader(path);
        const rawData = this.options.yamlParser(content) as { stages: RawStageYAML[] };

        const stageMap = new Map<LifecycleStage, LifecycleStageConfig>();
        for (const rawStage of rawData.stages) {
            const stage = transformStage(rawStage);
            stageMap.set(stage.id, stage);
            this.cache.stages.set(stage.id, stage);
        }

        return stageMap;
    }

    /**
     * Load stage transitions configuration
     */
    async loadTransitions(): Promise<StageTransition[]> {
        const path = `${this.options.basePath}/lifecycle/transitions.yaml`;
        const content = await this.options.fileReader(path);
        const rawData = this.options.yamlParser(content) as {
            transitions: RawTransitionYAML[];
        };

        const transitions = rawData.transitions.map(transformTransition);
        this.cache.transitions = transitions;
        return transitions;
    }

    /**
     * Load workflow configurations
     */
    async loadWorkflows(): Promise<Map<string, WorkflowDefinition>> {
        const path = `${this.options.basePath}/workflows/canonical-workflows.yaml`;
        const content = await this.options.fileReader(path);
        const rawData = this.options.yamlParser(content) as {
            workflows: RawWorkflowYAML[];
        };

        const workflowMap = new Map<string, WorkflowDefinition>();
        for (const rawWorkflow of rawData.workflows) {
            const workflow = transformWorkflow(rawWorkflow);
            workflowMap.set(workflow.id, workflow);
            this.cache.workflows.set(workflow.id, workflow);
        }

        return workflowMap;
    }

    /**
     * Get cached domain
     */
    getDomain(id: TaskDomainId): TaskDomain | undefined {
        return this.cache.domains.get(id);
    }

    /**
     * Get cached stage
     */
    getStage(id: LifecycleStage): LifecycleStageConfig | undefined {
        return this.cache.stages.get(id);
    }

    /**
     * Get cached workflow
     */
    getWorkflow(id: string): WorkflowDefinition | undefined {
        return this.cache.workflows.get(id);
    }

    /**
     * Get all cached domains
     */
    getAllDomains(): TaskDomain[] {
        return Array.from(this.cache.domains.values());
    }

    /**
     * Get all cached stages
     */
    getAllStages(): LifecycleStageConfig[] {
        return Array.from(this.cache.stages.values());
    }

    /**
     * Get all cached transitions
     */
    getAllTransitions(): StageTransition[] {
        return this.cache.transitions;
    }

    /**
     * Get all cached workflows
     */
    getAllWorkflows(): WorkflowDefinition[] {
        return Array.from(this.cache.workflows.values());
    }

    /**
     * Clear cache
     */
    clearCache(): void {
        this.cache.domains.clear();
        this.cache.stages.clear();
        this.cache.workflows.clear();
        this.cache.transitions = [];
    }
}

// ============================================================================
// Factory Functions
// ============================================================================

/**
 * Create a browser-compatible config loader
 */
export function createBrowserConfigLoader(
    basePath: string
): TaskConfigLoader {
    return new TaskConfigLoader({
        basePath,
        yamlParser: (content: string) => {
            // In browser, use a YAML parsing library
            // This is a placeholder - actual implementation would use js-yaml
            throw new Error('YAML parser not implemented for browser');
        },
        fileReader: async (path: string) => {
            const response = await fetch(path);
            if (!response.ok) {
                throw new Error(`Failed to load config: ${path}`);
            }
            return response.text();
        },
    });
}

/**
 * Create a Node.js-compatible config loader
 */
export function createNodeConfigLoader(basePath: string): TaskConfigLoader {
    return new TaskConfigLoader({
        basePath,
        yamlParser: (content: string) => {
            // In Node.js, use js-yaml or yaml library
            // This is a placeholder - actual implementation would import yaml
            throw new Error('YAML parser not implemented for Node.js');
        },
        fileReader: async (path: string) => {
            // In Node.js, use fs.readFile
            throw new Error('File reader not implemented for Node.js');
        },
    });
}

// ============================================================================
// Export
// ============================================================================

export type {
    RawTaskYAML,
    RawDomainYAML,
    RawStageYAML,
    RawTransitionYAML,
    RawWorkflowYAML,
};
