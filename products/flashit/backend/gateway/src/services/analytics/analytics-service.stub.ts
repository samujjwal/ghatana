import { randomUUID } from 'crypto';

type DashboardData = {
    dailyData: any[];
    insights: any[];
    sphereActivity: any[];
    trends: { productivity: 'up' | 'down' | 'stable'; emotion: 'up' | 'down' | 'stable' };
    summary: {
        totalMoments: number;
        avgProductivity: number;
        totalSearches: number;
        activeDays: number;
    };
};

export class AnalyticsService {
    static async trackActivity(_userId: string, _activity: string, _metadata?: Record<string, any>) {
        return { tracked: true, id: randomUUID() };
    }

    static async getMetrics(_metricName: string, _since?: string, _sphereId?: string) {
        return [];
    }

    static async getReportStatus(_jobId: string) {
        return { status: 'completed', url: null };
    }

    static async generateReport(_params: any) {
        return { jobId: randomUUID() };
    }
}

export class AnalyticsAggregator {
    static async getUserDashboardData(_userId: string): Promise<DashboardData> {
        return {
            dailyData: [],
            insights: [],
            sphereActivity: [],
            trends: { productivity: 'stable', emotion: 'stable' },
            summary: { totalMoments: 0, avgProductivity: 0, totalSearches: 0, activeDays: 0 },
        };
    }
}

export class InsightsGenerator {
    static async generateInsights(_userId: string) {
        return [];
    }

    static async generateMoodPatternInsights(_userId: string) {
        return null;
    }

    static async generateProductivityInsights(_userId: string) {
        return [];
    }
}

export class MetricsCollector {
    static async recordMetric(_name: string, _value: number, _meta?: Record<string, any>) {
        return;
    }
}
