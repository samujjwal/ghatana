import {
    OrgConfig,
    DepartmentConfig,
    PersonaConfig,
    WorkflowConfig,
    ServiceConfig,
    IntegrationConfig,
    PhaseConfig,
    StageMapping,
    FlowConfig,
    OperatorConfig,
    InteractionConfig,
    AgentConfig,
    KpiConfig
} from '../../types/config.types.js';

/**
 * SPI for Configuration Loading.
 * Implementations can load configuration from File System, Database, or Remote Service (Virtual Org).
 */
export interface ConfigLoader {
    /**
     * Loads the full organization configuration.
     */
    loadOrgConfig(): Promise<OrgConfig>;

    /**
     * Loads a specific department configuration.
     */
    loadDepartmentConfig(id: string): Promise<DepartmentConfig | null>;

    /**
     * Loads all department configurations.
     */
    loadAllDepartments(): Promise<DepartmentConfig[]>;

    /**
     * Loads a specific persona configuration.
     */
    loadPersonaConfig(id: string): Promise<PersonaConfig | null>;

    /**
     * Loads all persona configurations.
     */
    loadAllPersonas(): Promise<PersonaConfig[]>;

    /**
     * Loads a specific workflow configuration.
     */
    loadWorkflowConfig(id: string): Promise<WorkflowConfig | null>;

    /**
     * Loads all workflow configurations.
     */
    loadAllWorkflows(): Promise<WorkflowConfig[]>;

    /**
     * Loads a specific service configuration.
     */
    loadServiceConfig(id: string): Promise<ServiceConfig | null>;

    /**
     * Loads all service configurations.
     */
    loadAllServices(): Promise<ServiceConfig[]>;

    /**
     * Loads a specific integration configuration.
     */
    loadIntegrationConfig(id: string): Promise<IntegrationConfig | null>;

    /**
     * Loads all integration configurations.
     */
    loadAllIntegrations(): Promise<IntegrationConfig[]>;

    /**
     * Loads all phases.
     */
    loadPhases(): Promise<PhaseConfig[]>;

    /**
     * Loads all stage mappings.
     */
    loadStageMappings(): Promise<StageMapping[]>;

    /**
     * Loads all flows.
     */
    loadFlows(): Promise<FlowConfig[]>;

    /**
     * Loads a specific flow.
     */
    loadFlow(id: string): Promise<FlowConfig | null>;

    /**
     * Loads all operators.
     */
    loadOperators(): Promise<OperatorConfig[]>;

    /**
     * Loads a specific operator.
     */
    loadOperator(id: string): Promise<OperatorConfig | null>;

    /**
     * Loads all interactions.
     */
    loadInteractions(): Promise<InteractionConfig[]>;

    /**
     * Loads a specific interaction.
     */
    loadInteraction(id: string): Promise<InteractionConfig | null>;

    /**
     * Loads all agents.
     */
    loadAllAgents(): Promise<AgentConfig[]>;

    /**
     * Loads all KPIs.
     */
    loadAllKpis(): Promise<KpiConfig[]>;

    /**
     * Clears any internal cache.
     */
    clearCache(): void;
}
