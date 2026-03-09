/**
 * React Router v7 Framework Mode Route Configuration
 *
 * CEO Cockpit UI Architecture - 3 Pillars:
 * - CREATE: Genesis wizard for organization creation
 * - MANAGE: Structure, People, Resources (Org Chart, Agents, Norms, Budget)
 * - OPERATE: Day-to-day operations (Live Feed, Tasks, Insights)
 *
 * Plus: PEOPLE pillar for Reviews & Growth
 *
 * @see https://reactrouter.com/start/framework/routing
 */
import { type RouteConfig, route, index } from "@react-router/dev/routes";

export default [
    // =========================================================================
    // CREATE - Organization Genesis (AI-Powered Creation Wizard)
    // =========================================================================
    route("/genesis", "./routes/genesis.tsx"),

    // =========================================================================
    // MANAGE - Organization Structure & Resources
    // =========================================================================

    // Org Chart - Visual organization hierarchy
    route("/manage/org-chart", "./routes/manage/org-chart.tsx"),

    // Norms - Natural language norm definition
    route("/manage/norms", "./routes/manage/norms.tsx"),

    // Agents - Hiring Hall / Agent Marketplace
    route("/manage/agents", "./routes/manage/agents.tsx"),

    // Budget - Resource allocation & planning
    route("/manage/budget", "./routes/manage/budget.tsx"),

    // =========================================================================
    // OPERATE - Day-to-day Operations
    // =========================================================================

    // Dashboard is the main entry point (operational hub)
    index("./routes/operate/dashboard.tsx"),

    // Live Feed - Real-time activity stream
    route("/operate/live-feed", "./routes/operate/live-feed.tsx"),

    // Tasks - Unified work queue (PRs, approvals, decisions)
    route("/operate/tasks", "./routes/operate/tasks.tsx"),

    // Insights - AI Analyst chat interface
    route("/operate/insights", "./routes/operate/insights.tsx"),

    // Legacy operate routes (kept for backward compatibility)
    route("/operate/stages", "./routes/operate/stages.tsx"),
    route("/operate/stages/:stageKey", "./routes/operate/stage-dashboard.tsx"),
    route("/operate/queue", "./routes/operate/queue.tsx"),
    route("/operate/approvals", "./routes/operate/approvals.tsx"),
    route("/operate/incidents", "./routes/operate/incidents.tsx"),

    // =========================================================================
    // PEOPLE - Reviews & Growth
    // =========================================================================

    // Performance Reviews
    route("/people/reviews", "./routes/people/reviews.tsx"),

    // Growth & Development
    route("/people/growth", "./routes/people/growth.tsx"),

    // Legacy team routes (kept for backward compatibility)
    route("/team/performance-reviews", "./routes/team/performance-reviews.tsx"),
    route("/team/performance-reviews/new", "./routes/team/performance-review-new.tsx"),
    route("/team/performance-reviews/:id", "./routes/team/performance-review-detail.tsx"),

    // Budget Planning - fiscal planning and allocations (legacy)
    route("/budget/planning", "./routes/budget/budget-planning.tsx"),

    // IC Journeys (legacy)
    route("/ic/time-off", "./routes/ic/time-off.tsx"),
    route("/ic/growth", "./routes/ic/growth.tsx"),

    // =========================================================================
    // CONFIGURE - Configuring Organization
    // =========================================================================

    // --- Admin & Settings ---
    // Organization - departments, teams, personas (consolidated)
    route("/admin/organization", "./routes/admin/organization.tsx"),
    route("/admin/organization/departments/:id", "./routes/admin/department-detail.tsx"),
    route("/admin/organization/teams/:id", "./routes/admin/team-detail.tsx"),

    // Org restructuring (legacy page wrapper)
    route("/org/restructure", "./routes/org/restructure.tsx"),

    // Security - access control and audit
    route("/admin/security", "./routes/admin/security.tsx"),

    // Settings - system preferences
    route("/admin/settings", "./routes/admin/settings.tsx"),

    // --- Build & Automation ---
    // Workflows - create, edit, test automation workflows
    route("/build/workflows", "./routes/build/workflows.tsx"),
    route("/build/workflows/new", "./routes/build/workflow-new.tsx"),
    route("/build/workflows/:id", "./routes/build/workflow-detail.tsx"),
    route("/build/workflows/:id/edit", "./routes/build/workflow-edit.tsx"),

    // Agents - AI agents configuration
    route("/build/agents", "./routes/build/agents.tsx"),
    route("/build/agents/new", "./routes/build/agent-new.tsx"),
    route("/build/agents/:id", "./routes/build/agent-detail.tsx"),
    route("/build/agents/:id/edit", "./routes/build/agent-edit.tsx"),

    // Simulator - test events and scenarios
    route("/build/simulator", "./routes/build/simulator.tsx"),

    // --- Config Visualization ---
    // Config Dashboard - overview of all YAML config entities
    route("/config", "./routes/config/dashboard.tsx"),

    // =========================================================================
    // UNIFIED CONFIG ENTITY ROUTES
    // These unified routes use the entity-registry for consistent CRUD UI
    // =========================================================================

    // Agents
    route("/config/agents", "./routes/config/agents-list.tsx"),
    route("/config/agents/new", "./routes/config/entity-create.tsx", { id: "create-agent" }),
    route("/config/agents/:id", "./routes/config/agent-detail.tsx"),
    route("/config/agents/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-agent" }),

    // Departments
    route("/config/departments", "./routes/config/departments-list.tsx"),
    route("/config/departments/new", "./routes/config/entity-create.tsx", { id: "create-department" }),
    route("/config/departments/:id", "./routes/config/department-detail.tsx"),
    route("/config/departments/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-department" }),

    // Workflows
    route("/config/workflows", "./routes/config/workflows-list.tsx"),
    route("/config/workflows/new", "./routes/config/entity-create.tsx", { id: "create-workflow" }),
    route("/config/workflows/:id", "./routes/config/workflow-detail.tsx"),
    route("/config/workflows/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-workflow" }),

    // Interactions
    route("/config/interactions", "./routes/config/interactions-list.tsx"),
    route("/config/interactions/new", "./routes/config/entity-create.tsx", { id: "create-interaction" }),
    route("/config/interactions/:id", "./routes/config/interaction-detail.tsx"),
    route("/config/interactions/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-interaction" }),

    // Personas
    route("/config/personas", "./routes/config/personas-list.tsx"),
    route("/config/personas/new", "./routes/config/entity-create.tsx", { id: "create-persona" }),
    route("/config/personas/:id", "./routes/config/persona-detail.tsx"),
    route("/config/personas/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-persona" }),

    // Phases
    route("/config/phases", "./routes/config/phases-list.tsx"),
    route("/config/phases/new", "./routes/config/entity-create.tsx", { id: "create-phase" }),
    route("/config/phases/:id", "./routes/config/phase-detail.tsx"),
    route("/config/phases/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-phase" }),

    // Stages
    route("/config/stages", "./routes/config/stages-list.tsx"),
    route("/config/stages/new", "./routes/config/entity-create.tsx", { id: "create-stage" }),
    route("/config/stages/:id", "./routes/config/stage-detail.tsx"),
    route("/config/stages/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-stage" }),

    // Services
    route("/config/services", "./routes/config/services-list.tsx"),
    route("/config/services/new", "./routes/config/entity-create.tsx", { id: "create-service" }),
    route("/config/services/:id", "./routes/config/entity-detail.tsx", { id: "detail-service" }),
    route("/config/services/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-service" }),

    // Integrations
    route("/config/integrations", "./routes/config/integrations-list.tsx"),
    route("/config/integrations/new", "./routes/config/entity-create.tsx", { id: "create-integration" }),
    route("/config/integrations/:id", "./routes/config/entity-detail.tsx", { id: "detail-integration" }),
    route("/config/integrations/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-integration" }),

    // Flows
    route("/config/flows", "./routes/config/flows-list.tsx"),
    route("/config/flows/new", "./routes/config/entity-create.tsx", { id: "create-flow" }),
    route("/config/flows/:id", "./routes/config/entity-detail.tsx", { id: "detail-flow" }),
    route("/config/flows/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-flow" }),

    // Operators
    route("/config/operators", "./routes/config/operators-list.tsx"),
    route("/config/operators/new", "./routes/config/entity-create.tsx", { id: "create-operator" }),
    route("/config/operators/:id", "./routes/config/entity-detail.tsx", { id: "detail-operator" }),
    route("/config/operators/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-operator" }),

    // KPIs
    route("/config/kpis", "./routes/config/kpis-list.tsx"),
    route("/config/kpis/new", "./routes/config/entity-create.tsx", { id: "create-kpi" }),
    route("/config/kpis/:id", "./routes/config/kpi-detail.tsx"),
    route("/config/kpis/:id/edit", "./routes/config/entity-edit.tsx", { id: "edit-kpi" }),

    // =========================================================================
    // MONITOR - Monitoring Organization
    // =========================================================================

    // Metrics - KPIs and performance data
    route("/observe/metrics", "./routes/observe/metrics.tsx"),
    route("/observe/metrics/:id", "./routes/observe/metric-detail.tsx"),

    // Reports - generated reports and analytics
    route("/observe/reports", "./routes/observe/reports.tsx"),

    // ML Observatory - model performance and monitoring
    route("/observe/ml", "./routes/observe/ml-observatory.tsx"),

    // Cross-functional - knowledge, innovation, skills
    route("/observe/knowledge-base", "./routes/observe/knowledge-base.tsx"),
    route("/observe/innovation", "./routes/observe/innovation-tracker.tsx"),
    route("/observe/skills-matrix", "./routes/observe/skills-matrix.tsx"),

    // =========================================================================
    // Auth & Utility Routes
    // =========================================================================

    route("/login", "./routes/login.tsx"),

    // =========================================================================
    // Legacy Route Redirects (for backward compatibility)
    // These will redirect to new locations
    // =========================================================================

    // Old dashboard routes -> new dashboard
    route("/dashboard", "./routes/redirects/to-dashboard.tsx", { id: "redirect-dashboard" }),

    // Old entity routes -> admin/organization
    route("/departments", "./routes/redirects/to-organization.tsx", { id: "redirect-departments" }),
    route("/departments/:id", "./routes/redirects/to-organization.tsx", { id: "redirect-department-detail" }),
    route("/personas", "./routes/redirects/to-organization.tsx", { id: "redirect-personas" }),
    route("/personas/:id", "./routes/redirects/to-organization.tsx", { id: "redirect-persona-detail" }),

    // Old feature routes -> new locations
    route("/workflows", "./routes/redirects/to-build-workflows.tsx", { id: "redirect-workflows" }),
    route("/workflows/:id", "./routes/redirects/to-build-workflows.tsx", { id: "redirect-workflow-detail" }),
    route("/agents", "./routes/redirects/to-build-agents.tsx", { id: "redirect-agents" }),
    route("/hitl", "./routes/redirects/to-operate-queue.tsx", { id: "redirect-hintl" }),
    route("/simulator", "./routes/redirects/to-build-simulator.tsx", { id: "redirect-simulator" }),
    route("/reports", "./routes/redirects/to-observe-reports.tsx", { id: "redirect-reports" }),
    route("/security", "./routes/redirects/to-admin-security.tsx", { id: "redirect-security" }),
    route("/kpis", "./routes/redirects/to-observe-metrics.tsx", { id: "redirect-kpis" }),
    route("/realtime-monitor", "./routes/redirects/to-dashboard.tsx", { id: "redirect-realtime-monitor" }),
    route("/ml-observatory", "./routes/redirects/to-observe-ml.tsx", { id: "redirect-ml-observatory" }),
    route("/models", "./routes/redirects/to-observe-ml.tsx", { id: "redirect-models" }),

    // Catch-all for 404 (must be last)
    route("*", "./routes/placeholder.tsx"),
] satisfies RouteConfig;
