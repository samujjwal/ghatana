/**
 * Seed script for observability test data (alerts and logs)
 * Run with: npx tsx prisma/seed-observability.ts
 */

import { config } from 'dotenv';
import * as prismaModule from '@prisma/client';
import type { PrismaClient as PrismaClientType } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';

// Load environment variables
config();

const PrismaClient = (prismaModule as any).PrismaClient ?? (prismaModule as any).default;

const connectionString = process.env.DATABASE_URL;
if (!connectionString) {
    throw new Error('DATABASE_URL environment variable is required');
}

const pool = new Pool({ connectionString });
const adapter = new PrismaPg(pool);

const prisma = new (PrismaClient as any)({
    adapter,
    log: ['error'],
}) as PrismaClientType;

async function main() {
    console.log('🌱 Seeding observability data...');

    // Create or find a test tenant
    let tenant = await prisma.tenant.findFirst({
        where: { key: 'test-org' },
    });

    if (!tenant) {
        console.log('Creating test tenant...');
        tenant = await prisma.tenant.create({
            data: {
                key: 'test-org',
                name: 'Test Organization',
                displayName: 'Test Organization',
                description: 'Test tenant for observability data',
                status: 'active',
                plan: 'standard',
            },
        });
        console.log(`✅ Created tenant: ${tenant.id}`);
    }

    const tenantId = tenant.id;

    // Clear existing data
    await prisma.logEntry.deleteMany({ where: { tenantId } });
    await prisma.alert.deleteMany({ where: { tenantId } });

    // Seed Alerts
    const alertData = [
        {
            tenantId,
            severity: 'critical',
            status: 'active',
            title: 'Database Connection Pool Exhausted',
            message: 'All database connections are in use. New requests are being queued.',
            source: 'database',
            metadata: {
                category: 'infrastructure',
                poolSize: 20,
                activeConnections: 20,
                queuedRequests: 45,
            },
        },
        {
            tenantId,
            severity: 'high',
            status: 'active',
            title: 'High Memory Usage Detected',
            message: 'Application memory usage has exceeded 85% threshold.',
            source: 'monitoring',
            metadata: {
                category: 'performance',
                currentUsage: '6.8 GB',
                threshold: '8 GB',
                percentage: 85,
            },
        },
        {
            tenantId,
            severity: 'medium',
            status: 'acknowledged',
            title: 'Slow API Response Times',
            message: 'Average API response time increased to 2.5s (threshold: 1s).',
            source: 'api-gateway',
            acknowledgedAt: new Date(Date.now() - 1800000), // 30 mins ago
            acknowledgedBy: 'john.doe@example.com',
            metadata: {
                category: 'performance',
                avgResponseTime: '2.5s',
                threshold: '1s',
                affectedEndpoints: ['/api/v1/users', '/api/v1/orders'],
            },
        },
        {
            tenantId,
            severity: 'low',
            status: 'resolved',
            title: 'SSL Certificate Expiring Soon',
            message: 'SSL certificate for api.example.com expires in 15 days.',
            source: 'security',
            resolvedAt: new Date(Date.now() - 3600000), // 1 hour ago
            resolvedBy: 'devops-team@example.com',
            metadata: {
                category: 'security',
                domain: 'api.example.com',
                expiresAt: new Date(Date.now() + 15 * 24 * 60 * 60 * 1000).toISOString(),
            },
        },
        {
            tenantId,
            severity: 'critical',
            status: 'active',
            title: 'Service Dependency Failure',
            message: 'External payment gateway returning 503 errors.',
            source: 'payment-service',
            relatedIncidents: ['INC-2024-001', 'INC-2024-002'],
            metadata: {
                category: 'integration',
                service: 'payment-gateway',
                errorRate: '100%',
                lastSuccessfulRequest: new Date(Date.now() - 600000).toISOString(),
            },
        },
    ];

    const alerts = await Promise.all(
        alertData.map((data) => prisma.alert.create({ data })),
    );

    console.log(`✅ Created ${alerts.length} alerts`);

    // Seed Log Entries
    const logLevels = ['debug', 'info', 'warn', 'error'] as const;
    const logSources = [
        'api-gateway',
        'auth-service',
        'payment-service',
        'database',
        'worker-queue',
    ];
    const logMessages = [
        'Request processed successfully',
        'User authentication completed',
        'Payment transaction initiated',
        'Database query executed',
        'Background job completed',
        'Rate limit exceeded for IP',
        'Invalid API key provided',
        'Connection timeout to external service',
        'Database connection pool at 80% capacity',
        'Cache miss - fetching from database',
    ];

    const logsData = Array.from({ length: 100 }, (_, i) => {
        const level = logLevels[Math.floor(Math.random() * logLevels.length)];
        const source = logSources[Math.floor(Math.random() * logSources.length)];
        const message = logMessages[Math.floor(Math.random() * logMessages.length)];

        return {
            tenantId,
            level,
            source,
            message,
            timestamp: new Date(Date.now() - Math.random() * 3600000), // Last hour
            metadata:
                level === 'error'
                    ? {
                          errorCode: `ERR_${Math.floor(Math.random() * 9000) + 1000}`,
                          stack: 'Error: Example stack trace',
                      }
                    : {
                          requestId: `req_${Math.random().toString(36).substring(7)}`,
                          duration: Math.floor(Math.random() * 500),
                      },
        };
    });

    const logs = await prisma.logEntry.createMany({
        data: logsData,
    });

    console.log(`✅ Created ${logs.count} log entries`);

    // Summary
    console.log('\n📊 Seed Summary:');
    console.log(`   Tenant ID: ${tenantId}`);
    console.log(`   Alerts: ${alerts.length}`);
    console.log(`   - Active: ${alerts.filter((a) => a.status === 'active').length}`);
    console.log(`   - Acknowledged: ${alerts.filter((a) => a.status === 'acknowledged').length}`);
    console.log(`   - Resolved: ${alerts.filter((a) => a.status === 'resolved').length}`);
    console.log(`   Log Entries: ${logs.count}`);
    console.log(`\n✨ Seeding complete!`);
}

main()
    .catch((e) => {
        console.error('❌ Seeding failed:', e);
        process.exit(1);
    })
    .finally(async () => {
        await prisma.$disconnect();
    });
