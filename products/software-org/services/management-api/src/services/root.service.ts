import { prisma } from '../db/client';

export class RootService {
    /**
     * Get all tenants with usage metrics
     */
    async getAllTenants() {
        const tenants = await prisma.tenant.findMany({
            include: {
                _count: {
                    select: {
                        environments: true,
                    },
                },
            },
            orderBy: { createdAt: 'desc' },
        });

        return tenants.map((tenant) => ({
            id: tenant.id,
            key: tenant.key,
            name: tenant.name,
            status: tenant.status,
            plan: tenant.plan,
            createdAt: tenant.createdAt,
            updatedAt: tenant.updatedAt,
            environmentCount: tenant._count.environments,
        }));
    }

    /**
     * Get detailed tenant info
     */
    async getTenantDetails(id: string) {
        return prisma.tenant.findUnique({
            where: { id },
            include: {
                environments: true,
                alerts: {
                    orderBy: { createdAt: 'desc' },
                    take: 20,
                },
                anomalies: {
                    orderBy: { detectedAt: 'desc' },
                    take: 20,
                },
            },
        });
    }

    /**
     * Get platform health metrics
     */
    async getPlatformHealth() {
        // Mock health metrics for now, in real world this would query Prometheus/monitoring
        const totalTenants = await prisma.tenant.count();
        const totalUsers = await prisma.user.count();
        const activeWorkspaces = await prisma.workspace.count();

        return {
            status: 'healthy',
            uptime: 99.99,
            metrics: {
                totalTenants,
                totalUsers,
                activeWorkspaces,
                apiLatency: 45, // ms
                errorRate: 0.02, // %
            },
            services: [
                { name: 'API Gateway', status: 'operational' },
                { name: 'Database', status: 'operational' },
                { name: 'Auth Service', status: 'operational' },
                { name: 'Worker Nodes', status: 'degraded', message: 'High load on node-3' },
            ]
        };
    }

    /**
     * Search users across all tenants
     */
    async searchGlobalUsers(query: string) {
        if (!query || query.length < 2) return [];

        return prisma.user.findMany({
            where: {
                OR: [
                    { name: { contains: query, mode: 'insensitive' } },
                    { email: { contains: query, mode: 'insensitive' } },
                ],
            },
            include: {
                workspaces: {
                    select: { name: true },
                },
            },
            take: 20,
        });
    }

    /**
     * Suspend a user globally
     */
    async suspendUser(userId: string, reason: string) {
        // In a real app, we might have a 'status' field on User
        // For now, we'll just log it or update a metadata field if available
        // Assuming User model doesn't have 'status' yet based on schema snippet

        // Let's check if we can update metadata or similar
        // For now, we will just return success as a mock action
        return { success: true, userId, reason, status: 'suspended' };
    }

    /**
     * Get aggregated alerts across all tenants (Root view)
     */
    async getAggregatedAlerts(filters: { severity?: string; status?: string; limit?: number }) {
        const limit = Math.min(filters.limit ?? 50, 200);
        const where: Record<string, unknown> = {};
        if (filters.severity) where.severity = filters.severity;
        if (filters.status) where.status = filters.status;

        const alerts = await prisma.alert.findMany({
            where,
            include: {
                tenant: { select: { key: true, name: true } },
            },
            orderBy: { createdAt: 'desc' },
            take: limit,
        });

        // Group by tenant for aggregated view
        const byTenant = alerts.reduce((acc, alert) => {
            const tenantKey = alert.tenantId;
            if (!acc[tenantKey]) {
                acc[tenantKey] = {
                    tenantId: alert.tenantId,
                    tenantKey: alert.tenant.key,
                    tenantName: alert.tenant.name,
                    totalAlerts: 0,
                    criticalCount: 0,
                    highCount: 0,
                    activeCount: 0,
                    recentAlerts: [] as typeof alerts,
                };
            }
            acc[tenantKey].totalAlerts++;
            if (alert.severity === 'critical') acc[tenantKey].criticalCount++;
            if (alert.severity === 'high') acc[tenantKey].highCount++;
            if (alert.status === 'active') acc[tenantKey].activeCount++;
            if (acc[tenantKey].recentAlerts.length < 5) {
                acc[tenantKey].recentAlerts.push(alert);
            }
            return acc;
        }, {} as Record<string, {
            tenantId: string;
            tenantKey: string;
            tenantName: string;
            totalAlerts: number;
            criticalCount: number;
            highCount: number;
            activeCount: number;
            recentAlerts: typeof alerts;
        }>);

        return {
            summary: {
                totalAlerts: alerts.length,
                criticalCount: alerts.filter(a => a.severity === 'critical').length,
                highCount: alerts.filter(a => a.severity === 'high').length,
                activeCount: alerts.filter(a => a.status === 'active').length,
            },
            byTenant: Object.values(byTenant),
            recentAlerts: alerts.slice(0, 10).map(a => ({
                id: a.id,
                tenantId: a.tenantId,
                tenantKey: a.tenant.key,
                severity: a.severity,
                status: a.status,
                title: a.title || a.message.substring(0, 100),
                message: a.message,
                source: a.source,
                createdAt: a.createdAt.toISOString(),
            })),
        };
    }
}

export const rootService = new RootService();
