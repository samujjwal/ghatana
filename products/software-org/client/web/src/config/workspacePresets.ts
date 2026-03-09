/**
 * Workspace Presets Configuration
 *
 * <p><b>Purpose</b><br>
 * Defines per-persona workspace presets including:
 * - Onboarding messages and AI-style guidance
 * - Primary workflow steps and CTAs
 * - Key metrics to highlight
 * - DevSecOps flow focus areas
 *
 * <p><b>Extends</b><br>
 * Works alongside `personaConfig.ts` to provide workspace-specific
 * content for the `/workspace/:personaId` routes.
 *
 * @doc.type configuration
 * @doc.purpose Persona workspace presets
 * @doc.layer product
 * @doc.pattern Configuration
 */

import type { PersonaId, DevSecOpsPhaseId } from '@/shared/types/org';

/**
 * Onboarding configuration for a persona workspace
 */
export interface WorkspaceOnboarding {
    /** Welcome title */
    title: string;
    /** Welcome subtitle/description */
    subtitle: string;
    /** AI-style guidance message */
    aiGuidance: string;
    /** Primary CTA label */
    primaryCtaLabel: string;
    /** Primary CTA href */
    primaryCtaHref: string;
    /** Secondary CTA label (optional) */
    secondaryCtaLabel?: string;
    /** Secondary CTA href (optional) */
    secondaryCtaHref?: string;
    /** LocalStorage key for dismissal */
    dismissalKey: string;
}

/**
 * Key metric highlight for a persona workspace
 */
export interface WorkspaceMetricHighlight {
    /** Metric identifier */
    id: string;
    /** Display label */
    label: string;
    /** Icon (emoji) */
    icon: string;
    /** Data source key */
    dataKey: string;
    /** Threshold for warning state */
    warningThreshold?: number;
    /** Threshold for critical state */
    criticalThreshold?: number;
    /** Link to drill-down */
    href?: string;
}

/**
 * Workflow step for a persona workspace
 */
export interface WorkspaceWorkflowStep {
    /** Step identifier */
    id: string;
    /** Step label */
    label: string;
    /** Step description */
    description: string;
    /** Icon (emoji) */
    icon: string;
    /** Target route */
    href: string;
    /** Associated DevSecOps phase */
    phaseId: DevSecOpsPhaseId;
    /** Whether this is a primary action */
    primary?: boolean;
}

/**
 * Complete workspace preset for a persona
 */
export interface WorkspacePreset {
    /** Persona identifier */
    personaId: PersonaId;
    /** Display name */
    displayName: string;
    /** Short tagline */
    tagline: string;
    /** Theme color (Tailwind class prefix) */
    themeColor: string;
    /** Icon (emoji) */
    icon: string;
    /** Onboarding configuration */
    onboarding: WorkspaceOnboarding;
    /** Key metrics to highlight */
    metricHighlights: WorkspaceMetricHighlight[];
    /** Primary workflow steps */
    workflowSteps: WorkspaceWorkflowStep[];
    /** DevSecOps phases this persona focuses on */
    focusPhases: DevSecOpsPhaseId[];
    /** Contextual tips (rotated) */
    tips: string[];
}

/**
 * Engineer workspace preset
 */
const engineerPreset: WorkspacePreset = {
    personaId: 'engineer',
    displayName: 'Engineer Workspace',
    tagline: 'Build, test, and deploy with confidence',
    themeColor: 'blue',
    icon: '👨‍💻',
    onboarding: {
        title: 'Welcome to your Engineer Workspace',
        subtitle: 'Your central hub for development workflow - from story intake to production deployment.',
        aiGuidance: 'AI tip: Start with your assigned stories in the DevSecOps board, then use the pipeline strip to track progress through each phase. The system will guide you with contextual actions at each step.',
        primaryCtaLabel: 'View My Stories',
        primaryCtaHref: '/devsecops/board?persona=engineer&assignee=me',
        secondaryCtaLabel: 'Open Work Items',
        secondaryCtaHref: '/work-items',
        dismissalKey: 'softwareOrg.workspace.engineer.onboarding.dismissed',
    },
    metricHighlights: [
        { id: 'stories-in-progress', label: 'Stories In Progress', icon: '📝', dataKey: 'storiesInProgress', href: '/work-items?status=in-progress' },
        { id: 'prs-pending', label: 'PRs Pending Review', icon: '🔍', dataKey: 'prsPending', warningThreshold: 3, href: '/work-items?status=review' },
        { id: 'builds-today', label: 'Builds Today', icon: '🔨', dataKey: 'buildsToday', href: '/automation' },
        { id: 'deploy-success', label: 'Deploy Success Rate', icon: '🚀', dataKey: 'deploySuccessRate', warningThreshold: 95, criticalThreshold: 90 },
    ],
    workflowSteps: [
        { id: 'eng-intake', label: 'Pick Up Story', description: 'Select a story from the backlog', icon: '📋', href: '/devsecops/board?persona=engineer', phaseId: 'intake', primary: true },
        { id: 'eng-plan', label: 'Plan Implementation', description: 'Break down into tasks', icon: '📝', href: '/work-items', phaseId: 'plan' },
        { id: 'eng-build', label: 'Develop', description: 'Write code and tests', icon: '💻', href: '/work-items', phaseId: 'build' },
        { id: 'eng-verify', label: 'Run Tests', description: 'Execute test suites', icon: '🧪', href: '/automation', phaseId: 'verify' },
        { id: 'eng-review', label: 'Code Review', description: 'Submit PR for review', icon: '👀', href: '/work-items', phaseId: 'review' },
        { id: 'eng-deploy', label: 'Deploy', description: 'Ship to production', icon: '🚀', href: '/models', phaseId: 'deploy' },
    ],
    focusPhases: ['intake', 'plan', 'build', 'verify', 'review', 'staging', 'deploy'],
    tips: [
        'Use keyboard shortcuts (Ctrl+K) to quickly navigate between pages.',
        'Pin frequently used features to your dashboard for quick access.',
        'The DevSecOps pipeline strip shows your current phase - click any phase to jump there.',
        'Set up notifications in Settings to stay updated on PR reviews and build status.',
    ],
};

/**
 * Lead workspace preset
 */
const leadPreset: WorkspacePreset = {
    personaId: 'lead',
    displayName: 'Lead Workspace',
    tagline: 'Oversee team progress and approvals',
    themeColor: 'purple',
    icon: '👔',
    onboarding: {
        title: 'Welcome to your Lead Workspace',
        subtitle: 'Monitor team velocity, manage approvals, and ensure quality delivery.',
        aiGuidance: 'AI tip: Focus on blocked items first - they often indicate process bottlenecks. Use the DevSecOps board with the Lead filter to see items awaiting your review or approval.',
        primaryCtaLabel: 'Review Pending Approvals',
        primaryCtaHref: '/hitl?filter=pending',
        secondaryCtaLabel: 'View Team Board',
        secondaryCtaHref: '/devsecops/board?persona=lead',
        dismissalKey: 'softwareOrg.workspace.lead.onboarding.dismissed',
    },
    metricHighlights: [
        { id: 'pending-approvals', label: 'Pending Approvals', icon: '✋', dataKey: 'pendingApprovals', warningThreshold: 5, criticalThreshold: 10, href: '/hitl' },
        { id: 'team-velocity', label: 'Team Velocity', icon: '📈', dataKey: 'teamVelocity', href: '/reports?type=velocity' },
        { id: 'blocked-items', label: 'Blocked Items', icon: '🚧', dataKey: 'blockedItems', warningThreshold: 2, criticalThreshold: 5, href: '/devsecops/board?status=blocked' },
        { id: 'release-readiness', label: 'Release Readiness', icon: '🎯', dataKey: 'releaseReadiness', href: '/reports?type=release' },
    ],
    workflowSteps: [
        { id: 'lead-review', label: 'Review Backlog', description: 'Prioritize and assign stories', icon: '📋', href: '/devsecops/board?persona=lead', phaseId: 'intake', primary: true },
        { id: 'lead-approve', label: 'Approve PRs', description: 'Review and approve code changes', icon: '✅', href: '/hitl', phaseId: 'review' },
        { id: 'lead-release', label: 'Release Review', description: 'Validate release readiness', icon: '🔍', href: '/reports?type=release', phaseId: 'staging' },
        { id: 'lead-deploy', label: 'Deploy Approval', description: 'Authorize production deploys', icon: '🚀', href: '/hitl?type=deploy', phaseId: 'deploy' },
        { id: 'lead-report', label: 'Team Reports', description: 'Review team performance', icon: '📊', href: '/reports', phaseId: 'learn' },
    ],
    focusPhases: ['intake', 'review', 'staging', 'deploy', 'learn'],
    tips: [
        'Blocked items often indicate systemic issues - look for patterns.',
        'Use the comparison mode in reports to track week-over-week improvements.',
        'Set up Slack notifications for high-priority approval requests.',
        'The Org Builder shows team structure and dependencies at a glance.',
    ],
};

/**
 * SRE workspace preset
 */
const srePreset: WorkspacePreset = {
    personaId: 'sre',
    displayName: 'SRE Workspace',
    tagline: 'Ensure reliability and rapid incident response',
    themeColor: 'orange',
    icon: '🔧',
    onboarding: {
        title: 'Welcome to your SRE Workspace',
        subtitle: 'Monitor system health, respond to incidents, and improve reliability.',
        aiGuidance: 'AI tip: When alerts fire, stabilize first, then capture follow-up work on the DevSecOps board. This ensures incidents turn into tracked improvements rather than recurring issues.',
        primaryCtaLabel: 'Open Real-Time Monitor',
        primaryCtaHref: '/realtime-monitor',
        secondaryCtaLabel: 'View SRE Board',
        secondaryCtaHref: '/devsecops/board?persona=sre',
        dismissalKey: 'softwareOrg.workspace.sre.onboarding.dismissed',
    },
    metricHighlights: [
        { id: 'active-alerts', label: 'Active Alerts', icon: '🚨', dataKey: 'activeAlerts', warningThreshold: 3, criticalThreshold: 5, href: '/realtime-monitor' },
        { id: 'mttr', label: 'MTTR', icon: '⏱️', dataKey: 'mttr', warningThreshold: 60, criticalThreshold: 120, href: '/reports?type=incidents' },
        { id: 'uptime', label: 'Uptime', icon: '✅', dataKey: 'uptime', warningThreshold: 99.5, criticalThreshold: 99, href: '/dashboard' },
        { id: 'error-rate', label: 'Error Rate', icon: '❌', dataKey: 'errorRate', warningThreshold: 1, criticalThreshold: 5, href: '/realtime-monitor' },
    ],
    workflowSteps: [
        { id: 'sre-triage', label: 'Triage Alerts', description: 'Assess and prioritize incidents', icon: '🚨', href: '/realtime-monitor', phaseId: 'intake', primary: true },
        { id: 'sre-remediate', label: 'Plan Remediation', description: 'Create fix work items', icon: '📝', href: '/work-items', phaseId: 'plan' },
        { id: 'sre-verify', label: 'Validate Fix', description: 'Test remediation', icon: '🧪', href: '/automation', phaseId: 'verify' },
        { id: 'sre-deploy', label: 'Deploy Fix', description: 'Roll out remediation', icon: '🚀', href: '/models', phaseId: 'deploy' },
        { id: 'sre-monitor', label: 'Monitor Recovery', description: 'Verify system stability', icon: '📊', href: '/dashboard', phaseId: 'operate' },
        { id: 'sre-retro', label: 'Post-Mortem', description: 'Document learnings', icon: '📈', href: '/reports', phaseId: 'learn' },
    ],
    focusPhases: ['intake', 'plan', 'verify', 'deploy', 'operate', 'learn'],
    tips: [
        'Set up PagerDuty integration in Settings for on-call alerts.',
        'Use the ML Observatory to catch model drift before it causes incidents.',
        'The Control Tower dashboard shows system-wide health at a glance.',
        'Create runbooks in the Help Center for common incident types.',
    ],
};

/**
 * Security workspace preset
 */
const securityPreset: WorkspacePreset = {
    personaId: 'security',
    displayName: 'Security Workspace',
    tagline: 'Protect assets and ensure compliance',
    themeColor: 'red',
    icon: '🛡️',
    onboarding: {
        title: 'Welcome to your Security Workspace',
        subtitle: 'Monitor security posture, track vulnerabilities, and ensure compliance.',
        aiGuidance: 'AI tip: Focus on critical findings first using the DevSecOps board with Security filter. Track mitigations through the pipeline to ensure nothing falls through the cracks.',
        primaryCtaLabel: 'Open Security Center',
        primaryCtaHref: '/security',
        secondaryCtaLabel: 'View Security Board',
        secondaryCtaHref: '/devsecops/board?persona=security',
        dismissalKey: 'softwareOrg.workspace.security.onboarding.dismissed',
    },
    metricHighlights: [
        { id: 'critical-vulns', label: 'Critical Vulnerabilities', icon: '🔴', dataKey: 'criticalVulns', warningThreshold: 1, criticalThreshold: 3, href: '/security' },
        { id: 'compliance-score', label: 'Compliance Score', icon: '📋', dataKey: 'complianceScore', warningThreshold: 90, criticalThreshold: 80, href: '/reports?type=compliance' },
        { id: 'open-findings', label: 'Open Findings', icon: '🔍', dataKey: 'openFindings', warningThreshold: 10, criticalThreshold: 25, href: '/devsecops/board?persona=security' },
        { id: 'scan-coverage', label: 'Scan Coverage', icon: '🔬', dataKey: 'scanCoverage', warningThreshold: 95, criticalThreshold: 90, href: '/automation' },
    ],
    workflowSteps: [
        { id: 'sec-posture', label: 'Review Posture', description: 'Assess security status', icon: '🛡️', href: '/security', phaseId: 'intake', primary: true },
        { id: 'sec-plan', label: 'Plan Controls', description: 'Define mitigations', icon: '📝', href: '/work-items', phaseId: 'plan' },
        { id: 'sec-scan', label: 'Security Scan', description: 'Run vulnerability scans', icon: '🔍', href: '/automation', phaseId: 'verify' },
        { id: 'sec-monitor', label: 'Monitor Compliance', description: 'Track compliance status', icon: '📊', href: '/security', phaseId: 'operate' },
        { id: 'sec-report', label: 'Compliance Report', description: 'Generate audit reports', icon: '📈', href: '/reports?type=compliance', phaseId: 'learn' },
    ],
    focusPhases: ['intake', 'plan', 'verify', 'operate', 'learn'],
    tips: [
        'Critical vulnerabilities should be addressed within 24 hours.',
        'Use the compliance reports for audit preparation.',
        'Set up automated scans in the Automation Engine for continuous monitoring.',
        'The Org Builder shows which services handle sensitive data.',
    ],
};

/**
 * Admin workspace preset
 */
const adminPreset: WorkspacePreset = {
    personaId: 'admin',
    displayName: 'Admin Workspace',
    tagline: 'Configure and govern the platform',
    themeColor: 'slate',
    icon: '⚡',
    onboarding: {
        title: 'Welcome to your Admin Workspace',
        subtitle: 'Configure the organization, manage personas, and oversee platform governance.',
        aiGuidance: 'AI tip: Start with the Org Builder to visualize your organization structure. Then configure persona permissions and integrations to enable your teams.',
        primaryCtaLabel: 'Open Org Builder',
        primaryCtaHref: '/org',
        secondaryCtaLabel: 'Manage Personas',
        secondaryCtaHref: '/personas',
        dismissalKey: 'softwareOrg.workspace.admin.onboarding.dismissed',
    },
    metricHighlights: [
        { id: 'active-users', label: 'Active Users', icon: '👥', dataKey: 'activeUsers', href: '/personas' },
        { id: 'integrations', label: 'Active Integrations', icon: '🔌', dataKey: 'activeIntegrations', href: '/settings?tab=integrations' },
        { id: 'audit-events', label: 'Audit Events (24h)', icon: '📜', dataKey: 'auditEvents', href: '/reports?type=audit' },
        { id: 'system-health', label: 'System Health', icon: '💚', dataKey: 'systemHealth', warningThreshold: 95, criticalThreshold: 90, href: '/dashboard' },
    ],
    workflowSteps: [
        { id: 'admin-org', label: 'Org Overview', description: 'View organization structure', icon: '🏗️', href: '/org', phaseId: 'intake', primary: true },
        { id: 'admin-personas', label: 'Configure Personas', description: 'Manage roles and permissions', icon: '👤', href: '/personas', phaseId: 'plan' },
        { id: 'admin-integrations', label: 'Manage Integrations', description: 'Configure external tools', icon: '🔌', href: '/settings?tab=integrations', phaseId: 'build' },
        { id: 'admin-security', label: 'Security Center', description: 'Review security posture', icon: '🔒', href: '/security', phaseId: 'operate' },
        { id: 'admin-audit', label: 'Audit Reports', description: 'Review audit trail', icon: '📈', href: '/reports?type=audit', phaseId: 'learn' },
    ],
    focusPhases: ['intake', 'plan', 'build', 'operate', 'learn'],
    tips: [
        'Use the Org Builder to visualize dependencies between teams and services.',
        'Review audit logs regularly for security and compliance.',
        'Configure SSO and MFA in Settings for enhanced security.',
        'Set up role-based access control to limit sensitive operations.',
    ],
};

/**
 * Viewer workspace preset
 */
const viewerPreset: WorkspacePreset = {
    personaId: 'viewer',
    displayName: 'Viewer Workspace',
    tagline: 'Monitor and explore the platform',
    themeColor: 'gray',
    icon: '👁️',
    onboarding: {
        title: 'Welcome to your Viewer Workspace',
        subtitle: 'Explore dashboards, reports, and organization insights.',
        aiGuidance: 'AI tip: Use the Control Tower dashboard for a high-level overview, then drill into specific reports for detailed analysis.',
        primaryCtaLabel: 'Open Control Tower',
        primaryCtaHref: '/dashboard',
        secondaryCtaLabel: 'View Reports',
        secondaryCtaHref: '/reports',
        dismissalKey: 'softwareOrg.workspace.viewer.onboarding.dismissed',
    },
    metricHighlights: [
        { id: 'deployments', label: 'Deployments (7d)', icon: '🚀', dataKey: 'deployments7d', href: '/reports?type=deployments' },
        { id: 'team-velocity', label: 'Team Velocity', icon: '📈', dataKey: 'teamVelocity', href: '/reports?type=velocity' },
        { id: 'uptime', label: 'System Uptime', icon: '✅', dataKey: 'uptime', href: '/dashboard' },
        { id: 'incidents', label: 'Incidents (7d)', icon: '🚨', dataKey: 'incidents7d', href: '/reports?type=incidents' },
    ],
    workflowSteps: [
        { id: 'viewer-dashboard', label: 'Control Tower', description: 'View system overview', icon: '📊', href: '/dashboard', phaseId: 'operate', primary: true },
        { id: 'viewer-reports', label: 'Reports', description: 'Explore detailed reports', icon: '📈', href: '/reports', phaseId: 'learn' },
    ],
    focusPhases: ['operate', 'learn'],
    tips: [
        'Use the time range filter to compare different periods.',
        'Export reports to PDF or CSV for offline analysis.',
        'Pin frequently viewed dashboards for quick access.',
    ],
};

/**
 * All workspace presets indexed by persona ID
 */
export const WORKSPACE_PRESETS: Record<PersonaId, WorkspacePreset> = {
    engineer: engineerPreset,
    lead: leadPreset,
    sre: srePreset,
    security: securityPreset,
    admin: adminPreset,
    viewer: viewerPreset,
};

/**
 * Get workspace preset for a persona
 *
 * @param personaId Persona identifier
 * @returns Workspace preset or undefined
 */
export function getWorkspacePreset(personaId: PersonaId | string): WorkspacePreset | undefined {
    return WORKSPACE_PRESETS[personaId as PersonaId];
}

/**
 * Get onboarding config for a persona
 *
 * @param personaId Persona identifier
 * @returns Onboarding config or undefined
 */
export function getWorkspaceOnboarding(personaId: PersonaId | string): WorkspaceOnboarding | undefined {
    return getWorkspacePreset(personaId)?.onboarding;
}

/**
 * Get a random tip for a persona
 *
 * @param personaId Persona identifier
 * @returns Random tip string
 */
export function getWorkspaceTip(personaId: PersonaId | string): string {
    const preset = getWorkspacePreset(personaId);
    if (!preset || preset.tips.length === 0) {
        return 'Explore the platform to discover all available features.';
    }
    return preset.tips[Math.floor(Math.random() * preset.tips.length)];
}

export default WORKSPACE_PRESETS;
