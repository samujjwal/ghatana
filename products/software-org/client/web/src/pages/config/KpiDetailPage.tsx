/**
 * KPI Detail Page
 *
 * Detailed view of a KPI configuration with targets, measurements, and trends.
 *
 * @doc.type page
 * @doc.purpose KPI detail view
 * @doc.layer product
 */

import { useParams, useNavigate } from 'react-router';
import { BarChart3, TrendingUp, TrendingDown, Target, AlertCircle } from 'lucide-react';
import { EntityDetailPage, type EntitySection, type RelatedEntity } from '@/shared/components/EntityDetailPage';
import { useKpis, useConfigDepartments } from '@/hooks/useConfig';
import { components, typography, cn } from '@/lib/theme';

// Mock KPI data with complete information
interface MockKpi {
    id: string;
    name: string;
    description: string;
    department: string;
    category: string;
    target: string;
    measurement: string;
    unit: string;
    frequency: string;
    currentValue: number;
    targetValue: number;
    trend: 'up' | 'down' | 'stable';
    trendPercentage: number;
    status: 'on-track' | 'at-risk' | 'off-track';
    thresholds: {
        green: string;
        yellow: string;
        red: string;
    };
    history: { date: string; value: number }[];
    owner: string;
    createdAt: string;
    updatedAt: string;
}

const mockKpis: MockKpi[] = [
    {
        id: 'kpi-deployment-frequency',
        name: 'Deployment Frequency',
        description: 'Number of deployments to production per week',
        department: 'engineering',
        category: 'DORA Metrics',
        target: '> 10 per week',
        measurement: 'Count of production deployments',
        unit: 'deployments/week',
        frequency: 'Weekly',
        currentValue: 12,
        targetValue: 10,
        trend: 'up',
        trendPercentage: 15,
        status: 'on-track',
        thresholds: {
            green: '≥ 10',
            yellow: '5-9',
            red: '< 5',
        },
        history: [
            { date: '2024-01-08', value: 11 },
            { date: '2024-01-15', value: 12 },
            { date: '2024-01-22', value: 10 },
            { date: '2024-01-29', value: 12 },
        ],
        owner: 'Engineering Manager',
        createdAt: '2023-01-01T00:00:00Z',
        updatedAt: '2024-01-29T00:00:00Z',
    },
    {
        id: 'kpi-lead-time',
        name: 'Lead Time for Changes',
        description: 'Time from code commit to production deployment',
        department: 'engineering',
        category: 'DORA Metrics',
        target: '< 1 day',
        measurement: 'Median time from commit to deploy',
        unit: 'hours',
        frequency: 'Weekly',
        currentValue: 18,
        targetValue: 24,
        trend: 'down',
        trendPercentage: 8,
        status: 'on-track',
        thresholds: {
            green: '< 24 hours',
            yellow: '24-72 hours',
            red: '> 72 hours',
        },
        history: [
            { date: '2024-01-08', value: 22 },
            { date: '2024-01-15', value: 20 },
            { date: '2024-01-22', value: 19 },
            { date: '2024-01-29', value: 18 },
        ],
        owner: 'Engineering Manager',
        createdAt: '2023-01-01T00:00:00Z',
        updatedAt: '2024-01-29T00:00:00Z',
    },
    {
        id: 'kpi-mttr',
        name: 'Mean Time to Recovery',
        description: 'Average time to restore service after an incident',
        department: 'devops',
        category: 'DORA Metrics',
        target: '< 1 hour',
        measurement: 'Average incident resolution time',
        unit: 'minutes',
        frequency: 'Weekly',
        currentValue: 45,
        targetValue: 60,
        trend: 'down',
        trendPercentage: 12,
        status: 'on-track',
        thresholds: {
            green: '< 60 minutes',
            yellow: '60-120 minutes',
            red: '> 120 minutes',
        },
        history: [
            { date: '2024-01-08', value: 55 },
            { date: '2024-01-15', value: 52 },
            { date: '2024-01-22', value: 48 },
            { date: '2024-01-29', value: 45 },
        ],
        owner: 'SRE Lead',
        createdAt: '2023-01-01T00:00:00Z',
        updatedAt: '2024-01-29T00:00:00Z',
    },
    {
        id: 'kpi-change-failure-rate',
        name: 'Change Failure Rate',
        description: 'Percentage of deployments causing failures in production',
        department: 'engineering',
        category: 'DORA Metrics',
        target: '< 5%',
        measurement: 'Failed deployments / Total deployments',
        unit: '%',
        frequency: 'Weekly',
        currentValue: 3.2,
        targetValue: 5,
        trend: 'stable',
        trendPercentage: 0,
        status: 'on-track',
        thresholds: {
            green: '< 5%',
            yellow: '5-10%',
            red: '> 10%',
        },
        history: [
            { date: '2024-01-08', value: 3.5 },
            { date: '2024-01-15', value: 3.1 },
            { date: '2024-01-22', value: 3.4 },
            { date: '2024-01-29', value: 3.2 },
        ],
        owner: 'Engineering Manager',
        createdAt: '2023-01-01T00:00:00Z',
        updatedAt: '2024-01-29T00:00:00Z',
    },
];

function KpiTrendChart({ history, unit }: { history: MockKpi['history']; unit: string }) {
    const maxValue = Math.max(...history.map(h => h.value));
    const minValue = Math.min(...history.map(h => h.value));
    const range = maxValue - minValue || 1;

    return (
        <div className="space-y-4">
            <div className="flex items-end justify-between h-32 gap-2">
                {history.map((point, i) => {
                    const height = ((point.value - minValue) / range) * 100;
                    return (
                        <div key={i} className="flex-1 flex flex-col items-center gap-1">
                            <span className="text-xs font-medium text-gray-600 dark:text-gray-400">
                                {point.value}{unit === '%' ? '%' : ''}
                            </span>
                            <div
                                className="w-full bg-blue-500 dark:bg-blue-600 rounded-t"
                                style={{ height: `${Math.max(height, 10)}%` }}
                            />
                            <span className="text-xs text-gray-500 dark:text-gray-500">
                                {new Date(point.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                            </span>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

function ThresholdIndicator({ thresholds, currentValue, status }: { thresholds: MockKpi['thresholds']; currentValue: number; status: string }) {
    return (
        <div className="space-y-3">
            <div className="flex items-center gap-2">
                <div className={cn(
                    'w-3 h-3 rounded-full',
                    status === 'on-track' ? 'bg-green-500' : status === 'at-risk' ? 'bg-amber-500' : 'bg-red-500'
                )} />
                <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                    Current: {currentValue}
                </span>
            </div>
            <div className="space-y-2 text-sm">
                <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full bg-green-500" />
                    <span className="text-gray-600 dark:text-gray-400">Green: {thresholds.green}</span>
                </div>
                <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full bg-amber-500" />
                    <span className="text-gray-600 dark:text-gray-400">Yellow: {thresholds.yellow}</span>
                </div>
                <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full bg-red-500" />
                    <span className="text-gray-600 dark:text-gray-400">Red: {thresholds.red}</span>
                </div>
            </div>
        </div>
    );
}

export function KpiDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const { data: kpis, isLoading } = useKpis();
    const { data: departments } = useConfigDepartments();

    // Find KPI from API or mock data
    const kpi = kpis?.find(k => k.id === id) || mockKpis.find(k => k.id === id);
    const mockData = mockKpis.find(k => k.id === id);

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (!kpi) {
        return (
            <div className="p-8 text-center">
                <AlertCircle className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <h2 className={typography.h3}>KPI Not Found</h2>
                <p className="text-gray-500 mt-2">The KPI "{id}" could not be found.</p>
                <button
                    onClick={() => navigate('/config/kpis')}
                    className={cn(components.button.primary, 'mt-4')}
                >
                    Back to KPIs
                </button>
            </div>
        );
    }

    // Build sections
    const sections: EntitySection[] = [
        {
            id: 'overview',
            title: 'Overview',
            fields: [
                { key: 'name', label: 'Name', value: kpi.name },
                { key: 'category', label: 'Category', value: mockData?.category, type: 'badge' },
                { key: 'department', label: 'Department', value: mockData?.department, type: 'link', linkTo: `/config/departments/${mockData?.department}` },
                { key: 'owner', label: 'Owner', value: mockData?.owner },
                { key: 'frequency', label: 'Measurement Frequency', value: mockData?.frequency },
            ],
        },
        {
            id: 'target',
            title: 'Target & Measurement',
            fields: [
                { key: 'target', label: 'Target', value: kpi.target },
                { key: 'measurement', label: 'Measurement Method', value: kpi.measurement },
                { key: 'unit', label: 'Unit', value: mockData?.unit },
                { key: 'currentValue', label: 'Current Value', value: mockData?.currentValue },
                { key: 'targetValue', label: 'Target Value', value: mockData?.targetValue },
            ],
        },
        {
            id: 'status',
            title: 'Current Status',
            content: mockData ? (
                <div className="grid grid-cols-2 gap-6">
                    <div>
                        <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">Trend</h4>
                        <div className="flex items-center gap-3">
                            {mockData.trend === 'up' ? (
                                <TrendingUp className={cn('w-8 h-8', mockData.status === 'on-track' ? 'text-green-500' : 'text-red-500')} />
                            ) : mockData.trend === 'down' ? (
                                <TrendingDown className={cn('w-8 h-8', mockData.status === 'on-track' ? 'text-green-500' : 'text-red-500')} />
                            ) : (
                                <Target className="w-8 h-8 text-gray-500" />
                            )}
                            <div>
                                <span className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                                    {mockData.trendPercentage > 0 ? '+' : ''}{mockData.trendPercentage}%
                                </span>
                                <span className="text-sm text-gray-500 dark:text-gray-400 ml-2">
                                    vs last period
                                </span>
                            </div>
                        </div>
                    </div>
                    <div>
                        <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">Thresholds</h4>
                        <ThresholdIndicator
                            thresholds={mockData.thresholds}
                            currentValue={mockData.currentValue}
                            status={mockData.status}
                        />
                    </div>
                </div>
            ) : null,
        },
        {
            id: 'history',
            title: 'Historical Trend',
            description: 'Performance over the last 4 weeks',
            content: mockData?.history ? (
                <KpiTrendChart history={mockData.history} unit={mockData.unit} />
            ) : (
                <p className="text-gray-500">No historical data available</p>
            ),
        },
        {
            id: 'metadata',
            title: 'Metadata',
            fields: [
                { key: 'id', label: 'KPI ID', value: kpi.id, type: 'code' },
                { key: 'createdAt', label: 'Created', value: mockData?.createdAt, type: 'date' },
                { key: 'updatedAt', label: 'Last Updated', value: mockData?.updatedAt, type: 'date' },
            ],
        },
    ];

    // Build related entities
    const relatedEntities: RelatedEntity[] = [];

    if (mockData?.department) {
        const dept = departments?.find(d => d.id === mockData.department);
        relatedEntities.push({
            id: mockData.department,
            name: dept?.name || mockData.department,
            type: 'Department',
            href: `/config/departments/${mockData.department}`,
            status: 'active',
        });
    }

    return (
        <EntityDetailPage
            entityType="KPI"
            entityId={kpi.id}
            title={kpi.name}
            description={mockData?.description}
            status={mockData?.status}
            icon={<BarChart3 className="w-6 h-6" />}
            backHref="/config/kpis"
            backLabel="Back to KPIs"
            sections={sections}
            relatedEntities={relatedEntities}
            onEdit={() => console.log('Edit KPI')}
        />
    );
}

export default KpiDetailPage;
