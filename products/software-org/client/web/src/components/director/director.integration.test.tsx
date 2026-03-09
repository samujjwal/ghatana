import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PortfolioDashboard } from './PortfolioDashboard';
import { ResourcePlanner } from './ResourcePlanner';
import { BudgetTracker } from './BudgetTracker';
import type { Project, PortfolioMetrics } from './PortfolioDashboard';
import type { TeamCapacity, ResourceConflict } from './ResourcePlanner';
import type { BudgetCategory, BudgetMetrics, BudgetRequest } from './BudgetTracker';

/**
 * Director Features Integration Tests
 *
 * Test coverage:
 * - PortfolioDashboard (12 tests)
 * - ResourcePlanner (12 tests)
 * - BudgetTracker (11 tests)
 *
 * Total: 35 comprehensive integration tests
 *
 * REUSE: Following manager.integration.test.tsx patterns
 */

// ============================================================================
// PortfolioDashboard Tests
// ============================================================================

describe('PortfolioDashboard', () => {
    const mockMetrics: PortfolioMetrics = {
        totalProjects: 12,
        activeProjects: 8,
        completedProjects: 3,
        atRiskProjects: 2,
        totalBudget: 6600000,
        budgetUtilized: 4500000,
        teamUtilization: 82,
    };

    const mockProjects: Project[] = [
        {
            id: 'proj-1',
            name: 'Platform Modernization',
            description: 'Migrate legacy systems to modern cloud architecture',
            status: 'on-track',
            priority: 'critical',
            department: 'Engineering',
            progress: 65,
            health: 85,
            budget: {
                allocated: 2000000,
                spent: 1200000,
                remaining: 800000,
            },
            timeline: {
                startDate: new Date('2025-01-01'),
                endDate: new Date('2025-12-31'),
                daysRemaining: 230,
            },
            team: {
                lead: 'Sarah Manager',
                memberCount: 12,
            },
            kpis: {
                velocity: 85,
                quality: 92,
                satisfaction: 88,
            },
            healthIndicators: {
                budget: 'healthy',
                timeline: 'healthy',
                scope: 'healthy',
            },
        },
        {
            id: 'proj-2',
            name: 'Mobile App 2.0',
            description: 'Next generation mobile application',
            status: 'at-risk',
            priority: 'high',
            department: 'Product',
            progress: 45,
            health: 65,
            budget: {
                allocated: 1500000,
                spent: 900000,
                remaining: 600000,
            },
            timeline: {
                startDate: new Date('2025-02-01'),
                endDate: new Date('2025-08-31'),
                daysRemaining: 150,
            },
            team: {
                lead: 'John Product',
                memberCount: 8,
            },
            kpis: {
                velocity: 70,
                quality: 85,
                satisfaction: 75,
            },
            healthIndicators: {
                budget: 'warning',
                timeline: 'at-risk',
                scope: 'warning',
            },
        },
    ];

    const mockCallbacks = {
        onProjectClick: vi.fn(),
        onCreateProject: vi.fn(),
        onViewDetails: vi.fn(),
        onExportReport: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders dashboard with portfolio metrics', () => {
        render(<PortfolioDashboard metrics={mockMetrics} projects={mockProjects} {...mockCallbacks} />);

        expect(screen.getByText('Portfolio Dashboard')).toBeInTheDocument();
        expect(screen.getByText('Active Projects')).toBeInTheDocument();
        expect(screen.getByText('At Risk')).toBeInTheDocument();
        expect(screen.getByText('Budget Utilized')).toBeInTheDocument();
        expect(screen.getByText('Team Utilization')).toBeInTheDocument();
    });

    it('displays correct KPI values', () => {
        render(<PortfolioDashboard metrics={mockMetrics} projects={mockProjects} {...mockCallbacks} />);

        expect(screen.getByText('8')).toBeInTheDocument(); // Active projects
        expect(screen.getByText('2')).toBeInTheDocument(); // At risk
        expect(screen.getByText('68%')).toBeInTheDocument(); // Budget utilized
        expect(screen.getByText('82%')).toBeInTheDocument(); // Team utilization
    });

    it('shows portfolio health summary with metrics', () => {
        render(<PortfolioDashboard metrics={mockMetrics} projects={mockProjects} {...mockCallbacks} />);

        expect(screen.getByText('Portfolio Health')).toBeInTheDocument();
        expect(screen.getByText(/Budget Health/i)).toBeInTheDocument();
        expect(screen.getByText(/Overall Health/i)).toBeInTheDocument();
    });

    it('renders all project cards', () => {
        render(<PortfolioDashboard projects={mockProjects} {...mockCallbacks} />);

        expect(screen.getByText('Platform Modernization')).toBeInTheDocument();
        expect(screen.getByText('Mobile App 2.0')).toBeInTheDocument();
    });

    it('displays project status chips correctly', () => {
        render(<PortfolioDashboard projects={mockProjects} {...mockCallbacks} />);

        const onTrackChip = screen.getByText('on track');
        const atRiskChip = screen.getByText('at risk');

        expect(onTrackChip).toBeInTheDocument();
        expect(atRiskChip).toBeInTheDocument();
    });

    it('shows project budget information', () => {
        render(<PortfolioDashboard projects={mockProjects} {...mockCallbacks} />);

        expect(screen.getByText(/\$2\.0M/i)).toBeInTheDocument(); // Allocated
        expect(screen.getByText(/\$1\.2M/i)).toBeInTheDocument(); // Spent
        expect(screen.getByText(/\$800K/i)).toBeInTheDocument(); // Remaining
    });

    it('displays team information', () => {
        render(<PortfolioDashboard projects={mockProjects} {...mockCallbacks} />);

        expect(screen.getByText('Sarah Manager')).toBeInTheDocument();
        expect(screen.getByText('12 members')).toBeInTheDocument();
    });

    it('filters projects by tab selection', async () => {
        const user = userEvent.setup();
        render(<PortfolioDashboard projects={mockProjects} {...mockCallbacks} />);

        const atRiskTab = screen.getByRole('tab', { name: /at risk/i });
        await user.click(atRiskTab);

        // Should show only at-risk project
        expect(screen.getByText('Mobile App 2.0')).toBeInTheDocument();
        expect(screen.queryByText('Platform Modernization')).not.toBeInTheDocument();
    });

    it('calls onProjectClick when project card is clicked', async () => {
        const user = userEvent.setup();
        render(<PortfolioDashboard projects={mockProjects} {...mockCallbacks} />);

        const projectCard = screen.getByText('Platform Modernization').closest('div[class*="MuiCard"]');
        if (projectCard) {
            await user.click(projectCard);
            expect(mockCallbacks.onProjectClick).toHaveBeenCalledWith('proj-1');
        }
    });

    it('calls onCreateProject when New Project button is clicked', async () => {
        const user = userEvent.setup();
        render(<PortfolioDashboard projects={mockProjects} {...mockCallbacks} />);

        const newProjectButton = screen.getByRole('button', { name: /new project/i });
        await user.click(newProjectButton);

        expect(mockCallbacks.onCreateProject).toHaveBeenCalled();
    });

    it('calls onExportReport when Export Report button is clicked', async () => {
        const user = userEvent.setup();
        render(<PortfolioDashboard projects={mockProjects} {...mockCallbacks} />);

        const exportButton = screen.getByRole('button', { name: /export report/i });
        await user.click(exportButton);

        expect(mockCallbacks.onExportReport).toHaveBeenCalled();
    });

    it('shows empty state when no projects', () => {
        render(<PortfolioDashboard projects={[]} {...mockCallbacks} />);

        expect(screen.getByText(/no projects/i)).toBeInTheDocument();
    });
});

// ============================================================================
// ResourcePlanner Tests
// ============================================================================

describe('ResourcePlanner', () => {
    const mockTeams: TeamCapacity[] = [
        {
            teamId: 'team-backend',
            teamName: 'Backend Engineering',
            totalMembers: 8,
            availableHours: 320,
            allocatedHours: 280,
            utilizationPercent: 88,
            overallocatedCount: 1,
            availableCount: 2,
            members: [
                {
                    id: 'eng-1',
                    name: 'Alice Johnson',
                    role: 'Senior Engineer',
                    email: 'alice@example.com',
                    avatar: 'AJ',
                    weeklyHours: 40,
                    allocatedHours: 45,
                    utilization: 113,
                    status: 'overallocated',
                    skills: [
                        { name: 'Java', category: 'technical', proficiency: 95, certified: true },
                        { name: 'Spring Boot', category: 'technical', proficiency: 90 },
                    ],
                    assignments: [
                        {
                            projectId: 'proj-1',
                            projectName: 'Platform Modernization',
                            hoursPerWeek: 25,
                            startDate: new Date('2025-01-01'),
                            endDate: new Date('2025-06-30'),
                            role: 'Tech Lead',
                        },
                    ],
                },
            ],
        },
    ];

    const mockCallbacks = {
        onTeamSelect: vi.fn(),
        onAllocateResource: vi.fn(),
        onRequestResource: vi.fn(),
        onViewMember: vi.fn(),
        onResolveConflict: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders resource planner with capacity KPIs', () => {
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        expect(screen.getByText('Resource Planning')).toBeInTheDocument();
        expect(screen.getByText('Total Resources')).toBeInTheDocument();
        expect(screen.getByText('Overall Utilization')).toBeInTheDocument();
        expect(screen.getByText('Available Hours')).toBeInTheDocument();
        expect(screen.getByText('Overallocated')).toBeInTheDocument();
    });

    it('displays team selector chips', () => {
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        expect(screen.getByText(/Backend Engineering \(8\)/i)).toBeInTheDocument();
    });

    it('shows team member cards with capacity info', () => {
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
        expect(screen.getByText('Senior Engineer')).toBeInTheDocument();
        expect(screen.getByText(/45h \/ 40h per week/i)).toBeInTheDocument();
        expect(screen.getByText('113%')).toBeInTheDocument();
    });

    it('displays member skills with proficiency', () => {
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        expect(screen.getByText('Java')).toBeInTheDocument();
        expect(screen.getByText('Spring Boot')).toBeInTheDocument();
    });

    it('shows current assignments for members', () => {
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        expect(screen.getByText('Platform Modernization')).toBeInTheDocument();
        expect(screen.getByText(/Tech Lead.*25h\/week/i)).toBeInTheDocument();
    });

    it('highlights overallocated members', () => {
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        expect(screen.getByText(/overallocated/i)).toBeInTheDocument();
        expect(screen.getByText(/Overallocated by 13%/i)).toBeInTheDocument();
    });

    it('switches to skill matrix tab', async () => {
        const user = userEvent.setup();
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        const skillMatrixTab = screen.getByRole('tab', { name: /skill matrix/i });
        await user.click(skillMatrixTab);

        expect(screen.getByText(/Skill Gaps/i)).toBeInTheDocument();
        expect(screen.getByText(/Team Skills Coverage/i)).toBeInTheDocument();
    });

    it('switches to conflicts tab', async () => {
        const user = userEvent.setup();
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        const conflictsTab = screen.getByRole('tab', { name: /conflicts/i });
        await user.click(conflictsTab);

        // Should show conflicts or empty state
        expect(screen.getByText(/resource conflict/i)).toBeInTheDocument();
    });

    it('calls onViewMember when View Details is clicked', async () => {
        const user = userEvent.setup();
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        const viewButton = screen.getByRole('button', { name: /view details/i });
        await user.click(viewButton);

        expect(mockCallbacks.onViewMember).toHaveBeenCalledWith('eng-1');
    });

    it('calls onRequestResource when Request Resource is clicked', async () => {
        const user = userEvent.setup();
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        const requestButton = screen.getByRole('button', { name: /request resource/i });
        await user.click(requestButton);

        expect(mockCallbacks.onRequestResource).toHaveBeenCalled();
    });

    it('calls onAllocateResource when Allocate Resource is clicked', async () => {
        const user = userEvent.setup();
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        const allocateButton = screen.getByRole('button', { name: /allocate resource/i });
        await user.click(allocateButton);

        expect(mockCallbacks.onAllocateResource).toHaveBeenCalled();
    });

    it('displays skill gaps with priorities', async () => {
        const user = userEvent.setup();
        render(<ResourcePlanner teams={mockTeams} {...mockCallbacks} />);

        const skillMatrixTab = screen.getByRole('tab', { name: /skill matrix/i });
        await user.click(skillMatrixTab);

        expect(screen.getByText('Kubernetes')).toBeInTheDocument();
        expect(screen.getByText('critical')).toBeInTheDocument();
    });
});

// ============================================================================
// BudgetTracker Tests
// ============================================================================

describe('BudgetTracker', () => {
    const mockMetrics: BudgetMetrics = {
        totalBudget: 5000000,
        totalSpent: 3200000,
        totalCommitted: 600000,
        totalRemaining: 1200000,
        utilizationPercent: 76,
        projectedEndOfYearSpend: 4800000,
        variance: -4,
        categoriesOverBudget: 1,
        pendingRequests: 3,
    };

    const mockCategories: BudgetCategory[] = [
        {
            id: 'cat-personnel',
            name: 'Personnel',
            allocated: 3000000,
            spent: 2100000,
            committed: 300000,
            remaining: 600000,
            variance: -20,
            status: 'on-track',
            subcategories: [
                { id: 'sub-salaries', name: 'Salaries', allocated: 2500000, spent: 1800000, remaining: 700000 },
            ],
        },
        {
            id: 'cat-infrastructure',
            name: 'Infrastructure',
            allocated: 800000,
            spent: 550000,
            committed: 150000,
            remaining: 100000,
            variance: -12.5,
            status: 'warning',
        },
    ];

    const mockRequests: BudgetRequest[] = [
        {
            id: 'req-1',
            requestedBy: 'Sarah Manager',
            category: 'Personnel',
            amount: 150000,
            purpose: 'Hire 2 additional engineers',
            priority: 'high',
            status: 'pending',
            requestDate: new Date('2025-11-20'),
        },
    ];

    const mockCallbacks = {
        onApproveBudget: vi.fn(),
        onRejectBudget: vi.fn(),
        onViewDetails: vi.fn(),
        onExportReport: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders budget tracker with KPIs', () => {
        render(<BudgetTracker metrics={mockMetrics} categories={mockCategories} {...mockCallbacks} />);

        expect(screen.getByText('Budget Tracker')).toBeInTheDocument();
        expect(screen.getByText('Total Budget')).toBeInTheDocument();
        expect(screen.getByText('Spent')).toBeInTheDocument();
        expect(screen.getByText('Utilization')).toBeInTheDocument();
        expect(screen.getByText('Remaining')).toBeInTheDocument();
    });

    it('displays correct budget values in KPIs', () => {
        render(<BudgetTracker metrics={mockMetrics} categories={mockCategories} {...mockCallbacks} />);

        expect(screen.getByText('$5,000,000')).toBeInTheDocument(); // Total budget
        expect(screen.getByText('$3,200,000')).toBeInTheDocument(); // Spent
        expect(screen.getByText('76%')).toBeInTheDocument(); // Utilization
        expect(screen.getByText('$1,200,000')).toBeInTheDocument(); // Remaining
    });

    it('shows warning for over-budget categories', () => {
        render(<BudgetTracker metrics={mockMetrics} categories={mockCategories} {...mockCallbacks} />);

        expect(screen.getByText(/1 category is over budget/i)).toBeInTheDocument();
    });

    it('displays budget categories with allocation details', () => {
        render(<BudgetTracker categories={mockCategories} {...mockCallbacks} />);

        expect(screen.getByText('Personnel')).toBeInTheDocument();
        expect(screen.getByText('Infrastructure')).toBeInTheDocument();
        expect(screen.getByText('on track')).toBeInTheDocument();
        expect(screen.getByText('warning')).toBeInTheDocument();
    });

    it('shows subcategories when available', () => {
        render(<BudgetTracker categories={mockCategories} {...mockCallbacks} />);

        expect(screen.getByText('Salaries')).toBeInTheDocument();
    });

    it('switches to forecasts tab', async () => {
        const user = userEvent.setup();
        render(<BudgetTracker {...mockCallbacks} />);

        const forecastsTab = screen.getByRole('tab', { name: /forecasts/i });
        await user.click(forecastsTab);

        expect(screen.getByText(/Monthly Forecast/i)).toBeInTheDocument();
        expect(screen.getByText(/Year-End Projection/i)).toBeInTheDocument();
    });

    it('switches to requests tab and shows pending count', async () => {
        const user = userEvent.setup();
        render(<BudgetTracker requests={mockRequests} {...mockCallbacks} />);

        const requestsTab = screen.getByRole('tab', { name: /requests.*1/i });
        await user.click(requestsTab);

        expect(screen.getByText('Hire 2 additional engineers')).toBeInTheDocument();
        expect(screen.getByText('Sarah Manager')).toBeInTheDocument();
    });

    it('displays budget request with priority', async () => {
        const user = userEvent.setup();
        render(<BudgetTracker requests={mockRequests} {...mockCallbacks} />);

        const requestsTab = screen.getByRole('tab', { name: /requests/i });
        await user.click(requestsTab);

        expect(screen.getByText('$150,000')).toBeInTheDocument();
        expect(screen.getByText('high')).toBeInTheDocument();
        expect(screen.getByText('pending')).toBeInTheDocument();
    });

    it('calls onApproveBudget when Approve is clicked', async () => {
        const user = userEvent.setup();
        render(<BudgetTracker requests={mockRequests} {...mockCallbacks} />);

        const requestsTab = screen.getByRole('tab', { name: /requests/i });
        await user.click(requestsTab);

        const approveButton = screen.getByRole('button', { name: /approve/i });
        await user.click(approveButton);

        expect(mockCallbacks.onApproveBudget).toHaveBeenCalledWith('req-1', 150000);
    });

    it('calls onRejectBudget when Reject is clicked', async () => {
        const user = userEvent.setup();
        render(<BudgetTracker requests={mockRequests} {...mockCallbacks} />);

        const requestsTab = screen.getByRole('tab', { name: /requests/i });
        await user.click(requestsTab);

        const rejectButton = screen.getByRole('button', { name: /reject/i });
        await user.click(rejectButton);

        expect(mockCallbacks.onRejectBudget).toHaveBeenCalled();
    });

    it('calls onExportReport when Export Report is clicked', async () => {
        const user = userEvent.setup();
        render(<BudgetTracker {...mockCallbacks} />);

        const exportButton = screen.getByRole('button', { name: /export report/i });
        await user.click(exportButton);

        expect(mockCallbacks.onExportReport).toHaveBeenCalled();
    });
});
