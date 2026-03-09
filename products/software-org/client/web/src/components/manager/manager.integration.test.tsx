import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PerformanceReviewDashboard } from '../PerformanceReviewDashboard';
import { PerformanceReviewForm } from '../PerformanceReviewForm';
import { ResourceHeatmap } from '../ResourceHeatmap';
import { HeadcountRequestForm } from '../HeadcountRequestForm';
import type { EmployeeReviewStatus, ReviewCycle } from '../PerformanceReviewDashboard';
import type { GoalRating } from '../PerformanceReviewForm';
import type { TeamMemberAllocation } from '../ResourceHeatmap';
import type { TeamUtilizationMetrics } from '../HeadcountRequestForm';

/**
 * Manager Features Integration Tests
 *
 * Test coverage:
 * - PerformanceReviewDashboard (8 tests)
 * - PerformanceReviewForm (10 tests)
 * - ResourceHeatmap (8 tests)
 * - HeadcountRequestForm (9 tests)
 *
 * Total: 35 comprehensive integration tests
 */

describe('PerformanceReviewDashboard', () => {
    const mockCycles: ReviewCycle[] = [
        {
            id: 'q1-2025',
            name: 'Q1 2025',
            startDate: '2025-01-01',
            endDate: '2025-03-31',
            dueDate: '2025-04-15',
            status: 'active',
        },
    ];

    const mockEmployeeReviews: EmployeeReviewStatus[] = [
        {
            employeeId: 'emp-1',
            employeeName: 'Alice Johnson',
            employeeEmail: 'alice@company.com',
            employeeRole: 'Senior Engineer',
            status: 'completed',
            progress: 100,
            dueDate: '2025-04-15',
            reviewId: 'review-1',
            overallRating: 4.5,
            lastUpdated: '2025-03-28',
        },
        {
            employeeId: 'emp-2',
            employeeName: 'Bob Smith',
            employeeEmail: 'bob@company.com',
            employeeRole: 'Engineer II',
            status: 'in-progress',
            progress: 60,
            dueDate: '2025-04-15',
            reviewId: 'review-2',
        },
        {
            employeeId: 'emp-3',
            employeeName: 'Carol Williams',
            employeeEmail: 'carol@company.com',
            employeeRole: 'Engineer II',
            status: 'not-started',
            progress: 0,
            dueDate: '2025-04-15',
        },
    ];

    const mockCallbacks = {
        onCycleChange: vi.fn(),
        onStartReview: vi.fn(),
        onContinueReview: vi.fn(),
        onViewReview: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders dashboard with review cycle selector', () => {
        render(
            <PerformanceReviewDashboard
                cycles={mockCycles}
                employeeReviews={mockEmployeeReviews}
                {...mockCallbacks}
            />
        );

        expect(screen.getByText('Performance Reviews')).toBeInTheDocument();
        expect(screen.getByLabelText(/review cycle/i)).toBeInTheDocument();
    });

    it('displays correct metrics summary', () => {
        render(<PerformanceReviewDashboard employeeReviews={mockEmployeeReviews} {...mockCallbacks} />);

        expect(screen.getByText('Reviews Completed')).toBeInTheDocument();
        expect(screen.getByText('1/3')).toBeInTheDocument();
        expect(screen.getByText('Due In')).toBeInTheDocument();
        expect(screen.getByText('Average Rating')).toBeInTheDocument();
        expect(screen.getByText('4.5')).toBeInTheDocument();
    });

    it('shows employee list with correct statuses', () => {
        render(<PerformanceReviewDashboard employeeReviews={mockEmployeeReviews} {...mockCallbacks} />);

        expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
        expect(screen.getByText('Bob Smith')).toBeInTheDocument();
        expect(screen.getByText('Carol Williams')).toBeInTheDocument();

        expect(screen.getByText('Completed')).toBeInTheDocument();
        expect(screen.getByText('In Progress')).toBeInTheDocument();
        expect(screen.getByText('Not Started')).toBeInTheDocument();
    });

    it('filters employees by search query', async () => {
        render(<PerformanceReviewDashboard employeeReviews={mockEmployeeReviews} {...mockCallbacks} />);

        const searchInput = screen.getByPlaceholderText(/search employees/i);
        await userEvent.type(searchInput, 'alice');

        expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
        expect(screen.queryByText('Bob Smith')).not.toBeInTheDocument();
    });

    it('filters employees by status', async () => {
        render(<PerformanceReviewDashboard employeeReviews={mockEmployeeReviews} {...mockCallbacks} />);

        const statusFilter = screen.getByLabelText(/status/i);
        await userEvent.click(statusFilter);
        const completedOption = await screen.findByText(/^completed$/i);
        await userEvent.click(completedOption);

        await waitFor(() => {
            expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
            expect(screen.queryByText('Bob Smith')).not.toBeInTheDocument();
        });
    });

    it('calls onStartReview for not-started employees', async () => {
        render(<PerformanceReviewDashboard employeeReviews={mockEmployeeReviews} {...mockCallbacks} />);

        const carolRow = screen.getByText('Carol Williams').closest('button');
        const startButton = within(carolRow!.parentElement!).getByRole('button', {
            name: /start review/i,
        });

        await userEvent.click(startButton);

        expect(mockCallbacks.onStartReview).toHaveBeenCalledWith('emp-3');
    });

    it('calls onContinueReview for in-progress reviews', async () => {
        render(<PerformanceReviewDashboard employeeReviews={mockEmployeeReviews} {...mockCallbacks} />);

        const bobRow = screen.getByText('Bob Smith').closest('button');
        const continueButton = within(bobRow!.parentElement!).getByRole('button', {
            name: /continue/i,
        });

        await userEvent.click(continueButton);

        expect(mockCallbacks.onContinueReview).toHaveBeenCalledWith('review-2');
    });

    it('calls onViewReview for completed reviews', async () => {
        render(<PerformanceReviewDashboard employeeReviews={mockEmployeeReviews} {...mockCallbacks} />);

        const aliceRow = screen.getByText('Alice Johnson').closest('button');
        const viewButton = within(aliceRow!.parentElement!).getByRole('button', { name: /view/i });

        await userEvent.click(viewButton);

        expect(mockCallbacks.onViewReview).toHaveBeenCalledWith('review-1');
    });
});

describe('PerformanceReviewForm', () => {
    const mockGoals: GoalRating[] = [
        {
            id: 'goal-1',
            title: 'Lead microservices migration',
            description: 'Migrate 3 services',
            targetDate: '2025-03-31',
            achieved: true,
        },
        {
            id: 'goal-2',
            title: 'Mentor 2 engineers',
            description: 'Weekly mentorship',
            targetDate: '2025-03-31',
            achieved: true,
        },
    ];

    const mockCallbacks = {
        onSubmit: vi.fn(),
        onSaveDraft: vi.fn(),
        onCancel: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders form with all sections', () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                previousGoals={mockGoals}
                {...mockCallbacks}
            />
        );

        expect(screen.getByText(/performance review: alice johnson/i)).toBeInTheDocument();
        expect(screen.getByText(/1\. goal achievement/i)).toBeInTheDocument();
        expect(screen.getByText(/2\. competency assessment/i)).toBeInTheDocument();
        expect(screen.getByText(/3\. written feedback/i)).toBeInTheDocument();
        expect(screen.getByText(/4\. goals for next cycle/i)).toBeInTheDocument();
        expect(screen.getByText(/5\. overall rating/i)).toBeInTheDocument();
    });

    it('displays previous goals for rating', () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                previousGoals={mockGoals}
                {...mockCallbacks}
            />
        );

        expect(screen.getByText('Lead microservices migration')).toBeInTheDocument();
        expect(screen.getByText('Mentor 2 engineers')).toBeInTheDocument();
    });

    it('allows rating goals with stars', async () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                previousGoals={mockGoals}
                {...mockCallbacks}
            />
        );

        const ratings = screen.getAllByRole('radiogroup');
        const firstGoalRating = ratings[0];

        const fiveStars = within(firstGoalRating).getAllByRole('radio')[4];
        await userEvent.click(fiveStars);

        // Check that rating was applied
        expect(fiveStars).toBeChecked();
    });

    it('displays all competency categories', () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                {...mockCallbacks}
            />
        );

        expect(screen.getByText('Technical Skills')).toBeInTheDocument();
        expect(screen.getByText('Problem Solving')).toBeInTheDocument();
        expect(screen.getByText('Communication')).toBeInTheDocument();
        expect(screen.getByText('Leadership & Mentorship')).toBeInTheDocument();
        expect(screen.getByText('Collaboration')).toBeInTheDocument();
        expect(screen.getByText('Initiative & Ownership')).toBeInTheDocument();
    });

    it('allows adding goals for next cycle', async () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                {...mockCallbacks}
            />
        );

        const titleInput = screen.getByPlaceholderText(/goal title/i);
        const descriptionInput = screen.getByPlaceholderText(/goal description/i);
        const addButton = screen.getByRole('button', { name: /add goal/i });

        await userEvent.type(titleInput, 'New Goal Title');
        await userEvent.type(descriptionInput, 'New goal description');
        await userEvent.click(addButton);

        expect(screen.getByText('New Goal Title')).toBeInTheDocument();
    });

    it('calculates overall rating automatically', async () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                previousGoals={mockGoals}
                {...mockCallbacks}
            />
        );

        // Form auto-calculates from competencies (default 3.0) = 3.0
        const overallRatingSection = screen.getByText(/overall performance rating/i).closest('div');
        expect(within(overallRatingSection!).getByText(/3\.0/)).toBeInTheDocument();
    });

    it('allows manual override of overall rating', async () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                {...mockCallbacks}
            />
        );

        const manualOverrideSwitch = screen.getByRole('checkbox', { name: /manual override/i });
        await userEvent.click(manualOverrideSwitch);

        // Now can change rating manually
        expect(manualOverrideSwitch).toBeChecked();
    });

    it('validates required fields before submit', () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                {...mockCallbacks}
            />
        );

        const submitButton = screen.getByRole('button', { name: /submit review/i });
        expect(submitButton).toBeDisabled();
    });

    it('enables submit when all required fields are filled', async () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                previousGoals={mockGoals}
                {...mockCallbacks}
            />
        );

        // Rate all goals
        const ratings = screen.getAllByRole('radiogroup');
        for (let i = 0; i < 2; i++) {
            const stars = within(ratings[i]).getAllByRole('radio')[3]; // 4 stars
            await userEvent.click(stars);
        }

        // Fill feedback
        const strengthsInput = screen.getByPlaceholderText(/what does this employee do exceptionally/i);
        await userEvent.type(strengthsInput, 'Great technical skills and leadership');

        const improvementsInput = screen.getByPlaceholderText(/where can this employee grow/i);
        await userEvent.type(improvementsInput, 'Could improve communication with stakeholders');

        const careerInput = screen.getByPlaceholderText(/what skills, training/i);
        await userEvent.type(careerInput, 'Consider taking leadership training');

        // Set next review date
        const dateInput = screen.getByLabelText(/next review date/i);
        await userEvent.type(dateInput, '2025-07-15');

        await waitFor(() => {
            const submitButton = screen.getByRole('button', { name: /submit review/i });
            expect(submitButton).not.toBeDisabled();
        });
    });

    it('calls onSubmit with correct data', async () => {
        render(
            <PerformanceReviewForm
                employeeId="emp-1"
                employeeName="Alice Johnson"
                employeeRole="Senior Engineer"
                cycleId="q1-2025"
                cycleName="Q1 2025"
                previousGoals={mockGoals}
                {...mockCallbacks}
            />
        );

        // Fill required fields (abbreviated)
        const ratings = screen.getAllByRole('radiogroup');
        await userEvent.click(within(ratings[0]).getAllByRole('radio')[3]);
        await userEvent.click(within(ratings[1]).getAllByRole('radio')[3]);

        await userEvent.type(
            screen.getByPlaceholderText(/what does this employee do exceptionally/i),
            'Excellent work'
        );
        await userEvent.type(
            screen.getByPlaceholderText(/where can this employee grow/i),
            'Needs improvement in X'
        );
        await userEvent.type(
            screen.getByPlaceholderText(/what skills, training/i),
            'Training in Y'
        );
        await userEvent.type(screen.getByLabelText(/next review date/i), '2025-07-15');

        const submitButton = screen.getByRole('button', { name: /submit review/i });
        await userEvent.click(submitButton);

        await waitFor(() => {
            expect(mockCallbacks.onSubmit).toHaveBeenCalledWith(
                expect.objectContaining({
                    employeeId: 'emp-1',
                    cycleId: 'q1-2025',
                    nextReviewDate: '2025-07-15',
                })
            );
        });
    });
});

describe('ResourceHeatmap', () => {
    const mockTeamMembers: TeamMemberAllocation[] = [
        {
            memberId: 'member-1',
            memberName: 'Alice Johnson',
            role: 'Senior Engineer',
            weeklyAllocations: [
                {
                    weekStart: '2025-04-06',
                    totalPercentage: 110,
                    projects: [
                        { projectId: 'proj-a', projectName: 'Project Alpha', percentage: 60, color: '#3b82f6' },
                        { projectId: 'proj-b', projectName: 'Project Beta', percentage: 50, color: '#10b981' },
                    ],
                },
            ],
        },
        {
            memberId: 'member-2',
            memberName: 'Bob Smith',
            role: 'Engineer II',
            weeklyAllocations: [
                {
                    weekStart: '2025-04-06',
                    totalPercentage: 80,
                    projects: [
                        { projectId: 'proj-a', projectName: 'Project Alpha', percentage: 80, color: '#3b82f6' },
                    ],
                },
            ],
        },
    ];

    const mockCallbacks = {
        onCellClick: vi.fn(),
        onReassign: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders heatmap with team members', () => {
        render(<ResourceHeatmap teamMembers={mockTeamMembers} {...mockCallbacks} />);

        expect(screen.getByText('Resource Allocation')).toBeInTheDocument();
        expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
        expect(screen.getByText('Bob Smith')).toBeInTheDocument();
    });

    it('displays allocation percentages in cells', () => {
        render(<ResourceHeatmap teamMembers={mockTeamMembers} {...mockCallbacks} />);

        expect(screen.getByText('110%')).toBeInTheDocument();
        expect(screen.getByText('80%')).toBeInTheDocument();
    });

    it('shows week labels', () => {
        render(<ResourceHeatmap teamMembers={mockTeamMembers} weeks={4} {...mockCallbacks} />);

        const weekLabel = screen.getByText(/apr/i);
        expect(weekLabel).toBeInTheDocument();
    });

    it('opens detail panel on cell click', async () => {
        render(<ResourceHeatmap teamMembers={mockTeamMembers} {...mockCallbacks} />);

        const cell = screen.getByText('110%');
        await userEvent.click(cell);

        await waitFor(() => {
            expect(screen.getByText('Allocation Details')).toBeInTheDocument();
            expect(screen.getByText('Project Alpha')).toBeInTheDocument();
            expect(screen.getByText('Project Beta')).toBeInTheDocument();
        });
    });

    it('shows overallocation warning in detail panel', async () => {
        render(<ResourceHeatmap teamMembers={mockTeamMembers} {...mockCallbacks} />);

        const overallocatedCell = screen.getByText('110%');
        await userEvent.click(overallocatedCell);

        await waitFor(() => {
            expect(screen.getByText(/overallocation warning/i)).toBeInTheDocument();
        });
    });

    it('closes detail panel when close button clicked', async () => {
        render(<ResourceHeatmap teamMembers={mockTeamMembers} {...mockCallbacks} />);

        const cell = screen.getByText('110%');
        await userEvent.click(cell);

        await waitFor(() => {
            expect(screen.getByText('Allocation Details')).toBeInTheDocument();
        });

        const closeButton = screen.getByRole('button', { name: /✕/ });
        await userEvent.click(closeButton);

        await waitFor(() => {
            expect(screen.queryByText('Allocation Details')).not.toBeInTheDocument();
        });
    });

    it('switches between percentage and projects view', async () => {
        render(<ResourceHeatmap teamMembers={mockTeamMembers} {...mockCallbacks} />);

        const viewSelect = screen.getByLabelText(/view/i);
        await userEvent.click(viewSelect);

        const projectsOption = await screen.findByText('Projects');
        await userEvent.click(projectsOption);

        // In projects view, percentages are replaced with colored bars
        await waitFor(() => {
            expect(screen.queryByText('110%')).not.toBeInTheDocument();
        });
    });

    it('calls onCellClick callback when cell is clicked', async () => {
        render(<ResourceHeatmap teamMembers={mockTeamMembers} {...mockCallbacks} />);

        const cell = screen.getByText('110%');
        await userEvent.click(cell);

        expect(mockCallbacks.onCellClick).toHaveBeenCalledWith('member-1', '2025-04-06');
    });
});

describe('HeadcountRequestForm', () => {
    const mockMetrics: TeamUtilizationMetrics = {
        currentTeamSize: 8,
        averageUtilization: 92,
        overallocatedMembers: 3,
        underutilizedMembers: 0,
        upcomingProjects: 2,
        projectedCapacityGap: 35,
    };

    const mockCallbacks = {
        onSubmit: vi.fn(),
        onCancel: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders form with team metrics', () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        expect(screen.getByText('Request Additional Headcount')).toBeInTheDocument();
        expect(screen.getByText('Current Team Metrics')).toBeInTheDocument();
        expect(screen.getByText('8')).toBeInTheDocument(); // Team size
        expect(screen.getByText('92%')).toBeInTheDocument(); // Utilization
    });

    it('shows all form sections', () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        expect(screen.getByText('Request Details')).toBeInTheDocument();
        expect(screen.getByText('Justification')).toBeInTheDocument();
        expect(screen.getByText('Budget Impact')).toBeInTheDocument();
    });

    it('allows selecting number of headcount', async () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        const headcountSelect = screen.getByLabelText(/number of headcount/i);
        await userEvent.click(headcountSelect);

        const threeOption = await screen.findByText('3 people');
        await userEvent.click(threeOption);

        expect(headcountSelect).toHaveTextContent('3 people');
    });

    it('updates salary range when seniority changes', async () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        const senioritySelect = screen.getByLabelText(/seniority level/i);
        await userEvent.click(senioritySelect);

        const seniorOption = await screen.findByText(/senior \(l5\)/i);
        await userEvent.click(seniorOption);

        await waitFor(() => {
            expect(screen.getByText(/range: \$150k - \$200k/i)).toBeInTheDocument();
        });
    });

    it('applies justification templates', async () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        const capacityChip = screen.getByText('Capacity Constraints');
        await userEvent.click(capacityChip);

        const justificationInput = screen.getByLabelText(/detailed justification/i);
        expect(justificationInput).toHaveValue(expect.stringContaining('operating at 92%'));
    });

    it('calculates budget impact correctly', async () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        // Default is 1 mid-level at $110k
        expect(screen.getByText(/\$110k/)).toBeInTheDocument(); // Salary
        expect(screen.getByText(/\$143k/)).toBeInTheDocument(); // Total with 30% overhead
    });

    it('shows contract duration for contract hires', async () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        const contractRadio = screen.getByRole('radio', { name: /contract/i });
        await userEvent.click(contractRadio);

        await waitFor(() => {
            expect(screen.getByLabelText(/contract duration/i)).toBeInTheDocument();
        });
    });

    it('validates required fields', () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        const submitButton = screen.getByRole('button', { name: /submit request/i });
        expect(submitButton).toBeDisabled();
    });

    it('enables submit when all fields are filled', async () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        // Fill required fields
        await userEvent.type(screen.getByLabelText(/role\/position/i), 'Senior Engineer');
        await userEvent.type(screen.getByLabelText(/desired start date/i), '2025-05-01');

        const justificationInput = screen.getByLabelText(/detailed justification/i);
        await userEvent.type(
            justificationInput,
            'We need additional capacity to handle upcoming projects and reduce team overallocation'
        );

        const businessImpactInput = screen.getByLabelText(/business impact/i);
        await userEvent.type(
            businessImpactInput,
            'Project delays and team burnout if not approved'
        );

        await waitFor(() => {
            const submitButton = screen.getByRole('button', { name: /submit request/i });
            expect(submitButton).not.toBeDisabled();
        });
    });

    it('calls onSubmit with correct data', async () => {
        render(<HeadcountRequestForm teamMetrics={mockMetrics} {...mockCallbacks} />);

        // Fill form
        await userEvent.type(screen.getByLabelText(/role\/position/i), 'Senior Engineer');
        await userEvent.type(screen.getByLabelText(/desired start date/i), '2025-05-01');
        await userEvent.type(
            screen.getByLabelText(/detailed justification/i),
            'Long justification text here with more than 50 characters to pass validation'
        );
        await userEvent.type(
            screen.getByLabelText(/business impact/i),
            'Business impact statement'
        );

        const submitButton = screen.getByRole('button', { name: /submit request/i });
        await userEvent.click(submitButton);

        await waitFor(() => {
            expect(mockCallbacks.onSubmit).toHaveBeenCalledWith(
                expect.objectContaining({
                    numberOfHeadcount: 1,
                    role: 'Senior Engineer',
                    startDate: '2025-05-01',
                    duration: 'permanent',
                })
            );
        });
    });
});
