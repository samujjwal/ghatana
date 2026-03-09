/**
 * Configuration API Routes
 *
 * <p><b>Purpose</b><br>
 * REST API endpoints for managing organization configuration entities.
 * Provides CRUD operations for departments, personas, phases, stages,
 * services, integrations, flows, and operators.
 *
 * <p><b>Endpoints</b><br>
 * - GET /api/v1/config - Get full org configuration
 * - GET /api/v1/config/departments - List departments
 * - GET /api/v1/config/departments/:id - Get department
 * - GET /api/v1/config/personas - List personas
 * - GET /api/v1/config/phases - List phases
 * - GET /api/v1/config/stages - List stage mappings
 * - GET /api/v1/config/services - List services
 * - GET /api/v1/config/integrations - List integrations
 * - GET /api/v1/config/flows - List flows
 * - GET /api/v1/config/operators - List operators
 * - GET /api/v1/config/agents - List all agents
 * - GET /api/v1/config/workflows - List all workflows
 * - GET /api/v1/config/kpis - List all KPIs
 *
 * @doc.type route
 * @doc.purpose Configuration API endpoints
 * @doc.layer infrastructure
 * @doc.pattern REST API
 */

import { Router, Request, Response } from 'express';
import type { Router as RouterType } from 'express';
import { getConfigLoader } from '../services/config-loader.service';

const router: RouterType = Router();

// ============================================================================
// Helper Functions
// ============================================================================

interface ApiResponse<T> {
    data: T;
    success: boolean;
    message?: string;
    timestamp: string;
}

function successResponse<T>(data: T, message?: string): ApiResponse<T> {
    return {
        data,
        success: true,
        message,
        timestamp: new Date().toISOString(),
    };
}

function errorResponse(message: string): ApiResponse<null> {
    return {
        data: null,
        success: false,
        message,
        timestamp: new Date().toISOString(),
    };
}

// ============================================================================
// Full Configuration
// ============================================================================

/**
 * GET /api/v1/config
 * Get the complete organization configuration
 */
router.get('/', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const orgConfig = await configLoader.loadOrgConfig();
        res.json(successResponse(orgConfig));
    } catch (error) {
        console.error('[Config API] Error loading org config:', error);
        res.status(500).json(errorResponse('Failed to load organization configuration'));
    }
});

/**
 * POST /api/v1/config/reload
 * Reload configuration from files (clears cache)
 */
router.post('/reload', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        configLoader.clearCache();
        const orgConfig = await configLoader.loadOrgConfig();
        res.json(successResponse(orgConfig, 'Configuration reloaded'));
    } catch (error) {
        console.error('[Config API] Error reloading config:', error);
        res.status(500).json(errorResponse('Failed to reload configuration'));
    }
});

// ============================================================================
// Departments
// ============================================================================

/**
 * GET /api/v1/config/departments
 * List all departments
 */
router.get('/departments', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const departments = await configLoader.loadAllDepartments();
        res.json(successResponse(departments));
    } catch (error) {
        console.error('[Config API] Error loading departments:', error);
        res.status(500).json(errorResponse('Failed to load departments'));
    }
});

/**
 * GET /api/v1/config/departments/:id
 * Get a single department by ID
 */
router.get('/departments/:id', async (req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const department = await configLoader.loadDepartmentConfig(req.params.id);
        
        if (!department) {
            return res.status(404).json(errorResponse(`Department not found: ${req.params.id}`));
        }
        
        res.json(successResponse(department));
    } catch (error) {
        console.error('[Config API] Error loading department:', error);
        res.status(500).json(errorResponse('Failed to load department'));
    }
});

// ============================================================================
// Personas
// ============================================================================

/**
 * GET /api/v1/config/personas
 * List all personas
 */
router.get('/personas', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const personas = await configLoader.loadAllPersonas();
        res.json(successResponse(personas));
    } catch (error) {
        console.error('[Config API] Error loading personas:', error);
        res.status(500).json(errorResponse('Failed to load personas'));
    }
});

/**
 * GET /api/v1/config/personas/:id
 * Get a single persona by ID
 */
router.get('/personas/:id', async (req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const persona = await configLoader.loadPersonaConfig(req.params.id);
        
        if (!persona) {
            return res.status(404).json(errorResponse(`Persona not found: ${req.params.id}`));
        }
        
        res.json(successResponse(persona));
    } catch (error) {
        console.error('[Config API] Error loading persona:', error);
        res.status(500).json(errorResponse('Failed to load persona'));
    }
});

// ============================================================================
// Phases
// ============================================================================

/**
 * GET /api/v1/config/phases
 * List all phases
 */
router.get('/phases', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const phases = await configLoader.loadPhases();
        res.json(successResponse(phases));
    } catch (error) {
        console.error('[Config API] Error loading phases:', error);
        res.status(500).json(errorResponse('Failed to load phases'));
    }
});

/**
 * GET /api/v1/config/phases/:id
 * Get a single phase by ID
 */
router.get('/phases/:id', async (req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const phases = await configLoader.loadPhases();
        const phase = phases.find(p => p.id === req.params.id);
        
        if (!phase) {
            return res.status(404).json(errorResponse(`Phase not found: ${req.params.id}`));
        }
        
        res.json(successResponse(phase));
    } catch (error) {
        console.error('[Config API] Error loading phase:', error);
        res.status(500).json(errorResponse('Failed to load phase'));
    }
});

// ============================================================================
// Stages
// ============================================================================

/**
 * GET /api/v1/config/stages
 * List all stage mappings
 */
router.get('/stages', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const stages = await configLoader.loadStageMappings();
        res.json(successResponse(stages));
    } catch (error) {
        console.error('[Config API] Error loading stages:', error);
        res.status(500).json(errorResponse('Failed to load stages'));
    }
});

// ============================================================================
// Services
// ============================================================================

/**
 * GET /api/v1/config/services
 * List all services
 */
router.get('/services', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const services = await configLoader.loadAllServices();
        res.json(successResponse(services));
    } catch (error) {
        console.error('[Config API] Error loading services:', error);
        res.status(500).json(errorResponse('Failed to load services'));
    }
});

/**
 * GET /api/v1/config/services/:id
 * Get a single service by ID
 */
router.get('/services/:id', async (req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const service = await configLoader.loadServiceConfig(req.params.id);
        
        if (!service) {
            return res.status(404).json(errorResponse(`Service not found: ${req.params.id}`));
        }
        
        res.json(successResponse(service));
    } catch (error) {
        console.error('[Config API] Error loading service:', error);
        res.status(500).json(errorResponse('Failed to load service'));
    }
});

// ============================================================================
// Integrations
// ============================================================================

/**
 * GET /api/v1/config/integrations
 * List all integrations
 */
router.get('/integrations', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const integrations = await configLoader.loadAllIntegrations();
        res.json(successResponse(integrations));
    } catch (error) {
        console.error('[Config API] Error loading integrations:', error);
        res.status(500).json(errorResponse('Failed to load integrations'));
    }
});

/**
 * GET /api/v1/config/integrations/:id
 * Get a single integration by ID
 */
router.get('/integrations/:id', async (req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const integration = await configLoader.loadIntegrationConfig(req.params.id);
        
        if (!integration) {
            return res.status(404).json(errorResponse(`Integration not found: ${req.params.id}`));
        }
        
        res.json(successResponse(integration));
    } catch (error) {
        console.error('[Config API] Error loading integration:', error);
        res.status(500).json(errorResponse('Failed to load integration'));
    }
});

// ============================================================================
// Flows
// ============================================================================

/**
 * GET /api/v1/config/flows
 * List all DevSecOps flows
 */
router.get('/flows', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const flows = await configLoader.loadFlows();
        res.json(successResponse(flows));
    } catch (error) {
        console.error('[Config API] Error loading flows:', error);
        res.status(500).json(errorResponse('Failed to load flows'));
    }
});

/**
 * GET /api/v1/config/flows/:id
 * Get a single flow by ID
 */
router.get('/flows/:id', async (req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const flow = await configLoader.loadFlow(req.params.id);
        
        if (!flow) {
            return res.status(404).json(errorResponse(`Flow not found: ${req.params.id}`));
        }
        
        res.json(successResponse(flow));
    } catch (error) {
        console.error('[Config API] Error loading flow:', error);
        res.status(500).json(errorResponse('Failed to load flow'));
    }
});

// ============================================================================
// Operators
// ============================================================================

/**
 * GET /api/v1/config/operators
 * List all operators
 */
router.get('/operators', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const operators = await configLoader.loadOperators();
        res.json(successResponse(operators));
    } catch (error) {
        console.error('[Config API] Error loading operators:', error);
        res.status(500).json(errorResponse('Failed to load operators'));
    }
});

/**
 * GET /api/v1/config/operators/:id
 * Get a single operator by ID
 */
router.get('/operators/:id', async (req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const operator = await configLoader.loadOperator(req.params.id);
        
        if (!operator) {
            return res.status(404).json(errorResponse(`Operator not found: ${req.params.id}`));
        }
        
        res.json(successResponse(operator));
    } catch (error) {
        console.error('[Config API] Error loading operator:', error);
        res.status(500).json(errorResponse('Failed to load operator'));
    }
});

// ============================================================================
// Aggregated Endpoints
// ============================================================================

/**
 * GET /api/v1/config/agents
 * List all agents across all departments
 */
router.get('/agents', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const agents = await configLoader.loadAllAgents();
        res.json(successResponse(agents));
    } catch (error) {
        console.error('[Config API] Error loading agents:', error);
        res.status(500).json(errorResponse('Failed to load agents'));
    }
});

/**
 * GET /api/v1/config/workflows
 * List all workflows across all departments
 */
router.get('/workflows', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const workflows = await configLoader.loadAllWorkflows();
        res.json(successResponse(workflows));
    } catch (error) {
        console.error('[Config API] Error loading workflows:', error);
        res.status(500).json(errorResponse('Failed to load workflows'));
    }
});

/**
 * GET /api/v1/config/kpis
 * List all KPIs across all departments
 */
router.get('/kpis', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const kpis = await configLoader.loadAllKpis();
        res.json(successResponse(kpis));
    } catch (error) {
        console.error('[Config API] Error loading KPIs:', error);
        res.status(500).json(errorResponse('Failed to load KPIs'));
    }
});

// ============================================================================
// Graph Data
// ============================================================================

/**
 * GET /api/v1/config/graph
 * Get organization graph data for visualization
 */
router.get('/graph', async (_req: Request, res: Response) => {
    try {
        const configLoader = getConfigLoader();
        const orgConfig = await configLoader.loadOrgConfig();
        
        // Transform to graph format
        const nodes: Array<{
            id: string;
            type: string;
            label: string;
            data: unknown;
        }> = [];
        
        const edges: Array<{
            id: string;
            source: string;
            target: string;
            type: string;
        }> = [];
        
        // Add department nodes
        for (const dept of orgConfig.departments) {
            nodes.push({
                id: dept.id,
                type: 'department',
                label: dept.name,
                data: dept,
            });
        }
        
        // Add service nodes and edges
        for (const svc of orgConfig.services) {
            nodes.push({
                id: svc.id,
                type: 'service',
                label: svc.name,
                data: svc,
            });
            
            // Edge: department owns service
            edges.push({
                id: `edge-${svc.department_id}-${svc.id}`,
                source: svc.department_id,
                target: svc.id,
                type: 'owns',
            });
            
            // Edges: service dependencies
            if (svc.dependencies) {
                for (const depId of svc.dependencies) {
                    edges.push({
                        id: `edge-${svc.id}-${depId}`,
                        source: svc.id,
                        target: depId,
                        type: 'depends-on',
                    });
                }
            }
        }
        
        // Add integration nodes
        for (const int of orgConfig.integrations) {
            nodes.push({
                id: int.id,
                type: 'integration',
                label: int.name,
                data: int,
            });
        }
        
        // Add persona nodes
        for (const persona of orgConfig.personas) {
            nodes.push({
                id: `persona-${persona.id}`,
                type: 'persona',
                label: persona.display_name,
                data: persona,
            });
        }
        
        res.json(successResponse({ nodes, edges }));
    } catch (error) {
        console.error('[Config API] Error generating graph:', error);
        res.status(500).json(errorResponse('Failed to generate organization graph'));
    }
});

export default router;
