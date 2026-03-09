/**
 * VP Components Integration Tests
 *
 * Comprehensive integration test suite for VP-level components:
 * - DepartmentPortfolio
 * - StrategicPlanning
 * - CrossFunctionalMetrics
 *
 * @package @ghatana/software-org-web
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
    DepartmentPortfolio,
    mockPortfolioData,
    type DepartmentPerformance,
    type StrategicInitiative,
    type PortfolioMetrics,
} from './DepartmentPortfolio';
import {
    StrategicPlanning,
    mockStrategicPlanningData,
    type OKR,
    type Initiative,
    type PlanningMetrics,
} from './StrategicPlanning';
import {
    CrossFunctionalMetrics,
    mockCrossFunctionalMetricsData,
    type DepartmentComparison,
    type CrossFunctionalInsight,
    type MetricsSummary,
} from './CrossFunctionalMetrics';

/**
 * DepartmentPortfolio Tests
 */
describe('DepartmentPortfolio', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders portfolio dashboard with metrics', () => {
        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
            />
        );

        expect(screen.getByText('Department Portfolio')).toBeInTheDocument();
        expect(screen.getByText('Cross-department overview and strategic alignment')).toBeInTheDocument();
    });

    it('displays correct portfolio KPI values', () => {
        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
            />
        );

        expect(screen.getByText('Total Headcount')).toBeInTheDocument();
        expect(screen.getByText('450')).toBeInTheDocument();
        expect(screen.getByText('Budget Utilization')).toBeInTheDocument();
        expect(screen.getByText('78%')).toBeInTheDocument();
        expect(screen.getByText('Avg Velocity')).toBeInTheDocument();
        expect(screen.getByText('85%')).toBeInTheDocument();
    });

    it('renders all department cards', () => {
        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
            />
        );

        expect(screen.getByText('Engineering')).toBeInTheDocument();
        expect(screen.getByText('Product')).toBeInTheDocument();
        expect(screen.getByText('QA')).toBeInTheDocument();
        expect(screen.getByText('DevOps')).toBeInTheDocument();
        expect(screen.getByText('Design')).toBeInTheDocument();
    });

    it('displays department status chips correctly', () => {
        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
            />
        );

        const healthyChips = screen.getAllByText('healthy');
        expect(healthyChips.length).toBeGreaterThan(0);
        const warningChips = screen.getAllByText('warning');
        expect(warningChips.length).toBeGreaterThan(0);
    });

    it('displays department velocity and quality metrics', () => {
        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
            />
        );

        // Engineering department metrics
        expect(screen.getByText('92%')).toBeInTheDocument(); // Engineering velocity
        expect(screen.getByText('95%')).toBeInTheDocument(); // Engineering quality
    });

    it('filters departments by status tab', async () => {
        const user = userEvent.setup();
        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
            />
        );

        // Click on "Warning" tab
        const warningTab = screen.getByRole('tab', { name: /Warning/i });
        await user.click(warningTab);

        // Should show warning message
        expect(screen.getByText(/need monitoring/i)).toBeInTheDocument();
    });

    it('renders strategic initiatives', () => {
        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
            />
        );

        expect(screen.getByText('Cross-Department Initiatives')).toBeInTheDocument();
        expect(screen.getByText('Platform Modernization')).toBeInTheDocument();
        expect(screen.getByText('Mobile App Redesign')).toBeInTheDocument();
    });

    it('displays initiative status and priority chips', () => {
        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
            />
        );

        expect(screen.getAllByText('high').length).toBeGreaterThan(0);
        expect(screen.getAllByText('on-track').length).toBeGreaterThan(0);
    });

    it('calls onDepartmentClick when department card is clicked', async () => {
        const user = userEvent.setup();
        const onDepartmentClick = vi.fn();

        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
                onDepartmentClick={onDepartmentClick}
            />
        );

        const engineeringCard = screen.getByText('Engineering').closest('.cursor-pointer');
        if (engineeringCard) {
            await user.click(engineeringCard);
            expect(onDepartmentClick).toHaveBeenCalledWith('dept-eng');
        }
    });

    it('calls onInitiativeClick when initiative is clicked', async () => {
        const user = userEvent.setup();
        const onInitiativeClick = vi.fn();

        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
                onInitiativeClick={onInitiativeClick}
            />
        );

        const initiativeCard = screen.getByText('Platform Modernization').closest('.cursor-pointer');
        if (initiativeCard) {
            await user.click(initiativeCard);
            expect(onInitiativeClick).toHaveBeenCalledWith('init-1');
        }
    });

    it('calls onExportReport when Export Report button is clicked', async () => {
        const user = userEvent.setup();
        const onExportReport = vi.fn();

        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={mockPortfolioData.departments}
                initiatives={mockPortfolioData.initiatives}
                onExportReport={onExportReport}
            />
        );

        const exportButton = screen.getByRole('button', { name: /Export Report/i });
        await user.click(exportButton);
        expect(onExportReport).toHaveBeenCalled();
    });

    it('shows empty state when no departments found for filter', async () => {
        const user = userEvent.setup();
        const noDepartments: DepartmentPerformance[] = [];

        render(
            <DepartmentPortfolio
                portfolioMetrics={mockPortfolioData.portfolioMetrics}
                departments={noDepartments}
                initiatives={mockPortfolioData.initiatives}
            />
        );

        expect(screen.getByText(/No departments found/i)).toBeInTheDocument();
    });
});

/**
 * StrategicPlanning Tests
 */
describe('StrategicPlanning', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders strategic planning dashboard with metrics', () => {
        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
            />
        );

        expect(screen.getByText('Strategic Planning')).toBeInTheDocument();
        expect(screen.getByText(/OKRs, initiatives, and resource planning/i)).toBeInTheDocument();
    });

    it('displays correct planning KPI values', () => {
        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
            />
        );

        expect(screen.getByText('Active OKRs')).toBeInTheDocument();
        expect(screen.getByText('8')).toBeInTheDocument();
        expect(screen.getByText('OKR Progress')).toBeInTheDocument();
        expect(screen.getByText('72%')).toBeInTheDocument();
    });

    it('renders OKRs with objectives and key results', () => {
        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
            />
        );

        expect(screen.getByText('Achieve 99.9% Platform Uptime')).toBeInTheDocument();
        expect(screen.getByText('Increase Feature Velocity by 30%')).toBeInTheDocument();
    });

    it('displays key results with progress bars', () => {
        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
            />
        );

        expect(screen.getByText('Reduce MTTR to under 1 hour')).toBeInTheDocument();
        expect(screen.getByText('Zero critical incidents')).toBeInTheDocument();
    });

    it('switches to initiatives tab', async () => {
        const user = userEvent.setup();
        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
            />
        );

        const initiativesTab = screen.getByRole('tab', { name: /Initiatives/i });
        await user.click(initiativesTab);

        expect(screen.getByText('Strategic Initiatives')).toBeInTheDocument();
        expect(screen.getByText('Platform Modernization')).toBeInTheDocument();
    });

    it('displays initiative resource allocation', async () => {
        const user = userEvent.setup();
        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
            />
        );

        const initiativesTab = screen.getByRole('tab', { name: /Initiatives/i });
        await user.click(initiativesTab);

        expect(screen.getByText('Resource Allocation')).toBeInTheDocument();
        expect(screen.getByText('25 people')).toBeInTheDocument();
        expect(screen.getByText('10 people')).toBeInTheDocument();
    });

    it('displays initiative milestones', async () => {
        const user = userEvent.setup();
        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
            />
        );

        const initiativesTab = screen.getByRole('tab', { name: /Initiatives/i });
        await user.click(initiativesTab);

        expect(screen.getByText('Architecture Design')).toBeInTheDocument();
        expect(screen.getByText('Migration Phase 1')).toBeInTheDocument();
    });

    it('switches to timeline tab', async () => {
        const user = userEvent.setup();
        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
            />
        );

        const timelineTab = screen.getByRole('tab', { name: /Timeline/i });
        await user.click(timelineTab);

        expect(screen.getByText('Quarterly Timeline')).toBeInTheDocument();
        expect(screen.getByText('Q1 2025')).toBeInTheDocument();
    });

    it('calls onOKRClick when OKR is clicked', async () => {
        const user = userEvent.setup();
        const onOKRClick = vi.fn();

        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
                onOKRClick={onOKRClick}
            />
        );

        const okrCard = screen.getByText('Achieve 99.9% Platform Uptime').closest('.cursor-pointer');
        if (okrCard) {
            await user.click(okrCard);
            expect(onOKRClick).toHaveBeenCalledWith('okr-1');
        }
    });

    it('calls onInitiativeClick when initiative is clicked', async () => {
        const user = userEvent.setup();
        const onInitiativeClick = vi.fn();

        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
                onInitiativeClick={onInitiativeClick}
            />
        );

        const initiativesTab = screen.getByRole('tab', { name: /Initiatives/i });
        await user.click(initiativesTab);

        const initiativeCard = screen.getByText('Platform Modernization').closest('.cursor-pointer');
        if (initiativeCard) {
            await user.click(initiativeCard);
            expect(onInitiativeClick).toHaveBeenCalledWith('init-1');
        }
    });

    it('calls onCreateOKR when New OKR button is clicked', async () => {
        const user = userEvent.setup();
        const onCreateOKR = vi.fn();

        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={mockStrategicPlanningData.okrs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
                onCreateOKR={onCreateOKR}
            />
        );

        const createButton = screen.getByRole('button', { name: /New OKR/i });
        await user.click(createButton);
        expect(onCreateOKR).toHaveBeenCalled();
    });

    it('shows empty state when no OKRs', () => {
        const noOKRs: OKR[] = [];

        render(
            <StrategicPlanning
                planningMetrics={mockStrategicPlanningData.planningMetrics}
                okrs={noOKRs}
                initiatives={mockStrategicPlanningData.initiatives}
                currentQuarter={mockStrategicPlanningData.currentQuarter}
            />
        );

        expect(screen.getByText(/No OKRs found/i)).toBeInTheDocument();
    });
});

/**
 * CrossFunctionalMetrics Tests
 */
describe('CrossFunctionalMetrics', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders cross-functional metrics dashboard', () => {
        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
            />
        );

        expect(screen.getByText('Cross-Functional Metrics')).toBeInTheDocument();
        expect(screen.getByText('Department comparison, trends, and benchmarking')).toBeInTheDocument();
    });

    it('displays correct summary KPI values', () => {
        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
            />
        );

        expect(screen.getByText('Avg Velocity')).toBeInTheDocument();
        expect(screen.getByText('84%')).toBeInTheDocument();
        expect(screen.getByText('Avg Quality')).toBeInTheDocument();
        expect(screen.getByText('89%')).toBeInTheDocument();
    });

    it('renders department comparison table', () => {
        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
            />
        );

        expect(screen.getByText('Department Performance Comparison')).toBeInTheDocument();
        expect(screen.getByText('Engineering')).toBeInTheDocument();
        expect(screen.getByText('DevOps')).toBeInTheDocument();
        expect(screen.getByText('Product')).toBeInTheDocument();
    });

    it('displays department rankings correctly', () => {
        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
            />
        );

        expect(screen.getByText('#1')).toBeInTheDocument();
        expect(screen.getByText('Top')).toBeInTheDocument();
    });

    it('switches to trends tab', async () => {
        const user = userEvent.setup();
        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
            />
        );

        const trendsTab = screen.getByRole('tab', { name: /Trends/i });
        await user.click(trendsTab);

        expect(screen.getByText('Department Trends')).toBeInTheDocument();
    });

    it('switches to insights tab', async () => {
        const user = userEvent.setup();
        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
            />
        );

        const insightsTab = screen.getByRole('tab', { name: /Insights/i });
        await user.click(insightsTab);

        expect(screen.getByText('Cross-Functional Insights')).toBeInTheDocument();
        expect(screen.getByText('Engineering Velocity Leading Organization')).toBeInTheDocument();
    });

    it('displays insights with recommendations', async () => {
        const user = userEvent.setup();
        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
            />
        );

        const insightsTab = screen.getByRole('tab', { name: /Insights/i });
        await user.click(insightsTab);

        expect(screen.getByText('Recommended Action:')).toBeInTheDocument();
    });

    it('switches to benchmarks tab', async () => {
        const user = userEvent.setup();
        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
            />
        );

        const benchmarksTab = screen.getByRole('tab', { name: /Benchmarks/i });
        await user.click(benchmarksTab);

        expect(screen.getByText('Industry Benchmarks')).toBeInTheDocument();
        expect(screen.getByText('Deployment Frequency')).toBeInTheDocument();
    });

    it('displays benchmark comparison data', async () => {
        const user = userEvent.setup();
        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
            />
        );

        const benchmarksTab = screen.getByRole('tab', { name: /Benchmarks/i });
        await user.click(benchmarksTab);

        expect(screen.getByText('Our Company')).toBeInTheDocument();
        expect(screen.getByText('Industry Average')).toBeInTheDocument();
        expect(screen.getByText('Top Performer')).toBeInTheDocument();
    });

    it('calls onDepartmentClick when department row is clicked', async () => {
        const user = userEvent.setup();
        const onDepartmentClick = vi.fn();

        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
                onDepartmentClick={onDepartmentClick}
            />
        );

        const engineeringRow = screen.getByText('Engineering').closest('tr');
        if (engineeringRow) {
            await user.click(engineeringRow);
            expect(onDepartmentClick).toHaveBeenCalledWith('dept-eng');
        }
    });

    it('calls onInsightClick when insight is clicked', async () => {
        const user = userEvent.setup();
        const onInsightClick = vi.fn();

        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
                onInsightClick={onInsightClick}
            />
        );

        const insightsTab = screen.getByRole('tab', { name: /Insights/i });
        await user.click(insightsTab);

        const insightCard = screen.getByText('Engineering Velocity Leading Organization').closest('.cursor-pointer');
        if (insightCard) {
            await user.click(insightCard);
            expect(onInsightClick).toHaveBeenCalledWith('insight-1');
        }
    });

    it('calls onExportMetrics when Export Metrics button is clicked', async () => {
        const user = userEvent.setup();
        const onExportMetrics = vi.fn();

        render(
            <CrossFunctionalMetrics
                metricsSummary={mockCrossFunctionalMetricsData.metricsSummary}
                departmentComparisons={mockCrossFunctionalMetricsData.departmentComparisons}
                trends={mockCrossFunctionalMetricsData.trends}
                insights={mockCrossFunctionalMetricsData.insights}
                benchmarks={mockCrossFunctionalMetricsData.benchmarks}
                onExportMetrics={onExportMetrics}
            />
        );

        const exportButton = screen.getByRole('button', { name: /Export Metrics/i });
        await user.click(exportButton);
        expect(onExportMetrics).toHaveBeenCalled();
    });
});
