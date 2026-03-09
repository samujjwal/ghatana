/**
 * Observe API Routes
 *
 * Endpoints for metrics, reports, and ML observatory.
 * Implements Journey V1 (Metrics), V2 (Reports), V3 (ML Observatory).
 *
 * @doc.type route
 * @doc.purpose Observe API for monitoring and reporting
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';
import { generateCsvExport, generateAlertsPdf, generateLogsPdf } from '../services/pdf-export.js';
import { notificationOrchestrator } from '../services/notification-orchestrator.js';

// =============================================================================
// Types
// =============================================================================

interface MetricDataPoint {
    timestamp: string;
    value: number;
}

interface MetricResponse {
    id: string;
    tenantId: string;
    name: string;
    description: string;
    category: string;
    value: string;
    target: string;
    trend: number;
    status: 'on-track' | 'at-risk' | 'off-track';
    unit: string;
    timeSeries: MetricDataPoint[];
    relatedIncidents: string[];
    relatedDeployments: string[];
    createdAt: string;
    updatedAt: string;
}

interface ReportResponse {
    id: string;
    tenantId: string;
    name: string;
    type: 'reliability' | 'change-management' | 'incident-summary';
    scope: string;
    period: {
        start: string;
        end: string;
    };
    metrics: Record<string, unknown>;
    charts: Array<{
        type: string;
        data: unknown;
    }>;
    summary: string;
    createdAt: string;
}

interface MLModelResponse {
    id: string;
    tenantId: string;
    name: string;
    version: string;
    status: 'healthy' | 'degraded' | 'failed';
    serviceId: string;
    lastDeployedAt: string;
    metrics: {
        accuracy?: number;
        precision?: number;
        recall?: number;
        f1Score?: number;
        drift?: number;
        latencyP50?: number;
        latencyP99?: number;
    };
    timeSeries: MetricDataPoint[];
    relatedIncidents: string[];
    createdAt: string;
    updatedAt: string;
}

interface AlertResponse {
    id: string;
    tenantId: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    status: 'active' | 'acknowledged' | 'resolved';
    title: string;
    message: string;
    source: string;
    relatedIncidents: string[];
    acknowledgedBy?: string;
    acknowledgedAt?: string;
    resolvedBy?: string;
    resolvedAt?: string;
    createdAt: string;
}

interface LogEntry {
    id: string;
    tenantId: string;
    timestamp: string;
    level: 'error' | 'warn' | 'info' | 'debug';
    source: string;
    message: string;
    metadata?: Record<string, unknown>;
}

// =============================================================================
// Mock Data (Replace with real data sources)
// =============================================================================

const mockMetrics: MetricResponse[] = [
    {
        id: 'deployment-freq',
        tenantId: 'acme-payments-id',
        name: 'Deployment Frequency',
        description: 'Number of deployments per day',
        category: 'Velocity',
        value: '12/day',
        target: '10/day',
        trend: 8,
        status: 'on-track',
        unit: 'deployments/day',
        timeSeries: Array.from({ length: 30 }, (_, i) => ({
            timestamp: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
            value: 10 + Math.random() * 5,
        })),
        relatedIncidents: [],
        relatedDeployments: ['deploy-123', 'deploy-456'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'lead-time',
        tenantId: 'acme-payments-id',
        name: 'Lead Time for Changes',
        description: 'Time from commit to production',
        category: 'Velocity',
        value: '45 min',
        target: '< 1 hour',
        trend: -12,
        status: 'on-track',
        unit: 'minutes',
        timeSeries: Array.from({ length: 30 }, (_, i) => ({
            timestamp: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
            value: 40 + Math.random() * 20,
        })),
        relatedIncidents: [],
        relatedDeployments: ['deploy-123'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'mttr',
        tenantId: 'acme-payments-id',
        name: 'Mean Time to Recovery',
        description: 'Average time to resolve incidents',
        category: 'Stability',
        value: '23 min',
        target: '< 30 min',
        trend: -5,
        status: 'on-track',
        unit: 'minutes',
        timeSeries: Array.from({ length: 30 }, (_, i) => ({
            timestamp: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
            value: 20 + Math.random() * 15,
        })),
        relatedIncidents: ['incident-789'],
        relatedDeployments: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'change-failure',
        tenantId: 'acme-payments-id',
        name: 'Change Failure Rate',
        description: 'Percentage of changes that fail',
        category: 'Stability',
        value: '2.1%',
        target: '< 5%',
        trend: -15,
        status: 'on-track',
        unit: 'percentage',
        timeSeries: Array.from({ length: 30 }, (_, i) => ({
            timestamp: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
            value: 2 + Math.random() * 3,
        })),
        relatedIncidents: ['incident-456'],
        relatedDeployments: ['deploy-789'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'availability',
        tenantId: 'acme-payments-id',
        name: 'System Availability',
        description: 'Percentage of uptime',
        category: 'Reliability',
        value: '99.95%',
        target: '99.9%',
        trend: 0.02,
        status: 'on-track',
        unit: 'percentage',
        timeSeries: Array.from({ length: 30 }, (_, i) => ({
            timestamp: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
            value: 99.9 + Math.random() * 0.1,
        })),
        relatedIncidents: [],
        relatedDeployments: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'incident-count',
        tenantId: 'acme-payments-id',
        name: 'Incident Count',
        description: 'Number of incidents per week',
        category: 'Stability',
        value: '3',
        target: '< 5/week',
        trend: 25,
        status: 'at-risk',
        unit: 'incidents/week',
        timeSeries: Array.from({ length: 30 }, (_, i) => ({
            timestamp: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
            value: 2 + Math.floor(Math.random() * 4),
        })),
        relatedIncidents: ['incident-123', 'incident-456', 'incident-789'],
        relatedDeployments: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
];

const mockMLModels: MLModelResponse[] = [
    {
        id: 'fraud-detection-v2',
        tenantId: 'acme-payments-id',
        name: 'Fraud Detection Model',
        version: 'v2.3.1',
        status: 'healthy',
        serviceId: 'payment-service-id',
        lastDeployedAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(),
        metrics: {
            accuracy: 0.97,
            precision: 0.95,
            recall: 0.93,
            f1Score: 0.94,
            drift: 0.02,
            latencyP50: 45,
            latencyP99: 120,
        },
        timeSeries: Array.from({ length: 30 }, (_, i) => ({
            timestamp: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
            value: 0.95 + Math.random() * 0.04,
        })),
        relatedIncidents: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'churn-prediction-v1',
        tenantId: 'acme-payments-id',
        name: 'Customer Churn Prediction',
        version: 'v1.2.0',
        status: 'degraded',
        serviceId: 'analytics-service-id',
        lastDeployedAt: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000).toISOString(),
        metrics: {
            accuracy: 0.82,
            precision: 0.80,
            recall: 0.75,
            f1Score: 0.77,
            drift: 0.15,
            latencyP50: 200,
            latencyP99: 500,
        },
        timeSeries: Array.from({ length: 30 }, (_, i) => ({
            timestamp: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
            value: 0.85 - (i * 0.001),
        })),
        relatedIncidents: ['incident-ml-001'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
];

// =============================================================================
// Routes
// =============================================================================

export async function observeRoutes(fastify: FastifyInstance) {
    // =========================================================================
    // Metrics
    // =========================================================================

    /**
     * GET /api/v1/observe/metrics
     * List all metrics for a tenant
     */
    fastify.get('/api/v1/observe/metrics', async (request, reply) => {
        const { tenantId, category, status } = request.query as {
            tenantId?: string;
            category?: string;
            status?: string;
        };

        let filteredMetrics = mockMetrics;

        if (tenantId) {
            filteredMetrics = filteredMetrics.filter((m) => m.tenantId === tenantId);
        }

        if (category) {
            filteredMetrics = filteredMetrics.filter((m) => m.category === category);
        }

        if (status) {
            filteredMetrics = filteredMetrics.filter((m) => m.status === status);
        }

        return reply.send({
            data: filteredMetrics,
            pagination: {
                page: 1,
                pageSize: filteredMetrics.length,
                total: filteredMetrics.length,
            },
        });
    });

    /**
     * GET /api/v1/observe/metrics/:id
     * Get a single metric by ID
     */
    fastify.get('/api/v1/observe/metrics/:id', async (request, reply) => {
        const { id } = request.params as { id: string };

        const metric = mockMetrics.find((m) => m.id === id);

        if (!metric) {
            return reply.status(404).send({ error: 'Metric not found' });
        }

        return reply.send({ data: metric });
    });

    // =========================================================================
    // Reports
    // =========================================================================

    /**
     * POST /api/v1/observe/reports/generate
     * Generate a new report
     */
    fastify.post('/api/v1/observe/reports/generate', async (request, reply) => {
        const { tenantId, type, scope, startDate, endDate } = request.body as {
            tenantId: string;
            type: 'reliability' | 'change-management' | 'incident-summary';
            scope: string;
            startDate: string;
            endDate: string;
        };

        const reportId = `report-${Date.now()}`;

        // Mock report generation
        const report: ReportResponse = {
            id: reportId,
            tenantId,
            name: `${type} Report`,
            type,
            scope,
            period: {
                start: startDate,
                end: endDate,
            },
            metrics: {
                availability: '99.95%',
                mttr: '23 min',
                deploymentFrequency: '12/day',
                changeFailureRate: '2.1%',
            },
            charts: [
                {
                    type: 'line',
                    data: mockMetrics[0].timeSeries,
                },
            ],
            summary: 'Overall system performance is healthy with all key metrics within target ranges.',
            createdAt: new Date().toISOString(),
        };

        return reply.status(201).send({ data: report });
    });

    /**
     * GET /api/v1/observe/reports
     * List reports
     */
    fastify.get('/api/v1/observe/reports', async (request, reply) => {
        // Mock report list (would fetch from database)
        const reports: ReportResponse[] = [];

        return reply.send({
            data: reports,
            pagination: {
                page: 1,
                pageSize: reports.length,
                total: reports.length,
            },
        });
    });

    // =========================================================================
    // ML Observatory
    // =========================================================================

    /**
     * GET /api/v1/observe/ml/models
     * List all ML models
     */
    fastify.get('/api/v1/observe/ml/models', async (request, reply) => {
        const { tenantId, status } = request.query as {
            tenantId?: string;
            status?: string;
        };

        let filteredModels = mockMLModels;

        if (tenantId) {
            filteredModels = filteredModels.filter((m) => m.tenantId === tenantId);
        }

        if (status) {
            filteredModels = filteredModels.filter((m) => m.status === status);
        }

        return reply.send({
            data: filteredModels,
            pagination: {
                page: 1,
                pageSize: filteredModels.length,
                total: filteredModels.length,
            },
        });
    });

    /**
     * GET /api/v1/observe/ml/models/:id
     * Get a single ML model by ID
     */
    fastify.get('/api/v1/observe/ml/models/:id', async (request, reply) => {
        const { id } = request.params as { id: string };

        const model = mockMLModels.find((m) => m.id === id);

        if (!model) {
            return reply.status(404).send({ error: 'Model not found' });
        }

        return reply.send({ data: model });
    });

    // =========================================================================
    // Alerts
    // =========================================================================

    /**
     * GET /api/v1/observe/alerts
     * List all alerts with filtering
     */
    fastify.get('/observe/alerts', async (request, reply) => {
        const { tenantId, severity, status, page = '1', pageSize = '20' } = request.query as {
            tenantId?: string;
            severity?: string;
            status?: string;
            page?: string;
            pageSize?: string;
        };

        try {
            const pageNum = parseInt(page);
            const pageSizeNum = parseInt(pageSize);
            const skip = (pageNum - 1) * pageSizeNum;

            // Build where clause
            const where: any = {};
            if (tenantId) where.tenantId = tenantId;
            if (severity) where.severity = severity;
            if (status) where.status = status;

            // Query database with pagination
            const [alerts, total] = await Promise.all([
                prisma.alert.findMany({
                    where,
                    orderBy: { createdAt: 'desc' },
                    skip,
                    take: pageSizeNum,
                }),
                prisma.alert.count({ where }),
            ]);

            // Map to response format
            const data: AlertResponse[] = alerts.map((alert) => ({
                id: alert.id,
                tenantId: alert.tenantId,
                severity: alert.severity as AlertResponse['severity'],
                status: alert.status as AlertResponse['status'],
                title: alert.title || alert.message.substring(0, 100),
                message: alert.message,
                source: alert.source || 'unknown',
                relatedIncidents: alert.relatedIncidents,
                acknowledgedBy: alert.acknowledgedBy || undefined,
                acknowledgedAt: alert.acknowledgedAt?.toISOString(),
                resolvedBy: alert.resolvedBy || undefined,
                resolvedAt: alert.resolvedAt?.toISOString(),
                createdAt: alert.createdAt.toISOString(),
            }));

            return reply.send({
                data,
                pagination: {
                    page: pageNum,
                    pageSize: pageSizeNum,
                    total,
                    totalPages: Math.ceil(total / pageSizeNum),
                },
            });
        } catch (error) {
            fastify.log.error(error, '[Observe API] Error fetching alerts');
            return reply.status(500).send({ error: 'Failed to fetch alerts' });
        }
    });

    /**
     * POST /api/v1/observe/alerts/:id/acknowledge
     * Acknowledge an alert
     */
    fastify.post('/observe/alerts/:id/acknowledge', async (request, reply) => {
        const { id } = request.params as { id: string };
        const { userId } = request.body as { userId: string };

        try {
            const alert = await prisma.alert.update({
                where: { id },
                data: {
                    status: 'acknowledged',
                    acknowledgedBy: userId,
                    acknowledgedAt: new Date(),
                },
            });

            return reply.send({
                success: true,
                data: {
                    id: alert.id,
                    status: alert.status,
                    acknowledgedBy: alert.acknowledgedBy,
                    acknowledgedAt: alert.acknowledgedAt?.toISOString(),
                },
            });
        } catch (error) {
            fastify.log.error(error, '[Observe API] Error acknowledging alert');
            return reply.status(500).send({ error: 'Failed to acknowledge alert' });
        }
    });

    /**
     * POST /api/v1/observe/alerts/:id/resolve
     * Resolve an alert
     */
    fastify.post('/observe/alerts/:id/resolve', async (request, reply) => {
        const { id } = request.params as { id: string };
        const { userId } = request.body as { userId: string };

        try {
            const alert = await prisma.alert.update({
                where: { id },
                data: {
                    status: 'resolved',
                    resolved: true,
                    resolvedBy: userId,
                    resolvedAt: new Date(),
                },
            });

            return reply.send({
                success: true,
                data: {
                    id: alert.id,
                    status: alert.status,
                    resolvedBy: alert.resolvedBy,
                    resolvedAt: alert.resolvedAt?.toISOString(),
                },
            });
        } catch (error) {
            fastify.log.error(error, '[Observe API] Error resolving alert');
            return reply.status(500).send({ error: 'Failed to resolve alert' });
        }
    });

    // =========================================================================
    // Logs
    // =========================================================================

    /**
     * GET /api/v1/observe/logs
     * List logs with filtering
     */
    fastify.get('/observe/logs', async (request, reply) => {
        const { tenantId, level, source, limit = '100', cursor, search } = request.query as {
            tenantId?: string;
            level?: string;
            source?: string;
            limit?: string;
            cursor?: string;
            search?: string;
        };

        try {
            const limitNum = Math.min(parseInt(limit), 1000); // Max 1000 logs per request

            // Build where clause
            const where: any = {};
            if (tenantId) where.tenantId = tenantId;
            if (level) where.level = level;
            if (source) where.source = source;
            if (search) {
                where.message = {
                    contains: search,
                    mode: 'insensitive',
                };
            }

            // Cursor-based pagination for efficient log streaming
            const logs = await prisma.logEntry.findMany({
                where,
                orderBy: { timestamp: 'desc' },
                take: limitNum + 1, // Take one extra to determine if there are more
                ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
            });

            // Check if there are more logs
            const hasMore = logs.length > limitNum;
            const data = hasMore ? logs.slice(0, limitNum) : logs;
            const nextCursor = hasMore ? data[data.length - 1].id : null;

            // Map to response format
            const responseData: LogEntry[] = data.map((log) => ({
                id: log.id,
                tenantId: log.tenantId,
                timestamp: log.timestamp.toISOString(),
                level: log.level as LogEntry['level'],
                source: log.source,
                message: log.message,
                metadata: log.metadata as Record<string, unknown> | undefined,
            }));

            return reply.send({
                data: responseData,
                pagination: {
                    limit: limitNum,
                    hasMore,
                    nextCursor,
                },
            });
        } catch (error) {
            fastify.log.error(error, '[Observe API] Error fetching logs');
            return reply.status(500).send({ error: 'Failed to fetch logs' });
        }
    });

    /**
     * GET /api/v1/observe/logs/sources
     * Get available log sources
     */
    fastify.get('/observe/logs/sources', async (request, reply) => {
        const { tenantId } = request.query as { tenantId?: string };

        try {
            // Get distinct sources from database
            const sources = await prisma.logEntry.findMany({
                where: tenantId ? { tenantId } : undefined,
                select: { source: true },
                distinct: ['source'],
                orderBy: { source: 'asc' },
            });

            return reply.send({
                data: sources.map((s) => s.source),
            });
        } catch (error) {
            fastify.log.error(error, '[Observe API] Error fetching log sources');
            // Fallback to mock sources
            return reply.send({
                data: [
                    'api-gateway',
                    'payment-service',
                    'auth-service',
                    'database',
                    'queue',
                ],
            });
        }
    });

    /**
     * POST /api/v1/observe/logs/export
     * Export logs to CSV or JSON
     */
    fastify.post('/observe/logs/export', async (request, reply) => {
        const { tenantId, filters = {}, format = 'csv' } = request.body as {
            tenantId: string;
            filters?: { level?: string; source?: string; search?: string };
            format?: 'csv' | 'json' | 'pdf';
        };

        try {
            // Fetch logs with filters
            const where: any = { tenantId };
            if (filters.level) where.level = filters.level;
            if (filters.source) where.source = filters.source;
            if (filters.search) {
                where.message = { contains: filters.search, mode: 'insensitive' };
            }

            const logs = await prisma.logEntry.findMany({
                where,
                orderBy: { timestamp: 'desc' },
                take: 10000, // Limit for export
            });

            const exportId = `export-${Date.now()}`;

            if (format === 'csv') {
                const csv = await generateCsvExport(logs, [
                    'id',
                    'level',
                    'source',
                    'message',
                    'timestamp',
                ]);

                reply.header('Content-Type', 'text/csv');
                reply.header(
                    'Content-Disposition',
                    `attachment; filename="logs-export-${exportId}.csv"`,
                );
                return reply.send(csv);
            } else if (format === 'json') {
                reply.header('Content-Type', 'application/json');
                reply.header(
                    'Content-Disposition',
                    `attachment; filename="logs-export-${exportId}.json"`,
                );
                return reply.send({ data: logs, total: logs.length, exportId });
            } else if (format === 'pdf') {
                const pdfResult = await generateLogsPdf(logs);
                return reply.send({
                    success: true,
                    data: pdfResult,
                });
            }

            return reply.status(400).send({ error: 'Invalid format specified' });
        } catch (error) {
            fastify.log.error({ err: error }, 'Failed to export logs');
            return reply.status(500).send({ error: 'Failed to export logs' });
        }
    });

    /**
     * POST /api/v1/observe/alerts/export
     * Export alerts to CSV or PDF
     */
    fastify.post('/observe/alerts/export', async (request, reply) => {
        const { tenantId, filters = {}, format = 'csv' } = request.body as {
            tenantId: string;
            filters?: { severity?: string; status?: string };
            format?: 'csv' | 'json' | 'pdf';
        };

        try {
            // Fetch alerts with filters
            const where: any = { tenantId };
            if (filters.severity) where.severity = filters.severity;
            if (filters.status) where.status = filters.status;

            const alerts = await prisma.alert.findMany({
                where,
                orderBy: { createdAt: 'desc' },
            });

            const exportId = `export-${Date.now()}`;

            if (format === 'csv') {
                const csv = await generateCsvExport(alerts, [
                    'id',
                    'severity',
                    'status',
                    'title',
                    'message',
                    'source',
                    'createdAt',
                ]);

                reply.header('Content-Type', 'text/csv');
                reply.header(
                    'Content-Disposition',
                    `attachment; filename="alerts-export-${exportId}.csv"`,
                );
                return reply.send(csv);
            } else if (format === 'json') {
                reply.header('Content-Type', 'application/json');
                reply.header(
                    'Content-Disposition',
                    `attachment; filename="alerts-export-${exportId}.json"`,
                );
                return reply.send({ data: alerts, total: alerts.length, exportId });
            } else if (format === 'pdf') {
                const pdfResult = await generateAlertsPdf(alerts);
                return reply.send({
                    success: true,
                    data: pdfResult,
                });
            }

            return reply.status(400).send({ error: 'Invalid format specified' });
        } catch (error) {
            fastify.log.error({ err: error }, 'Failed to export alerts');
            return reply.status(500).send({ error: 'Failed to export alerts' });
        }
    });

    /**
     * POST /api/v1/observe/notifications/send
     * Manually trigger notifications for an alert
     */
    fastify.post('/observe/notifications/send', async (request, reply) => {
        const { alertId } = request.body as { alertId: string };

        try {
            const alert = await prisma.alert.findUnique({
                where: { id: alertId },
            });

            if (!alert) {
                return reply.status(404).send({ error: 'Alert not found' });
            }

            const result = await notificationOrchestrator.notifyAlert(alert);

            return reply.send({
                success: true,
                data: result,
            });
        } catch (error) {
            fastify.log.error({ err: error }, 'Failed to send notification');
            return reply.status(500).send({ error: 'Failed to send notification' });
        }
    });
}
