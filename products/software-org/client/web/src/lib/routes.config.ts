/**
 * Application Routes Configuration
 *
 * Centralized route definitions for all pages in the application.
 * Organized into 4 clear sections:
 * - OPERATE: Day-to-day operations
 * - BUILD: Design & create automation
 * - OBSERVE: Monitor & analyze
 * - ADMIN: Configure & manage
 *
 * @doc.type configuration
 * @doc.purpose Route definitions and navigation structure
 */

export interface RouteDefinition {
    path: string;
    label: string;
    icon: string;
    section: "configure" | "operate" | "monitor";
    description: string;
}

/**
 * Navigation sections with their routes
 */
export const NAVIGATION_SECTIONS = {
    configure: {
        label: "Configure",
        description: "Organization structure & automation",
        routes: [
            { path: "/admin/organization", label: "Organization", icon: "🏢", description: "Departments, teams, personas" },
            { path: "/config/agents", label: "Agents", icon: "🤖", description: "AI agents configuration" },
            { path: "/config/workflows", label: "Workflows", icon: "🔗", description: "Automation workflows" },
            { path: "/config/interactions", label: "Interactions", icon: "🔄", description: "Department interactions" },
            { path: "/build/simulator", label: "Simulator", icon: "⚡", description: "Event testing environment" },
            { path: "/admin/security", label: "Security", icon: "🔒", description: "Access control and audit" },
            { path: "/admin/settings", label: "Settings", icon: "⚙️", description: "System preferences" },
        ],
    },
    operate: {
        label: "Operate",
        description: "Day-to-day operations",
        routes: [
            { path: "/", label: "Dashboard", icon: "📊", description: "Real-time operational hub" },
            { path: "/operate/queue", label: "Work Queue", icon: "📋", description: "Pending tasks and approvals" },
            { path: "/operate/incidents", label: "Incidents", icon: "🚨", description: "Active incident management" },
            { path: "/team/performance-reviews", label: "Performance", icon: "⭐", description: "Team performance reviews" },
            { path: "/budget/planning", label: "Budget", icon: "💰", description: "Fiscal planning & allocation" },
            { path: "/ic/growth", label: "Growth", icon: "🌱", description: "Career growth & skills" },
        ],
    },
    monitor: {
        label: "Monitor",
        description: "Observe & analyze",
        routes: [
            { path: "/observe/metrics", label: "Metrics", icon: "📈", description: "KPIs and performance data" },
            { path: "/observe/reports", label: "Reports", icon: "📄", description: "Generated reports" },
            { path: "/observe/ml", label: "ML Observatory", icon: "🔬", description: "Model performance monitoring" },
            { path: "/observe/skills-matrix", label: "Skills Matrix", icon: "🧠", description: "Organization skills overview" },
            { path: "/observe/innovation", label: "Innovation", icon: "💡", description: "Innovation tracking" },
        ],
    },
} as const;

/**
 * Flat list of all routes for lookup
 */
export const ALL_ROUTES: RouteDefinition[] = Object.entries(NAVIGATION_SECTIONS).flatMap(
    ([section, config]) =>
        config.routes.map((route) => ({
            ...route,
            section: section as RouteDefinition["section"],
        }))
);

/**
 * Get route by path
 */
export function getRouteByPath(path: string): RouteDefinition | undefined {
    return ALL_ROUTES.find((route) => route.path === path);
}

/**
 * Get routes by section
 */
export function getRoutesBySection(section: RouteDefinition["section"]): RouteDefinition[] {
    return ALL_ROUTES.filter((route) => route.section === section);
}
