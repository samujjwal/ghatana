import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GrowthPlanDashboard } from '../GrowthPlanDashboard';
import { GoalSettingForm } from '../GoalSettingForm';
import { TimeLoggingPanel } from '../TimeLoggingPanel';
import { MentionInput, MentionsDisplay } from '../MentionInput';
import type { GrowthPlan, DevelopmentGoal, Skill } from '../GrowthPlanDashboard';
import type { UserMention } from '../MentionInput';
import type { TimeEntry } from '../TimeLoggingPanel';

/**
 * IC Features Integration Tests
 *
 * Test coverage:
 * - GrowthPlanDashboard (10 tests)
 * - GoalSettingForm (10 tests)
 * - TimeLoggingPanel (8 tests)
 * - MentionInput (7 tests)
 *
 * Total: 35 comprehensive integration tests
 */

describe('GrowthPlanDashboard', () => {
    const mockPlan: GrowthPlan = {
        id: 'plan-1',
        userId: 'user-1',
        currentRole: 'Software Engineer II',
        targetRole: 'Senior Software Engineer',
        startDate: '2024-01-01',
        reviewDate: '2024-06-01',
        visibility: 'manager',
        competencies: [
            {
                id: 'comp-1',
                name: 'Technical Excellence',
                description: 'Core technical skills',
                currentScore: 3,
                targetScore: 4,
                skills: [
                    {
                        id: 'skill-1',
                        name: 'TypeScript',
                        category: 'Programming',
                        currentLevel: 'intermediate',
                        targetLevel: 'advanced',
                        progress: 60,
                        endorsements: 5,
                        lastUpdated: '2024-03-01',
                    },
                ],
            },
        ],
        goals: [
            {
                id: 'goal-1',
                title: 'Master Microservices',
                description: 'Learn microservices architecture',
                category: 'technical',
                status: 'in-progress',
                progress: 60,
                targetDate: '2024-06-01',
                milestones: [
                    { id: 'm1', title: 'Complete course', completed: true, completedAt: '2024-02-01' },
                    { id: 'm2', title: 'Build demo app', completed: false, dueDate: '2024-05-01' },
                ],
                relatedSkills: ['skill-1'],
                createdAt: '2024-01-15',
                updatedAt: '2024-03-01',
            },
        ],
        completedGoals: 2,
        totalGoals: 5,
        overallProgress: 65,
    };

    const mockCallbacks = {
        onUpdateVisibility: vi.fn(),
        onCreateGoal: vi.fn(),
        onEditGoal: vi.fn(),
        onUpdateSkill: vi.fn(),
        onCompleteMilestone: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders growth plan with correct role progression', () => {
        render(<GrowthPlanDashboard plan={mockPlan} {...mockCallbacks} />);

        expect(screen.getByText('Software Engineer II')).toBeInTheDocument();
        expect(screen.getByText('Senior Software Engineer')).toBeInTheDocument();
    });

    it('displays overall progress correctly', () => {
        render(<GrowthPlanDashboard plan={mockPlan} {...mockCallbacks} />);

        expect(screen.getByText('Overall Progress')).toBeInTheDocument();
        expect(screen.getByText('65%')).toBeInTheDocument();
    });

    it('shows correct active goals count', () => {
        render(<GrowthPlanDashboard plan={mockPlan} {...mockCallbacks} />);

        const activeGoalsCard = screen.getByText('Active Goals').closest('div');
        expect(within(activeGoalsCard!).getByText('1')).toBeInTheDocument();
    });

    it('displays competency areas with scores', () => {
        render(<GrowthPlanDashboard plan={mockPlan} {...mockCallbacks} />);

        expect(screen.getByText('Technical Excellence')).toBeInTheDocument();
        expect(screen.getByText('3 / 5')).toBeInTheDocument();
    });

    it('renders skills with progress bars', () => {
        render(<GrowthPlanDashboard plan={mockPlan} {...mockCallbacks} />);

        expect(screen.getByText('TypeScript')).toBeInTheDocument();
        expect(screen.getByText('60%')).toBeInTheDocument();
        expect(screen.getByText('5 endorsements')).toBeInTheDocument();
    });

    it('displays development goals with milestones', () => {
        render(<GrowthPlanDashboard plan={mockPlan} {...mockCallbacks} />);

        expect(screen.getByText('Master Microservices')).toBeInTheDocument();
        expect(screen.getByText('Complete course')).toBeInTheDocument();
        expect(screen.getByText('Build demo app')).toBeInTheDocument();
    });

    it('handles visibility toggle correctly', async () => {
        render(<GrowthPlanDashboard plan={mockPlan} {...mockCallbacks} isEditable />);

        const visibilitySwitch = screen.getByRole('checkbox');
        await userEvent.click(visibilitySwitch);

        await waitFor(() => {
            expect(mockCallbacks.onUpdateVisibility).toHaveBeenCalledWith('public');
        });
    });

    it('calls onCompleteMilestone when milestone checkbox is clicked', async () => {
        render(<GrowthPlanDashboard plan={mockPlan} {...mockCallbacks} isEditable />);

        const milestoneCheckbox = screen.getByRole('checkbox', { name: /build demo app/i });
        await userEvent.click(milestoneCheckbox);

        await waitFor(() => {
            expect(mockCallbacks.onCompleteMilestone).toHaveBeenCalledWith('goal-1', 'm2');
        });
    });

    it('shows empty state when no goals exist', () => {
        const emptyPlan = { ...mockPlan, goals: [] };
        render(<GrowthPlanDashboard plan={emptyPlan} {...mockCallbacks} />);

        expect(screen.getByText('No development goals yet')).toBeInTheDocument();
    });

    it('calls onCreateGoal when Create Goal button is clicked', async () => {
        render(<GrowthPlanDashboard plan={mockPlan} {...mockCallbacks} isEditable />);

        const createButton = screen.getByRole('button', { name: /create goal/i });
        await userEvent.click(createButton);

        expect(mockCallbacks.onCreateGoal).toHaveBeenCalled();
    });
});

describe('GoalSettingForm', () => {
    const mockSkills = [
        { id: 'skill-1', name: 'TypeScript' },
        { id: 'skill-2', name: 'React' },
        { id: 'skill-3', name: 'Node.js' },
    ];

    const mockCallbacks = {
        onSubmit: vi.fn(),
        onCancel: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders form with all required fields', () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/category/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/status/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/target date/i)).toBeInTheDocument();
    });

    it('shows SMART criteria helper', () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        expect(screen.getByText(/SMART Goal Framework/i)).toBeInTheDocument();
        expect(screen.getByText(/Specific/i)).toBeInTheDocument();
        expect(screen.getByText(/Measurable/i)).toBeInTheDocument();
        expect(screen.getByText(/Achievable/i)).toBeInTheDocument();
        expect(screen.getByText(/Relevant/i)).toBeInTheDocument();
        expect(screen.getByText(/Time-bound/i)).toBeInTheDocument();
    });

    it('allows adding milestones', async () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        const milestoneInput = screen.getByPlaceholderText(/add a milestone/i);
        await userEvent.type(milestoneInput, 'Complete tutorial');

        const addButton = screen.getByRole('button', { name: /add milestone/i });
        await userEvent.click(addButton);

        expect(screen.getByText('Complete tutorial')).toBeInTheDocument();
    });

    it('allows removing milestones', async () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        // Add milestone
        const milestoneInput = screen.getByPlaceholderText(/add a milestone/i);
        await userEvent.type(milestoneInput, 'Test milestone');
        const addButton = screen.getByRole('button', { name: /add milestone/i });
        await userEvent.click(addButton);

        // Remove milestone
        const removeButton = screen.getAllByText('✕')[0];
        await userEvent.click(removeButton);

        await waitFor(() => {
            expect(screen.queryByText('Test milestone')).not.toBeInTheDocument();
        });
    });

    it('allows adding success criteria', async () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        const criterionInput = screen.getByPlaceholderText(/add a success criterion/i);
        await userEvent.type(criterionInput, 'Deploy to production');

        const addButton = screen.getByRole('button', { name: /add criterion/i });
        await userEvent.click(addButton);

        expect(screen.getByText(/Deploy to production/i)).toBeInTheDocument();
    });

    it('allows selecting related skills', async () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        const skillSelect = screen.getByLabelText(/select skill/i);
        await userEvent.click(skillSelect);

        const skillOption = await screen.findByText('TypeScript');
        await userEvent.click(skillOption);

        const addSkillButton = screen.getByRole('button', { name: /add skill/i });
        await userEvent.click(addSkillButton);

        expect(screen.getByText('TypeScript')).toBeInTheDocument();
    });

    it('validates SMART criteria in real-time', async () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        // Initially not SMART
        expect(screen.getByText(/This goal does not meet all SMART criteria/i)).toBeInTheDocument();

        // Fill required fields
        await userEvent.type(screen.getByLabelText(/title/i), 'Test Goal Title');
        await userEvent.type(screen.getByLabelText(/description/i), 'Test goal description text');

        // Add milestone
        const milestoneInput = screen.getByPlaceholderText(/add a milestone/i);
        await userEvent.type(milestoneInput, 'Milestone 1');
        await userEvent.click(screen.getByRole('button', { name: /add milestone/i }));

        // Select category
        const categorySelect = screen.getByLabelText(/category/i);
        await userEvent.click(categorySelect);
        const categoryOption = await screen.findByText('Technical');
        await userEvent.click(categoryOption);

        // Add skill
        const skillSelect = screen.getByLabelText(/select skill/i);
        await userEvent.click(skillSelect);
        const skillOption = await screen.findByText('TypeScript');
        await userEvent.click(skillOption);
        await userEvent.click(screen.getByRole('button', { name: /add skill/i }));

        // Set target date
        const dateInput = screen.getByLabelText(/target date/i);
        await userEvent.type(dateInput, '2024-12-31');

        await waitFor(() => {
            expect(screen.getByText(/Great! This goal meets all SMART criteria/i)).toBeInTheDocument();
        });
    });

    it('applies template correctly', async () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        const templateButton = screen.getByRole('button', { name: /technical/i });
        await userEvent.click(templateButton);

        await waitFor(() => {
            expect(screen.getByDisplayValue(/Master/i)).toBeInTheDocument();
        });
    });

    it('disables submit when form is invalid', () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        const submitButton = screen.getByRole('button', { name: /create goal/i });
        expect(submitButton).toBeDisabled();
    });

    it('calls onSubmit with correct data when valid', async () => {
        render(<GoalSettingForm availableSkills={mockSkills} {...mockCallbacks} />);

        // Fill form
        await userEvent.type(screen.getByLabelText(/title/i), 'Test Goal');
        await userEvent.type(screen.getByLabelText(/description/i), 'Test description here');

        const milestoneInput = screen.getByPlaceholderText(/add a milestone/i);
        await userEvent.type(milestoneInput, 'Milestone 1');
        await userEvent.click(screen.getByRole('button', { name: /add milestone/i }));

        const dateInput = screen.getByLabelText(/target date/i);
        await userEvent.type(dateInput, '2024-12-31');

        const submitButton = screen.getByRole('button', { name: /create goal/i });
        await userEvent.click(submitButton);

        await waitFor(() => {
            expect(mockCallbacks.onSubmit).toHaveBeenCalledWith(
                expect.objectContaining({
                    title: 'Test Goal',
                    description: 'Test description here',
                    milestones: expect.arrayContaining(['Milestone 1']),
                    targetDate: '2024-12-31',
                })
            );
        });
    });
});

describe('TimeLoggingPanel', () => {
    const mockTasks = [
        { id: 'task-1', title: 'Implement feature X' },
        { id: 'task-2', title: 'Fix bug Y' },
    ];

    const mockEntries: TimeEntry[] = [
        {
            id: 'e1',
            date: new Date().toISOString().split('T')[0],
            taskTitle: 'Implement feature X',
            category: 'development',
            hours: 4,
            description: 'Built new API endpoint',
        },
    ];

    const mockCallbacks = {
        onAddEntry: vi.fn(),
        onUpdateEntry: vi.fn(),
        onDeleteEntry: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders time logging form', () => {
        render(<TimeLoggingPanel availableTasks={mockTasks} {...mockCallbacks} />);

        expect(screen.getByLabelText(/date/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/task/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/category/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/hours/i)).toBeInTheDocument();
    });

    it('displays today and week tabs', () => {
        render(<TimeLoggingPanel availableTasks={mockTasks} {...mockCallbacks} />);

        expect(screen.getByRole('tab', { name: /today/i })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: /this week/i })).toBeInTheDocument();
    });

    it('shows today summary stats', () => {
        render(<TimeLoggingPanel existingEntries={mockEntries} {...mockCallbacks} />);

        expect(screen.getByText('Total Hours Today')).toBeInTheDocument();
        expect(screen.getByText('Entries Logged')).toBeInTheDocument();
        expect(screen.getByText('Top Category')).toBeInTheDocument();
    });

    it('allows adding new time entry', async () => {
        render(<TimeLoggingPanel availableTasks={mockTasks} {...mockCallbacks} />);

        // Select task
        const taskSelect = screen.getByLabelText(/task/i);
        await userEvent.click(taskSelect);
        const taskOption = await screen.findByText('Implement feature X');
        await userEvent.click(taskOption);

        // Enter hours
        const hoursInput = screen.getByLabelText(/hours/i);
        await userEvent.clear(hoursInput);
        await userEvent.type(hoursInput, '3');

        // Submit
        const addButton = screen.getByRole('button', { name: /add entry/i });
        await userEvent.click(addButton);

        await waitFor(() => {
            expect(mockCallbacks.onAddEntry).toHaveBeenCalledWith(
                expect.objectContaining({
                    taskTitle: 'Implement feature X',
                    hours: 3,
                })
            );
        });
    });

    it('displays existing time entries', () => {
        render(<TimeLoggingPanel existingEntries={mockEntries} {...mockCallbacks} />);

        expect(screen.getByText('Implement feature X')).toBeInTheDocument();
        expect(screen.getByText('4h')).toBeInTheDocument();
    });

    it('allows deleting time entry', async () => {
        render(<TimeLoggingPanel existingEntries={mockEntries} {...mockCallbacks} />);

        const deleteButton = screen.getByText('✕');
        await userEvent.click(deleteButton);

        await waitFor(() => {
            expect(mockCallbacks.onDeleteEntry).toHaveBeenCalledWith('e1');
        });
    });

    it('switches to week view and shows weekly stats', async () => {
        render(<TimeLoggingPanel existingEntries={mockEntries} {...mockCallbacks} />);

        const weekTab = screen.getByRole('tab', { name: /this week/i });
        await userEvent.click(weekTab);

        await waitFor(() => {
            expect(screen.getByText('Total Hours This Week')).toBeInTheDocument();
            expect(screen.getByText('Daily Average')).toBeInTheDocument();
            expect(screen.getByText('Time by Category')).toBeInTheDocument();
        });
    });

    it('shows weekly progress toward target', async () => {
        render(
            <TimeLoggingPanel
                existingEntries={mockEntries}
                targetHoursPerWeek={40}
                {...mockCallbacks}
            />
        );

        const weekTab = screen.getByRole('tab', { name: /this week/i });
        await userEvent.click(weekTab);

        await waitFor(() => {
            expect(screen.getByText(/of 40h target/i)).toBeInTheDocument();
        });
    });
});

describe('MentionInput', () => {
    const mockUsers: UserMention[] = [
        {
            id: 'u1',
            name: 'Alice Johnson',
            email: 'alice@company.com',
            role: 'Engineer',
        },
        {
            id: 'u2',
            name: 'Bob Smith',
            email: 'bob@company.com',
            role: 'Manager',
        },
    ];

    const mockOnChange = vi.fn();
    const mockOnMentionSelect = vi.fn();

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders text input correctly', () => {
        render(<MentionInput value="" onChange={mockOnChange} users={mockUsers} />);

        expect(screen.getByPlaceholderText(/Type @ to mention/i)).toBeInTheDocument();
    });

    it('shows user suggestions when typing @', async () => {
        render(<MentionInput value="" onChange={mockOnChange} users={mockUsers} />);

        const input = screen.getByPlaceholderText(/Type @ to mention/i);
        await userEvent.type(input, '@ali');

        await waitFor(() => {
            expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
        });
    });

    it('filters users based on search query', async () => {
        render(<MentionInput value="" onChange={mockOnChange} users={mockUsers} />);

        const input = screen.getByPlaceholderText(/Type @ to mention/i);
        await userEvent.type(input, '@bob');

        await waitFor(() => {
            expect(screen.getByText('Bob Smith')).toBeInTheDocument();
            expect(screen.queryByText('Alice Johnson')).not.toBeInTheDocument();
        });
    });

    it('selects user on click', async () => {
        render(<MentionInput value="" onChange={mockOnChange} users={mockUsers} />);

        const input = screen.getByPlaceholderText(/Type @ to mention/i);
        await userEvent.type(input, '@ali');

        await waitFor(() => {
            expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
        });

        const userOption = screen.getByText('Alice Johnson');
        await userEvent.click(userOption);

        await waitFor(() => {
            expect(mockOnChange).toHaveBeenCalled();
            expect(mockOnMentionSelect).toHaveBeenCalledWith(mockUsers[0]);
        });
    });

    it('supports keyboard navigation', async () => {
        render(
            <MentionInput
                value=""
                onChange={mockOnChange}
                users={mockUsers}
                onMentionSelect={mockOnMentionSelect}
            />
        );

        const input = screen.getByPlaceholderText(/Type @ to mention/i);
        await userEvent.type(input, '@');

        await waitFor(() => {
            expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
        });

        // Navigate down
        fireEvent.keyDown(input, { key: 'ArrowDown' });

        // Select with Enter
        fireEvent.keyDown(input, { key: 'Enter' });

        await waitFor(() => {
            expect(mockOnMentionSelect).toHaveBeenCalled();
        });
    });

    it('closes suggestions on Escape', async () => {
        render(<MentionInput value="" onChange={mockOnChange} users={mockUsers} />);

        const input = screen.getByPlaceholderText(/Type @ to mention/i);
        await userEvent.type(input, '@');

        await waitFor(() => {
            expect(screen.getByText('Alice Johnson')).toBeInTheDocument();
        });

        fireEvent.keyDown(input, { key: 'Escape' });

        await waitFor(() => {
            expect(screen.queryByText('Alice Johnson')).not.toBeInTheDocument();
        });
    });

    it('displays mentions as formatted text', () => {
        const textWithMention = 'Hey @[Alice Johnson](u1), can you review?';
        const { container } = render(
            <MentionsDisplay text={textWithMention} onMentionClick={vi.fn()} />
        );

        expect(container.textContent).toContain('@Alice Johnson');
        expect(screen.getByText('Hey')).toBeInTheDocument();
        expect(screen.getByText(', can you review?')).toBeInTheDocument();
    });
});
