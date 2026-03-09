/**
 * Mock Stage Events
 *
 * Sample event timelines for DevSecOps stages.
 * In production, these would come from event streaming APIs.
 *
 * @doc.type mock-data
 * @doc.purpose Sample timeline events for stage dashboards
 */

/**
 * Stage event type (moved from deleted StageTimeline component)
 */
export interface StageEvent {
    id: string;
    type: 'milestone' | 'deployment' | 'test' | 'incident' | 'security' | 'approval' | 'alert';
    title: string;
    description?: string;
    actor: string;
    timestamp: string;
    status: 'success' | 'failure' | 'warning' | 'pending';
    metadata?: Record<string, unknown>;
}

export const mockStageEvents: Record<string, StageEvent[]> = {
    develop: [
        {
            id: 'evt-dev-001',
            type: 'milestone',
            title: 'Feature branch merged to main',
            description: 'PR #1234: Payment API refactoring',
            actor: 'alice@example.com',
            timestamp: new Date(Date.now() - 30 * 60000).toISOString(), // 30 mins ago
            status: 'success',
            metadata: { branch: 'feature/payment-api', commits: '12', tenantId: 'acme-payments' },
        },
        {
            id: 'evt-dev-002',
            type: 'test',
            title: 'Unit tests passed',
            description: 'All 156 unit tests executed successfully',
            actor: 'ci-bot',
            timestamp: new Date(Date.now() - 2 * 3600000).toISOString(), // 2 hours ago
            status: 'success',
            metadata: { tests: '156', coverage: '87%', tenantId: 'acme-payments' },
        },
        {
            id: 'evt-dev-003',
            type: 'incident',
            title: 'Blocked: Dependency conflict',
            description: 'Incompatible versions of authentication library',
            actor: 'bob@example.com',
            timestamp: new Date(Date.now() - 4 * 3600000).toISOString(), // 4 hours ago
            status: 'failure',
            metadata: { issue: 'DEV-302', severity: 'high', incidentId: 'incident-456', tenantId: 'acme-payments' },
        },
    ],

    build: [
        {
            id: 'evt-build-001',
            type: 'deployment',
            title: 'Build artifacts published',
            description: 'Docker image pushed to registry',
            actor: 'ci-bot',
            timestamp: new Date(Date.now() - 45 * 60000).toISOString(),
            status: 'success',
            metadata: { image: 'payment-api:v2.3.0', size: '245MB' },
        },
        {
            id: 'evt-build-002',
            type: 'test',
            title: 'Integration tests passed',
            description: 'Payment flow end-to-end validation',
            actor: 'ci-bot',
            timestamp: new Date(Date.now() - 90 * 60000).toISOString(),
            status: 'success',
            metadata: { duration: '4m 32s', tests: '42' },
        },
        {
            id: 'evt-build-003',
            type: 'milestone',
            title: 'Build optimization completed',
            description: 'Reduced build time from 12min to 4min',
            actor: 'carol@example.com',
            timestamp: new Date(Date.now() - 24 * 3600000).toISOString(),
            status: 'success',
            metadata: { improvement: '67%', issue: 'BUILD-403' },
        },
    ],

    test: [
        {
            id: 'evt-test-001',
            type: 'incident',
            title: 'Integration tests failing',
            description: 'Checkout flow E2E tests timing out',
            actor: 'qa-automation',
            timestamp: new Date(Date.now() - 20 * 60000).toISOString(),
            status: 'failure',
            metadata: { suite: 'checkout-e2e', failed: '3/15' },
        },
        {
            id: 'evt-test-002',
            type: 'test',
            title: 'Load testing completed',
            description: 'Payment gateway handled 1000 concurrent requests',
            actor: 'qa-team',
            timestamp: new Date(Date.now() - 3 * 3600000).toISOString(),
            status: 'success',
            metadata: { p95: '245ms', throughput: '1000 req/s' },
        },
        {
            id: 'evt-test-003',
            type: 'incident',
            title: 'Flaky test investigation',
            description: 'Test suite intermittently failing on CI',
            actor: 'dave@example.com',
            timestamp: new Date(Date.now() - 6 * 3600000).toISOString(),
            status: 'warning',
            metadata: { issue: 'TEST-503', flakiness: '23%' },
        },
        {
            id: 'evt-test-004',
            type: 'milestone',
            title: 'Test coverage milestone reached',
            description: 'Achieved 82% code coverage',
            actor: 'qa-team',
            timestamp: new Date(Date.now() - 2 * 86400000).toISOString(),
            status: 'success',
            metadata: { coverage: '82%', target: '80%' },
        },
    ],

    secure: [
        {
            id: 'evt-sec-001',
            type: 'incident',
            title: 'Critical vulnerability detected',
            description: 'SQL injection vulnerability in payment API',
            actor: 'security-scanner',
            timestamp: new Date(Date.now() - 4 * 3600000).toISOString(),
            status: 'failure',
            metadata: { cvss: '9.1', cve: 'CVE-2025-1234', incidentId: 'incident-123', tenantId: 'acme-payments' },
        },
        {
            id: 'evt-sec-002',
            type: 'security',
            title: 'TLS certificate renewal in progress',
            description: 'Updating certificates expiring Jan 15',
            actor: 'security-team',
            timestamp: new Date(Date.now() - 90 * 60000).toISOString(),
            status: 'pending',
            metadata: { domains: '12', expires: '2025-01-15', tenantId: 'acme-payments' },
        },
        {
            id: 'evt-sec-003',
            type: 'incident',
            title: 'Secrets rotation blocked',
            description: 'API key rotation requires downtime approval',
            actor: 'security-team',
            timestamp: new Date(Date.now() - 5 * 3600000).toISOString(),
            status: 'failure',
            metadata: { issue: 'SEC-603', approval: 'pending' },
        },
        {
            id: 'evt-sec-004',
            type: 'security',
            title: 'Dependency scan completed',
            description: 'OWASP scan found 8 medium-severity issues',
            actor: 'security-scanner',
            timestamp: new Date(Date.now() - 12 * 3600000).toISOString(),
            status: 'warning',
            metadata: { vulnerabilities: '8', severity: 'medium' },
        },
    ],

    deploy: [
        {
            id: 'evt-deploy-001',
            type: 'incident',
            title: 'Production deployment blocked',
            description: 'Payment API v2.3.0 deployment waiting for approval',
            actor: 'platform-team',
            timestamp: new Date(Date.now() - 60 * 60000).toISOString(),
            status: 'failure',
            metadata: { version: 'v2.3.0', strategy: 'canary', approval: 'pending' },
        },
        {
            id: 'evt-deploy-002',
            type: 'incident',
            title: 'Emergency rollback initiated',
            description: 'Checkout service v1.5.2 causing critical errors',
            actor: 'sre-team',
            timestamp: new Date(Date.now() - 90 * 60000).toISOString(),
            status: 'failure',
            metadata: { service: 'checkout', version: 'v1.5.1', rollback: 'in-progress' },
        },
        {
            id: 'evt-deploy-003',
            type: 'deployment',
            title: 'ML model deployed to production',
            description: 'Fraud detection model v2 with 94% accuracy',
            actor: 'ml-team',
            timestamp: new Date(Date.now() - 3 * 3600000).toISOString(),
            status: 'success',
            metadata: { model: 'fraud-detector-v2', accuracy: '94%' },
        },
        {
            id: 'evt-deploy-004',
            type: 'approval',
            title: 'Database migration approved',
            description: 'Orders table schema change approved by DBA',
            actor: 'dba-team',
            timestamp: new Date(Date.now() - 24 * 3600000).toISOString(),
            status: 'success',
            metadata: { migration: 'orders-v2', downtime: '5min' },
        },
    ],

    operate: [
        {
            id: 'evt-ops-001',
            type: 'incident',
            title: 'Payment API latency spike',
            description: 'P95 latency increased to 850ms (threshold: 500ms)',
            actor: 'monitoring-system',
            timestamp: new Date(Date.now() - 15 * 60000).toISOString(),
            status: 'warning',
            metadata: { p95: '850ms', threshold: '500ms', service: 'payment-api' },
        },
        {
            id: 'evt-ops-002',
            type: 'deployment',
            title: 'Auto-scaling triggered',
            description: 'Checkout service scaled from 5 to 10 replicas',
            actor: 'k8s-autoscaler',
            timestamp: new Date(Date.now() - 45 * 60000).toISOString(),
            status: 'success',
            metadata: { service: 'checkout', replicas: '10', cpu: '78%' },
        },
        {
            id: 'evt-ops-003',
            type: 'milestone',
            title: 'Zero downtime deployment',
            description: 'Successfully deployed 3 services with 100% uptime',
            actor: 'platform-team',
            timestamp: new Date(Date.now() - 2 * 86400000).toISOString(),
            status: 'success',
            metadata: { services: '3', uptime: '100%' },
        },
    ],

    monitor: [
        {
            id: 'evt-mon-001',
            type: 'milestone',
            title: 'Fraud detection dashboard launched',
            description: 'Real-time metrics and alert thresholds configured',
            actor: 'observability-team',
            timestamp: new Date(Date.now() - 2 * 3600000).toISOString(),
            status: 'success',
            metadata: { metrics: '15', alerts: '8' },
        },
        {
            id: 'evt-mon-002',
            type: 'milestone',
            title: 'Log volume reduced by 42%',
            description: 'Improved filtering and sampling rules',
            actor: 'observability-team',
            timestamp: new Date(Date.now() - 3 * 86400000).toISOString(),
            status: 'success',
            metadata: { reduction: '42%', 'cost-savings': '$2.1k/mo' },
        },
    ],
};
