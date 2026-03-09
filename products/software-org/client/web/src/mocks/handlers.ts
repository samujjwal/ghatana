import { http, HttpResponse, delay, passthrough } from "msw";

// KPI DTO types aligned with apps/web src/services/api/kpisApi.ts
interface KpiResponse {
    id: string;
    name: string;
    value: number;
    unit: string;
    trend: number; // percentage change
    target?: number;
    lastUpdated: string;
}

interface KpiTrendResponse {
    timestamp: string;
    value: number;
}

interface DepartmentKpiResponse {
    departmentId: string;
    departmentName: string;
    kpis: KpiResponse[];
}

const nowIso = () => new Date().toISOString();

const mockKpis: KpiResponse[] = [
    {
        id: "deployments",
        name: "Deployments",
        value: 156,
        unit: "/week",
        trend: 23,
        target: 150,
        lastUpdated: nowIso(),
    },
    {
        id: "change-failure-rate",
        name: "Change Failure Rate",
        value: 3.2,
        unit: "%",
        trend: -12,
        target: 5,
        lastUpdated: nowIso(),
    },
    {
        id: "lead-time",
        name: "Lead Time",
        value: 3.2,
        unit: "h",
        trend: -45,
        target: 4,
        lastUpdated: nowIso(),
    },
    {
        id: "mttr",
        name: "MTTR",
        value: 12,
        unit: "m",
        trend: -67,
        target: 15,
        lastUpdated: nowIso(),
    },
    {
        id: "security-issues",
        name: "Security Issues",
        value: 0,
        unit: "critical",
        trend: 0,
        lastUpdated: nowIso(),
    },
    {
        id: "cost-savings",
        name: "Cost Savings",
        value: 2400,
        unit: "USD/mo",
        trend: 30,
        lastUpdated: nowIso(),
    },
];

const mockTrends = (kpiId: string): KpiTrendResponse[] => {
    const base = mockKpis.find((k) => k.id === kpiId)?.value ?? 100;
    return Array.from({ length: 10 }).map((_, idx) => ({
        timestamp: new Date(Date.now() - (9 - idx) * 60 * 60 * 1000).toISOString(),
        value: base + Math.sin(idx / 2) * 10,
    }));
};

const mockDepartmentKpis: DepartmentKpiResponse[] = [
    {
        departmentId: "eng",
        departmentName: "Engineering",
        kpis: mockKpis,
    },
    {
        departmentId: "ops",
        departmentName: "Operations",
        kpis: mockKpis,
    },
];

// Reusable mock models for the catalog (placed after department KPIs)
const mockModels = [
    {
        id: 'model-1',
        name: 'Churn Predictor',
        status: 'deployed',
        createdAt: nowIso(),
        accuracy: 0.92,
        precision: 0.9,
        recall: 0.88,
        f1Score: 0.89,
        currentVersion: '2.3.1',
        latency: 120,
        throughput: 125,
        deployedAt: nowIso(),
        lastUpdated: nowIso(),
        type: 'classification',
    },
    {
        id: 'model-2',
        name: 'Fraud Detector',
        status: 'training',
        createdAt: nowIso(),
        accuracy: 0.78,
        precision: 0.81,
        recall: 0.75,
        f1Score: 0.78,
        currentVersion: '1.0.0',
        latency: 220,
        throughput: 40,
        deployedAt: null,
        lastUpdated: nowIso(),
        type: 'classification',
    },
];

export const handlers = [
    // ============================================================================
    // Configuration API Handlers
    // ============================================================================

    // GET /api/v1/config - Full organization configuration
    http.get("/api/v1/config", async () => {
        // Also match relative path for axios with baseURL
    }),
    http.get("*/config", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config mock hit");

        return HttpResponse.json({
            data: {
                id: 'software-org',
                name: 'Software Organization',
                description: 'Virtual software organization',
                version: '1.0.0',
                departments: [
                    { id: 'engineering', name: 'Engineering', type: 'ENGINEERING', description: 'Software development' },
                    { id: 'devops', name: 'DevOps', type: 'DEVOPS', description: 'Infrastructure and operations' },
                ],
                personas: [
                    { id: 'product_manager', display_name: 'Product Manager', tags: ['product'], permissions: ['read:all'] },
                    { id: 'backend_engineer', display_name: 'Backend Engineer', tags: ['engineering'], permissions: ['write:code'] },
                ],
                phases: [
                    { id: 'PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY', display_name: 'Problem Discovery', personas: ['product_manager'] },
                    { id: 'PRODUCT_LIFECYCLE_PHASE_BUILD_AND_INTEGRATE', display_name: 'Build & Integrate', personas: ['backend_engineer'] },
                ],
                stages: [],
                services: [],
                integrations: [],
                flows: [],
                operators: [],
                metadata: {
                    loaded_at: nowIso(),
                    config_path: 'mock',
                },
            },
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/departments
    http.get("*/config/departments", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/departments mock hit");

        return HttpResponse.json({
            data: [
                { id: 'engineering', name: 'Engineering', type: 'ENGINEERING', description: 'Core software development and technical infrastructure. Responsible for building and maintaining all product features.' },
                { id: 'devops', name: 'DevOps', type: 'DEVOPS', description: 'Infrastructure, deployment, and site reliability. Ensures system uptime and deployment automation.' },
                { id: 'security', name: 'Security', type: 'SECURITY', description: 'Application and infrastructure security. Responsible for vulnerability management and compliance.' },
                { id: 'product', name: 'Product', type: 'PRODUCT', description: 'Product management and strategy. Defines roadmap and prioritizes features.' },
            ],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/personas
    http.get("*/config/personas", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/personas mock hit");

        return HttpResponse.json({
            data: [
                { id: 'product_manager', display_name: 'Product Manager', description: 'Defines product strategy and roadmap', tags: ['product'], permissions: ['read:all', 'write:roadmap', 'approve:features'] },
                { id: 'backend_engineer', display_name: 'Backend Engineer', description: 'Builds and maintains backend services', tags: ['engineering'], permissions: ['write:code', 'read:metrics', 'deploy:staging'] },
                { id: 'frontend_engineer', display_name: 'Frontend Engineer', description: 'Builds user interfaces and experiences', tags: ['engineering'], permissions: ['write:code', 'read:metrics'] },
                { id: 'sre', display_name: 'Site Reliability Engineer', description: 'Ensures system reliability and performance', tags: ['operations'], permissions: ['deploy:production', 'manage:infrastructure', 'respond:incidents'] },
                { id: 'security_engineer', display_name: 'Security Engineer', description: 'Manages application and infrastructure security', tags: ['security'], permissions: ['audit:security', 'manage:vulnerabilities'] },
            ],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/phases
    http.get("*/config/phases", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/phases mock hit");

        return HttpResponse.json({
            data: [
                { id: 'PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY', display_name: 'Problem Discovery', description: 'Identify and validate customer problems', personas: ['product_manager'] },
                { id: 'PRODUCT_LIFECYCLE_PHASE_SOLUTION_DESIGN', display_name: 'Solution Design', description: 'Design solutions to validated problems', personas: ['product_manager', 'backend_engineer'] },
                { id: 'PRODUCT_LIFECYCLE_PHASE_BUILD_AND_INTEGRATE', display_name: 'Build & Integrate', description: 'Implement and integrate solutions', personas: ['backend_engineer'] },
                { id: 'PRODUCT_LIFECYCLE_PHASE_VALIDATE', display_name: 'Validate', description: 'Test and validate implementations', personas: ['backend_engineer', 'sre'] },
                { id: 'PRODUCT_LIFECYCLE_PHASE_RELEASE', display_name: 'Release', description: 'Deploy to production environments', personas: ['sre'] },
                { id: 'PRODUCT_LIFECYCLE_PHASE_OPERATE', display_name: 'Operate', description: 'Monitor and maintain production systems', personas: ['sre'] },
            ],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/stages
    http.get("*/config/stages", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/stages mock hit");

        return HttpResponse.json({
            data: [
                { stage: 'stage-plan', phases: ['PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY', 'PRODUCT_LIFECYCLE_PHASE_SOLUTION_DESIGN'] },
                { stage: 'stage-develop', phases: ['PRODUCT_LIFECYCLE_PHASE_BUILD_AND_INTEGRATE'] },
                { stage: 'stage-test', phases: ['PRODUCT_LIFECYCLE_PHASE_VALIDATE'] },
                { stage: 'stage-deploy', phases: ['PRODUCT_LIFECYCLE_PHASE_RELEASE', 'PRODUCT_LIFECYCLE_PHASE_OPERATE'] },
            ],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/services
    http.get("*/config/services", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/services mock hit");

        return HttpResponse.json({
            data: [],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/integrations
    http.get("*/config/integrations", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/integrations mock hit");

        return HttpResponse.json({
            data: [],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/flows
    http.get("*/config/flows", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/flows mock hit");

        return HttpResponse.json({
            data: [],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/operators
    http.get("*/config/operators", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/operators mock hit");

        return HttpResponse.json({
            data: [],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/agents
    http.get("*/config/agents", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/agents mock hit");

        return HttpResponse.json({
            data: [
                { id: 'agent-build', name: 'Build Agent', role: 'CI/CD Automation', department: 'engineering', capabilities: ['compile', 'package', 'artifact-upload'] },
                { id: 'agent-security', name: 'Security Agent', role: 'Security Scanning', department: 'security', capabilities: ['vulnerability-scan', 'sast', 'dast'] },
                { id: 'agent-deploy', name: 'Deploy Agent', role: 'Deployment Automation', department: 'devops', capabilities: ['deploy', 'rollback', 'health-check'] },
                { id: 'agent-triage', name: 'Triage Agent', role: 'Incident Triage', department: 'devops', capabilities: ['incident-analysis', 'context-gathering'] },
            ],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/workflows
    http.get("*/config/workflows", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/workflows mock hit");

        return HttpResponse.json({
            data: [
                { id: 'wf-ci-cd-pipeline', name: 'CI/CD Pipeline', trigger: { event: 'git.push' }, steps: [{ id: 'step-1', agent: 'build-agent', action: 'compile' }] },
                { id: 'wf-security-scan', name: 'Security Scan', trigger: { event: 'schedule' }, steps: [{ id: 'step-1', agent: 'security-agent', action: 'scan' }] },
                { id: 'wf-incident-response', name: 'Incident Response', trigger: { event: 'alert.triggered' }, steps: [{ id: 'step-1', agent: 'triage-agent', action: 'analyze' }] },
            ],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/kpis
    http.get("*/config/kpis", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/kpis mock hit");

        return HttpResponse.json({
            data: [
                { id: 'kpi-deployment-frequency', name: 'Deployment Frequency', target: '> 10 per week', measurement: 'Count of production deployments' },
                { id: 'kpi-lead-time', name: 'Lead Time for Changes', target: '< 1 day', measurement: 'Median time from commit to deploy' },
                { id: 'kpi-mttr', name: 'Mean Time to Recovery', target: '< 1 hour', measurement: 'Average incident resolution time' },
                { id: 'kpi-change-failure-rate', name: 'Change Failure Rate', target: '< 5%', measurement: 'Failed deployments / Total deployments' },
            ],
            success: true,
            timestamp: nowIso(),
        });
    }),

    // GET /api/v1/config/graph
    http.get("*/config/graph", async () => {
        await delay(150);
        console.debug("[MSW] /api/v1/config/graph mock hit");

        return HttpResponse.json({
            data: {
                nodes: [],
                edges: [],
            },
            success: true,
            timestamp: nowIso(),
        });
    }),

    // POST /api/v1/config/reload
    http.post("*/config/reload", async () => {
        await delay(200);
        console.debug("[MSW] /api/v1/config/reload mock hit");

        return HttpResponse.json({
            data: {
                id: 'software-org',
                name: 'Software Organization',
                description: 'Virtual software organization (reloaded)',
                version: '1.0.0',
                departments: [
                    { id: 'engineering', name: 'Engineering', type: 'ENGINEERING', description: 'Software development' },
                    { id: 'devops', name: 'DevOps', type: 'DEVOPS', description: 'Infrastructure and operations' },
                ],
                personas: [
                    { id: 'product_manager', display_name: 'Product Manager', tags: ['product'], permissions: ['read:all'] },
                    { id: 'backend_engineer', display_name: 'Backend Engineer', tags: ['engineering'], permissions: ['write:code'] },
                ],
                phases: [
                    { id: 'PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY', display_name: 'Problem Discovery', personas: ['product_manager'] },
                    { id: 'PRODUCT_LIFECYCLE_PHASE_BUILD_AND_INTEGRATE', display_name: 'Build & Integrate', personas: ['backend_engineer'] },
                ],
                stages: [],
                services: [],
                integrations: [],
                flows: [],
                operators: [],
                metadata: {
                    loaded_at: nowIso(),
                    config_path: 'mock',
                },
            },
            success: true,
            message: 'Configuration reloaded successfully',
            timestamp: nowIso(),
        });
    }),

    // ============================================================================
    // KPI API Handlers
    // ============================================================================

    // Organization KPI summary
    // Align with app-creator style: match the relative path that axios
    // calls when baseURL is '/api/v1'.
    http.get("/api/v1/kpis", async ({ request }) => {
        await delay(150);
        const url = new URL(request.url);
        const timeRange = url.searchParams.get("timeRange") ?? "7d";

        console.debug("[MSW] /api/v1/kpis mock hit", { timeRange });

        return HttpResponse.json(mockKpis);
    }),

    // KPI trends
    http.get("/api/v1/kpis/:kpiId/trends", async ({ params, request }) => {
        await delay(150);
        const { kpiId } = params as { kpiId: string };
        const url = new URL(request.url);
        const timeRange = url.searchParams.get("timeRange") ?? "7d";

        console.debug("[MSW] /api/v1/kpis/:kpiId/trends mock hit", { kpiId, timeRange });

        return HttpResponse.json(mockTrends(kpiId));
    }),

    // Per-department KPI breakdown
    http.get("/api/v1/kpis/departments", async ({ request }) => {
        await delay(150);
        const url = new URL(request.url);
        const timeRange = url.searchParams.get("timeRange") ?? "7d";

        console.debug("[MSW] /api/v1/kpis/departments mock hit", { timeRange });

        return HttpResponse.json(mockDepartmentKpis);
    }),

    // AI-generated KPI narratives
    http.get("/api/v1/kpis/narratives", async ({ request }) => {
        await delay(200);
        const url = new URL(request.url);
        const timeRange = url.searchParams.get("timeRange") ?? "7d";

        console.debug("[MSW] /api/v1/kpis/narratives mock hit", { timeRange });

        return HttpResponse.json([
            {
                insight:
                    "Deployment frequency is trending upward with low change failure rate, indicating healthy delivery pipelines.",
                confidence: 0.92,
            },
            {
                insight:
                    "MTTR remains below target, suggesting effective incident response and remediation practices.",
                confidence: 0.88,
            },
        ]);
    }),

    // Audit trail endpoints
    http.post("*/api/v1/audit/decisions", ({ request }) => {
        return HttpResponse.json(
            {
                id: `decision-${Date.now()}`,
                success: true,
                message: "Decision recorded successfully",
            },
            { status: 201 }
        );
    }),

    http.get("*/api/v1/audit/trails/:entityType/:entityId", ({ params }) => {
        const { entityType, entityId } = params;
        return HttpResponse.json(
            {
                total: 5,
                records: [
                    {
                        id: "audit-1",
                        entityType,
                        entityId,
                        decision: "approve",
                        reason: "Approved by reviewer",
                        userId: "user-1",
                        userName: "John Doe",
                        timestamp: nowIso(),
                    },
                ],
                avgApprovalRate: 80,
                avgDeferRate: 15,
                avgRejectionRate: 5,
            }
        );
    }),

    // Workflow execution endpoints
    http.post("*/api/v1/workflows/:workflowId/execute", ({ params }) => {
        return HttpResponse.json(
            {
                id: `exec-${Date.now()}`,
                workflowId: params.workflowId,
                status: "pending",
                startedAt: nowIso(),
                triggeredBy: "user-demo",
            },
            { status: 201 }
        );
    }),

    http.get("*/api/v1/executions/:executionId", ({ params }) => {
        return HttpResponse.json({
            id: params.executionId,
            workflowId: "workflow-123",
            status: "running",
            startedAt: nowIso(),
            triggeredBy: "user-demo",
            logs: ["Execution started", "Processing items..."],
        });
    }),

    http.post("*/api/v1/executions/:executionId/cancel", ({ params }) => {
        return HttpResponse.json(
            {
                id: params.executionId,
                status: "cancelled",
            },
            { status: 200 }
        );
    }),

    // Report scheduling endpoints
    http.post("*/api/v1/reports/:reportId/schedule", ({ params, request }) => {
        return HttpResponse.json(
            {
                id: `schedule-${Date.now()}`,
                reportId: params.reportId,
                frequency: "weekly",
                enabled: true,
                createdAt: nowIso(),
            },
            { status: 201 }
        );
    }),

    http.get("*/api/v1/reports/schedules", () => {
        return HttpResponse.json({
            schedules: [
                {
                    id: "schedule-1",
                    reportId: "weekly-kpis",
                    frequency: "weekly",
                    dayOfWeek: "Monday",
                    time: "09:00",
                    recipients: ["admin@example.com"],
                    formats: ["pdf", "csv"],
                    enabled: true,
                    nextRun: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
                },
            ],
        });
    }),

    // Bulk actions endpoints
    http.post("*/api/v1/bulk/actions/:actionType", async ({ params, request }) => {
        const body = (await request.json()) as { ids: string[] };
        return HttpResponse.json(
            {
                success: true,
                action: params.actionType,
                itemsProcessed: body.ids.length,
                results: body.ids.map((id) => ({
                    id,
                    status: "success",
                })),
            },
            { status: 200 }
        );
    }),

    // Reporting metrics (POST /api/v1/reports/metrics)
    http.post("*/api/v1/reports/metrics", async ({ request }) => {
        const body = (await request.json()) as {
            dateRange: { start: string; end: string };
            departments: string[];
            tenantId: string;
        };
        // Return synthetic report metrics per department
        const metrics = (body.departments || ["eng", "ops"]).map((d) => ({
            departmentName: d,
            velocity: Math.round(100 + Math.random() * 50),
            cycleTime: Math.round(2 + Math.random() * 8),
            deploymentFrequency: Math.round(5 + Math.random() * 10),
            coverage: Math.round(70 + Math.random() * 30),
            passRate: Math.round(80 + Math.random() * 20),
            trend: Math.random() > 0.5 ? "up" : "down",
        }));

        return HttpResponse.json(metrics, { status: 200 });
    }),

    // Generic metrics endpoint used by some pages: /api/v1/metrics
    http.get("/api/v1/metrics", async ({ request }) => {
        const url = new URL(request.url);
        const timeRange = url.searchParams.get("timeRange") ?? "7d";
        console.log('[MSW] ✅ /api/v1/metrics handler MATCHED - returning mock data');
        return HttpResponse.json({
            timestamp: nowIso(),
            value: Math.round(100 + Math.random() * 100),
            timeRange,
        });
    }),

    // Also match with wildcard as fallback
    http.get("*/api/v1/metrics", async ({ request }) => {
        const url = new URL(request.url);
        const timeRange = url.searchParams.get("timeRange") ?? "7d";
        console.log('[MSW] ✅ */api/v1/metrics handler MATCHED - returning mock data');
        return HttpResponse.json({
            timestamp: nowIso(),
            value: Math.round(100 + Math.random() * 100),
            timeRange,
        });
    }),

    // Models and ML endpoints
    // Global models list (non-tenant)
    http.get("/api/v1/models", ({ request }) => {
        console.log('[MSW] ✅ /api/v1/models handler MATCHED - returning mock data');
        return HttpResponse.json([
            { id: "model-1", name: "Churn Predictor", status: "deployed", createdAt: nowIso(), accuracy: 0.92, precision: 0.9, recall: 0.88, f1Score: 0.89 },
            { id: "model-2", name: "Fraud Detector", status: "training", createdAt: nowIso(), accuracy: 0.78, precision: 0.81, recall: 0.75, f1Score: 0.78 },
        ]);
    }),

    // Also match with wildcard as fallback
    http.get("*/api/v1/models", ({ request }) => {
        console.log('[MSW] ✅ */api/v1/models handler MATCHED - returning mock data');
        return HttpResponse.json([
            { id: "model-1", name: "Churn Predictor", status: "deployed", createdAt: nowIso(), accuracy: 0.92, precision: 0.9, recall: 0.88, f1Score: 0.89 },
            { id: "model-2", name: "Fraud Detector", status: "training", createdAt: nowIso(), accuracy: 0.78, precision: 0.81, recall: 0.75, f1Score: 0.78 },
        ]);
    }),

    // GET /api/v1/models/:modelId - details
    http.get("/api/v1/models/:modelId", ({ params }) => {
        const { modelId } = params as { modelId: string };
        console.log('[MSW] ✅ /api/v1/models/:modelId handler MATCHED - modelId=', modelId);
        const model = mockModels.find((m) => m.id === modelId) || mockModels[0];
        return HttpResponse.json(model);
    }),

    // Wildcard handler for tenant-scoped routes
    http.get("*/api/v1/models/:modelId", ({ params }) => {
        const { modelId } = params as { modelId: string };
        console.log('[MSW] ✅ */api/v1/models/:modelId handler MATCHED - modelId=', modelId);
        const model = mockModels.find((m) => m.id === modelId) || mockModels[0];
        return HttpResponse.json(model);
    }),

    http.get("*/api/v1/tenants/:tenantId/models", ({ params }) => {
        const models = [
            { id: "model-1", name: "Churn Predictor", status: "deployed", createdAt: nowIso(), accuracy: 0.92, precision: 0.9, recall: 0.88, f1Score: 0.89 },
            { id: "model-2", name: "Fraud Detector", status: "training", createdAt: nowIso(), accuracy: 0.78, precision: 0.81, recall: 0.75, f1Score: 0.78 },
        ];
        return HttpResponse.json(models);
    }),

    http.get("*/api/v1/models/:modelId/metrics", ({ params }) => {
        const { modelId } = params as { modelId: string };
        return HttpResponse.json([
            { timestamp: nowIso(), accuracy: 0.92, latency: 120 },
            { timestamp: nowIso(), accuracy: 0.93, latency: 110 },
        ]);
    }),

    http.get("*/api/v1/models/:modelId/feature-importance", ({ params }) => {
        return HttpResponse.json([
            { name: "feature_a", importance: 0.4 },
            { name: "feature_b", importance: 0.3 },
            { name: "feature_c", importance: 0.15 },
        ]);
    }),

    // Compare models endpoint
    http.get('/api/v1/models/compare', ({ request }) => {
        const url = new URL(request.url);
        const modelId1 = url.searchParams.get('modelId1');
        const modelId2 = url.searchParams.get('modelId2');
        console.log('[MSW] ✅ /api/v1/models/compare handler MATCHED', { modelId1, modelId2 });
        return HttpResponse.json({
            model1: mockModels[0],
            model2: mockModels[1],
            winner: mockModels[0].id,
            metrics: [
                { name: 'accuracy', model1: 0.92, model2: 0.78, delta: 0.14 },
                { name: 'latency', model1: 120, model2: 220, delta: -100 },
            ],
        });
    }),
    http.get('*/api/v1/models/compare', ({ request }) => {
        const url = new URL(request.url);
        const modelId1 = url.searchParams.get('modelId1');
        const modelId2 = url.searchParams.get('modelId2');
        console.log('[MSW] ✅ */api/v1/models/compare handler MATCHED', { modelId1, modelId2 });
        return HttpResponse.json({
            model1: mockModels[0],
            model2: mockModels[1],
            winner: mockModels[0].id,
            metrics: [
                { name: 'accuracy', model1: 0.92, model2: 0.78, delta: 0.14 },
                { name: 'latency', model1: 120, model2: 220, delta: -100 },
            ],
        });
    }),

    // Model version history
    http.get("/api/v1/models/:modelId/versions", ({ params }) => {
        const { modelId } = params as { modelId: string };
        const versions = [
            { version: '2.3.1', createdAt: nowIso(), accuracy: 0.956, precision: 0.95, recall: 0.94, f1Score: 0.945, latency: 120, throughput: 100, status: 'Current' },
            { version: '2.3.0', createdAt: nowIso(), accuracy: 0.951, precision: 0.94, recall: 0.93, f1Score: 0.935, latency: 125, throughput: 90, status: 'Previous' },
        ];
        return HttpResponse.json(versions);
    }),

    http.get("*/api/v1/models/:modelId/versions", ({ params }) => {
        const { modelId } = params as { modelId: string };
        const versions = [
            { version: '2.3.1', createdAt: nowIso(), accuracy: 0.956, precision: 0.95, recall: 0.94, f1Score: 0.945, latency: 120, throughput: 100, status: 'Current' },
            { version: '2.3.0', createdAt: nowIso(), accuracy: 0.951, precision: 0.94, recall: 0.93, f1Score: 0.935, latency: 125, throughput: 90, status: 'Previous' },
        ];
        return HttpResponse.json(versions);
    }),

    http.get("*/api/v1/tenants/:tenantId/training-jobs", ({ params }) => {
        return HttpResponse.json([
            { id: "job-1", modelId: "model-2", status: "running", progress: 42 },
        ]);
    }),

    // Tenant-scoped health/alerts/anomalies endpoints used by realtime monitor
    http.get("*/api/v1/tenants/:tenantId/metrics/health", ({ params }) => {
        return HttpResponse.json({
            tenantId: params.tenantId,
            healthy: true,
            lastChecked: nowIso(),
        });
    }),

    http.get("*/api/v1/tenants/:tenantId/alerts", ({ params }) => {
        return HttpResponse.json([
            { id: 'alert-1', severity: 'warning', message: 'High latency detected', timestamp: nowIso() }
        ]);
    }),

    http.get("*/api/v1/tenants/:tenantId/anomalies", ({ params }) => {
        return HttpResponse.json([
            {
                id: 'anom-1',
                metric: 'cpu',
                value: 95,
                baselineValue: 45,
                detectedAt: nowIso(),
                severity: 'high',
            },
        ]);
    }),

    http.get("*/api/v1/tenants/:tenantId/ab-tests", ({ params }) => {
        return HttpResponse.json([
            { id: "ab-1", name: "A/B - models", status: "running", startAt: nowIso() },
        ]);
    }),

    // Tenant-scoped workflows list (used by /automation)
    http.get("*/api/v1/tenants/:tenantId/workflows", ({ params }) => {
        const tenantId = params.tenantId;
        return HttpResponse.json([
            {
                id: `wf-${tenantId}-1`,
                name: "Daily Automation",
                description: "Runs daily data-sync and validations",
                enabled: true,
                schedule: "0 2 * * *",
                createdAt: nowIso(),
                updatedAt: nowIso(),
            },
        ]);
    }),

    http.post("*/api/v1/ab-tests", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: `ab-${Date.now()}`, ...body }, { status: 201 });
    }),

    http.post("*/api/v1/ab-tests/:testId/stop", ({ params }) => {
        return HttpResponse.json({ id: params.testId, status: "stopped" });
    }),

    http.get("*/api/v1/ab-tests/:testId/results", ({ params }) => {
        return HttpResponse.json({ id: params.testId, results: { winner: "A", significance: 0.95 } });
    }),

    // Workflow triggers endpoints
    http.get("*/api/v1/workflows/:workflowId/triggers", ({ params }) => {
        return HttpResponse.json([
            { id: "trigger-1", workflowId: params.workflowId, type: "schedule", enabled: true },
        ]);
    }),

    http.post("*/api/v1/workflows/:workflowId/triggers", async ({ params, request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: `trigger-${Date.now()}`, workflowId: params.workflowId, ...body }, { status: 201 });
    }),

    http.patch("*/api/v1/triggers/:triggerId", async ({ params, request }) => {
        const updates = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: params.triggerId, ...updates });
    }),

    http.delete("*/api/v1/triggers/:triggerId", ({ params }) => {
        return HttpResponse.json({ id: params.triggerId, deleted: true });
    }),

    // ============================================================================
    // Admin API Handlers (Org Management, Security, Settings)
    // ============================================================================

    // Mock data for Admin APIs
    // --- Tenants ---
    http.get("*/api/v1/admin/tenants", async () => {
        await delay(100);
        return HttpResponse.json({
            data: [
                { id: 'tenant-acme', key: 'acme-payments', name: 'Acme Payments', displayName: 'Acme Payments Inc.', status: 'active', plan: 'enterprise', createdAt: nowIso() },
                { id: 'tenant-beta', key: 'beta-retail', name: 'Beta Retail', displayName: 'Beta Retail Corp', status: 'active', plan: 'professional', createdAt: nowIso() },
            ],
        });
    }),

    http.get("*/api/v1/admin/tenants/:tenantId", async ({ params }) => {
        await delay(100);
        return HttpResponse.json({
            id: params.tenantId,
            key: 'acme-payments',
            name: 'Acme Payments',
            displayName: 'Acme Payments Inc.',
            description: 'Primary tenant for payments processing',
            status: 'active',
            plan: 'enterprise',
            createdAt: nowIso(),
            updatedAt: nowIso(),
        });
    }),

    http.post("*/api/v1/admin/tenants", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: `tenant-${Date.now()}`, ...body, createdAt: nowIso() }, { status: 201 });
    }),

    // --- Teams ---
    http.get("*/api/v1/admin/teams", async ({ request }) => {
        await delay(100);
        const url = new URL(request.url);
        const departmentId = url.searchParams.get('departmentId');
        return HttpResponse.json({
            data: [
                { id: 'team-1', name: 'Platform Core', slug: 'platform-core', departmentId: departmentId || 'dept-eng', status: 'active', memberCount: 8 },
                { id: 'team-2', name: 'SRE Core', slug: 'sre-core', departmentId: departmentId || 'dept-sre', status: 'active', memberCount: 5 },
                { id: 'team-3', name: 'Payments API Team', slug: 'payments-api-team', departmentId: departmentId || 'dept-eng', status: 'active', memberCount: 12 },
            ],
        });
    }),

    http.post("*/api/v1/admin/teams", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: `team-${Date.now()}`, ...body, createdAt: nowIso() }, { status: 201 });
    }),

    // --- Services ---
    http.get("*/api/v1/admin/services", async () => {
        await delay(100);
        return HttpResponse.json({
            data: [
                { id: 'svc-1', name: 'Payments API', slug: 'payments-api', type: 'api', status: 'active', ownerTeamId: 'team-3', ownerTeamName: 'Payments API Team' },
                { id: 'svc-2', name: 'Checkout Web', slug: 'checkout-web', type: 'frontend', status: 'active', ownerTeamId: 'team-1', ownerTeamName: 'Platform Core' },
                { id: 'svc-3', name: 'Fraud Detector', slug: 'fraud-detector', type: 'ml-service', status: 'active', ownerTeamId: 'team-4', ownerTeamName: 'Fraud Detection Team' },
            ],
        });
    }),

    http.post("*/api/v1/admin/services", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: `svc-${Date.now()}`, ...body, createdAt: nowIso() }, { status: 201 });
    }),

    // --- Roles ---
    http.get("*/api/v1/admin/roles", async () => {
        await delay(100);
        return HttpResponse.json({
            data: [
                { id: 'role-1', name: 'Organization Admin', slug: 'org-admin', permissions: ['admin:*', 'tenant:*'], isSystem: true, userCount: 2 },
                { id: 'role-2', name: 'Security Admin', slug: 'security-admin', permissions: ['security:*', 'policy:*'], isSystem: true, userCount: 3 },
                { id: 'role-3', name: 'Operate On-Call', slug: 'operate-oncall', permissions: ['incident:*', 'queue:*'], isSystem: true, userCount: 10 },
                { id: 'role-4', name: 'Build Author', slug: 'build-author', permissions: ['workflow:*', 'agent:*'], isSystem: true, userCount: 15 },
                { id: 'role-5', name: 'Observe Viewer', slug: 'observe-viewer', permissions: ['observe:read', 'report:read'], isSystem: false, userCount: 25 },
            ],
        });
    }),

    http.post("*/api/v1/admin/roles", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: `role-${Date.now()}`, ...body, createdAt: nowIso() }, { status: 201 });
    }),

    // --- Personas ---
    http.get("*/api/v1/admin/personas", async () => {
        await delay(100);
        return HttpResponse.json({
            data: [
                { id: 'persona-1', name: 'SRE On-Call', slug: 'sre-oncall', type: 'human', teamId: 'team-2', teamName: 'SRE Core', capabilities: ['incident-response', 'monitoring'] },
                { id: 'persona-2', name: 'Platform Engineer', slug: 'platform-engineer', type: 'human', teamId: 'team-1', teamName: 'Platform Core', capabilities: ['infrastructure', 'automation'] },
                { id: 'persona-3', name: 'AI Incident Agent', slug: 'ai-incident-agent', type: 'ai', teamId: null, teamName: null, capabilities: ['incident-triage', 'root-cause-analysis'] },
            ],
        });
    }),

    http.post("*/api/v1/admin/personas", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: `persona-${Date.now()}`, ...body, createdAt: nowIso() }, { status: 201 });
    }),

    // --- Policies ---
    http.get("*/api/v1/admin/policies", async () => {
        await delay(100);
        return HttpResponse.json({
            data: [
                { id: 'pol-1', name: 'Require Approval for Prod Deploy', slug: 'require-approval-prod-deploy', type: 'deployment', status: 'active', priority: 100 },
                { id: 'pol-2', name: 'Auto-Mitigate Low Risk Incidents', slug: 'auto-mitigate-low-risk', type: 'incident', status: 'active', priority: 50 },
                { id: 'pol-3', name: 'AI High Risk Action Guardrail', slug: 'guardrail-high-risk-ai', type: 'guardrail', status: 'active', priority: 200 },
            ],
        });
    }),

    http.post("*/api/v1/admin/policies", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: `pol-${Date.now()}`, ...body, status: 'draft', createdAt: nowIso() }, { status: 201 });
    }),

    http.put("*/api/v1/admin/policies/:policyId/status", async ({ params, request }) => {
        const body = (await request.json()) as { status?: unknown };
        return HttpResponse.json({ id: params.policyId, status: String(body.status ?? ''), updatedAt: nowIso() });
    }),

    // --- Platform Settings ---
    http.get("*/api/v1/admin/settings/platform", async () => {
        await delay(100);
        return HttpResponse.json({
            settings: {
                general: {
                    displayName: 'Acme Payments',
                    defaultTimezone: 'UTC',
                    defaultLocale: 'en-US',
                    features: { operate: true, observe: true, build: true },
                },
                appearance: {
                    defaultTheme: 'dark',
                    brandColorToken: 'brand.primary',
                },
            },
        });
    }),

    http.put("*/api/v1/admin/settings/platform", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...body, updatedAt: nowIso() });
    }),

    // --- AI & Agents Settings ---
    http.get("*/api/v1/admin/settings/ai-agents", async () => {
        await delay(100);
        return HttpResponse.json({
            settings: {
                enabled: true,
                allowedTools: ['metrics', 'incidents', 'git', 'ticketing'],
                guardrails: {
                    maxRiskLevel: 'medium',
                    requireApprovalAbove: 'high',
                    disallowedActions: ['direct_prod_deploy', 'delete_data'],
                },
                auditLevel: 'all-decisions',
            },
        });
    }),

    http.put("*/api/v1/admin/settings/ai-agents", async ({ request }) => {
        const body = (await request.json()) as { settings?: unknown };
        return HttpResponse.json({ settings: body.settings, updatedAt: nowIso() });
    }),

    // --- Integrations ---
    http.get("*/api/v1/admin/integrations", async () => {
        await delay(100);
        return HttpResponse.json({
            data: [
                { id: 'int-1', type: 'observability', provider: 'datadog', name: 'Datadog Integration', status: 'connected', healthStatus: 'healthy' },
                { id: 'int-2', type: 'git', provider: 'github', name: 'GitHub Integration', status: 'connected', healthStatus: 'healthy' },
                { id: 'int-3', type: 'ci', provider: 'github-actions', name: 'GitHub Actions CI', status: 'connected', healthStatus: 'healthy' },
                { id: 'int-4', type: 'ticketing', provider: 'jira', name: 'Jira Integration', status: 'connected', healthStatus: 'healthy' },
            ],
        });
    }),

    http.post("*/api/v1/admin/integrations", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: `int-${Date.now()}`, ...body, status: 'pending', healthStatus: 'unknown', createdAt: nowIso() }, { status: 201 });
    }),

    http.post("*/api/v1/admin/integrations/:integrationId/test", async ({ params }) => {
        await delay(500); // Simulate connection test
        return HttpResponse.json({ success: true, latencyMs: 45, message: 'Connection successful', testedAt: nowIso() });
    }),

    // --- Audit Log ---
    http.get("*/api/v1/admin/audit/logs", async ({ request }) => {
        await delay(100);
        const url = new URL(request.url);
        const limit = parseInt(url.searchParams.get('limit') || '20', 10);
        return HttpResponse.json({
            data: Array.from({ length: Math.min(limit, 20) }).map((_, i) => ({
                id: `audit-${i + 1}`,
                timestamp: new Date(Date.now() - i * 3600000).toISOString(),
                entityType: ['tenant', 'policy', 'role', 'integration'][i % 4],
                entityId: `entity-${i + 1}`,
                action: ['create', 'update', 'delete', 'activate'][i % 4],
                actorUserId: 'user-admin',
                actorEmail: 'admin@example.com',
                details: { changes: { field: 'status', from: 'draft', to: 'active' } },
            })),
            total: 100,
            hasMore: true,
        });
    }),

    // --- Norms ---
    http.get("*/api/v1/norms", async () => {
        await delay(300);
        return HttpResponse.json([
            {
                id: "norm-1",
                title: "Code Review SLA",
                description: "All PRs must be reviewed within 24 hours",
                category: "quality",
                severity: "warning",
                status: "active",
                source: "manual",
                createdAt: nowIso(),
                updatedAt: nowIso(),
            },
            {
                id: "norm-2",
                title: "Security Scanning",
                description: "No critical vulnerabilities allowed in production",
                category: "security",
                severity: "error",
                status: "active",
                source: "system",
                createdAt: nowIso(),
                updatedAt: nowIso(),
            }
        ]);
    }),

    // --- Agents Marketplace ---
    http.get("*/api/v1/agents/marketplace", async () => {
        await delay(300);
        return HttpResponse.json([
            {
                id: "agent-tmpl-1",
                name: "Code Reviewer",
                description: "Automated code review agent",
                category: "engineering",
                capabilities: ["code-analysis", "pr-commenting"],
                version: "1.0.0",
                author: "Ghatana",
                popularity: 5,
                isVerified: true,
            },
            {
                id: "agent-tmpl-2",
                name: "Security Guardian",
                description: "Continuous security monitoring",
                category: "security",
                capabilities: ["vulnerability-scanning", "threat-detection"],
                version: "1.2.0",
                author: "Ghatana",
                popularity: 4,
                isVerified: true,
            }
        ]);
    }),

    // Catch-all handler to log all requests and provide basic responses
    // Serve the Vite dev favicon directly from MSW to avoid noisy catch-all warnings
    http.get('/vite.svg', () => {
        const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none">
  <rect width="16" height="16" rx="3" fill="#4f46e5"/>
  <path d="M4 8.5c0-1.657 1.343-3 3-3s3 1.343 3 3-1.343 3-3 3-3-1.343-3-3z" fill="#fff" opacity="0.9"/>
</svg>`;

        return new Response(svg, {
            status: 200,
            headers: { 'Content-Type': 'image/svg+xml' },
        });
    }),
    // Also handle absolute URLs that include origin (e.g. http://localhost:3000/vite.svg)
    http.get('*/vite.svg', () => {
        const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none">
  <rect width="16" height="16" rx="3" fill="#4f46e5"/>
  <path d="M4 8.5c0-1.657 1.343-3 3-3s3 1.343 3 3-1.343 3-3 3-3-1.343-3-3z" fill="#fff" opacity="0.9"/>
</svg>`;

        return new Response(svg, {
            status: 200,
            headers: { 'Content-Type': 'image/svg+xml' },
        });
    }),

    // Passthrough for Organization API to allow real backend calls
    http.all('*/api/v1/org/*', () => {
        return passthrough();
    }),

    // Catch-all handler to log all requests and provide basic responses
    http.all("*", ({ request }) => {
        const url = new URL(request.url);

        // Pass through internal React Router requests
        if (url.pathname.startsWith('/__manifest') ||
            url.pathname.startsWith('/__rsc') ||
            url.pathname.startsWith('/__data') ||
            url.pathname.startsWith('/@') ||
            url.pathname.startsWith('/node_modules') ||
            url.pathname.startsWith('/src') ||
            url.pathname.endsWith('.tsx') ||
            url.pathname.endsWith('.ts') ||
            url.pathname.endsWith('.js') ||
            url.pathname.endsWith('.css') ||
            url.pathname.endsWith('.html') ||
            url.pathname.endsWith('.map')) {
            return; // passthrough - do not handle
        }

        console.warn(`[MSW] ⚠️ Catch-all matched: ${request.method} ${request.url}`);
        return HttpResponse.json(
            {
                message: 'MSW Catch-all Response',
                method: request.method,
                path: request.url,
                timestamp: new Date().toISOString(),
            },
            { status: 200 } // Return 200 instead of 404 so we can test
        );
    }),
];

