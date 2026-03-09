/**
 * CXO Integration Tests
 *
 * Comprehensive integration test suite for CXO-level components:
 * - OrganizationOverview
 * - ExecutiveReporting
 * - CompanyKPIs
 *
 * @package @ghatana/software-org-web
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {
    OrganizationOverview,
    type CompanyMetrics,
    type DepartmentHealth,
    type StrategicObjective,
    type FinancialSummary,
} from './OrganizationOverview';

import {
    ExecutiveReporting,
    type QuarterlyPerformance,
    type BoardReportItem,
    type ExecutiveKPI,
    type RiskIndicator,
} from './ExecutiveReporting';

import {
    CompanyKPIs,
    type CompanyKPI,
    type StrategicGoal,
    type GrowthMetric,
    type FinancialHealth,
} from './CompanyKPIs';

// ============================================================================
// OrganizationOverview Tests
// ============================================================================

describe('OrganizationOverview', () => {
    const mockCompanyMetrics: CompanyMetrics = {
        totalHeadcount: 650,
        departmentCount: 8,
        revenue: 50000000,
        growthRate: 25,
        profitMargin: 22,
        customerSatisfaction: 4.3,
        employeeSatisfaction: 4.2,
        marketShare: 15,
    };

    const mockDepartments: DepartmentHealth[] = [
        {
            id: 'dept-eng',
            name: 'Engineering',
            headcount: 220,
            health: 'excellent',
            velocity: 92,
            budgetStatus: 'on-track',
            budgetUtilization: 85,
            openPositions: 15,
            keyMetrics: { productivity: 88, quality: 95, satisfaction: 4.5, attrition: 8 },
        },
        {
            id: 'dept-product',
            name: 'Product',
            headcount: 65,
            health: 'good',
            velocity: 88,
            budgetStatus: 'on-track',
            budgetUtilization: 80,
            openPositions: 5,
            keyMetrics: { productivity: 85, quality: 90, satisfaction: 4.3, attrition: 10 },
        },
        {
            id: 'dept-marketing',
            name: 'Marketing',
            headcount: 45,
            health: 'fair',
            velocity: 78,
            budgetStatus: 'over',
            budgetUtilization: 105,
            openPositions: 3,
            keyMetrics: { productivity: 75, quality: 82, satisfaction: 3.9, attrition: 15 },
        },
    ];

    const mockObjectives: StrategicObjective[] = [
        {
            id: 'obj-1',
            title: 'Achieve $100M ARR by Q4',
            description: 'Scale revenue to $100M annual recurring revenue',
            category: 'growth',
            status: 'on-track',
            progress: 75,
            targetDate: '2025-12-31',
            owner: 'CEO',
            impact: 'high',
            departments: ['Sales', 'Marketing', 'Product'],
        },
        {
            id: 'obj-2',
            title: 'Launch AI-Powered Platform',
            description: 'Release next-generation AI features',
            category: 'innovation',
            status: 'at-risk',
            progress: 60,
            targetDate: '2025-09-30',
            owner: 'CTO',
            impact: 'high',
            departments: ['Engineering', 'Product'],
        },
    ];

    const mockFinancialSummary: FinancialSummary = {
        quarterlyRevenue: 12500000,
        quarterlyExpenses: 9500000,
        quarterlyProfit: 3000000,
        yearlyRevenue: 48000000,
        yearlyExpenses: 37000000,
        yearlyProfit: 11000000,
        cashReserves: 25000000,
        burnRate: 2000000,
        runway: 12,
    };

    let mockCallbacks: {
        onDepartmentClick: ReturnType<typeof vi.fn>;
        onObjectiveClick: ReturnType<typeof vi.fn>;
        onViewFinancials: ReturnType<typeof vi.fn>;
        onExportReport: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        mockCallbacks = {
            onDepartmentClick: vi.fn(),
            onObjectiveClick: vi.fn(),
            onViewFinancials: vi.fn(),
            onExportReport: vi.fn(),
        };
    });

    describe('Rendering & KPIs', () => {
        it('renders organization overview dashboard with header', () => {
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                />
            );

            expect(screen.getByText('Organization Overview')).toBeInTheDocument();
            expect(screen.getByText('Company-wide metrics and strategic dashboard')).toBeInTheDocument();
        });

        it('displays correct company KPI values', () => {
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                />
            );

            expect(screen.getByText('Total Headcount')).toBeInTheDocument();
            expect(screen.getByText('650')).toBeInTheDocument();
            expect(screen.getByText('Annual Revenue')).toBeInTheDocument();
            expect(screen.getByText('$50.0M')).toBeInTheDocument();
            expect(screen.getByText('22%')).toBeInTheDocument(); // Profit margin
            expect(screen.getByText('4.2')).toBeInTheDocument(); // Employee satisfaction
        });

        it('renders all department cards', () => {
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                />
            );

            expect(screen.getByText('Engineering')).toBeInTheDocument();
            expect(screen.getByText('Product')).toBeInTheDocument();
            expect(screen.getByText('Marketing')).toBeInTheDocument();
        });
    });

    describe('Department Display', () => {
        it('displays department health chips correctly', () => {
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                />
            );

            expect(screen.getByText('excellent')).toBeInTheDocument();
            expect(screen.getByText('good')).toBeInTheDocument();
            expect(screen.getByText('fair')).toBeInTheDocument();
        });

        it('displays department velocity and budget metrics', () => {
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                />
            );

            expect(screen.getByText('92%')).toBeInTheDocument(); // Engineering velocity
            expect(screen.getByText('85%')).toBeInTheDocument(); // Engineering budget
        });

        it('shows warning alert for departments with fair or poor health', () => {
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                />
            );

            expect(screen.getByText(/need monitoring/i)).toBeInTheDocument();
        });
    });

    describe('Strategic Objectives', () => {
        it('switches to objectives tab and renders objectives', async () => {
            const user = userEvent.setup();
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                />
            );

            const objectivesTab = screen.getByRole('tab', { name: /Objectives/i });
            await user.click(objectivesTab);

            expect(screen.getByText('Achieve $100M ARR by Q4')).toBeInTheDocument();
            expect(screen.getByText('Launch AI-Powered Platform')).toBeInTheDocument();
        });

        it('displays objective status and progress', async () => {
            const user = userEvent.setup();
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                />
            );

            const objectivesTab = screen.getByRole('tab', { name: /Objectives/i });
            await user.click(objectivesTab);

            expect(screen.getByText('on-track')).toBeInTheDocument();
            expect(screen.getByText('at-risk')).toBeInTheDocument();
            expect(screen.getByText('75%')).toBeInTheDocument(); // Progress
        });
    });

    describe('Financials Tab', () => {
        it('switches to financials tab and displays financial data', async () => {
            const user = userEvent.setup();
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                />
            );

            const financialsTab = screen.getByRole('tab', { name: /Financials/i });
            await user.click(financialsTab);

            expect(screen.getByText('Financial Summary')).toBeInTheDocument();
            expect(screen.getByText('$12.5M')).toBeInTheDocument(); // Quarterly revenue
            expect(screen.getByText('$48.0M')).toBeInTheDocument(); // Yearly revenue
        });

        it('displays cash runway with warning if below 12 months', async () => {
            const user = userEvent.setup();
            const lowRunwayFinancials = { ...mockFinancialSummary, runway: 10 };
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={lowRunwayFinancials}
                />
            );

            const financialsTab = screen.getByRole('tab', { name: /Financials/i });
            await user.click(financialsTab);

            expect(screen.getByText(/Cash runway below 12 months/i)).toBeInTheDocument();
        });
    });

    describe('User Interactions', () => {
        it('calls onDepartmentClick when department card is clicked', async () => {
            const user = userEvent.setup();
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                    {...mockCallbacks}
                />
            );

            const engCard = screen.getByText('Engineering').closest('.cursor-pointer');
            if (engCard) await user.click(engCard);

            expect(mockCallbacks.onDepartmentClick).toHaveBeenCalledWith('dept-eng');
        });

        it('calls onObjectiveClick when objective is clicked', async () => {
            const user = userEvent.setup();
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                    {...mockCallbacks}
                />
            );

            const objectivesTab = screen.getByRole('tab', { name: /Objectives/i });
            await user.click(objectivesTab);

            const objCard = screen.getByText('Achieve $100M ARR by Q4').closest('.cursor-pointer');
            if (objCard) await user.click(objCard);

            expect(mockCallbacks.onObjectiveClick).toHaveBeenCalledWith('obj-1');
        });

        it('calls onExportReport when Export Report button is clicked', async () => {
            const user = userEvent.setup();
            render(
                <OrganizationOverview
                    companyMetrics={mockCompanyMetrics}
                    departments={mockDepartments}
                    objectives={mockObjectives}
                    financialSummary={mockFinancialSummary}
                    {...mockCallbacks}
                />
            );

            const exportBtn = screen.getByRole('button', { name: /Export Report/i });
            await user.click(exportBtn);

            expect(mockCallbacks.onExportReport).toHaveBeenCalled();
        });
    });
});

// ============================================================================
// ExecutiveReporting Tests
// ============================================================================

describe('ExecutiveReporting', () => {
    const mockQuarterlyPerformance: QuarterlyPerformance[] = [
        { quarter: 'Q4 2024', revenue: 11000000, expenses: 8500000, profit: 2500000, margin: 23, headcount: 580, customerCount: 1200, churnRate: 4.2 },
        { quarter: 'Q1 2025', revenue: 12500000, expenses: 9500000, profit: 3000000, margin: 24, headcount: 650, customerCount: 1350, churnRate: 3.8 },
    ];

    const mockBoardReports: BoardReportItem[] = [
        {
            id: 'report-1',
            title: 'Q1 Financial Performance',
            category: 'financial',
            status: 'positive',
            summary: 'Revenue exceeded target by 12%',
            metrics: [
                { label: 'Revenue', value: '$12.5M', trend: 'up' },
                { label: 'Profit', value: '$3.0M', trend: 'up' },
            ],
        },
    ];

    const mockExecutiveKPIs: ExecutiveKPI[] = [
        { id: 'kpi-1', name: 'Annual Recurring Revenue', value: '$48M', target: '$100M', progress: 48, trend: 'up', trendValue: '+25%', status: 'on-track' },
        { id: 'kpi-2', name: 'Net Revenue Retention', value: '125%', target: '120%', progress: 104, trend: 'up', trendValue: '+5%', status: 'exceeding' },
    ];

    const mockRisks: RiskIndicator[] = [
        {
            id: 'risk-1',
            title: 'Enterprise Sales Pipeline Slowing',
            severity: 'high',
            category: 'market',
            impact: 'Q3 revenue target at risk',
            mitigation: 'Increased marketing spend',
            owner: 'CRO',
        },
    ];

    let mockCallbacks: {
        onExportBoardReport: ReturnType<typeof vi.fn>;
        onExportQuarterlyReport: ReturnType<typeof vi.fn>;
        onRiskClick: ReturnType<typeof vi.fn>;
        onKPIClick: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        mockCallbacks = {
            onExportBoardReport: vi.fn(),
            onExportQuarterlyReport: vi.fn(),
            onRiskClick: vi.fn(),
            onKPIClick: vi.fn(),
        };
    });

    describe('Rendering & KPIs', () => {
        it('renders executive reporting dashboard', () => {
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            expect(screen.getByText('Executive Reporting')).toBeInTheDocument();
            expect(screen.getByText('Board presentations and performance summaries')).toBeInTheDocument();
        });

        it('displays critical risk alert when high severity risks exist', () => {
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            expect(screen.getByText(/require immediate attention/i)).toBeInTheDocument();
        });
    });

    describe('Quarterly Performance', () => {
        it('renders quarterly performance table', () => {
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            expect(screen.getByText('Q4 2024')).toBeInTheDocument();
            expect(screen.getByText('Q1 2025')).toBeInTheDocument();
            expect(screen.getByText('$11.0M')).toBeInTheDocument(); // Q4 revenue
            expect(screen.getByText('$12.5M')).toBeInTheDocument(); // Q1 revenue
        });

        it('highlights current quarter', () => {
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            expect(screen.getByText('Current')).toBeInTheDocument();
        });
    });

    describe('Board Reports Tab', () => {
        it('switches to board reports tab', async () => {
            const user = userEvent.setup();
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            const boardTab = screen.getByRole('tab', { name: /Board Reports/i });
            await user.click(boardTab);

            expect(screen.getByText('Q1 Financial Performance')).toBeInTheDocument();
            expect(screen.getByText('Revenue exceeded target by 12%')).toBeInTheDocument();
        });

        it('displays board report metrics with trends', async () => {
            const user = userEvent.setup();
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            const boardTab = screen.getByRole('tab', { name: /Board Reports/i });
            await user.click(boardTab);

            expect(screen.getByText('Revenue')).toBeInTheDocument();
            expect(screen.getByText('$12.5M')).toBeInTheDocument();
        });
    });

    describe('Executive KPIs Tab', () => {
        it('switches to KPIs tab and displays executive KPIs', async () => {
            const user = userEvent.setup();
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            const kpisTab = screen.getByRole('tab', { name: /Executive KPIs/i });
            await user.click(kpisTab);

            expect(screen.getByText('Annual Recurring Revenue')).toBeInTheDocument();
            expect(screen.getByText('$48M')).toBeInTheDocument();
        });

        it('displays KPI progress bars', async () => {
            const user = userEvent.setup();
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            const kpisTab = screen.getByRole('tab', { name: /Executive KPIs/i });
            await user.click(kpisTab);

            expect(screen.getByText('48%')).toBeInTheDocument(); // ARR progress
            expect(screen.getByText('104%')).toBeInTheDocument(); // NRR progress
        });
    });

    describe('Risks Tab', () => {
        it('switches to risks tab and displays risk indicators', async () => {
            const user = userEvent.setup();
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            const risksTab = screen.getByRole('tab', { name: /Risks/i });
            await user.click(risksTab);

            expect(screen.getByText('Enterprise Sales Pipeline Slowing')).toBeInTheDocument();
            expect(screen.getByText('high')).toBeInTheDocument();
        });

        it('displays risk impact and mitigation', async () => {
            const user = userEvent.setup();
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                />
            );

            const risksTab = screen.getByRole('tab', { name: /Risks/i });
            await user.click(risksTab);

            expect(screen.getByText(/Q3 revenue target at risk/i)).toBeInTheDocument();
            expect(screen.getByText(/Increased marketing spend/i)).toBeInTheDocument();
        });
    });

    describe('User Interactions', () => {
        it('calls onKPIClick when KPI card is clicked', async () => {
            const user = userEvent.setup();
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                    {...mockCallbacks}
                />
            );

            const kpisTab = screen.getByRole('tab', { name: /Executive KPIs/i });
            await user.click(kpisTab);

            const kpiCard = screen.getByText('Annual Recurring Revenue').closest('.cursor-pointer');
            if (kpiCard) await user.click(kpiCard);

            expect(mockCallbacks.onKPIClick).toHaveBeenCalledWith('kpi-1');
        });

        it('calls onRiskClick when risk is clicked', async () => {
            const user = userEvent.setup();
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                    {...mockCallbacks}
                />
            );

            const risksTab = screen.getByRole('tab', { name: /Risks/i });
            await user.click(risksTab);

            const riskCard = screen.getByText('Enterprise Sales Pipeline Slowing').closest('.cursor-pointer');
            if (riskCard) await user.click(riskCard);

            expect(mockCallbacks.onRiskClick).toHaveBeenCalledWith('risk-1');
        });

        it('calls onExportBoardReport when export button is clicked', async () => {
            const user = userEvent.setup();
            render(
                <ExecutiveReporting
                    quarterlyPerformance={mockQuarterlyPerformance}
                    boardReports={mockBoardReports}
                    executiveKPIs={mockExecutiveKPIs}
                    risks={mockRisks}
                    currentQuarter="Q1 2025"
                    {...mockCallbacks}
                />
            );

            const exportBtn = screen.getByRole('button', { name: /Export Board Report/i });
            await user.click(exportBtn);

            expect(mockCallbacks.onExportBoardReport).toHaveBeenCalled();
        });
    });
});

// ============================================================================
// CompanyKPIs Tests
// ============================================================================

describe('CompanyKPIs', () => {
    const mockKPIs: CompanyKPI[] = [
        {
            id: 'kpi-1',
            category: 'financial',
            name: 'Annual Recurring Revenue',
            value: '$48M',
            target: '$100M',
            progress: 48,
            trend: 'up',
            trendValue: '+25%',
            period: 'yearly',
            status: 'on-track',
            description: 'Total contracted revenue',
        },
        {
            id: 'kpi-2',
            category: 'growth',
            name: 'Customer Count',
            value: '1,350',
            target: '2,000',
            progress: 68,
            trend: 'up',
            trendValue: '+15%',
            period: 'monthly',
            status: 'on-track',
            description: 'Total active customers',
        },
    ];

    const mockGoals: StrategicGoal[] = [
        {
            id: 'goal-1',
            title: 'Achieve $100M ARR',
            category: 'revenue',
            targetDate: '2025-12-31',
            progress: 48,
            status: 'on-track',
            owner: 'CEO',
            milestones: [
                { id: 'm1', title: 'Reach $50M ARR', completed: true, targetDate: '2025-06-30' },
                { id: 'm2', title: 'Expand to EMEA', completed: false, targetDate: '2025-09-30' },
            ],
        },
    ];

    const mockGrowthMetrics: GrowthMetric[] = [
        { id: 'growth-1', name: 'Monthly Recurring Revenue', current: 4000000, previous: 3500000, growthRate: 14, target: 8333333, unit: 'USD', status: 'on-track' },
    ];

    const mockFinancialHealth: FinancialHealth[] = [
        {
            id: 'health-1',
            metric: 'Cash Runway',
            value: '18 months',
            benchmark: '12+ months',
            status: 'healthy',
            description: 'Operational cash reserves',
        },
    ];

    let mockCallbacks: {
        onKPIClick: ReturnType<typeof vi.fn>;
        onGoalClick: ReturnType<typeof vi.fn>;
        onExportDashboard: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        mockCallbacks = {
            onKPIClick: vi.fn(),
            onGoalClick: vi.fn(),
            onExportDashboard: vi.fn(),
        };
    });

    describe('Rendering & Summary', () => {
        it('renders company KPIs dashboard', () => {
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            expect(screen.getByText('Company KPIs')).toBeInTheDocument();
            expect(screen.getByText('Top-level metrics and strategic goals')).toBeInTheDocument();
        });

        it('displays summary KPI counts', () => {
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            expect(screen.getByText('Critical KPIs')).toBeInTheDocument();
            expect(screen.getByText('On Track')).toBeInTheDocument();
        });
    });

    describe('KPIs Tab', () => {
        it('renders all KPIs with correct data', () => {
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            expect(screen.getByText('Annual Recurring Revenue')).toBeInTheDocument();
            expect(screen.getByText('$48M')).toBeInTheDocument();
            expect(screen.getByText('Customer Count')).toBeInTheDocument();
        });

        it('filters KPIs by category', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            const financialChip = screen.getByText('Financial');
            await user.click(financialChip);

            expect(screen.getByText('Annual Recurring Revenue')).toBeInTheDocument();
        });

        it('displays KPI progress bars', () => {
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            expect(screen.getByText('48%')).toBeInTheDocument(); // ARR progress
        });
    });

    describe('Goals Tab', () => {
        it('switches to goals tab and displays strategic goals', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            const goalsTab = screen.getByRole('tab', { name: /Goals/i });
            await user.click(goalsTab);

            expect(screen.getByText('Achieve $100M ARR')).toBeInTheDocument();
        });

        it('displays goal milestones', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            const goalsTab = screen.getByRole('tab', { name: /Goals/i });
            await user.click(goalsTab);

            expect(screen.getByText('Reach $50M ARR')).toBeInTheDocument();
            expect(screen.getByText('Expand to EMEA')).toBeInTheDocument();
        });
    });

    describe('Growth Tab', () => {
        it('switches to growth tab and displays growth metrics', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            const growthTab = screen.getByRole('tab', { name: /Growth/i });
            await user.click(growthTab);

            expect(screen.getByText('Monthly Recurring Revenue')).toBeInTheDocument();
        });

        it('displays growth rate and previous value', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            const growthTab = screen.getByRole('tab', { name: /Growth/i });
            await user.click(growthTab);

            expect(screen.getByText('+14%')).toBeInTheDocument(); // Growth rate
        });
    });

    describe('Health Tab', () => {
        it('switches to health tab and displays financial health', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            const healthTab = screen.getByRole('tab', { name: /Health/i });
            await user.click(healthTab);

            expect(screen.getByText('Cash Runway')).toBeInTheDocument();
            expect(screen.getByText('18 months')).toBeInTheDocument();
        });

        it('displays benchmark comparison', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} />);

            const healthTab = screen.getByRole('tab', { name: /Health/i });
            await user.click(healthTab);

            expect(screen.getByText('12+ months')).toBeInTheDocument(); // Benchmark
        });
    });

    describe('User Interactions', () => {
        it('calls onKPIClick when KPI card is clicked', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} {...mockCallbacks} />);

            const kpiCard = screen.getByText('Annual Recurring Revenue').closest('.cursor-pointer');
            if (kpiCard) await user.click(kpiCard);

            expect(mockCallbacks.onKPIClick).toHaveBeenCalledWith('kpi-1');
        });

        it('calls onGoalClick when goal is clicked', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} {...mockCallbacks} />);

            const goalsTab = screen.getByRole('tab', { name: /Goals/i });
            await user.click(goalsTab);

            const goalCard = screen.getByText('Achieve $100M ARR').closest('.cursor-pointer');
            if (goalCard) await user.click(goalCard);

            expect(mockCallbacks.onGoalClick).toHaveBeenCalledWith('goal-1');
        });

        it('calls onExportDashboard when export button is clicked', async () => {
            const user = userEvent.setup();
            render(<CompanyKPIs kpis={mockKPIs} goals={mockGoals} growthMetrics={mockGrowthMetrics} financialHealth={mockFinancialHealth} {...mockCallbacks} />);

            const exportBtn = screen.getByRole('button', { name: /Export Dashboard/i });
            await user.click(exportBtn);

            expect(mockCallbacks.onExportDashboard).toHaveBeenCalled();
        });
    });
});
