/**
 * Mock Organization Data
 *
 * <p><b>Purpose</b><br>
 * Provides mock data for the Org Builder feature during development.
 * This data structure mirrors what would come from the backend/virtual-org APIs.
 *
 * <p><b>TODO</b><br>
 * - Wire to backend virtual-org APIs when available
 * - Add CRUD operations via React Query mutations
 * - Implement real-time updates via WebSocket
 *
 * @doc.type mock
 * @doc.purpose Mock organization configuration data
 * @doc.layer product
 * @doc.pattern Mock Data
 */

import type {
    OrgConfig,
    OrgGraphData,
    DepartmentConfig,
    ServiceConfig,
    WorkflowConfig,
    IntegrationConfig,
    PersonaBinding,
    DevSecOpsFlowConfig,
} from '@/shared/types/org';

/**
 * Mock departments
 */
export const MOCK_DEPARTMENTS: DepartmentConfig[] = [
    {
        id: 'dept-platform',
        name: 'Platform Engineering',
        description: 'Core platform infrastructure and developer experience',
        owner: 'alice@example.com',
        members: ['alice@example.com', 'bob@example.com', 'charlie@example.com'],
        serviceIds: ['svc-api-gateway', 'svc-auth', 'svc-config'],
        workflowIds: ['wf-deploy-platform', 'wf-incident-response'],
        color: '#3B82F6',
        icon: '🏗️',
    },
    {
        id: 'dept-product',
        name: 'Product Engineering',
        description: 'Customer-facing product features and experiences',
        owner: 'diana@example.com',
        members: ['diana@example.com', 'eve@example.com', 'frank@example.com'],
        serviceIds: ['svc-web-app', 'svc-mobile-bff', 'svc-recommendations'],
        workflowIds: ['wf-feature-release', 'wf-ab-test'],
        color: '#10B981',
        icon: '🚀',
    },
    {
        id: 'dept-data',
        name: 'Data Engineering',
        description: 'Data pipelines, ML infrastructure, and analytics',
        owner: 'george@example.com',
        members: ['george@example.com', 'helen@example.com'],
        serviceIds: ['svc-data-pipeline', 'svc-ml-serving', 'svc-analytics'],
        workflowIds: ['wf-model-deploy', 'wf-data-quality'],
        color: '#8B5CF6',
        icon: '📊',
    },
    {
        id: 'dept-security',
        name: 'Security & Compliance',
        description: 'Security operations, compliance, and risk management',
        owner: 'ivan@example.com',
        members: ['ivan@example.com', 'julia@example.com'],
        serviceIds: ['svc-vault', 'svc-audit-log'],
        workflowIds: ['wf-security-scan', 'wf-compliance-check'],
        color: '#EF4444',
        icon: '🔒',
    },
];

/**
 * Mock services
 */
export const MOCK_SERVICES: ServiceConfig[] = [
    {
        id: 'svc-api-gateway',
        name: 'API Gateway',
        description: 'Central API gateway for all external traffic',
        departmentId: 'dept-platform',
        tier: 'tier-0',
        riskLevel: 'critical',
        slo: { availability: 99.99, latencyP95Ms: 50, errorRateThreshold: 0.1 },
        dependencies: [],
        dependents: ['svc-web-app', 'svc-mobile-bff'],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-datadog', 'int-pagerduty'],
        tags: ['gateway', 'critical-path'],
    },
    {
        id: 'svc-auth',
        name: 'Auth Service',
        description: 'Authentication and authorization service',
        departmentId: 'dept-platform',
        tier: 'tier-0',
        riskLevel: 'critical',
        slo: { availability: 99.99, latencyP95Ms: 100, errorRateThreshold: 0.05 },
        dependencies: ['svc-vault'],
        dependents: ['svc-api-gateway', 'svc-web-app'],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-datadog', 'int-github'],
        tags: ['auth', 'security', 'critical-path'],
    },
    {
        id: 'svc-config',
        name: 'Config Service',
        description: 'Centralized configuration management',
        departmentId: 'dept-platform',
        tier: 'tier-1',
        riskLevel: 'high',
        slo: { availability: 99.9, latencyP95Ms: 200, errorRateThreshold: 0.5 },
        dependencies: ['svc-vault'],
        dependents: ['svc-api-gateway', 'svc-auth', 'svc-web-app'],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-github'],
        tags: ['config', 'infrastructure'],
    },
    {
        id: 'svc-web-app',
        name: 'Web Application',
        description: 'Main customer-facing web application',
        departmentId: 'dept-product',
        tier: 'tier-1',
        riskLevel: 'high',
        slo: { availability: 99.9, latencyP95Ms: 500, errorRateThreshold: 1 },
        dependencies: ['svc-api-gateway', 'svc-auth', 'svc-recommendations'],
        dependents: [],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-datadog', 'int-github', 'int-jira'],
        tags: ['frontend', 'customer-facing'],
    },
    {
        id: 'svc-mobile-bff',
        name: 'Mobile BFF',
        description: 'Backend-for-frontend for mobile applications',
        departmentId: 'dept-product',
        tier: 'tier-1',
        riskLevel: 'medium',
        slo: { availability: 99.9, latencyP95Ms: 300, errorRateThreshold: 1 },
        dependencies: ['svc-api-gateway', 'svc-auth'],
        dependents: [],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-datadog', 'int-github'],
        tags: ['mobile', 'bff'],
    },
    {
        id: 'svc-recommendations',
        name: 'Recommendations Engine',
        description: 'ML-powered product recommendations',
        departmentId: 'dept-product',
        tier: 'tier-2',
        riskLevel: 'medium',
        slo: { availability: 99.5, latencyP95Ms: 200, errorRateThreshold: 2 },
        dependencies: ['svc-ml-serving'],
        dependents: ['svc-web-app'],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-datadog', 'int-mlflow'],
        tags: ['ml', 'recommendations'],
    },
    {
        id: 'svc-data-pipeline',
        name: 'Data Pipeline',
        description: 'ETL and data processing pipelines',
        departmentId: 'dept-data',
        tier: 'tier-2',
        riskLevel: 'medium',
        slo: { availability: 99.5, latencyP95Ms: 5000, errorRateThreshold: 1 },
        dependencies: [],
        dependents: ['svc-ml-serving', 'svc-analytics'],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-datadog', 'int-airflow'],
        tags: ['data', 'etl'],
    },
    {
        id: 'svc-ml-serving',
        name: 'ML Serving',
        description: 'Model serving infrastructure',
        departmentId: 'dept-data',
        tier: 'tier-1',
        riskLevel: 'high',
        slo: { availability: 99.9, latencyP95Ms: 100, errorRateThreshold: 0.5 },
        dependencies: ['svc-data-pipeline'],
        dependents: ['svc-recommendations'],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-datadog', 'int-mlflow'],
        tags: ['ml', 'serving'],
    },
    {
        id: 'svc-analytics',
        name: 'Analytics Service',
        description: 'Business analytics and reporting',
        departmentId: 'dept-data',
        tier: 'tier-2',
        riskLevel: 'low',
        slo: { availability: 99, latencyP95Ms: 2000, errorRateThreshold: 2 },
        dependencies: ['svc-data-pipeline'],
        dependents: [],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-datadog'],
        tags: ['analytics', 'reporting'],
    },
    {
        id: 'svc-vault',
        name: 'Secrets Vault',
        description: 'Secrets and credential management',
        departmentId: 'dept-security',
        tier: 'tier-0',
        riskLevel: 'critical',
        slo: { availability: 99.99, latencyP95Ms: 50, errorRateThreshold: 0.01 },
        dependencies: [],
        dependents: ['svc-auth', 'svc-config'],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-pagerduty'],
        tags: ['security', 'secrets'],
    },
    {
        id: 'svc-audit-log',
        name: 'Audit Log Service',
        description: 'Compliance audit logging and retention',
        departmentId: 'dept-security',
        tier: 'tier-1',
        riskLevel: 'high',
        slo: { availability: 99.9, latencyP95Ms: 500, errorRateThreshold: 0.1 },
        dependencies: [],
        dependents: [],
        environments: ['development', 'staging', 'production'],
        integrationIds: ['int-datadog'],
        tags: ['security', 'compliance', 'audit'],
    },
];

/**
 * Mock workflows
 */
export const MOCK_WORKFLOWS: WorkflowConfig[] = [
    {
        id: 'wf-deploy-platform',
        name: 'Platform Deployment',
        description: 'Deploy platform services with canary rollout',
        trigger: 'manual',
        steps: [
            { id: 'step-1', name: 'Run Tests', type: 'automated', integrationId: 'int-github' },
            { id: 'step-2', name: 'Security Scan', type: 'automated', integrationId: 'int-snyk' },
            { id: 'step-3', name: 'Lead Approval', type: 'approval', approvers: ['lead'] },
            { id: 'step-4', name: 'Deploy Canary', type: 'automated', integrationId: 'int-argocd' },
            { id: 'step-5', name: 'Full Rollout', type: 'automated', integrationId: 'int-argocd' },
        ],
        departmentIds: ['dept-platform'],
        serviceIds: ['svc-api-gateway', 'svc-auth', 'svc-config'],
        enabled: true,
    },
    {
        id: 'wf-incident-response',
        name: 'Incident Response',
        description: 'Automated incident response workflow',
        trigger: 'event',
        triggerConfig: { eventType: 'alert.critical' },
        steps: [
            { id: 'step-1', name: 'Create Incident', type: 'automated', integrationId: 'int-jira' },
            { id: 'step-2', name: 'Page On-Call', type: 'notification', integrationId: 'int-pagerduty' },
            { id: 'step-3', name: 'Triage', type: 'manual' },
            { id: 'step-4', name: 'Resolve', type: 'manual' },
        ],
        departmentIds: ['dept-platform', 'dept-security'],
        serviceIds: [],
        enabled: true,
    },
    {
        id: 'wf-feature-release',
        name: 'Feature Release',
        description: 'Standard feature release workflow',
        trigger: 'manual',
        steps: [
            { id: 'step-1', name: 'QA Sign-off', type: 'approval', approvers: ['lead'] },
            { id: 'step-2', name: 'Deploy to Staging', type: 'automated', integrationId: 'int-argocd' },
            { id: 'step-3', name: 'Smoke Tests', type: 'automated', integrationId: 'int-github' },
            { id: 'step-4', name: 'Production Approval', type: 'approval', approvers: ['lead', 'admin'] },
            { id: 'step-5', name: 'Deploy to Production', type: 'automated', integrationId: 'int-argocd' },
        ],
        departmentIds: ['dept-product'],
        serviceIds: ['svc-web-app', 'svc-mobile-bff'],
        enabled: true,
    },
    {
        id: 'wf-model-deploy',
        name: 'ML Model Deployment',
        description: 'Deploy ML models with A/B testing',
        trigger: 'manual',
        steps: [
            { id: 'step-1', name: 'Model Validation', type: 'automated', integrationId: 'int-mlflow' },
            { id: 'step-2', name: 'Data Science Review', type: 'approval', approvers: ['lead'] },
            { id: 'step-3', name: 'Shadow Deploy', type: 'automated', integrationId: 'int-argocd' },
            { id: 'step-4', name: 'A/B Test', type: 'automated', integrationId: 'int-mlflow' },
            { id: 'step-5', name: 'Full Rollout', type: 'automated', integrationId: 'int-argocd' },
        ],
        departmentIds: ['dept-data'],
        serviceIds: ['svc-ml-serving', 'svc-recommendations'],
        enabled: true,
    },
    {
        id: 'wf-security-scan',
        name: 'Security Scan',
        description: 'Automated security vulnerability scanning',
        trigger: 'schedule',
        triggerConfig: { cron: '0 2 * * *' },
        steps: [
            { id: 'step-1', name: 'SAST Scan', type: 'automated', integrationId: 'int-snyk' },
            { id: 'step-2', name: 'DAST Scan', type: 'automated', integrationId: 'int-snyk' },
            { id: 'step-3', name: 'Report Generation', type: 'automated' },
            { id: 'step-4', name: 'Security Review', type: 'approval', approvers: ['security'] },
        ],
        departmentIds: ['dept-security'],
        serviceIds: [],
        enabled: true,
    },
];

/**
 * Mock integrations
 */
export const MOCK_INTEGRATIONS: IntegrationConfig[] = [
    {
        id: 'int-github',
        name: 'GitHub',
        type: 'source-control',
        description: 'Source code management and CI/CD',
        enabled: true,
        departmentIds: ['dept-platform', 'dept-product', 'dept-data'],
        serviceIds: ['svc-api-gateway', 'svc-auth', 'svc-web-app', 'svc-ml-serving'],
        managedByPersonas: ['admin', 'lead'],
        configPath: '/settings?tab=integrations&id=github',
        externalUrl: 'https://github.com',
        icon: '🐙',
        status: 'healthy',
    },
    {
        id: 'int-datadog',
        name: 'Datadog',
        type: 'monitoring',
        description: 'Application performance monitoring',
        enabled: true,
        departmentIds: ['dept-platform', 'dept-product', 'dept-data', 'dept-security'],
        serviceIds: MOCK_SERVICES.map((s) => s.id),
        managedByPersonas: ['admin', 'sre'],
        configPath: '/settings?tab=integrations&id=datadog',
        externalUrl: 'https://app.datadoghq.com',
        icon: '🐕',
        status: 'healthy',
    },
    {
        id: 'int-pagerduty',
        name: 'PagerDuty',
        type: 'notification',
        description: 'Incident alerting and on-call management',
        enabled: true,
        departmentIds: ['dept-platform', 'dept-security'],
        serviceIds: ['svc-api-gateway', 'svc-vault'],
        managedByPersonas: ['admin', 'sre'],
        configPath: '/settings?tab=integrations&id=pagerduty',
        externalUrl: 'https://pagerduty.com',
        icon: '📟',
        status: 'healthy',
    },
    {
        id: 'int-jira',
        name: 'Jira',
        type: 'ticketing',
        description: 'Issue tracking and project management',
        enabled: true,
        departmentIds: ['dept-platform', 'dept-product', 'dept-data', 'dept-security'],
        serviceIds: [],
        managedByPersonas: ['admin', 'lead'],
        configPath: '/settings?tab=integrations&id=jira',
        externalUrl: 'https://jira.atlassian.com',
        icon: '📋',
        status: 'healthy',
    },
    {
        id: 'int-snyk',
        name: 'Snyk',
        type: 'security-scanner',
        description: 'Security vulnerability scanning',
        enabled: true,
        departmentIds: ['dept-security'],
        serviceIds: MOCK_SERVICES.map((s) => s.id),
        managedByPersonas: ['admin', 'security'],
        configPath: '/settings?tab=integrations&id=snyk',
        externalUrl: 'https://snyk.io',
        icon: '🔍',
        status: 'healthy',
    },
    {
        id: 'int-argocd',
        name: 'ArgoCD',
        type: 'ci-cd',
        description: 'GitOps continuous delivery',
        enabled: true,
        departmentIds: ['dept-platform', 'dept-product', 'dept-data'],
        serviceIds: MOCK_SERVICES.map((s) => s.id),
        managedByPersonas: ['admin', 'sre'],
        configPath: '/settings?tab=integrations&id=argocd',
        externalUrl: 'https://argocd.example.com',
        icon: '🚀',
        status: 'healthy',
    },
    {
        id: 'int-mlflow',
        name: 'MLflow',
        type: 'artifact-registry',
        description: 'ML experiment tracking and model registry',
        enabled: true,
        departmentIds: ['dept-data'],
        serviceIds: ['svc-ml-serving', 'svc-recommendations'],
        managedByPersonas: ['admin', 'lead'],
        configPath: '/settings?tab=integrations&id=mlflow',
        externalUrl: 'https://mlflow.example.com',
        icon: '🧪',
        status: 'healthy',
    },
    {
        id: 'int-ai-copilot',
        name: 'AI Copilot',
        type: 'ai-agent',
        description: 'AI-powered development assistant',
        enabled: true,
        departmentIds: ['dept-platform', 'dept-product', 'dept-data'],
        serviceIds: [],
        managedByPersonas: ['admin'],
        configPath: '/settings?tab=integrations&id=ai-copilot',
        icon: '🤖',
        status: 'healthy',
    },
];

/**
 * Mock persona bindings
 */
export const MOCK_PERSONA_BINDINGS: PersonaBinding[] = [
    {
        personaId: 'engineer',
        displayName: 'Engineer',
        description: 'Software engineers building features and fixing bugs',
        departmentIds: ['dept-platform', 'dept-product', 'dept-data'],
        serviceIds: MOCK_SERVICES.map((s) => s.id),
        workflowIds: ['wf-feature-release', 'wf-model-deploy'],
        permissions: ['read:all', 'write:code', 'deploy:staging'],
        defaultPhases: ['intake', 'plan', 'build', 'verify', 'review', 'staging'],
        quickActions: [
            { id: 'qa-stories', label: 'My Stories', icon: '📋', href: '/#my-stories' },
            { id: 'qa-devsecops', label: 'DevSecOps Board', icon: '🔄', href: '/devsecops/board?persona=engineer' },
            { id: 'qa-workflows', label: 'Workflows', icon: '🔗', href: '/workflows' },
        ],
    },
    {
        personaId: 'lead',
        displayName: 'Tech Lead',
        description: 'Technical leads overseeing teams and approving releases',
        departmentIds: ['dept-platform', 'dept-product', 'dept-data'],
        serviceIds: MOCK_SERVICES.map((s) => s.id),
        workflowIds: MOCK_WORKFLOWS.map((w) => w.id),
        permissions: ['read:all', 'write:code', 'approve:release', 'deploy:production'],
        defaultPhases: ['intake', 'plan', 'build', 'verify', 'review', 'staging', 'deploy'],
        quickActions: [
            { id: 'qa-approvals', label: 'Pending Approvals', icon: '✅', href: '/hitl' },
            { id: 'qa-devsecops', label: 'DevSecOps Board', icon: '🔄', href: '/devsecops/board?persona=lead' },
            { id: 'qa-reports', label: 'Reports', icon: '📈', href: '/reports' },
        ],
    },
    {
        personaId: 'sre',
        displayName: 'SRE',
        description: 'Site reliability engineers ensuring system health',
        departmentIds: ['dept-platform', 'dept-security'],
        serviceIds: MOCK_SERVICES.filter((s) => s.tier === 'tier-0' || s.tier === 'tier-1').map((s) => s.id),
        workflowIds: ['wf-deploy-platform', 'wf-incident-response'],
        permissions: ['read:all', 'write:infra', 'deploy:production', 'manage:incidents'],
        defaultPhases: ['intake', 'plan', 'verify', 'deploy', 'operate', 'learn'],
        quickActions: [
            { id: 'qa-monitor', label: 'Real-Time Monitor', icon: '⏱️', href: '/realtime-monitor' },
            { id: 'qa-devsecops', label: 'DevSecOps Board', icon: '🔄', href: '/devsecops/board?persona=sre' },
            { id: 'qa-dashboard', label: 'Control Tower', icon: '📊', href: '/dashboard' },
        ],
    },
    {
        personaId: 'security',
        displayName: 'Security Engineer',
        description: 'Security engineers managing compliance and vulnerabilities',
        departmentIds: ['dept-security'],
        serviceIds: MOCK_SERVICES.map((s) => s.id),
        workflowIds: ['wf-security-scan', 'wf-compliance-check'],
        permissions: ['read:all', 'write:security', 'manage:compliance'],
        defaultPhases: ['intake', 'plan', 'verify', 'operate', 'learn'],
        quickActions: [
            { id: 'qa-security', label: 'Security Center', icon: '🔒', href: '/security' },
            { id: 'qa-devsecops', label: 'DevSecOps Board', icon: '🔄', href: '/devsecops/board?persona=security' },
            { id: 'qa-reports', label: 'Compliance Reports', icon: '📈', href: '/reports?type=compliance' },
        ],
    },
    {
        personaId: 'admin',
        displayName: 'Admin',
        description: 'System administrators managing the organization',
        departmentIds: MOCK_DEPARTMENTS.map((d) => d.id),
        serviceIds: MOCK_SERVICES.map((s) => s.id),
        workflowIds: MOCK_WORKFLOWS.map((w) => w.id),
        permissions: ['admin:all'],
        defaultPhases: ['intake', 'plan', 'build', 'verify', 'review', 'staging', 'deploy', 'operate', 'learn'],
        quickActions: [
            { id: 'qa-org', label: 'Org Builder', icon: '🏗️', href: '/org' },
            { id: 'qa-personas', label: 'Personas', icon: '👤', href: '/personas' },
            { id: 'qa-settings', label: 'Settings', icon: '⚙️', href: '/settings' },
        ],
    },
];

/**
 * Mock DevSecOps flow configurations
 */
export const MOCK_DEVSECOPS_FLOWS: DevSecOpsFlowConfig[] = [
    {
        id: 'flow-engineer',
        name: 'Engineer Flow',
        personaId: 'engineer',
        phases: ['intake', 'plan', 'build', 'verify', 'review', 'staging', 'deploy', 'operate', 'learn'],
        steps: [
            { stepId: 'eng-1', phaseId: 'intake', label: 'View Story', route: '/work-items/:storyId' },
            { stepId: 'eng-2', phaseId: 'plan', label: 'Plan Implementation', route: '/work-items/:storyId/plan' },
            { stepId: 'eng-3', phaseId: 'build', label: 'Development', route: '/work-items/:storyId/dev' },
            { stepId: 'eng-4', phaseId: 'verify', label: 'Run Tests', route: '/automation' },
            { stepId: 'eng-5', phaseId: 'review', label: 'Code Review', route: '/work-items/:storyId/review' },
            { stepId: 'eng-6', phaseId: 'staging', label: 'Staging Deploy', route: '/models' },
            { stepId: 'eng-7', phaseId: 'deploy', label: 'Production Deploy', route: '/models' },
            { stepId: 'eng-8', phaseId: 'operate', label: 'Monitor', route: '/dashboard' },
            { stepId: 'eng-9', phaseId: 'learn', label: 'Retrospective', route: '/reports' },
        ],
        description: 'End-to-end development flow for engineers',
    },
    {
        id: 'flow-sre',
        name: 'SRE Flow',
        personaId: 'sre',
        phases: ['intake', 'plan', 'verify', 'deploy', 'operate', 'learn'],
        steps: [
            { stepId: 'sre-1', phaseId: 'intake', label: 'Triage Alert', route: '/realtime-monitor' },
            { stepId: 'sre-2', phaseId: 'plan', label: 'Plan Remediation', route: '/work-items/:storyId/plan' },
            { stepId: 'sre-3', phaseId: 'verify', label: 'Validate Fix', route: '/automation' },
            { stepId: 'sre-4', phaseId: 'deploy', label: 'Deploy Fix', route: '/models' },
            { stepId: 'sre-5', phaseId: 'operate', label: 'Monitor Recovery', route: '/dashboard' },
            { stepId: 'sre-6', phaseId: 'learn', label: 'Post-Mortem', route: '/reports' },
        ],
        description: 'Incident response and reliability flow for SREs',
    },
    {
        id: 'flow-security',
        name: 'Security Flow',
        personaId: 'security',
        phases: ['intake', 'plan', 'verify', 'operate', 'learn'],
        steps: [
            { stepId: 'sec-1', phaseId: 'intake', label: 'Review Posture', route: '/security' },
            { stepId: 'sec-2', phaseId: 'plan', label: 'Plan Controls', route: '/work-items/:storyId/plan' },
            { stepId: 'sec-3', phaseId: 'verify', label: 'Security Scan', route: '/automation' },
            { stepId: 'sec-4', phaseId: 'operate', label: 'Monitor Compliance', route: '/security' },
            { stepId: 'sec-5', phaseId: 'learn', label: 'Compliance Report', route: '/reports?type=compliance' },
        ],
        description: 'Security and compliance flow for security engineers',
    },
];

/**
 * Complete mock organization configuration
 */
export const MOCK_ORG_CONFIG: OrgConfig = {
    id: 'org-software-org',
    name: 'Software Org',
    description: 'Virtual software organization for development and operations',
    departments: MOCK_DEPARTMENTS,
    services: MOCK_SERVICES,
    workflows: MOCK_WORKFLOWS,
    personaBindings: MOCK_PERSONA_BINDINGS,
    integrations: MOCK_INTEGRATIONS,
    devSecOpsFlows: MOCK_DEVSECOPS_FLOWS,
    metadata: {
        version: '1.0.0',
        lastUpdated: new Date().toISOString(),
        createdBy: 'system',
    },
};

/**
 * Convert OrgConfig to OrgGraphData for visualization
 */
export function orgConfigToGraphData(config: OrgConfig): OrgGraphData {
    const nodes: OrgGraphData['nodes'] = [];
    const edges: OrgGraphData['edges'] = [];

    // Add department nodes
    config.departments.forEach((dept) => {
        nodes.push({
            id: dept.id,
            type: 'department',
            label: dept.name,
            data: dept,
            style: { icon: dept.icon, color: dept.color },
        });
    });

    // Add service nodes and edges
    config.services.forEach((svc) => {
        nodes.push({
            id: svc.id,
            type: 'service',
            label: svc.name,
            data: svc,
        });

        // Edge: department owns service
        edges.push({
            id: `edge-${svc.departmentId}-${svc.id}`,
            source: svc.departmentId,
            target: svc.id,
            type: 'owns',
        });

        // Edges: service dependencies
        svc.dependencies.forEach((depId) => {
            edges.push({
                id: `edge-${svc.id}-${depId}`,
                source: svc.id,
                target: depId,
                type: 'depends-on',
                style: { dashed: true },
            });
        });
    });

    // Add workflow nodes
    config.workflows.forEach((wf) => {
        nodes.push({
            id: wf.id,
            type: 'workflow',
            label: wf.name,
            data: wf,
        });

        // Edges: workflow belongs to departments
        wf.departmentIds.forEach((deptId) => {
            edges.push({
                id: `edge-${deptId}-${wf.id}`,
                source: deptId,
                target: wf.id,
                type: 'owns',
            });
        });
    });

    // Add integration nodes
    config.integrations.forEach((int) => {
        nodes.push({
            id: int.id,
            type: 'integration',
            label: int.name,
            data: int,
            style: { icon: int.icon },
        });
    });

    // Add persona nodes
    config.personaBindings.forEach((persona) => {
        nodes.push({
            id: `persona-${persona.personaId}`,
            type: 'persona',
            label: persona.displayName,
            data: persona,
        });
    });

    return { nodes, edges };
}

/**
 * Get mock graph data
 */
export function getMockOrgGraphData(): OrgGraphData {
    return orgConfigToGraphData(MOCK_ORG_CONFIG);
}

/**
 * Get mock org config
 */
export function getMockOrgConfig(): OrgConfig {
    return MOCK_ORG_CONFIG;
}
