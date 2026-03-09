/**
 * DevSecOps Engineer Flow Configuration
 *
 * Declarative configuration of the DevSecOps lifecycle for the Software Engineer
 * persona in the Software Org app. This mirrors the flow described in
 * ENGINEER_FLOW_IMPLEMENTATION_PLAN.md and is intended as a consumer-side
 * configuration that can later be replaced or synchronized with a Yappc-owned
 * devsecops-flows package.
 *
 * @doc.type config
 * @doc.purpose Declarative DevSecOps flow definition for Software Engineer persona
 * @doc.layer product
 */

export type DevSecOpsPhaseId =
    | 'intake'
    | 'plan'
    | 'build'
    | 'verify'
    | 'review'
    | 'staging'
    | 'deploy'
    | 'operate'
    | 'learn';

export const DEVSECOPS_PHASE_LABELS: Record<DevSecOpsPhaseId, string> = {
    intake: 'Intake',
    plan: 'Plan',
    build: 'Build',
    verify: 'Verify',
    review: 'Review',
    staging: 'Staging',
    deploy: 'Deploy',
    operate: 'Operate',
    learn: 'Learn',
};

export function resolveDevSecOpsRoute(
    routeTemplate: string,
    params: { storyId?: string },
): string {
    let route = routeTemplate;

    if (params.storyId) {
        route = route
            .replace(':storyId', params.storyId)
            .replace('storyId=:storyId', `storyId=${params.storyId}`);
    }

    return route;
}

export type DevSecOpsToolIntegrationId =
    | 'vcs'
    | 'ci'
    | 'feature-flags'
    | 'security-scanner'
    | 'observability'
    | 'incident-management';

export interface DevSecOpsToolIntegrationConfig {
    id: DevSecOpsToolIntegrationId;
    required?: boolean;
    description?: string;
}

export type DevSecOpsStepId =
    | 'engineer-intake'
    | 'engineer-plan-detail'
    | 'engineer-plan-implementation'
    | 'engineer-build-dev'
    | 'engineer-build-external'
    | 'engineer-verify-ci'
    | 'engineer-review'
    | 'engineer-staging'
    | 'engineer-deploy'
    | 'engineer-operate'
    | 'engineer-learn-retro'
    | 'lead-portfolio-overview'
    | 'lead-plan-backlog'
    | 'lead-review-readiness'
    | 'lead-operations-health'
    | 'lead-retro-planning'
    | 'sre-intake-incident'
    | 'sre-deploy'
    | 'sre-operate'
    | 'sre-learn-retro'
    | 'security-intake-risk'
    | 'security-plan-controls'
    | 'security-verify-scans'
    | 'security-operate'
    | 'security-learn-postmortems';

export type DevSecOpsComponentKey =
    | 'PersonaDashboard'
    | 'WorkItemPage'
    | 'WorkItemPlanPage'
    | 'WorkItemDevPage'
    | 'AutomationEngine'
    | 'WorkItemReviewPage'
    | 'ReportingDashboard'
    | 'ModelCatalog'
    | 'Dashboard'
    | 'ExternalTool';

export interface FlowStep {
    /** Stable identifier for this step within the persona flow. */
    stepId: DevSecOpsStepId;
    /** DevSecOps lifecycle phase this step belongs to. */
    phaseId: DevSecOpsPhaseId;
    /** Short, user-facing label for the step. */
    label: string;
    /** Optional longer description for tooltips and docs. */
    description?: string;
    /**
     * Route template or external location identifier.
     *
     * Use `:storyId` or `?storyId=:storyId` where applicable. External
     * integrations can use a logical identifier (e.g. `external:vcs`).
     */
    route: string;
    /** Logical key for the primary UI component handling this step. */
    componentKey: DevSecOpsComponentKey;
    /** Associated tool integrations (CI, VCS, observability, etc.). */
    toolIntegrations?: DevSecOpsToolIntegrationConfig[];
    /** Optional id of the next step in the happy-path flow. */
    nextStepId?: DevSecOpsStepId;
}

export interface PersonaFlow {
    /** Persona identifier; for this module we only support the engineer flow. */
    personaId: 'engineer' | 'lead' | 'sre' | 'security';
    /** Ordered list of DevSecOps phases that this persona participates in. */
    phases: DevSecOpsPhaseId[];
    /** All configured steps for this persona. */
    steps: FlowStep[];
    /** The initial step in the happy-path flow. */
    startStepId: DevSecOpsStepId;
}

/**
 * Engineer DevSecOps flow for Software Org.
 *
 * This mirrors the end-to-end flow described in ENGINEER_FLOW_IMPLEMENTATION_PLAN.md
 * and is intentionally simple and data-driven. It can later be derived from or
 * synchronized with a Yappc-owned devsecops-flows library.
 */
export const ENGINEER_DEVSECOPS_FLOW: PersonaFlow = {
    personaId: 'engineer',
    phases: [
        'intake',
        'plan',
        'build',
        'verify',
        'review',
        'staging',
        'deploy',
        'operate',
        'learn',
    ],
    startStepId: 'engineer-intake',
    steps: [
        {
            stepId: 'engineer-intake',
            phaseId: 'intake',
            label: 'Choose story',
            description: 'Select a story from My Stories on the persona dashboard.',
            route: '/persona-dashboard',
            componentKey: 'PersonaDashboard',
            toolIntegrations: [],
            nextStepId: 'engineer-plan-detail',
        },
        {
            stepId: 'engineer-plan-detail',
            phaseId: 'plan',
            label: 'Understand story',
            description: 'Review description, acceptance criteria and context.',
            route: '/work-items/:storyId',
            componentKey: 'WorkItemPage',
            toolIntegrations: [],
            nextStepId: 'engineer-plan-implementation',
        },
        {
            stepId: 'engineer-plan-implementation',
            phaseId: 'plan',
            label: 'Plan implementation',
            description: 'Capture design, affected services and rollout strategy.',
            route: '/work-items/:storyId/plan',
            componentKey: 'WorkItemPlanPage',
            toolIntegrations: [{ id: 'feature-flags' }],
            nextStepId: 'engineer-build-dev',
        },
        {
            stepId: 'engineer-build-dev',
            phaseId: 'build',
            label: 'Implement changes',
            description: 'Use the development view to track branch, PRs and local progress.',
            route: '/work-items/:storyId/dev',
            componentKey: 'WorkItemDevPage',
            toolIntegrations: [{ id: 'vcs' }],
            nextStepId: 'engineer-build-external',
        },
        {
            stepId: 'engineer-build-external',
            phaseId: 'build',
            label: 'Implement changes in Git/IDE',
            description: 'Work in the external repository using branch and PR links.',
            route: 'external:vcs',
            componentKey: 'ExternalTool',
            toolIntegrations: [{ id: 'vcs', required: true }],
            nextStepId: 'engineer-verify-ci',
        },
        {
            stepId: 'engineer-verify-ci',
            phaseId: 'verify',
            label: 'Verify CI pipelines',
            description: 'Review CI pipelines and fix failures until green.',
            route: '/automation?storyId=:storyId',
            componentKey: 'AutomationEngine',
            toolIntegrations: [{ id: 'ci', required: true }],
            nextStepId: 'engineer-review',
        },
        {
            stepId: 'engineer-review',
            phaseId: 'review',
            label: 'Code review & merge',
            description: 'Track review status and linked pull requests.',
            route: '/work-items/:storyId/review',
            componentKey: 'WorkItemReviewPage',
            toolIntegrations: [
                { id: 'vcs', required: true },
                { id: 'ci' },
            ],
            nextStepId: 'engineer-staging',
        },
        {
            stepId: 'engineer-staging',
            phaseId: 'staging',
            label: 'Validate in staging',
            description: 'Validate metrics and behavior in staging.',
            route: '/reports?view=staging&storyId=:storyId',
            componentKey: 'ReportingDashboard',
            toolIntegrations: [{ id: 'observability', required: true }],
            nextStepId: 'engineer-deploy',
        },
        {
            stepId: 'engineer-deploy',
            phaseId: 'deploy',
            label: 'Promote to production',
            description: 'Deploy the story’s artifact to production.',
            route: '/models?action=deploy&storyId=:storyId',
            componentKey: 'ModelCatalog',
            toolIntegrations: [{ id: 'ci' }],
            nextStepId: 'engineer-operate',
        },
        {
            stepId: 'engineer-operate',
            phaseId: 'operate',
            label: 'Monitor production & close story',
            description: 'Monitor KPIs, incidents and close the story when stable.',
            route: '/dashboard?storyId=:storyId',
            componentKey: 'Dashboard',
            toolIntegrations: [
                { id: 'observability', required: true },
                { id: 'incident-management' },
            ],
            nextStepId: 'engineer-learn-retro',
        },
        {
            stepId: 'engineer-learn-retro',
            phaseId: 'learn',
            label: 'Reflect & learn',
            description: 'Review outcomes and capture learnings/retrospective.',
            route: '/dashboard?storyId=:storyId&view=retro',
            componentKey: 'Dashboard',
            toolIntegrations: [
                { id: 'observability' },
                { id: 'incident-management' },
            ],
        },
    ],
};

export const LEAD_DEVSECOPS_FLOW: PersonaFlow = {
    personaId: 'lead',
    phases: ['intake', 'plan', 'verify', 'operate', 'learn'],
    startStepId: 'lead-portfolio-overview',
    steps: [
        {
            stepId: 'lead-portfolio-overview',
            phaseId: 'intake',
            label: 'Portfolio overview',
            route: '/devsecops/board?persona=lead',
            componentKey: 'Dashboard',
            toolIntegrations: [{ id: 'observability' }],
            nextStepId: 'lead-plan-backlog',
        },
        {
            stepId: 'lead-plan-backlog',
            phaseId: 'plan',
            label: 'Plan backlog',
            route: '/work-items',
            componentKey: 'WorkItemPage',
            toolIntegrations: [],
            nextStepId: 'lead-review-readiness',
        },
        {
            stepId: 'lead-review-readiness',
            phaseId: 'verify',
            label: 'Release readiness',
            route: '/reports',
            componentKey: 'ReportingDashboard',
            toolIntegrations: [{ id: 'ci' }],
            nextStepId: 'lead-operations-health',
        },
        {
            stepId: 'lead-operations-health',
            phaseId: 'operate',
            label: 'Operations health',
            route: '/dashboard',
            componentKey: 'Dashboard',
            toolIntegrations: [{ id: 'observability' }],
            nextStepId: 'lead-retro-planning',
        },
        {
            stepId: 'lead-retro-planning',
            phaseId: 'learn',
            label: 'Portfolio retrospectives',
            route: '/dashboard?view=retro',
            componentKey: 'Dashboard',
            toolIntegrations: [{ id: 'observability' }],
        },
    ],
};

export const SRE_DEVSECOPS_FLOW: PersonaFlow = {
    personaId: 'sre',
    phases: ['intake', 'deploy', 'operate', 'learn'],
    startStepId: 'sre-intake-incident',
    steps: [
        {
            stepId: 'sre-intake-incident',
            phaseId: 'intake',
            label: 'Triage incidents',
            route: '/realtime-monitor?view=alerts',
            componentKey: 'Dashboard',
            toolIntegrations: [
                { id: 'observability', required: true },
                { id: 'incident-management' },
            ],
            nextStepId: 'sre-deploy',
        },
        {
            stepId: 'sre-deploy',
            phaseId: 'deploy',
            label: 'Roll out fixes',
            route: '/models?action=deploy',
            componentKey: 'ModelCatalog',
            toolIntegrations: [{ id: 'ci' }],
            nextStepId: 'sre-operate',
        },
        {
            stepId: 'sre-operate',
            phaseId: 'operate',
            label: 'Monitor production',
            route: '/realtime-monitor',
            componentKey: 'Dashboard',
            toolIntegrations: [{ id: 'observability', required: true }],
            nextStepId: 'sre-learn-retro',
        },
        {
            stepId: 'sre-learn-retro',
            phaseId: 'learn',
            label: 'Post-incident review',
            route: '/dashboard?view=retro',
            componentKey: 'Dashboard',
            toolIntegrations: [
                { id: 'observability' },
                { id: 'incident-management' },
            ],
        },
    ],
};

export const SECURITY_DEVSECOPS_FLOW: PersonaFlow = {
    personaId: 'security',
    phases: ['intake', 'plan', 'verify', 'operate', 'learn'],
    startStepId: 'security-intake-risk',
    steps: [
        {
            stepId: 'security-intake-risk',
            phaseId: 'intake',
            label: 'Review security posture',
            route: '/security',
            componentKey: 'Dashboard',
            toolIntegrations: [{ id: 'security-scanner', required: true }],
            nextStepId: 'security-plan-controls',
        },
        {
            stepId: 'security-plan-controls',
            phaseId: 'plan',
            label: 'Plan controls & mitigations',
            route: '/reports?type=compliance',
            componentKey: 'ReportingDashboard',
            toolIntegrations: [{ id: 'security-scanner' }],
            nextStepId: 'security-verify-scans',
        },
        {
            stepId: 'security-verify-scans',
            phaseId: 'verify',
            label: 'Verify scans & policies',
            route: '/reports?type=compliance',
            componentKey: 'ReportingDashboard',
            toolIntegrations: [{ id: 'security-scanner', required: true }],
            nextStepId: 'security-operate',
        },
        {
            stepId: 'security-operate',
            phaseId: 'operate',
            label: 'Operate securely',
            route: '/security',
            componentKey: 'Dashboard',
            toolIntegrations: [
                { id: 'observability' },
                { id: 'incident-management' },
            ],
            nextStepId: 'security-learn-postmortems',
        },
        {
            stepId: 'security-learn-postmortems',
            phaseId: 'learn',
            label: 'Security retrospectives',
            route: '/dashboard?view=retro',
            componentKey: 'Dashboard',
            toolIntegrations: [
                { id: 'observability' },
                { id: 'incident-management' },
            ],
        },
    ],
};
