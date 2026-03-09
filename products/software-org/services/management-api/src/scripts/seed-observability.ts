/**
 * Database Seeding Script for Observability Features
 *
 * <p><b>Purpose</b><br>
 * Populates the database with realistic test data for:
 * - Alerts (various severities and statuses)
 * - Log entries (different levels and sources)
 * - Related incidents and metadata
 *
 * <p><b>Usage</b><br>
 * Run: pnpm run seed:observe
 *
 * @doc.type script
 * @doc.purpose Database seeding for testing
 * @doc.layer platform
 * @doc.pattern DataSeeding
 */

import { prisma } from '../db/client.js';

const TENANT_ID = '7146cb95-180c-485f-9ca4-5adf5dcaf7c4';

// Alert templates with realistic scenarios
const alertTemplates = [
    {
        severity: 'critical',
        title: 'Database Connection Pool Exhausted',
        message: 'PostgreSQL connection pool has reached maximum capacity (100/100). New connections are being rejected.',
        source: 'postgres-primary',
        status: 'active',
        metadata: {
            poolSize: 100,
            activeConnections: 100,
            waitingConnections: 45,
            endpoint: 'db-prod-1.internal',
        },
    },
    {
        severity: 'critical',
        title: 'API Gateway High Error Rate',
        message: '5xx error rate has exceeded 15% threshold. Current rate: 23.4%',
        source: 'api-gateway',
        status: 'active',
        metadata: {
            errorRate: 23.4,
            threshold: 15.0,
            totalRequests: 15234,
            errors: 3565,
            timeWindow: '5m',
        },
    },
    {
        severity: 'high',
        title: 'Memory Usage Above Threshold',
        message: 'Container memory usage at 89%, approaching limit of 4GB',
        source: 'kubernetes/payment-service',
        status: 'active',
        metadata: {
            memoryUsage: '3.56GB',
            memoryLimit: '4GB',
            percentage: 89,
            pod: 'payment-service-7d9f8c-xk2p9',
            namespace: 'production',
        },
    },
    {
        severity: 'high',
        title: 'Kafka Consumer Lag Increasing',
        message: 'Consumer group payment-processors has lag of 45,000 messages',
        source: 'kafka-monitoring',
        status: 'acknowledged',
        metadata: {
            consumerGroup: 'payment-processors',
            topic: 'payment-events',
            partition: 5,
            lag: 45000,
            offset: 1234567,
        },
    },
    {
        severity: 'medium',
        title: 'SSL Certificate Expiring Soon',
        message: 'SSL certificate for api.example.com expires in 14 days',
        source: 'cert-manager',
        status: 'active',
        metadata: {
            domain: 'api.example.com',
            expiryDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
            issuer: 'Let\'s Encrypt',
            daysRemaining: 14,
        },
    },
    {
        severity: 'medium',
        title: 'Slow Query Detected',
        message: 'Query execution time exceeded 5 seconds: SELECT * FROM orders WHERE...',
        source: 'mysql-slow-query-log',
        status: 'resolved',
        metadata: {
            executionTime: 7.3,
            query: 'SELECT * FROM orders WHERE status = "pending" AND created_at > NOW() - INTERVAL 1 YEAR',
            database: 'orders_db',
            rowsExamined: 2345678,
        },
    },
    {
        severity: 'low',
        title: 'Disk Space Usage Warning',
        message: 'Disk usage at 72% on /var/log volume',
        source: 'node-exporter',
        status: 'active',
        metadata: {
            mountPoint: '/var/log',
            used: '360GB',
            total: '500GB',
            percentage: 72,
            host: 'app-server-03',
        },
    },
    {
        severity: 'low',
        title: 'Cache Hit Rate Below Target',
        message: 'Redis cache hit rate dropped to 85%, target is 95%',
        source: 'redis-monitoring',
        status: 'resolved',
        metadata: {
            hitRate: 85,
            target: 95,
            hits: 850000,
            misses: 150000,
            cluster: 'redis-prod',
        },
    },
    {
        severity: 'critical',
        title: 'Service Mesh Network Partition',
        message: 'Network partition detected between zones us-east-1a and us-east-1b',
        source: 'istio-control-plane',
        status: 'acknowledged',
        metadata: {
            zones: ['us-east-1a', 'us-east-1b'],
            affectedServices: 12,
            packetLoss: 100,
            duration: '3m',
        },
    },
    {
        severity: 'high',
        title: '异常登录尝试检测到',
        message: '在过去5分钟内检测到来自可疑IP的1500次失败登录尝试',
        source: 'auth-service',
        status: 'active',
        metadata: {
            ipAddress: '192.168.100.45',
            attempts: 1500,
            timeWindow: '5m',
            country: 'CN',
            blocked: true,
        },
    },
];

// Log entry templates
const logTemplates = [
    {
        level: 'ERROR',
        source: 'payment-service',
        message: 'Failed to process payment transaction: Stripe API timeout after 30s',
        metadata: {
            transactionId: 'tx_9df83h2kjs',
            amount: 299.99,
            currency: 'USD',
            customerId: 'cust_abc123',
            errorCode: 'STRIPE_TIMEOUT',
            retryCount: 3,
        },
    },
    {
        level: 'ERROR',
        source: 'postgres-primary',
        message: 'Connection refused on port 5432: Connection pool exhausted',
        metadata: {
            poolSize: 100,
            activeConnections: 100,
            waitingThreads: 25,
            endpoint: '10.0.1.45:5432',
        },
    },
    {
        level: 'WARN',
        source: 'kafka-consumer',
        message: 'Consumer rebalance triggered, partition assignment changed',
        metadata: {
            consumerGroup: 'order-processors',
            oldPartitions: [0, 1, 2],
            newPartitions: [0, 1],
            cause: 'consumer-timeout',
        },
    },
    {
        level: 'WARN',
        source: 'auth-service',
        message: 'Rate limit exceeded for API key: sk_live_xxx...xxx',
        metadata: {
            apiKey: 'sk_live_***masked***',
            requests: 1050,
            limit: 1000,
            window: '1m',
            clientIp: '203.45.67.89',
        },
    },
    {
        level: 'INFO',
        source: 'api-gateway',
        message: 'Request processed successfully',
        metadata: {
            method: 'POST',
            path: '/api/v1/orders',
            statusCode: 201,
            responseTime: 145,
            requestId: 'req_abc123xyz',
        },
    },
    {
        level: 'INFO',
        source: 'kubernetes',
        message: 'Pod successfully deployed: payment-service-v2.3.1',
        metadata: {
            pod: 'payment-service-7d9f8c-xk2p9',
            namespace: 'production',
            image: 'gcr.io/project/payment-service:v2.3.1',
            node: 'node-pool-1-xyz',
        },
    },
    {
        level: 'DEBUG',
        source: 'redis-client',
        message: 'Cache key retrieved: user:12345:profile',
        metadata: {
            key: 'user:12345:profile',
            ttl: 3600,
            size: 1024,
            hit: true,
        },
    },
    {
        level: 'ERROR',
        source: 'email-service',
        message: 'SMTP connection failed: Connection timeout to smtp.sendgrid.net',
        metadata: {
            smtpHost: 'smtp.sendgrid.net',
            port: 587,
            timeout: 30000,
            recipientCount: 150,
            queueSize: 450,
        },
    },
    {
        level: 'WARN',
        source: 'backup-service',
        message: 'Backup duration exceeded SLA: 4h 23m (target: 4h)',
        metadata: {
            backupId: 'bkp_20251210_0200',
            duration: '4h 23m',
            dataSize: '2.3TB',
            compressionRatio: 0.65,
            target: 'S3://backups/prod',
        },
    },
    {
        level: 'INFO',
        source: 'scheduler',
        message: 'Cron job executed successfully: daily-report-generation',
        metadata: {
            jobName: 'daily-report-generation',
            executionTime: '45s',
            recordsProcessed: 123456,
            outputFile: 's3://reports/2025-12-10.pdf',
        },
    },
];

async function seedAlerts() {
    console.log('🚨 Seeding alerts...');

    const alerts = [];
    const now = Date.now();

    for (let i = 0; i < 50; i++) {
        const template = alertTemplates[i % alertTemplates.length];
        const timestamp = new Date(now - Math.random() * 7 * 24 * 60 * 60 * 1000); // Last 7 days

        const alert = {
            tenantId: TENANT_ID,
            severity: template.severity as 'critical' | 'high' | 'medium' | 'low',
            status: template.status as 'active' | 'acknowledged' | 'resolved',
            title: `${template.title} #${i + 1}`,
            message: template.message,
            source: template.source,
            timestamp,
            metadata: template.metadata,
            relatedIncidents: i % 3 === 0 ? [`INC-${1000 + i}`, `INC-${2000 + i}`] : [],
            acknowledgedAt: template.status === 'acknowledged' || template.status === 'resolved'
                ? new Date(timestamp.getTime() + Math.random() * 60 * 60 * 1000)
                : null,
            acknowledgedBy: template.status === 'acknowledged' || template.status === 'resolved'
                ? 'ops-engineer@example.com'
                : null,
            resolvedAt: template.status === 'resolved'
                ? new Date(timestamp.getTime() + Math.random() * 2 * 60 * 60 * 1000)
                : null,
            resolvedBy: template.status === 'resolved'
                ? 'sre-team@example.com'
                : null,
            snoozedUntil: i % 7 === 0 && template.status === 'active'
                ? new Date(Date.now() + 30 * 60 * 1000) // Snoozed for 30 minutes
                : null,
        };

        alerts.push(alert);
    }

    await prisma.alert.createMany({
        data: alerts,
        skipDuplicates: true,
    });

    console.log(`✅ Created ${alerts.length} alerts`);
}

async function seedLogs() {
    console.log('📝 Seeding log entries...');

    const logs = [];
    const now = Date.now();

    // Create logs for the last 24 hours
    for (let i = 0; i < 500; i++) {
        const template = logTemplates[i % logTemplates.length];
        const timestamp = new Date(now - Math.random() * 24 * 60 * 60 * 1000); // Last 24 hours

        const log = {
            tenantId: TENANT_ID,
            level: template.level,
            source: template.source,
            message: `${template.message} [${i + 1}]`,
            timestamp,
            metadata: {
                ...template.metadata,
                sequenceNumber: i + 1,
                hostname: `host-${Math.floor(Math.random() * 10) + 1}`,
                correlationId: `corr_${Math.random().toString(36).substring(7)}`,
            },
        };

        logs.push(log);
    }

    // Batch insert for better performance
    const batchSize = 100;
    for (let i = 0; i < logs.length; i += batchSize) {
        const batch = logs.slice(i, i + batchSize);
        await prisma.logEntry.createMany({
            data: batch,
            skipDuplicates: true,
        });
        console.log(`  📦 Inserted batch ${Math.floor(i / batchSize) + 1}/${Math.ceil(logs.length / batchSize)}`);
    }

    console.log(`✅ Created ${logs.length} log entries`);
}

async function seedStatistics() {
    console.log('📊 Generating statistics...');

    const alertStats = await prisma.alert.groupBy({
        by: ['severity', 'status'],
        where: { tenantId: TENANT_ID },
        _count: true,
    });

    console.log('\n📈 Alert Statistics:');
    alertStats.forEach((stat) => {
        console.log(`  ${stat.severity.padEnd(10)} ${stat.status.padEnd(15)} ${stat._count} alerts`);
    });

    const logStats = await prisma.logEntry.groupBy({
        by: ['level'],
        where: { tenantId: TENANT_ID },
        _count: true,
    });

    console.log('\n📊 Log Statistics:');
    logStats.forEach((stat) => {
        console.log(`  ${stat.level.padEnd(10)} ${stat._count} entries`);
    });

    const sources = await prisma.logEntry.findMany({
        where: { tenantId: TENANT_ID },
        distinct: ['source'],
        select: { source: true },
    });

    console.log(`\n🔍 Unique Sources: ${sources.length}`);
    sources.slice(0, 10).forEach((s) => {
        console.log(`  - ${s.source}`);
    });
    if (sources.length > 10) {
        console.log(`  ... and ${sources.length - 10} more`);
    }
}

async function main() {
    console.log('🌱 Starting database seeding for observability features...\n');

    try {
        // Ensure tenant exists
        console.log('🏢 Ensuring tenant exists...');
        await prisma.tenant.upsert({
            where: { id: TENANT_ID },
            update: {},
            create: {
                id: TENANT_ID,
                key: 'test-org',
                name: 'Test Organization',
            },
        });
        console.log('✅ Tenant ready\n');

        // Clear existing data
        console.log('🧹 Clearing existing data...');
        await prisma.alert.deleteMany({ where: { tenantId: TENANT_ID } });
        await prisma.logEntry.deleteMany({ where: { tenantId: TENANT_ID } });
        console.log('✅ Cleared existing data\n');

        // Seed new data
        await seedAlerts();
        console.log('');
        await seedLogs();
        console.log('');
        await seedStatistics();

        console.log('\n✨ Database seeding completed successfully!');
        console.log('\n🔗 Test the API:');
        console.log('  curl "http://localhost:3101/api/v1/observe/alerts?tenantId=7146cb95-180c-485f-9ca4-5adf5dcaf7c4&page=1&pageSize=10"');
        console.log('  curl "http://localhost:3101/api/v1/observe/logs?tenantId=7146cb95-180c-485f-9ca4-5adf5dcaf7c4&limit=10"');
    } catch (error) {
        console.error('❌ Error seeding database:', error);
        throw error;
    } finally {
        await prisma.$disconnect();
    }
}

main()
    .catch((error) => {
        console.error(error);
        process.exit(1);
    });
