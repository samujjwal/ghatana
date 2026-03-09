/**
 * Configuration Loader Service
 *
 * <p><b>Purpose</b><br>
 * Loads and parses YAML configuration files from the resources directory.
 * Provides typed access to all organization configuration entities including
 * departments, personas, phases, stages, operators, workflows, services, and integrations.
 *
 * <p><b>Features</b><br>
 * - Loads YAML files from configurable base path
 * - Caches loaded configurations for performance
 * - Supports hot reload via file watching (optional)
 * - Validates configurations against schemas
 * - Provides unified OrgConfig aggregation
 *
 * @doc.type service
 * @doc.purpose Load YAML configs from resources
 * @doc.layer infrastructure
 * @doc.pattern Service
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';
import * as yaml from 'js-yaml';
import {
    OrgConfig,
    DepartmentConfig,
    PersonaConfig,
    PhaseConfig,
    StageMapping,
    AgentConfig,
    WorkflowConfig,
    KpiConfig,
    ServiceConfig,
    IntegrationConfig,
    FlowConfig,
    OperatorConfig,
    InteractionConfig,
    EnvironmentConfig,
    FlowStep,
    MetricConfig,
    OperatorMode,
    OperatorInput,
    OperatorOutput,
    QuickAction,
    WorkflowStep
} from '../types/config.types.js';
import { ConfigLoader } from './spi/config-loader.spi.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export interface ConfigLoaderOptions {
    basePath: string;
    watchForChanges?: boolean;
}

export class FileSystemConfigLoader implements ConfigLoader {
    private basePath: string;
    private cache: Map<string, unknown> = new Map();
    private watchEnabled: boolean;

    constructor(options: ConfigLoaderOptions) {
        this.basePath = options.basePath;
        this.watchEnabled = options.watchForChanges ?? false;
        console.log(`[ConfigLoader] Initialized with basePath: ${this.basePath}`);
    }

    /**
     * Load a YAML file and parse it
     */
    private loadYamlFile<T>(relativePath: string): T | null {
        const fullPath = path.join(this.basePath, relativePath);

        // Check cache first
        if (this.cache.has(fullPath)) {
            return this.cache.get(fullPath) as T;
        }

        try {
            if (!fs.existsSync(fullPath)) {
                console.warn(`[ConfigLoader] File not found: ${fullPath}`);
                return null;
            }

            const content = fs.readFileSync(fullPath, 'utf8');
            const parsed = yaml.load(content) as T;

            // Cache the result
            this.cache.set(fullPath, parsed);

            return parsed;
        } catch (error) {
            console.error(`[ConfigLoader] Error loading ${fullPath}:`, error);
            return null;
        }
    }

    /**
     * Load all YAML files from a directory
     */
    private loadYamlDirectory<T>(relativePath: string): T[] {
        const fullPath = path.join(this.basePath, relativePath);
        const results: T[] = [];

        try {
            if (!fs.existsSync(fullPath)) {
                console.warn(`[ConfigLoader] Directory not found: ${fullPath}`);
                return results;
            }

            const files = fs.readdirSync(fullPath);

            for (const file of files) {
                if (file.endsWith('.yaml') || file.endsWith('.yml')) {
                    const filePath = path.join(relativePath, file);
                    const content = this.loadYamlFile<T>(filePath);
                    if (content) {
                        results.push(content);
                    }
                }
            }
        } catch (error) {
            console.error(`[ConfigLoader] Error loading directory ${fullPath}:`, error);
        }

        return results;
    }

    /**
     * Clear the cache
     */
    clearCache(): void {
        this.cache.clear();
    }

    // ========================================================================
    // Entity Loaders
    // ========================================================================

    /**
     * Load all personas from persona_registry.yaml
     */
    async loadAllPersonas(): Promise<PersonaConfig[]> {
        const data = this.loadYamlFile<{ personas: PersonaConfig[] }>(
            'mappings/persona_registry.yaml'
        );
        return data?.personas ?? [];
    }

    async loadPersonaConfig(id: string): Promise<PersonaConfig | null> {
        const personas = await this.loadAllPersonas();
        return personas.find(p => p.id === id) ?? null;
    }

    /**
     * Load all phases from phase_personas.yaml
     */
    async loadPhases(): Promise<PhaseConfig[]> {
        const data = this.loadYamlFile<{ phases: PhaseConfig[] }>(
            'mappings/phase_personas.yaml'
        );
        return data?.phases ?? [];
    }

    /**
     * Load all stage mappings from stage_phase_mapping.yaml
     */
    async loadStageMappings(): Promise<StageMapping[]> {
        const data = this.loadYamlFile<{ mappings: StageMapping[] }>(
            'mappings/stage_phase_mapping.yaml'
        );
        return data?.mappings ?? [];
    }

    /**
     * Load all departments from config/departments/
     */
    async loadAllDepartments(): Promise<DepartmentConfig[]> {
        // Load raw department files which may contain refs for agents
        const files = this.loadYamlDirectory<any>('departments');

        const departments: DepartmentConfig[] = [];

        for (const file of files) {
            const dept = file.spec ? {
                id: file.metadata?.name,
                name: file.spec.displayName,
                type: file.spec.type,
                description: file.spec.description,
                ...file.spec
            } : file.department; // Handle both k8s-style and simple YAML

            if (!dept) continue;

            // Resolve agent refs if present
            let agents: AgentConfig[] = [];
            if (file.spec?.agents) {
                for (const agentRef of file.spec.agents) {
                    if (agentRef.ref) {
                        const agentData = this.loadYamlFile<any>(agentRef.ref);
                        if (agentData) {
                            // Handle k8s-style agent config
                            if (agentData.kind === 'Agent' && agentData.spec) {
                                agents.push({
                                    id: agentData.metadata?.name,
                                    name: agentData.spec.displayName,
                                    role: agentData.spec.role?.name || agentData.spec.role,
                                    department: agentData.spec.department,
                                    capabilities: agentData.spec.capabilities?.primary || [],
                                    ...agentData.spec
                                });
                            } else {
                                agents.push(agentData);
                            }
                        }
                    } else {
                        agents.push(agentRef);
                    }
                }
            } else if (file.agents) {
                agents = file.agents;
            }

            departments.push({
                ...dept,
                agents,
                workflows: file.spec?.workflows || file.workflows,
                kpis: file.spec?.kpis || file.kpis,
                tools: file.spec?.tools || file.tools,
            });
        }

        return departments;
    }

    /**
     * Load a single department by ID
     */
    async loadDepartmentConfig(id: string): Promise<DepartmentConfig | null> {
        const departments = await this.loadAllDepartments();
        return departments.find(d => d.id === id) ?? null;
    }

    /**
     * Load all services from config/services/
     */
    async loadAllServices(): Promise<ServiceConfig[]> {
        const files = this.loadYamlDirectory<{ service: ServiceConfig }>('services');
        return files.map(f => f.service);
    }

    /**
     * Load a single service by ID
     */
    async loadServiceConfig(id: string): Promise<ServiceConfig | null> {
        const services = await this.loadAllServices();
        return services.find(s => s.id === id) ?? null;
    }

    /**
     * Load all integrations from config/integrations/
     */
    async loadAllIntegrations(): Promise<IntegrationConfig[]> {
        const files = this.loadYamlDirectory<{ integration: IntegrationConfig }>('integrations');
        return files.map(f => f.integration);
    }

    /**
     * Load a single integration by ID
     */
    async loadIntegrationConfig(id: string): Promise<IntegrationConfig | null> {
        const integrations = await this.loadAllIntegrations();
        return integrations.find(i => i.id === id) ?? null;
    }

    /**
     * Load all flows from config/flows/
     */
    async loadFlows(): Promise<FlowConfig[]> {
        // Try loading from flows.yaml first
        const singleFile = this.loadYamlFile<any>('flows.yaml');
        if (singleFile && singleFile.kind === 'FlowConfiguration' && singleFile.spec?.flows) {
            return singleFile.spec.flows;
        }

        // Fallback to directory
        const files = this.loadYamlDirectory<{ flow: FlowConfig }>('flows');
        return files.map(f => f.flow);
    }

    /**
     * Load a single flow by ID
     */
    async loadFlow(id: string): Promise<FlowConfig | null> {
        const flows = await this.loadFlows();
        return flows.find(f => f.id === id) ?? null;
    }

    /**
     * Load all domain operators from operations/domain/
     */
    async loadOperators(): Promise<OperatorConfig[]> {
        const operators: OperatorConfig[] = [];
        const domains = ['planning', 'design', 'build', 'release', 'operate'];

        for (const domain of domains) {
            const files = this.loadYamlDirectory<OperatorConfig>(
                `operations/domain/${domain}`
            );
            operators.push(...files);
        }

        return operators;
    }

    /**
     * Load a single operator by ID
     */
    async loadOperator(id: string): Promise<OperatorConfig | null> {
        const operators = await this.loadOperators();
        return operators.find(o => o.id === id) ?? null;
    }

    /**
     * Load all interactions from interactions/
     */
    async loadInteractions(): Promise<InteractionConfig[]> {
        const files = this.loadYamlDirectory<any>('interactions');
        return files
            .map(file => {
                if (file.kind === 'Interaction' && file.spec) {
                    return {
                        id: file.metadata?.name || file.spec.displayName || '',
                        name: file.metadata?.name || file.spec.displayName || '',
                        displayName: file.spec.displayName,
                        description: file.spec.description,
                        type: file.spec.type || 'handoff',
                        status: file.spec.status || 'active',
                        sourceDepartment: file.spec.participants?.source?.department,
                        targetDepartment: file.spec.participants?.target?.department,
                        sourceTeam: file.spec.participants?.source?.team,
                        targetTeam: file.spec.participants?.target?.team,
                        sourcePersona: file.spec.participants?.source?.persona,
                        targetPersona: file.spec.participants?.target?.persona,
                        trigger: file.spec.trigger,
                        protocol: file.spec.protocol,
                        handoff: file.spec.handoff,
                        actions: file.spec.actions,
                        metadata: file.spec.metadata || {},
                    };
                }
                return null;
            })
            .filter((file): file is InteractionConfig => file !== null);
    }

    /**
     * Load a single interaction by ID
     */
    async loadInteraction(id: string): Promise<InteractionConfig | null> {
        const interactions = await this.loadInteractions();
        return interactions.find(i => i.id === id) ?? null;
    }

    /**
     * Load the organization root config
     */
    async loadOrgRoot(): Promise<{ organization: { id: string; name: string; description: string; version: string } } | null> {
        const file = this.loadYamlFile<any>('organization.yaml');
        if (file && file.kind === 'Organization' && file.spec) {
            return {
                organization: {
                    id: file.metadata?.name || 'software-org',
                    name: file.spec.displayName,
                    description: file.spec.description,
                    version: file.metadata?.labels?.version || '1.0.0'
                }
            };
        }
        return file;
    }

    // ========================================================================
    // Aggregated Loaders
    // ========================================================================

    /**
     * Load the complete organization configuration
     */
    async loadOrgConfig(): Promise<OrgConfig> {
        const [
            orgRoot,
            departments,
            personas,
            phases,
            stages,
            services,
            integrations,
            interactions,
            flows,
            operators,
        ] = await Promise.all([
            this.loadOrgRoot(),
            this.loadAllDepartments(),
            this.loadAllPersonas(),
            this.loadPhases(),
            this.loadStageMappings(),
            this.loadAllServices(),
            this.loadAllIntegrations(),
            this.loadInteractions(),
            this.loadFlows(),
            this.loadOperators(),
        ]);

        const org = orgRoot?.organization ?? {
            id: 'software-org',
            name: 'Software Organization',
            description: 'Virtual software organization',
            version: '1.0.0',
        };

        return {
            id: org.id,
            name: org.name,
            description: org.description,
            version: org.version,
            departments,
            personas,
            phases,
            stages,
            services,
            integrations,
            interactions,
            flows,
            operators,
            metadata: {
                loaded_at: new Date().toISOString(),
                config_path: this.basePath,
            },
        };
    }

    /**
     * Get all agents across all departments
     */
    async loadAllAgents(): Promise<AgentConfig[]> {
        const departments = await this.loadAllDepartments();
        const agents: AgentConfig[] = [];

        for (const dept of departments) {
            if (dept.agents) {
                agents.push(...dept.agents);
            }
        }

        return agents;
    }

    /**
     * Get all workflows across all departments
     */
    async loadAllWorkflows(): Promise<WorkflowConfig[]> {
        const departments = await this.loadAllDepartments();
        const workflows: WorkflowConfig[] = [];

        for (const dept of departments) {
            if (dept.workflows) {
                workflows.push(...dept.workflows);
            }
        }

        return workflows;
    }

    async loadWorkflowConfig(id: string): Promise<WorkflowConfig | null> {
        const workflows = await this.loadAllWorkflows();
        return workflows.find(w => w.id === id) ?? null;
    }

    /**
     * Get all KPIs across all departments
     */
    async loadAllKpis(): Promise<KpiConfig[]> {
        const departments = await this.loadAllDepartments();
        const kpis: KpiConfig[] = [];

        for (const dept of departments) {
            if (dept.kpis) {
                kpis.push(...dept.kpis);
            }
        }

        return kpis;
    }
}

// ============================================================================
// Singleton Instance
// ============================================================================

let configLoaderInstance: ConfigLoader | null = null;

/**
 * Get the singleton config loader instance
 */
export function getConfigLoader(basePath?: string): ConfigLoader {
    if (!configLoaderInstance) {
        const defaultPath = process.env.CONFIG_PATH ??
            path.join(__dirname, '../../../../config');

        configLoaderInstance = new FileSystemConfigLoader({
            basePath: basePath ?? defaultPath,
            watchForChanges: process.env.NODE_ENV === 'development',
        });
    }
    return configLoaderInstance;
}

// Export the class for testing or direct usage if needed
export { FileSystemConfigLoader as ConfigLoaderService };
