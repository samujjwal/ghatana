import { FastifyInstance } from 'fastify';

/**
 * Operate Routes - Operations Dashboard, Incidents, and Work Queue
 *
 * Provides real-time operational data for SREs and Platform Engineers:
 * - Dashboard stats and recent activity
 * - Incident management (list, detail, triage, resolution)
 * - Work queue for HITL approvals and decisions
 *
 * @doc.type route
 * @doc.purpose Operations API
 * @doc.layer product
 */

// =============================================================================
// Mock Data Generators
// =============================================================================

function generateTasks() {
    return [
        {
            id: 'task-1',
            title: 'Review Q3 Budget Allocation',
            description: 'Analyze department spending and adjust allocations for Q3',
            status: 'pending',
            priority: 'high',
            assignee: { id: 'user-1', name: 'Sarah Chen', avatar: 'https://i.pravatar.cc/150?u=sarah' },
            dueDate: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString(),
            tags: ['finance', 'planning'],
            createdAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
        },
        {
            id: 'task-2',
            title: 'Update Security Protocols',
            description: 'Implement new authentication flow for external services',
            status: 'in-progress',
            priority: 'high',
            assignee: { id: 'user-2', name: 'Mike Johnson', avatar: 'https://i.pravatar.cc/150?u=mike' },
            dueDate: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString(),
            tags: ['security', 'devops'],
            createdAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
        },
        {
            id: 'task-3',
            title: 'Onboard New Frontend Engineers',
            description: 'Prepare access credentials and training materials',
            status: 'pending',
            priority: 'medium',
            assignee: { id: 'user-3', name: 'Emily Davis', avatar: 'https://i.pravatar.cc/150?u=emily' },
            dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
            tags: ['hr', 'onboarding'],
            createdAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
        },
        {
            id: 'task-4',
            title: 'Optimize Database Queries',
            description: 'Identify and fix slow queries in the reporting module',
            status: 'completed',
            priority: 'medium',
            assignee: { id: 'user-2', name: 'Mike Johnson', avatar: 'https://i.pravatar.cc/150?u=mike' },
            dueDate: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
            tags: ['performance', 'database'],
            createdAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(),
        }
    ];
}

function generateLiveFeed() {
    return [
        {
            id: 'feed-1',
            type: 'commit',
            content: 'Merged PR #1234: Fix login page layout issues',
            actor: { id: 'user-1', name: 'Sarah Chen', avatar: 'https://i.pravatar.cc/150?u=sarah' },
            timestamp: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
            action: 'pushed code to',
            target: 'frontend-repo',
            metadata: { branch: 'main', commit: 'a1b2c3d' }
        },
        {
            id: 'feed-2',
            type: 'alert',
            content: 'High CPU usage detected on worker-node-05',
            actor: { id: 'sys-1', name: 'System Monitor', avatar: 'https://ui-avatars.com/api/?name=System+Monitor' },
            timestamp: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
            action: 'triggered alert on',
            target: 'infrastructure',
            metadata: { severity: 'warning', value: '85%' }
        },
        {
            id: 'feed-3',
            type: 'task',
            content: 'Completed task: Optimize Database Queries',
            actor: { id: 'user-2', name: 'Mike Johnson', avatar: 'https://i.pravatar.cc/150?u=mike' },
            timestamp: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
            action: 'completed',
            target: 'task-4',
        },
        {
            id: 'feed-4',
            type: 'deployment',
            content: 'Successfully deployed v2.1.0 to production',
            actor: { id: 'sys-2', name: 'CI/CD Pipeline', avatar: 'https://ui-avatars.com/api/?name=CI+CD' },
            timestamp: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
            action: 'deployed',
            target: 'production',
            metadata: { version: 'v2.1.0', duration: '5m 30s' }
        },
        {
            id: 'feed-5',
            type: 'discussion',
            content: 'Commented on "Q3 Roadmap": We need to prioritize mobile support',
            actor: { id: 'user-3', name: 'Emily Davis', avatar: 'https://i.pravatar.cc/150?u=emily' },
            timestamp: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(),
            action: 'commented on',
            target: 'Q3 Roadmap',
        }
    ];
}

function generateInsights() {
    return {
        id: 'conv-1',
        context: { topic: 'general' },
        messages: [
            {
                id: 'msg-1',
                role: 'user',
                content: 'How is the team performing this sprint?',
                timestamp: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
            },
            {
                id: 'msg-2',
                role: 'assistant',
                content: 'The team is performing well. Velocity is up by 15% compared to last sprint. However, there are 3 blocked tasks in the "Backend" swimlane that might need attention.',
                timestamp: new Date(Date.now() - 9 * 60 * 1000).toISOString(),
                metadata: {
                    chartType: 'bar',
                    data: { velocity: [20, 22, 25, 29] }
                }
            }
        ]
    };
}

function generateDashboardStats() {
    return {
        activeIncidents: Math.floor(Math.random() * 5),
        pendingApprovals: Math.floor(Math.random() * 15) + 3,
        workflowsRunning: Math.floor(Math.random() * 30) + 10,
        systemHealth: 95 + Math.random() * 4,
        deploymentFrequency: 8 + Math.floor(Math.random() * 8),
        avgLeadTime: 30 + Math.floor(Math.random() * 30),
        mttr: 15 + Math.floor(Math.random() * 20),
        changeFailureRate: 1 + Math.random() * 4,
    };
}

function generateRecentActivity() {
    const activities = [
        { type: 'workflow', message: 'CI/CD Pipeline completed successfully', status: 'success' },
        { type: 'alert', message: 'High CPU usage on prod-api-3', status: 'warning' },
        { type: 'approval', message: 'Deployment to production pending', status: 'pending' },
        { type: 'agent', message: 'Security Agent detected vulnerability', status: 'warning' },
        { type: 'workflow', message: 'Rollback initiated for payment-service', status: 'error' },
        { type: 'incident', message: 'Database connection pool exhausted', status: 'critical' },
        { type: 'deployment', message: 'Deployed auth-service v2.1.0 to staging', status: 'success' },
        { type: 'agent', message: 'AI Agent auto-scaled web-tier', status: 'success' },
    ];

    return Array.from({ length: 10 }, (_, i) => {
        const activity = activities[Math.floor(Math.random() * activities.length)];
        const minutesAgo = Math.floor(Math.random() * 120) + 1;
        return {
            id: `activity-${i + 1}`,
            ...activity,
            time: minutesAgo < 60 ? `${minutesAgo} min ago` : `${Math.floor(minutesAgo / 60)} hours ago`,
            timestamp: new Date(Date.now() - minutesAgo * 60 * 1000).toISOString(),
        };
    }).sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
}

function generateIncidents(tenantId: string, status?: string, severity?: string) {
    const allIncidents = [
        {
            id: 'INC-001',
            tenantId,
            title: 'API Gateway High Latency',
            description: 'Response times exceeding 2s for /api/v1/users endpoint',
            severity: 'high',
            status: 'investigating',
            assignee: 'Sarah Chen',
            assigneeId: 'user-sarah',
            affectedServices: ['api-gateway', 'user-service'],
            affectedServicesIds: ['svc-api-gateway', 'svc-user-service'],
            rootCause: null,
            mitigation: 'Scaling API gateway instances',
            timeline: [
                { timestamp: new Date(Date.now() - 15 * 60 * 1000).toISOString(), event: 'Incident created', actor: 'Monitoring System' },
                { timestamp: new Date(Date.now() - 10 * 60 * 1000).toISOString(), event: 'Assigned to Sarah Chen', actor: 'On-call Bot' },
                { timestamp: new Date(Date.now() - 5 * 60 * 1000).toISOString(), event: 'Mitigation started', actor: 'Sarah Chen' },
            ],
            relatedWorkflows: ['workflow-scale-api', 'workflow-restart-pods'],
            relatedMetrics: ['metric-api-latency', 'metric-error-rate'],
            createdAt: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
            updatedAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
        },
        {
            id: 'INC-002',
            tenantId,
            title: 'Database Connection Pool Exhausted',
            description: 'Connection pool maxed out causing request failures',
            severity: 'critical',
            status: 'mitigating',
            assignee: 'Mike Johnson',
            assigneeId: 'user-mike',
            affectedServices: ['payment-service', 'order-service'],
            affectedServicesIds: ['svc-payment', 'svc-order'],
            rootCause: 'Connection leak in payment processing code',
            mitigation: 'Restarting services and applying connection timeout patch',
            timeline: [
                { timestamp: new Date(Date.now() - 45 * 60 * 1000).toISOString(), event: 'Incident created', actor: 'Monitoring System' },
                { timestamp: new Date(Date.now() - 40 * 60 * 1000).toISOString(), event: 'Escalated to critical', actor: 'Duty Manager' },
                { timestamp: new Date(Date.now() - 35 * 60 * 1000).toISOString(), event: 'Assigned to Mike Johnson', actor: 'Duty Manager' },
                { timestamp: new Date(Date.now() - 20 * 60 * 1000).toISOString(), event: 'Root cause identified', actor: 'Mike Johnson' },
                { timestamp: new Date(Date.now() - 10 * 60 * 1000).toISOString(), event: 'Mitigation deployed', actor: 'Mike Johnson' },
            ],
            relatedWorkflows: ['workflow-restart-services', 'workflow-apply-hotfix'],
            relatedMetrics: ['metric-db-connections', 'metric-error-rate'],
            createdAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
            updatedAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
        },
        {
            id: 'INC-003',
            tenantId,
            title: 'Memory Leak in Background Job',
            description: 'Worker processes consuming excessive memory',
            severity: 'medium',
            status: 'active',
            assignee: null,
            assigneeId: null,
            affectedServices: ['job-processor'],
            affectedServicesIds: ['svc-job-processor'],
            rootCause: null,
            mitigation: null,
            timeline: [
                { timestamp: new Date(Date.now() - 120 * 60 * 1000).toISOString(), event: 'Incident created', actor: 'Monitoring System' },
            ],
            relatedWorkflows: ['workflow-restart-workers'],
            relatedMetrics: ['metric-memory-usage'],
            createdAt: new Date(Date.now() - 120 * 60 * 1000).toISOString(),
            updatedAt: new Date(Date.now() - 120 * 60 * 1000).toISOString(),
        },
        {
            id: 'INC-004',
            tenantId,
            title: 'SSL Certificate Expiring Soon',
            description: 'SSL certificate for *.example.com expires in 5 days',
            severity: 'low',
            status: 'resolved',
            assignee: 'Platform Team',
            assigneeId: 'team-platform',
            affectedServices: ['all-services'],
            affectedServicesIds: ['svc-all'],
            rootCause: 'Certificate renewal process not automated',
            mitigation: 'Renewed certificate and automated renewal',
            timeline: [
                { timestamp: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(), event: 'Incident created', actor: 'Cert Monitor' },
                { timestamp: new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString(), event: 'Assigned to Platform Team', actor: 'Security Lead' },
                { timestamp: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(), event: 'Certificate renewed', actor: 'Platform Team' },
                { timestamp: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(), event: 'Resolved', actor: 'Platform Team' },
            ],
            relatedWorkflows: ['workflow-renew-cert'],
            relatedMetrics: [],
            createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
            updatedAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(),
        },
    ];

    let filtered = allIncidents;
    if (status && status !== 'all') {
        filtered = filtered.filter(inc => inc.status === status);
    }
    if (severity && severity !== 'all') {
        filtered = filtered.filter(inc => inc.severity === severity);
    }

    return filtered;
}

function generateQueueItems(tenantId: string, type?: string, priority?: string) {
    const allItems = [
        {
            id: 'queue-001',
            tenantId,
            type: 'approval',
            title: 'Production Deployment - API v2.3.0',
            description: 'Deploy new API version with breaking changes to production',
            priority: 'high',
            requestedBy: 'CI/CD Pipeline',
            requestedById: 'system-cicd',
            context: {
                service: 'api-gateway',
                version: 'v2.3.0',
                environment: 'production',
                impact: 'Breaking changes in /api/v1/users endpoint',
            },
            dueIn: '2 hours',
            createdAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
        },
        {
            id: 'queue-002',
            tenantId,
            type: 'hitl',
            title: 'AI Agent Suggested Scaling Action',
            description: 'AI recommends scaling payment-service from 3 to 8 instances',
            priority: 'medium',
            requestedBy: 'Autoscaling Agent',
            requestedById: 'agent-autoscale',
            context: {
                service: 'payment-service',
                currentInstances: 3,
                suggestedInstances: 8,
                reason: 'Traffic spike detected, 90% CPU utilization',
                estimatedCost: '+$45/hour',
            },
            dueIn: '30 minutes',
            createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
        },
        {
            id: 'queue-003',
            tenantId,
            type: 'review',
            title: 'Security Vulnerability Scan Results',
            description: '3 medium-severity vulnerabilities detected in dependencies',
            priority: 'medium',
            requestedBy: 'Security Scanner',
            requestedById: 'system-security',
            context: {
                service: 'user-service',
                vulnerabilities: ['CVE-2024-1234', 'CVE-2024-5678', 'CVE-2024-9012'],
                affectedDependencies: ['lodash', 'axios', 'express'],
            },
            dueIn: '1 day',
            createdAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
        },
        {
            id: 'queue-004',
            tenantId,
            type: 'access',
            title: 'Production Access Request',
            description: 'Sarah Chen requests production database read access',
            priority: 'low',
            requestedBy: 'Sarah Chen',
            requestedById: 'user-sarah',
            context: {
                resource: 'production-db',
                accessLevel: 'read-only',
                duration: '24 hours',
                reason: 'Debug customer issue INC-001',
            },
            dueIn: '4 hours',
            createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
        },
        {
            id: 'queue-005',
            tenantId,
            type: 'approval',
            title: 'Emergency Rollback - Payment Service',
            description: 'Rollback payment-service to v1.2.0 due to transaction failures',
            priority: 'high',
            requestedBy: 'Mike Johnson',
            requestedById: 'user-mike',
            context: {
                service: 'payment-service',
                currentVersion: 'v1.3.0',
                targetVersion: 'v1.2.0',
                reason: 'Critical bug causing transaction failures',
            },
            dueIn: '15 minutes',
            createdAt: new Date(Date.now() - 2 * 60 * 1000).toISOString(),
        },
    ];

    let filtered = allItems;
    if (type && type !== 'all') {
        filtered = filtered.filter(item => item.type === type);
    }
    if (priority && priority !== 'all') {
        filtered = filtered.filter(item => item.priority === priority);
    }

    return filtered;
}

// =============================================================================
// Routes
// =============================================================================

export async function operateRoutes(fastify: FastifyInstance) {
    // Dashboard Stats
    fastify.get('/operate/dashboard/stats', async (request, reply) => {
        const { tenantId } = request.query as { tenantId: string };

        if (!tenantId) {
            return reply.code(400).send({ error: 'tenantId is required' });
        }

        const stats = generateDashboardStats();
        return reply.send({ data: stats });
    });

    // Recent Activity
    fastify.get('/operate/dashboard/activity', async (request, reply) => {
        const { tenantId } = request.query as { tenantId: string };

        if (!tenantId) {
            return reply.code(400).send({ error: 'tenantId is required' });
        }

        const activity = generateRecentActivity();
        return reply.send({ data: activity });
    });

    // List Incidents
    fastify.get('/operate/incidents', async (request, reply) => {
        const { tenantId, status, severity } = request.query as {
            tenantId: string;
            status?: string;
            severity?: string;
        };

        if (!tenantId) {
            return reply.code(400).send({ error: 'tenantId is required' });
        }

        const incidents = generateIncidents(tenantId, status, severity);
        return reply.send({
            data: incidents,
            pagination: { page: 1, pageSize: 50, total: incidents.length },
        });
    });

    // Get Incident by ID
    fastify.get('/operate/incidents/:incidentId', async (request, reply) => {
        const { incidentId } = request.params as { incidentId: string };
        const { tenantId } = request.query as { tenantId: string };

        if (!tenantId) {
            return reply.code(400).send({ error: 'tenantId is required' });
        }

        const incidents = generateIncidents(tenantId);
        const incident = incidents.find(inc => inc.id === incidentId);

        if (!incident) {
            return reply.code(404).send({ error: 'Incident not found' });
        }

        return reply.send({ data: incident });
    });

    // Acknowledge Incident
    fastify.post('/operate/incidents/:incidentId/acknowledge', async (request, reply) => {
        const { incidentId } = request.params as { incidentId: string };
        const { tenantId, userId } = request.body as { tenantId: string; userId: string };

        // In production, update incident status and emit audit event
        return reply.send({
            success: true,
            message: `Incident ${incidentId} acknowledged by ${userId}`,
        });
    });

    // Assign Incident
    fastify.post('/operate/incidents/:incidentId/assign', async (request, reply) => {
        const { incidentId } = request.params as { incidentId: string };
        const { tenantId, assigneeId } = request.body as { tenantId: string; assigneeId: string };

        // In production, update incident assignee and emit audit event
        return reply.send({
            success: true,
            message: `Incident ${incidentId} assigned to ${assigneeId}`,
        });
    });

    // Update Incident Status
    fastify.post('/operate/incidents/:incidentId/status', async (request, reply) => {
        const { incidentId } = request.params as { incidentId: string };
        const { tenantId, status, summary } = request.body as {
            tenantId: string;
            status: string;
            summary?: string;
        };

        // In production, update incident status and emit audit event
        return reply.send({
            success: true,
            message: `Incident ${incidentId} status updated to ${status}`,
        });
    });

    // List Queue Items
    fastify.get('/operate/queue', async (request, reply) => {
        const { tenantId, type, priority } = request.query as {
            tenantId: string;
            type?: string;
            priority?: string;
        };

        if (!tenantId) {
            return reply.code(400).send({ error: 'tenantId is required' });
        }

        const items = generateQueueItems(tenantId, type, priority);
        return reply.send({
            data: items,
            pagination: { page: 1, pageSize: 50, total: items.length },
        });
    });

    // Get Queue Item by ID
    fastify.get('/operate/queue/:itemId', async (request, reply) => {
        const { itemId } = request.params as { itemId: string };
        const { tenantId } = request.query as { tenantId: string };

        if (!tenantId) {
            return reply.code(400).send({ error: 'tenantId is required' });
        }

        const items = generateQueueItems(tenantId);
        const item = items.find(i => i.id === itemId);

        if (!item) {
            return reply.code(404).send({ error: 'Queue item not found' });
        }

        return reply.send({ data: item });
    });

    // Approve Queue Item
    fastify.post('/operate/queue/:itemId/approve', async (request, reply) => {
        const { itemId } = request.params as { itemId: string };
        const { tenantId, userId, comment } = request.body as {
            tenantId: string;
            userId: string;
            comment?: string;
        };

        // In production, approve item and emit audit event
        return reply.send({
            success: true,
            message: `Queue item ${itemId} approved by ${userId}`,
        });
    });

    // Reject Queue Item
    fastify.post('/operate/queue/:itemId/reject', async (request, reply) => {
        const { itemId } = request.params as { itemId: string };
        const { tenantId, userId, reason } = request.body as {
            tenantId: string;
            userId: string;
            reason: string;
        };

        // In production, reject item and emit audit event
        return reply.send({
            success: true,
            message: `Queue item ${itemId} rejected by ${userId}`,
        });
    });

    // =========================================================================
    // Tasks API
    // =========================================================================
    
    fastify.get('/operate/tasks', async (request, reply) => {
        const tasks = generateTasks();
        return reply.send({ data: tasks });
    });

    fastify.post('/operate/tasks/:taskId/status', async (request, reply) => {
        const { taskId } = request.params as { taskId: string };
        const { updates } = request.body as { updates: any };
        return reply.send({ success: true, message: `Task ${taskId} updated`, data: { id: taskId, ...updates } });
    });

    fastify.post('/operate/tasks/:taskId/complete', async (request, reply) => {
        const { taskId } = request.params as { taskId: string };
        return reply.send({ success: true, message: `Task ${taskId} completed` });
    });

    // =========================================================================
    // Live Feed API
    // =========================================================================

    fastify.get('/operate/live-feed', async (request, reply) => {
        const feed = generateLiveFeed();
        return reply.send({ data: feed });
    });

    // =========================================================================
    // Insights API
    // =========================================================================

    fastify.get('/operate/insights/conversation', async (request, reply) => {
        const conversation = generateInsights();
        return reply.send({ data: conversation });
    });

    fastify.post('/operate/insights/message', async (request, reply) => {
        const { message } = request.body as { message: string };
        
        // Mock AI response
        const response = {
            id: `msg-${Date.now()}`,
            role: 'assistant',
            content: `I received your message: "${message}". Here is some insight based on our data...`,
            timestamp: new Date().toISOString(),
        };
        
        return reply.send({ data: response });
    });
}
