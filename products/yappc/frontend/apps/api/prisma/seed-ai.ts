/**
 * AI Models Seed Data
 *
 * Populates database with sample AI insights, predictions, anomalies,
 * copilot sessions, and embeddings for development and testing.
 *
 * @module prisma/seed-ai
 * @doc.type script
 * @doc.purpose Seed AI data
 * @doc.layer infrastructure
 */

import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

/**
 * Seed AI Insights
 */
async function seedAIInsights() {
    console.log('🔮 Seeding AI Insights...');

    const insights = [
        {
            type: 'prediction',
            category: 'velocity',
            title: 'Sprint Velocity Decline Detected',
            description: 'Current sprint velocity is 15% below the 3-month average. Team capacity may be overallocated or facing blockers.',
            confidence: 0.87,
            severity: 'medium',
            agentName: 'velocity-tracker',
            actionable: true,
            suggestedActions: [
                {
                    id: 'action-1',
                    type: 'navigate',
                    label: 'Review Sprint Backlog',
                    description: 'Check for blocking issues',
                    payload: { route: '/devsecops/phase/development' },
                    confirmRequired: false,
                    automatable: false,
                },
                {
                    id: 'action-2',
                    type: 'execute',
                    label: 'Schedule Team Sync',
                    description: 'Discuss capacity and blockers',
                    payload: { action: 'schedule_meeting' },
                    confirmRequired: true,
                    automatable: false,
                },
            ],
            relatedItems: [],
            expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days
            model: 'gpt-4-turbo',
            reasoning: 'Historical velocity analysis shows consistent decline over past 2 weeks. Contributing factors: increased bug count (+12%), reduced available capacity (-2 team members on leave).',
        },
        {
            type: 'anomaly',
            category: 'security',
            title: 'Unusual Security Scan Failures',
            description: 'Security scan failure rate increased from 2% to 18% in the past 48 hours. 5 new critical vulnerabilities detected.',
            confidence: 0.93,
            severity: 'critical',
            agentName: 'security-monitor',
            actionable: true,
            suggestedActions: [
                {
                    id: 'action-3',
                    type: 'navigate',
                    label: 'View Security Dashboard',
                    payload: { route: '/devsecops/phase/security' },
                    confirmRequired: false,
                    automatable: false,
                },
                {
                    id: 'action-4',
                    type: 'notify',
                    label: 'Alert Security Team',
                    payload: { team: 'security', priority: 'critical' },
                    confirmRequired: true,
                    automatable: true,
                },
            ],
            relatedItems: [],
            expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000), // 24 hours
            model: 'claude-3-5-sonnet',
            reasoning: 'Pattern analysis indicates introduction of new dependencies with known CVEs. Recommend immediate dependency audit.',
        },
        {
            type: 'recommendation',
            category: 'quality',
            title: 'Test Coverage Optimization Opportunity',
            description: 'Unit test coverage is 78%, but integration test coverage is only 42%. Increasing integration tests could prevent 30% more production bugs.',
            confidence: 0.82,
            severity: 'low', agentName: 'quality-analyzer', actionable: true,
            suggestedActions: [
                {
                    id: 'action-5',
                    type: 'create',
                    label: 'Generate Test Plan',
                    description: 'AI-powered test case generation',
                    payload: { type: 'integration_tests' },
                    confirmRequired: false,
                    automatable: true,
                },
            ],
            relatedItems: [],
            expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000), // 30 days
            model: 'gpt-4-turbo',
            reasoning: 'Analysis of bug escape patterns shows 68% of production bugs would have been caught by integration tests.',
        },
        {
            type: 'opportunity',
            category: 'performance',
            title: 'Code Refactoring Opportunity Detected',
            description: 'Module "UserService" shows high cyclomatic complexity (CC=25). Refactoring could improve maintainability by 40%.',
            confidence: 0.75,
            severity: 'info',
            agentName: 'performance-analyzer',
            actionable: true,
            suggestedActions: [
                {
                    id: 'action-6',
                    type: 'create',
                    label: 'Create Refactoring Task',
                    payload: { module: 'UserService', type: 'tech_debt' },
                    confirmRequired: true,
                    automatable: false,
                },
            ],
            relatedItems: [],
            model: 'gpt-4-turbo',
        },
        {
            type: 'warning',
            category: 'compliance',
            title: 'Compliance Audit Due Soon',
            description: 'SOC 2 audit scheduled in 14 days. 3 compliance controls show gaps that need remediation.',
            confidence: 1.0,
            severity: 'high',
            agentName: 'compliance-monitor',
            actionable: true,
            suggestedActions: [
                {
                    id: 'action-7',
                    type: 'navigate',
                    label: 'View Compliance Dashboard',
                    payload: { route: '/devsecops/compliance' },
                    confirmRequired: false,
                    automatable: false,
                },
            ],
            relatedItems: [],
            expiresAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000), // 14 days
            model: 'gpt-4-turbo',
        },
    ];

    for (const insight of insights) {
        await prisma.aIInsight.create({ data: insight as unknown });
    }

    console.log(`✅ Created ${insights.length} AI insights`);
}

/**
 * Seed Predictions
 */
async function seedPredictions() {
    console.log('📊 Seeding Predictions...');

    // Get all phases
    const phases = await prisma.phase.findMany();
    const developmentPhase = phases.find((p) => p.key === 'development');

    const predictions = [
        {
            type: 'deadline_risk',
            targetType: 'sprint',
            targetId: 'sprint-001',
            probability: 0.73,
            timeline: '2025-01-15',
            affectedItems: [],
            suggestedMitigation: [
                {
                    id: 'mit-1',
                    action: 'Reduce scope by 2 non-critical features',
                    impact: 'Increases on-time delivery probability to 92%',
                },
                {
                    id: 'mit-2',
                    action: 'Add 1 additional developer for 2 weeks',
                    impact: 'Increases on-time delivery probability to 85%',
                },
            ],
            model: 'xgboost-v2',
            modelName: 'xgboost',
            modelVersion: 'v2',
            confidence: 0.81,
            expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
            phaseId: developmentPhase?.id,
        },
        {
            type: 'blocker_likelihood',
            targetType: 'task',
            targetId: 'task-001',
            probability: 0.62,
            timeline: '2025-12-28',
            affectedItems: [],
            suggestedMitigation: [
                {
                    id: 'mit-3',
                    action: 'Pre-emptive dependency review',
                    impact: 'Reduces blocker probability to 35%',
                },
            ],
            model: 'prophet-forecast',
            modelName: 'prophet',
            modelVersion: 'v1',
            confidence: 0.68,
            expiresAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000),
        },
        {
            type: 'scope_creep',
            targetType: 'project',
            targetId: 'project-001',
            probability: 0.55,
            timeline: 'Ongoing',
            affectedItems: [],
            suggestedMitigation: [
                {
                    id: 'mit-4',
                    action: 'Implement stricter change request process',
                    impact: 'Reduces scope creep by 40%',
                },
            ],
            model: 'lstm-timeseries',
            modelName: 'lstm',
            modelVersion: 'timeseries',
            confidence: 0.72,
            expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
        },
        {
            type: 'resource_constraint',
            targetType: 'team',
            targetId: 'team-001',
            probability: 0.48,
            timeline: '2025-01-10',
            affectedItems: [],
            suggestedMitigation: [
                {
                    id: 'mit-5',
                    action: 'Cross-train 2 team members on critical skills',
                    impact: 'Reduces single-point-of-failure risk',
                },
            ],
            model: 'xgboost-v2',
            modelName: 'xgboost',
            modelVersion: 'v2',
            confidence: 0.65,
            expiresAt: new Date(Date.now() + 21 * 24 * 60 * 60 * 1000),
        },
    ];

    for (const prediction of predictions) {
        await prisma.prediction.create({ data: prediction as unknown });
    }

    console.log(`✅ Created ${predictions.length} predictions`);
}

/**
 * Seed Anomaly Alerts
 */
async function seedAnomalyAlerts() {
    console.log('🚨 Seeding Anomaly Alerts...');

    const anomalies = [
        {
            type: 'VELOCITY',
            severity: 'WARNING',
            title: 'Story Point Velocity Dropped 25%',
            description: 'Current sprint velocity is 30 points vs 40 point average. Deviation exceeds 2 standard deviations.',
            detectedAt: new Date(Date.now() - 2 * 60 * 60 * 1000), // 2 hours ago
            affectedItems: [],
            baselineValue: 40.0,
            currentValue: 30.0,
            deviationPercent: -25.0,
            suggestedActions: [
                {
                    id: 'anom-action-1',
                    action: 'Review team capacity and workload distribution',
                },
            ],
            acknowledged: false,
            falsePositive: false,
            confidence: 0.89,
            modelVersion: 'isolation-forest-v3',
        },
        {
            type: 'QUALITY',
            severity: 'CRITICAL',
            title: 'Bug Escape Rate Spike',
            description: 'Production bugs increased by 180% in the past week. Critical quality regression detected.',
            detectedAt: new Date(Date.now() - 6 * 60 * 60 * 1000), // 6 hours ago
            affectedItems: [],
            baselineValue: 5.0,
            currentValue: 14.0,
            deviationPercent: 180.0,
            suggestedActions: [
                {
                    id: 'anom-action-2',
                    action: 'Emergency code review of recent changes',
                },
                {
                    id: 'anom-action-3',
                    action: 'Halt non-critical deployments',
                },
            ],
            acknowledged: false,
            falsePositive: false,
            confidence: 0.95,
            modelVersion: 'lstm-anomaly-v2',
        },
        {
            type: 'SECURITY',
            severity: 'CRITICAL',
            title: 'Unusual API Error Rate',
            description: 'API 500 errors increased from 0.1% to 3.5%. Potential security incident or infrastructure issue.',
            detectedAt: new Date(Date.now() - 30 * 60 * 1000), // 30 minutes ago
            affectedItems: [],
            baselineValue: 0.1,
            currentValue: 3.5,
            deviationPercent: 3400.0,
            suggestedActions: [
                {
                    id: 'anom-action-4',
                    action: 'Investigate error logs immediately',
                },
                {
                    id: 'anom-action-5',
                    action: 'Check for DDoS or malicious traffic',
                },
            ],
            acknowledged: false,
            falsePositive: false,
            confidence: 0.97,
            modelVersion: 'isolation-forest-v3',
        },
        {
            type: 'PATTERN',
            severity: 'INFO',
            title: 'Unusual Deployment Pattern',
            description: 'Deployments occurring outside normal business hours (3 in past 48 hours vs 0 typical).',
            detectedAt: new Date(Date.now() - 12 * 60 * 60 * 1000), // 12 hours ago
            affectedItems: [],
            baselineValue: 0.0,
            currentValue: 3.0,
            deviationPercent: 300.0,
            suggestedActions: [
                {
                    id: 'anom-action-6',
                    action: 'Review deployment audit logs',
                },
            ],
            acknowledged: true,
            acknowledgedAt: new Date(Date.now() - 2 * 60 * 60 * 1000),
            acknowledgedBy: 'admin-user-id',
            falsePositive: false,
            confidence: 0.71,
            modelVersion: 'lstm-anomaly-v2',
        },
    ];

    for (const anomaly of anomalies) {
        await prisma.anomalyAlert.create({ data: anomaly as unknown });
    }

    console.log(`✅ Created ${anomalies.length} anomaly alerts`);
}

/**
 * Seed Copilot Sessions
 */
async function seedCopilotSessions() {
    console.log('💬 Seeding Copilot Sessions...');

    const sessions = [
        {
            id: 'session-demo-1',
            userId: 'user-1',
            persona: 'developer',
            messages: [
                {
                    role: 'user',
                    content: 'What are the highest priority bugs blocking our release?',
                    timestamp: new Date(Date.now() - 10 * 60 * 1000),
                },
                {
                    role: 'assistant',
                    content: 'Based on current data, there are 3 critical bugs blocking the release:\n\n1. **AUTH-1234**: Authentication bypass vulnerability (Severity: Critical)\n   - Affects login flow\n   - Assigned to Sarah Chen\n   - ETA: 2 hours\n\n2. **DB-5678**: Database connection pool exhaustion (Severity: High)\n   - Causing intermittent 500 errors\n   - Assigned to Mike Johnson\n   - ETA: 4 hours\n\n3. **API-9012**: Rate limiting not enforced (Severity: Critical)\n   - Security vulnerability\n   - Assigned to Alex Kumar\n   - ETA: 6 hours\n\nWould you like me to schedule a war room meeting to expedite resolution?',
                    timestamp: new Date(Date.now() - 9 * 60 * 1000),
                },
                {
                    role: 'user',
                    content: 'Yes, schedule the meeting and notify the team.',
                    timestamp: new Date(Date.now() - 8 * 60 * 1000),
                },
                {
                    role: 'assistant',
                    content: '✅ War room meeting scheduled for 2:00 PM today (30 minutes from now).\n\nNotifications sent to:\n- Sarah Chen\n- Mike Johnson\n- Alex Kumar\n- Release Manager (Emily Davis)\n\nMeeting agenda created with links to all 3 blocking issues. I\'ve also created a shared document for real-time collaboration.',
                    timestamp: new Date(Date.now() - 7 * 60 * 1000),
                },
            ],
            context: {
                currentRoute: '/devsecops/phase/security',
                selectedItems: ['AUTH-1234', 'DB-5678', 'API-9012'],
                recentActions: ['viewed_security_dashboard', 'filtered_critical_bugs'],
            },
            modelUsed: 'gpt-4-turbo',
            tokensUsed: 342,
            costUSD: 0.01026,
        },
        {
            id: 'session-demo-2',
            userId: 'user-2',
            persona: 'product-manager',
            messages: [
                {
                    role: 'user',
                    content: 'Give me a status update on the Q1 roadmap.',
                    timestamp: new Date(Date.now() - 60 * 60 * 1000),
                },
                {
                    role: 'assistant',
                    content: '📊 **Q1 Roadmap Status Report**\n\n**Overall Progress:** 62% complete\n\n**On Track (Green):**\n- User Authentication Revamp: 85% complete\n- API Performance Optimization: 70% complete\n- Mobile App v2.0: 60% complete\n\n**At Risk (Yellow):**\n- Real-time Notifications: 45% complete (delayed by 1 week)\n  - Blocker: WebSocket infrastructure not ready\n  - Mitigation: Added 2 backend engineers\n\n**Blocked (Red):**\n- Analytics Dashboard: 30% complete (delayed by 2 weeks)\n  - Blocker: Data warehouse migration pending\n  - Recommendation: Consider descoping advanced features\n\n**Key Metrics:**\n- Team Velocity: 30 story points/week (stable)\n- Bug Count: 12 critical, 34 high (within acceptable range)\n- Test Coverage: 82% (↑3% from last month)\n\nWould you like a detailed breakdown of any specific initiative?',
                    timestamp: new Date(Date.now() - 59 * 60 * 1000),
                },
            ],
            context: {
                currentRoute: '/devsecops/reports/executive',
                persona: 'product-manager',
            },
            modelUsed: 'claude-3-5-sonnet',
            tokensUsed: 456,
            costUSD: 0.01368,
            satisfactionRating: 5,
            endedAt: new Date(Date.now() - 50 * 60 * 1000),
        },
    ];

    for (const session of sessions) {
        await prisma.copilotSession.create({ data: session as unknown });
    }

    console.log(`✅ Created ${sessions.length} copilot sessions`);
}

/**
 * Seed AI Metrics
 */
async function seedAIMetrics() {
    console.log('📈 Seeding AI Metrics...');

    const now = Date.now();
    const metrics = [];

    // Generate metrics for the past 24 hours
    for (let i = 0; i < 100; i++) {
        const timestamp = new Date(now - Math.random() * 24 * 60 * 60 * 1000);
        const agents = ['CopilotAgent', 'PredictionAgent', 'AnomalyDetectorAgent', 'CodeGeneratorAgent'];
        const models = ['gpt-4-turbo', 'claude-3-5-sonnet', 'gpt-3.5-turbo'];
        const operations = ['completion', 'embedding', 'prediction', 'analysis'];

        const success = Math.random() > 0.05; // 95% success rate
        const tokensUsed = Math.floor(Math.random() * 2000) + 100;
        const latencyMs = Math.floor(Math.random() * 3000) + 200;
        const costUSD = tokensUsed * (Math.random() > 0.5 ? 0.00003 : 0.00001);

        metrics.push({
            agentName: agents[Math.floor(Math.random() * agents.length)],
            model: models[Math.floor(Math.random() * models.length)],
            operation: operations[Math.floor(Math.random() * operations.length)],
            tokensUsed,
            latencyMs,
            costUSD,
            success,
            errorMessage: success ? null : 'Rate limit exceeded',
            userId: Math.random() > 0.5 ? 'user-1' : 'user-2',
            sessionId: Math.random() > 0.7 ? 'session-demo-1' : null,
            timestamp,
        });
    }

    for (const metric of metrics) {
        await prisma.aIMetric.create({ data: metric });
    }

    console.log(`✅ Created ${metrics.length} AI metrics`);
}

/**
 * Seed User AI Preferences
 */
async function seedUserAIPreferences() {
    console.log('⚙️ Seeding User AI Preferences...');

    // Get all users
    const users = await prisma.user.findMany();

    if (users.length === 0) {
        console.log('⚠️ No users found, skipping AI preferences seed');
        return;
    }

    for (const user of users.slice(0, 3)) {
        // Just seed for first 3 users
        await prisma.userAIPreferences.create({
            data: {
                userId: user.id,
                enableAISuggestions: true,
                enableAutoComplete: true,
                enablePredictions: true,
                suggestionFrequency: 'moderate',
                preferredModel: Math.random() > 0.5 ? 'gpt-4-turbo' : 'claude-3-5-sonnet',
                privacyLevel: 'full',
            } as unknown,
        });
    }

    console.log(`✅ Created AI preferences for ${users.slice(0, 3).length} users`);
}

/**
 * Main seed function
 */
async function main() {
    console.log('🌱 Starting AI data seeding...\n');

    try {
        await seedAIInsights();
        await seedPredictions();
        await seedAnomalyAlerts();
        await seedCopilotSessions();
        await seedAIMetrics();
        await seedUserAIPreferences();

        console.log('\n✨ AI data seeding completed successfully!');
    } catch (error) {
        console.error('❌ Error during seeding:', error);
        throw error;
    }
}

// Execute seed
main()
    .catch((e) => {
        console.error(e);
        process.exit(1);
    })
    .finally(async () => {
        await prisma.$disconnect();
    });
