import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { kpiDashboardAtom } from '../state/orgState';

export interface KpiDashboard {
    delivery: {
        featureCount: number;
        cycleTime: number;
        leadTime: number;
        deploymentsLastQuarter: number;
    };
    quality: {
        coverage: number;
        bugsReported: number;
        passRate: number;
        testSuitesRun: number;
    };
    operations: {
        deploymentsPerMonth: number;
        mttr: number;
        healthScore: number;
        incidentsResolved: number;
    };
    team: {
        satisfaction: number;
        employeesOnboarded: number;
        trainingHours: number;
        attritionRate: number;
    };
    business: {
        mrr: number;
        nps: number;
        activeCustomers: number;
        churnRate: number;
    };
    timestamp: string;
}

// Note: Vite requires static access to import.meta.env properties for SSR compatibility
const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true' || import.meta.env.VITE_MOCK_API === 'true';

const mockDashboard: KpiDashboard = {
    delivery: {
        featureCount: 42,
        cycleTime: 3.2,
        leadTime: 4.5,
        deploymentsLastQuarter: 156,
    },
    quality: {
        coverage: 87,
        bugsReported: 12,
        passRate: 98,
        testSuitesRun: 320,
    },
    operations: {
        deploymentsPerMonth: 24,
        mttr: 12,
        healthScore: 94,
        incidentsResolved: 18,
    },
    team: {
        satisfaction: 4.6,
        employeesOnboarded: 8,
        trainingHours: 120,
        attritionRate: 3,
    },
    business: {
        mrr: 120_000,
        nps: 64,
        activeCustomers: 320,
        churnRate: 2.1,
    },
    timestamp: new Date().toISOString(),
};

const mockDepartmentKpis: Record<string, number> = {
    deployments: 42,
    mttr: 10,
    leadTime: 3.1,
    changeFailureRate: 2.8,
};

/**
 * Hook for fetching organization KPI dashboard data.
 * 
 * Returns aggregated KPIs across all departments:
 * - Delivery metrics (lead time, cycle time, deployment frequency)
 * - Quality metrics (coverage, pass rate, bugs)
 * - Operations metrics (deployments, MTTR, health)
 * - Team metrics (satisfaction, onboarding, training, attrition)
 * - Business metrics (MRR, NPS, customer churn)
 * 
 * Polling interval: 30 seconds for near real-time updates
 */
export function useKpis() {
    const [, setDashboard] = useAtom(kpiDashboardAtom);

    const { data: kpis, isLoading, error } = useQuery<KpiDashboard>({
        queryKey: ['kpi-dashboard'],
        queryFn: async () => {
            if (USE_MOCKS) {
                setDashboard(mockDashboard);
                return mockDashboard;
            }

            const response = await fetch('/api/v1/kpis/dashboard', {
                headers: {
                    'X-Tenant-Id': localStorage.getItem('tenantId') || 'default',
                    'Content-Type': 'application/json',
                },
            });
            if (!response.ok) throw new Error('Failed to fetch KPIs');
            const data = await response.json();
            setDashboard(data);
            return data;
        },
        staleTime: 30000, // 30 seconds
        refetchInterval: 30000, // Poll every 30 seconds
        retry: 3,
    });

    return { kpis, isLoading, error };
}

/**
 * Hook for fetching single-department KPIs.
 */
export function useDepartmentKpis(department: string) {
    const { data: kpis, isLoading, error } = useQuery<Record<string, number>>({
        queryKey: ['kpi-department', department],
        queryFn: async () => {
            if (USE_MOCKS) {
                return mockDepartmentKpis;
            }

            const response = await fetch(`/api/v1/kpis/${encodeURIComponent(department)}`, {
                headers: {
                    'X-Tenant-Id': localStorage.getItem('tenantId') || 'default',
                },
            });
            if (!response.ok) throw new Error(`Failed to fetch KPIs for ${department}`);
            return response.json();
        },
        staleTime: 30000,
        refetchInterval: 30000,
    });

    return { kpis, isLoading, error };
}
