/**
 * Organization API Client
 *
 * <p><b>Purpose</b><br>
 * Client for interacting with the Organization API backend.
 * Provides type-safe methods for all organization operations.
 *
 * <p><b>Features</b><br>
 * - Organization configuration retrieval
 * - Hierarchy graph operations
 * - Node movement
 * - Department and agent listing
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const config = await organizationApi.getConfig();
 * const graph = await organizationApi.getGraph();
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Organization API client
 * @doc.layer product
 * @doc.pattern Service
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export interface OrgConfig {
    name: string;
    namespace: string;
    displayName: string;
    description: string;
    structure: {
        type: string;
        maxDepth: number;
    };
    settings: {
        defaultTimezone: string;
        events: {
            enabled: boolean;
            publishTo: string;
            eventPrefix: string;
        };
        hitl: {
            enabled: boolean;
            confidenceThreshold: number;
        };
        ai: {
            enabled: boolean;
            primaryProvider: string;
            fallbackProvider: string;
        };
    };
    departments: Array<{
        ref: string;
    }>;
    workflows: Array<any>;
    organizationKpis: Array<any>;
}

export interface OrgNode {
    id: string;
    label: string;
    type: string;
    metadata: Record<string, any>;
}

export interface OrgEdge {
    from: string;
    to: string;
    type: string;
}

export interface OrgGraph {
    nodes: OrgNode[];
    edges: OrgEdge[];
    metadata: {
        nodeCount: number;
        edgeCount: number;
        maxDepth: number;
    };
}

export interface MoveRequest {
    nodeId: string;
    nodeType: string;
    fromParentId?: string;
    toParentId: string;
    position?: number;
}

export interface MoveResult {
    success: boolean;
    nodeId: string;
    newParentId?: string;
    message: string;
    metadata: Record<string, any>;
}

export interface DepartmentSummary {
    id: string;
    name: string;
    agentCount: number;
}

export interface AgentSummary {
    id: string;
    name: string;
    role: string;
    department: string;
    status: string;
}

class OrganizationApiClient {
    private baseUrl: string;

    constructor(baseUrl: string = API_BASE_URL) {
        this.baseUrl = baseUrl;
    }

    /**
     * Get organization configuration
     */
    async getConfig(): Promise<OrgConfig> {
        const response = await fetch(`${this.baseUrl}/api/v1/org/config`);
        if (!response.ok) {
            throw new Error(`Failed to fetch org config: ${response.statusText}`);
        }
        return response.json();
    }

    /**
     * Get organization hierarchy graph
     */
    async getGraph(): Promise<OrgGraph> {
        const response = await fetch(`${this.baseUrl}/api/v1/org/graph`);
        if (!response.ok) {
            throw new Error(`Failed to fetch org graph: ${response.statusText}`);
        }
        return response.json();
    }

    /**
     * Move a node in the hierarchy
     */
    async moveNode(request: MoveRequest): Promise<MoveResult> {
        const response = await fetch(`${this.baseUrl}/api/v1/org/hierarchy/move`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(request),
        });
        if (!response.ok) {
            throw new Error(`Failed to move node: ${response.statusText}`);
        }
        return response.json();
    }

    /**
     * List all departments
     */
    async listDepartments(): Promise<DepartmentSummary[]> {
        const response = await fetch(`${this.baseUrl}/api/v1/org/departments`);
        if (!response.ok) {
            throw new Error(`Failed to list departments: ${response.statusText}`);
        }
        return response.json();
    }

    /**
     * List all agents
     */
    async listAgents(): Promise<AgentSummary[]> {
        const response = await fetch(`${this.baseUrl}/api/v1/org/agents`);
        if (!response.ok) {
            throw new Error(`Failed to list agents: ${response.statusText}`);
        }
        return response.json();
    }
}

export const organizationApi = new OrganizationApiClient();
export default organizationApi;
