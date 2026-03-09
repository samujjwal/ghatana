/**
 * Organization Configuration Loader Service
 *
 * Comprehensive loader for all organization configuration data from YAML files.
 * Handles departments, agents, workflows, integrations, interactions, and more.
 *
 * @doc.type service
 * @doc.purpose Load and parse all org config YAML files
 * @doc.layer infrastructure
 * @doc.pattern Service
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';
import * as yaml from 'js-yaml';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// ============================================================================
// Types for Organization Configuration
// ============================================================================

export interface AgentYaml {
    apiVersion: string;
    kind: 'Agent';
    metadata: {
        name: string;
        labels?: Record<string, string>;
    };
    spec: {
        displayName: string;
        description?: string;
        role: {
            name: string;
            level: string;
            title: string;
        };
        department: string;
        reportingTo?: string;
        directReports?: string[];
        authority?: {
            decisionScope: string;
            budgetLimit: number;
            canApprove?: string[];
            canEscalateTo?: string[];
        };
        capabilities?: string[];
        personality?: {
            temperature: number;
            creativity: number;
            autonomy: number;
        };
        systemPrompt?: string;
        model?: {
            name: string;
            maxTokens: number;
        };
        [key: string]: unknown;
    };
}

export interface DepartmentYaml {
    apiVersion: string;
    kind: 'Department';
    metadata: {
        name: string;
        labels?: Record<string, string>;
    };
    spec: {
        displayName: string;
        type: string;
        description?: string;
        hierarchy?: {
            parent?: string | null;
            reportingTo?: string;
            children?: string[];
        };
        agents?: Array<{ ref: string }>;
        workflows?: Array<{ name: string; displayName: string }>;
        settings?: Record<string, unknown>;
        [key: string]: unknown;
    };
}

export interface WorkflowYaml {
    apiVersion: string;
    kind: 'Workflow';
    metadata: {
        name: string;
        namespace?: string;
        labels?: Record<string, string>;
    };
    spec: {
        trigger?: Record<string, unknown>;
        execution?: Record<string, unknown>;
        stages?: Array<{ name: string; displayName?: string }>;
        [key: string]: unknown;
    };
}

export interface IntegrationYaml {
    apiVersion: string;
    kind: 'Integration';
    metadata: {
        name: string;
    };
    spec: {
        displayName: string;
        type: string;
        description?: string;
        [key: string]: unknown;
    };
}

// ============================================================================
// Organization Config Loader Service
// ============================================================================

export class OrgConfigLoaderService {
    private configBasePath: string;
    private cache: Map<string, unknown> = new Map();

    constructor(configBasePath?: string) {
        this.configBasePath =
            configBasePath || path.join(__dirname, '../../../../config');
    }

    /**
     * Load a single YAML file
     */
    private loadYamlFile<T>(relativePath: string): T | null {
        const fullPath = path.join(this.configBasePath, relativePath);

        // Check cache first
        if (this.cache.has(fullPath)) {
            return this.cache.get(fullPath) as T;
        }

        try {
            if (!fs.existsSync(fullPath)) {
                console.warn(`[OrgConfigLoader] File not found: ${fullPath}`);
                return null;
            }

            const content = fs.readFileSync(fullPath, 'utf8');
            const parsed = yaml.load(content) as T;

            // Cache the result
            this.cache.set(fullPath, parsed);

            return parsed;
        } catch (error) {
            console.error(
                `[OrgConfigLoader] Error loading ${fullPath}:`,
                error
            );
            return null;
        }
    }

    /**
     * Load all YAML files from a directory
     */
    private loadYamlDirectory<T>(relativePath: string): T[] {
        const fullPath = path.join(this.configBasePath, relativePath);
        const results: T[] = [];

        try {
            if (!fs.existsSync(fullPath)) {
                console.warn(
                    `[OrgConfigLoader] Directory not found: ${fullPath}`
                );
                return results;
            }

            const files = fs
                .readdirSync(fullPath)
                .filter(
                    (file) =>
                        file.endsWith('.yaml') || file.endsWith('.yml')
                );

            for (const file of files) {
                const filePath = path.join(fullPath, file);
                try {
                    const content = fs.readFileSync(filePath, 'utf8');
                    const parsed = yaml.load(content) as T;
                    if (parsed) {
                        results.push(parsed);
                    }
                } catch (error) {
                    console.error(
                        `[OrgConfigLoader] Error loading file ${file}:`,
                        error
                    );
                }
            }

            return results;
        } catch (error) {
            console.error(
                `[OrgConfigLoader] Error reading directory ${fullPath}:`,
                error
            );
            return results;
        }
    }

    /**
     * Load all agents from agents/ directory
     */
    async loadAllAgents(): Promise<AgentYaml[]> {
        return this.loadYamlDirectory<AgentYaml>('agents');
    }

    /**
     * Load a single agent by name
     */
    async loadAgent(name: string): Promise<AgentYaml | null> {
        return this.loadYamlFile<AgentYaml>(`agents/${name}.yaml`);
    }

    /**
     * Load all departments from departments/ directory
     */
    async loadAllDepartments(): Promise<DepartmentYaml[]> {
        return this.loadYamlDirectory<DepartmentYaml>('departments');
    }

    /**
     * Load a single department by name
     */
    async loadDepartment(name: string): Promise<DepartmentYaml | null> {
        return this.loadYamlFile<DepartmentYaml>(`departments/${name}.yaml`);
    }

    /**
     * Load all workflows from workflows/ directory
     */
    async loadAllWorkflows(): Promise<WorkflowYaml[]> {
        return this.loadYamlDirectory<WorkflowYaml>('workflows');
    }

    /**
     * Load a single workflow by name
     */
    async loadWorkflow(name: string): Promise<WorkflowYaml | null> {
        return this.loadYamlFile<WorkflowYaml>(`workflows/${name}.yaml`);
    }

    /**
     * Load all integrations from integrations/ directory
     */
    async loadAllIntegrations(): Promise<IntegrationYaml[]> {
        return this.loadYamlDirectory<IntegrationYaml>('integrations');
    }

    /**
     * Load a single integration by name
     */
    async loadIntegration(name: string): Promise<IntegrationYaml | null> {
        return this.loadYamlFile<IntegrationYaml>(
            `integrations/${name}.yaml`
        );
    }

    /**
     * Load the main organization config
     */
    async loadOrganization() {
        return this.loadYamlFile<any>('organization.yaml');
    }

    /**
     * Load flows configuration
     */
    async loadFlows() {
        return this.loadYamlFile<any>('flows.yaml');
    }

    /**
     * Load all configuration data
     */
    async loadAllConfig() {
        const [agents, departments, workflows, integrations, organization, flows] =
            await Promise.all([
                this.loadAllAgents(),
                this.loadAllDepartments(),
                this.loadAllWorkflows(),
                this.loadAllIntegrations(),
                this.loadOrganization(),
                this.loadFlows(),
            ]);

        return {
            agents,
            departments,
            workflows,
            integrations,
            organization,
            flows,
        };
    }

    /**
     * Clear the cache
     */
    clearCache(): void {
        this.cache.clear();
    }
}

// Create and export singleton instance
const loaderInstance = new OrgConfigLoaderService();

export function getOrgConfigLoader(): OrgConfigLoaderService {
    return loaderInstance;
}
