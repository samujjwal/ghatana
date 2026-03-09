import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PersonalDashboard, type Task, type Activity, type Notification, type Goal } from '../PersonalDashboard';
import { GoalTracker, type Objective, type KeyResult } from '../GoalTracker';
import { SkillDevelopment, type Skill, type LearningPath, type Certification } from '../SkillDevelopment';

// ==================== PERSONAL DASHBOARD TESTS ====================

describe('PersonalDashboard', () => {
    const mockUser = {
        name: 'Alex Johnson',
        email: 'alex.johnson@acme.com',
        role: 'Senior Software Engineer',
        department: 'Engineering',
    };

    const mockTasks: Task[] = [
        {
            id: 'task-1',
            title: 'Complete API integration',
            description: 'Implement REST endpoints',
            status: 'in_progress',
            priority: 'high',
            dueDate: '2025-12-15T17:00:00Z',
            project: 'User Management',
        },
        {
            id: 'task-2',
            title: 'Review pull request',
            status: 'not_started',
            priority: 'urgent',
            dueDate: '2025-12-12T12:00:00Z',
        },
    ];

    const mockCallbacks = {
        onTaskClick: vi.fn(),
        onTaskStatusChange: vi.fn(),
        onNotificationRead: vi.fn(),
        onGoalClick: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render welcome message with user name', () => {
        render(<PersonalDashboard currentUser={mockUser} />);

        expect(screen.getByText(/Welcome back, Alex!/i)).toBeInTheDocument();
        expect(screen.getByText(/Senior Software Engineer/i)).toBeInTheDocument();
        expect(screen.getByText(/Engineering/i)).toBeInTheDocument();
    });

    it('should display quick stats cards', () => {
        render(<PersonalDashboard tasks={mockTasks} />);

        expect(screen.getByText(/Active Tasks/i)).toBeInTheDocument();
        expect(screen.getByText(/Goals Progress/i)).toBeInTheDocument();
        expect(screen.getByText(/Notifications/i)).toBeInTheDocument();
        expect(screen.getByText(/This Week/i)).toBeInTheDocument();
    });

    it('should show active tasks count', () => {
        render(<PersonalDashboard tasks={mockTasks} />);

        const activeTasksCard = screen.getByText(/Active Tasks/i).closest('div')?.parentElement;
        expect(activeTasksCard).toHaveTextContent('1'); // 1 in_progress task
        expect(activeTasksCard).toHaveTextContent('1 pending'); // 1 not_started task
    });

    it('should display quick actions', () => {
        render(<PersonalDashboard />);

        expect(screen.getByRole('button', { name: /New Task/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Update Goal/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Log Time/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Add Skill/i })).toBeInTheDocument();
    });

    it('should filter tasks by all/today/upcoming/overdue', async () => {
        const user = userEvent.setup();
        render(<PersonalDashboard tasks={mockTasks} />);

        // Default shows all tasks
        expect(screen.getAllByText(/Complete API integration/i)).toHaveLength(1);

        // Click upcoming filter
        const upcomingFilter = screen.getByRole('button', { name: /Upcoming/i });
        await user.click(upcomingFilter);

        // Should still show tasks (within 7 days)
        await waitFor(() => {
            expect(screen.getByText(/Complete API integration/i)).toBeInTheDocument();
        });
    });

    it('should display tasks with priority and status chips', () => {
        render(<PersonalDashboard tasks={mockTasks} />);

        expect(screen.getByText('HIGH')).toBeInTheDocument();
        expect(screen.getByText('URGENT')).toBeInTheDocument();
        expect(screen.getByText('IN_PROGRESS')).toBeInTheDocument();
        expect(screen.getByText('NOT_STARTED')).toBeInTheDocument();
    });

    it('should call onTaskClick when task is clicked', async () => {
        const user = userEvent.setup();
        render(<PersonalDashboard tasks={mockTasks} {...mockCallbacks} />);

        const taskItem = screen.getByText(/Complete API integration/i).closest('li');
        if (taskItem) {
            await user.click(taskItem);
            expect(mockCallbacks.onTaskClick).toHaveBeenCalledWith('task-1');
        }
    });

    it('should toggle task status when checkbox is clicked', async () => {
        const user = userEvent.setup();
        render(<PersonalDashboard tasks={mockTasks} {...mockCallbacks} />);

        const checkboxes = screen.getAllByRole('button').filter(btn =>
            btn.querySelector('svg')
        );

        if (checkboxes.length > 0) {
            await user.click(checkboxes[0]);
            expect(mockCallbacks.onTaskStatusChange).toHaveBeenCalled();
        }
    });

    it('should display task details', () => {
        render(<PersonalDashboard tasks={mockTasks} />);

        expect(screen.getByText(/Implement REST endpoints/i)).toBeInTheDocument();
        expect(screen.getByText(/User Management/i)).toBeInTheDocument();
    });

    it('should show overdue indicator for past due tasks', () => {
        const overdueTasks: Task[] = [
            {
                id: 'task-overdue',
                title: 'Overdue task',
                status: 'not_started',
                priority: 'high',
                dueDate: '2025-11-01T17:00:00Z', // Past date
            },
        ];

        render(<PersonalDashboard tasks={overdueTasks} />);

        expect(screen.getByText(/Overdue/i)).toBeInTheDocument();
    });

    it('should display goals with progress bars', () => {
        const mockGoals: Goal[] = [
            {
                id: 'goal-1',
                title: 'Q4 OKR: Reduce API latency',
                type: 'okr',
                progress: 75,
                dueDate: '2025-12-31T23:59:59Z',
                status: 'on_track',
            },
        ];

        render(<PersonalDashboard goals={mockGoals} />);

        expect(screen.getByText(/Q4 OKR: Reduce API latency/i)).toBeInTheDocument();
        expect(screen.getByText('75%')).toBeInTheDocument();
        expect(screen.getByText('ON_TRACK')).toBeInTheDocument();
    });

    it('should call onGoalClick when goal is clicked', async () => {
        const user = userEvent.setup();
        const mockGoals: Goal[] = [
            {
                id: 'goal-1',
                title: 'Q4 OKR',
                type: 'okr',
                progress: 75,
                dueDate: '2025-12-31T23:59:59Z',
                status: 'on_track',
            },
        ];

        render(<PersonalDashboard goals={mockGoals} {...mockCallbacks} />);

        const goalItem = screen.getByText(/Q4 OKR/i).closest('li');
        if (goalItem) {
            await user.click(goalItem);
            expect(mockCallbacks.onGoalClick).toHaveBeenCalledWith('goal-1');
        }
    });

    it('should display notifications with unread indicator', () => {
        const mockNotifications: Notification[] = [
            {
                id: 'notif-1',
                type: 'warning',
                title: 'Deadline Approaching',
                message: 'Task due tomorrow',
                timestamp: '2025-12-11T08:00:00Z',
                read: false,
            },
        ];

        render(<PersonalDashboard notifications={mockNotifications} />);

        expect(screen.getByText(/Deadline Approaching/i)).toBeInTheDocument();
        expect(screen.getByText(/Task due tomorrow/i)).toBeInTheDocument();
        expect(screen.getByText('NEW')).toBeInTheDocument();
    });

    it('should call onNotificationAction when action button is clicked', async () => {
        const user = userEvent.setup();
        const mockNotifications: Notification[] = [
            {
                id: 'notif-1',
                type: 'info',
                title: 'New Task',
                message: 'You have a new task',
                timestamp: '2025-12-11T08:00:00Z',
                read: false,
                actionLabel: 'View Task',
                actionUrl: '/tasks/1',
            },
        ];

        render(<PersonalDashboard notifications={mockNotifications} {...mockCallbacks} />);

        const viewButton = screen.getByRole('button', { name: /View Task/i });
        await user.click(viewButton);

        expect(mockCallbacks.onNotificationAction).toHaveBeenCalledWith('notif-1', '/tasks/1');
    });

    it('should display recent activity with icons', () => {
        const mockActivity: Activity[] = [
            {
                id: 'act-1',
                type: 'task_completed',
                title: 'Task Completed',
                description: 'Set up CI/CD pipeline',
                timestamp: '2025-12-11T10:30:00Z',
            },
        ];

        render(<PersonalDashboard recentActivity={mockActivity} />);

        expect(screen.getByText('Task Completed')).toBeInTheDocument();
        expect(screen.getByText(/Set up CI\/CD pipeline/i)).toBeInTheDocument();
    });
});

// ==================== GOAL TRACKER TESTS ====================

describe('GoalTracker', () => {
    const mockObjectives: Objective[] = [
        {
            id: 'obj-1',
            title: 'Reduce API Response Time',
            description: 'Improve API performance',
            type: 'individual',
            category: 'performance',
            priority: 'high',
            startDate: '2025-10-01T00:00:00Z',
            endDate: '2025-12-31T23:59:59Z',
            progress: 75,
            status: 'in_progress',
            keyResults: [
                {
                    id: 'kr-1',
                    description: 'Reduce average API latency',
                    targetValue: 100,
                    currentValue: 75,
                    unit: 'ms',
                    progress: 75,
                    status: 'on_track',
                },
            ],
            milestones: [
                {
                    id: 'ms-1',
                    title: 'Implement Redis caching',
                    dueDate: '2025-10-31T23:59:59Z',
                    completed: true,
                },
            ],
        },
    ];

    const mockCallbacks = {
        onCreateObjective: vi.fn(),
        onUpdateObjective: vi.fn(),
        onDeleteObjective: vi.fn(),
        onUpdateKeyResult: vi.fn(),
        onCompleteMilestone: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render header with New Objective button', () => {
        render(<GoalTracker />);

        expect(screen.getByText(/Goals & OKRs/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /New Objective/i })).toBeInTheDocument();
    });

    it('should display tabs with counts', () => {
        render(<GoalTracker objectives={mockObjectives} />);

        expect(screen.getByRole('tab', { name: /All \(1\)/i })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: /In Progress \(1\)/i })).toBeInTheDocument();
    });

    it('should display objectives with chips', () => {
        render(<GoalTracker objectives={mockObjectives} />);

        expect(screen.getByText(/Reduce API Response Time/i)).toBeInTheDocument();
        expect(screen.getByText('INDIVIDUAL')).toBeInTheDocument();
        expect(screen.getByText('HIGH')).toBeInTheDocument();
        expect(screen.getByText('IN_PROGRESS')).toBeInTheDocument();
    });

    it('should show overall progress with percentage', () => {
        render(<GoalTracker objectives={mockObjectives} />);

        expect(screen.getByText('75%')).toBeInTheDocument();
        expect(screen.getByText(/Overall Progress/i)).toBeInTheDocument();
    });

    it('should display key results with progress', () => {
        render(<GoalTracker objectives={mockObjectives} />);

        expect(screen.getByText(/Reduce average API latency/i)).toBeInTheDocument();
        expect(screen.getByText(/Current: 75ms \/ Target: 100ms/i)).toBeInTheDocument();
        expect(screen.getByText('ON_TRACK')).toBeInTheDocument();
    });

    it('should display milestones with completion status', () => {
        render(<GoalTracker objectives={mockObjectives} />);

        expect(screen.getByText(/Implement Redis caching/i)).toBeInTheDocument();
    });

    it('should open create dialog when New Objective is clicked', async () => {
        const user = userEvent.setup();
        render(<GoalTracker />);

        await user.click(screen.getByRole('button', { name: /New Objective/i }));

        await waitFor(() => {
            expect(screen.getByText('Create New Objective')).toBeInTheDocument();
        });
    });

    it('should have required fields in create dialog', async () => {
        const user = userEvent.setup();
        render(<GoalTracker />);

        await user.click(screen.getByRole('button', { name: /New Objective/i }));

        await waitFor(() => {
            expect(screen.getByLabelText(/Objective Title/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/Description/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/Type/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/Category/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/Priority/i)).toBeInTheDocument();
        });
    });

    it('should disable create button when required fields are empty', async () => {
        const user = userEvent.setup();
        render(<GoalTracker {...mockCallbacks} />);

        await user.click(screen.getByRole('button', { name: /New Objective/i }));

        await waitFor(() => {
            const createButton = screen.getByRole('button', { name: /Create Objective/i });
            expect(createButton).toBeDisabled();
        });
    });

    it('should call onCreateObjective when form is submitted', async () => {
        const user = userEvent.setup();
        render(<GoalTracker {...mockCallbacks} />);

        await user.click(screen.getByRole('button', { name: /New Objective/i }));

        await waitFor(() => screen.getByLabelText(/Objective Title/i));

        await user.type(screen.getByLabelText(/Objective Title/i), 'New Goal');
        await user.type(screen.getByLabelText(/Description/i), 'Goal description');

        const createButton = screen.getByRole('button', { name: /Create Objective/i });
        await user.click(createButton);

        expect(mockCallbacks.onCreateObjective).toHaveBeenCalled();
    });

    it('should open edit dialog when edit icon is clicked', async () => {
        const user = userEvent.setup();
        render(<GoalTracker objectives={mockObjectives} />);

        const editButtons = screen.getAllByRole('button').filter(btn =>
            btn.querySelector('svg[data-testid="EditIcon"]')
        );

        if (editButtons.length > 0) {
            await user.click(editButtons[0]);

            await waitFor(() => {
                expect(screen.getByText('Edit Objective')).toBeInTheDocument();
            });
        }
    });

    it('should call onDeleteObjective when delete is clicked', async () => {
        const user = userEvent.setup();
        render(<GoalTracker objectives={mockObjectives} {...mockCallbacks} />);

        const deleteButtons = screen.getAllByRole('button').filter(btn =>
            btn.querySelector('svg[data-testid="DeleteIcon"]')
        );

        if (deleteButtons.length > 0) {
            await user.click(deleteButtons[0]);
            expect(mockCallbacks.onDeleteObjective).toHaveBeenCalledWith('obj-1');
        }
    });

    it('should open update key result dialog when edit KR is clicked', async () => {
        const user = userEvent.setup();
        render(<GoalTracker objectives={mockObjectives} />);

        const editKRButtons = screen.getAllByRole('button').filter(btn =>
            btn.querySelector('svg[data-testid="EditIcon"]')
        );

        if (editKRButtons.length > 1) {
            await user.click(editKRButtons[1]); // Second edit button is for KR

            await waitFor(() => {
                expect(screen.getByText('Update Key Result')).toBeInTheDocument();
            });
        }
    });

    it('should call onCompleteMilestone when milestone is marked complete', async () => {
        const incompleteMilestone: Objective = {
            ...mockObjectives[0],
            milestones: [
                {
                    id: 'ms-2',
                    title: 'Deploy CDN',
                    dueDate: '2025-12-15T23:59:59Z',
                    completed: false,
                },
            ],
        };

        const user = userEvent.setup();
        render(<GoalTracker objectives={[incompleteMilestone]} {...mockCallbacks} />);

        const completeButton = screen.getByRole('button', { name: /Complete/i });
        await user.click(completeButton);

        expect(mockCallbacks.onCompleteMilestone).toHaveBeenCalledWith('obj-1', 'ms-2');
    });

    it('should filter objectives by tab selection', async () => {
        const completedObj: Objective = {
            ...mockObjectives[0],
            id: 'obj-2',
            status: 'completed',
        };

        const user = userEvent.setup();
        render(<GoalTracker objectives={[...mockObjectives, completedObj]} />);

        // Switch to Completed tab
        await user.click(screen.getByRole('tab', { name: /Completed/i }));

        await waitFor(() => {
            expect(screen.queryByText(/Reduce API Response Time/i)).not.toBeInTheDocument();
        });
    });

    it('should show empty state when no objectives', () => {
        render(<GoalTracker objectives={[]} />);

        expect(screen.getByText(/No objectives found/i)).toBeInTheDocument();
    });
});

// ==================== SKILL DEVELOPMENT TESTS ====================

describe('SkillDevelopment', () => {
    const mockSkills: Skill[] = [
        {
            id: 'skill-1',
            name: 'React',
            category: 'technical',
            proficiency: 4,
            yearsOfExperience: 3,
            endorsements: 12,
            verified: true,
        },
        {
            id: 'skill-2',
            name: 'Leadership',
            category: 'soft',
            proficiency: 3,
            yearsOfExperience: 2,
            endorsements: 7,
        },
    ];

    const mockLearningPaths: LearningPath[] = [
        {
            id: 'path-1',
            title: 'AWS Solutions Architect',
            description: 'Master AWS cloud services',
            category: 'tools',
            targetSkills: ['skill-3'],
            progress: 60,
            status: 'in_progress',
            estimatedHours: 100,
            actualHours: 45,
            resources: [
                {
                    id: 'res-1',
                    title: 'AWS Course',
                    type: 'course',
                    completed: true,
                },
                {
                    id: 'res-2',
                    title: 'Practice Exam',
                    type: 'certification',
                    completed: false,
                },
            ],
        },
    ];

    const mockCertifications: Certification[] = [
        {
            id: 'cert-1',
            name: 'AWS Solutions Architect',
            provider: 'Amazon Web Services',
            category: 'tools',
            status: 'in_progress',
            relatedSkills: ['skill-3'],
        },
    ];

    const mockCallbacks = {
        onAddSkill: vi.fn(),
        onUpdateSkill: vi.fn(),
        onDeleteSkill: vi.fn(),
        onCompleteResource: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render header with Add Skill button', () => {
        render(<SkillDevelopment />);

        expect(screen.getByText(/Skill Development/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Add Skill/i })).toBeInTheDocument();
    });

    it('should display three tabs', () => {
        render(<SkillDevelopment />);

        expect(screen.getByRole('tab', { name: /Skill Matrix/i })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: /Learning Paths/i })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: /Certifications/i })).toBeInTheDocument();
    });

    it('should display skill overview cards', () => {
        render(<SkillDevelopment skills={mockSkills} />);

        expect(screen.getByText(/Total Skills/i)).toBeInTheDocument();
        expect(screen.getByText(/Expert Level/i)).toBeInTheDocument();
        expect(screen.getByText(/Endorsements/i)).toBeInTheDocument();
        expect(screen.getByText(/Learning Progress/i)).toBeInTheDocument();
    });

    it('should show total skills count', () => {
        render(<SkillDevelopment skills={mockSkills} />);

        const totalSkillsCard = screen.getByText(/Total Skills/i).closest('div')?.parentElement;
        expect(totalSkillsCard).toHaveTextContent('2');
        expect(totalSkillsCard).toHaveTextContent('1 verified');
    });

    it('should display skills grouped by category', () => {
        render(<SkillDevelopment skills={mockSkills} />);

        expect(screen.getByText(/Technical Skills/i)).toBeInTheDocument();
        expect(screen.getByText(/Soft Skills/i)).toBeInTheDocument();
    });

    it('should show skill details in table', () => {
        render(<SkillDevelopment skills={mockSkills} />);

        expect(screen.getByText('React')).toBeInTheDocument();
        expect(screen.getByText('Leadership')).toBeInTheDocument();
        expect(screen.getByText('3 years')).toBeInTheDocument();
        expect(screen.getByText('2 years')).toBeInTheDocument();
    });

    it('should display proficiency rating', () => {
        render(<SkillDevelopment skills={mockSkills} />);

        expect(screen.getByText('Expert')).toBeInTheDocument();
        expect(screen.getByText('Advanced')).toBeInTheDocument();
    });

    it('should show verified badge for verified skills', () => {
        render(<SkillDevelopment skills={mockSkills} />);

        const verifiedIcons = screen.getAllByTestId('VerifiedIcon');
        expect(verifiedIcons.length).toBeGreaterThan(0);
    });

    it('should open add skill dialog when Add Skill is clicked', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment />);

        await user.click(screen.getByRole('button', { name: /Add Skill/i }));

        await waitFor(() => {
            expect(screen.getByText('Add New Skill')).toBeInTheDocument();
        });
    });

    it('should have required fields in add skill dialog', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment />);

        await user.click(screen.getByRole('button', { name: /Add Skill/i }));

        await waitFor(() => {
            expect(screen.getByLabelText(/Skill Name/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/Category/i)).toBeInTheDocument();
            expect(screen.getByText(/Proficiency Level/i)).toBeInTheDocument();
        });
    });

    it('should call onAddSkill when skill is added', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment {...mockCallbacks} />);

        await user.click(screen.getByRole('button', { name: /Add Skill/i }));

        await waitFor(() => screen.getByLabelText(/Skill Name/i));

        await user.type(screen.getByLabelText(/Skill Name/i), 'TypeScript');

        // Select proficiency using rating component
        const ratings = screen.getAllByRole('radio');
        if (ratings.length > 0) {
            await user.click(ratings[3]); // 4 stars
        }

        const addButton = screen.getByRole('button', { name: /Add Skill/i });
        await user.click(addButton);

        expect(mockCallbacks.onAddSkill).toHaveBeenCalled();
    });

    it('should display learning paths on tab switch', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment learningPaths={mockLearningPaths} />);

        await user.click(screen.getByRole('tab', { name: /Learning Paths/i }));

        await waitFor(() => {
            expect(screen.getByText('AWS Solutions Architect')).toBeInTheDocument();
            expect(screen.getByText(/Master AWS cloud services/i)).toBeInTheDocument();
        });
    });

    it('should show learning path progress', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment learningPaths={mockLearningPaths} />);

        await user.click(screen.getByRole('tab', { name: /Learning Paths/i }));

        await waitFor(() => {
            expect(screen.getByText('60%')).toBeInTheDocument();
            expect(screen.getByText('IN_PROGRESS')).toBeInTheDocument();
        });
    });

    it('should display learning resources with completion status', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment learningPaths={mockLearningPaths} />);

        await user.click(screen.getByRole('tab', { name: /Learning Paths/i }));

        await waitFor(() => {
            expect(screen.getByText('AWS Course')).toBeInTheDocument();
            expect(screen.getByText('Practice Exam')).toBeInTheDocument();
        });
    });

    it('should call onCompleteResource when resource is marked complete', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment learningPaths={mockLearningPaths} {...mockCallbacks} />);

        await user.click(screen.getByRole('tab', { name: /Learning Paths/i }));

        await waitFor(() => screen.getByRole('button', { name: /Mark Complete/i }));

        const completeButton = screen.getByRole('button', { name: /Mark Complete/i });
        await user.click(completeButton);

        expect(mockCallbacks.onCompleteResource).toHaveBeenCalledWith('path-1', 'res-2');
    });

    it('should display certifications on tab switch', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment certifications={mockCertifications} />);

        await user.click(screen.getByRole('tab', { name: /Certifications/i }));

        await waitFor(() => {
            expect(screen.getByText('AWS Solutions Architect')).toBeInTheDocument();
            expect(screen.getByText('Amazon Web Services')).toBeInTheDocument();
        });
    });

    it('should show certification status', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment certifications={mockCertifications} />);

        await user.click(screen.getByRole('tab', { name: /Certifications/i }));

        await waitFor(() => {
            expect(screen.getByText('IN_PROGRESS')).toBeInTheDocument();
        });
    });

    it('should show empty state when no learning paths', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment learningPaths={[]} />);

        await user.click(screen.getByRole('tab', { name: /Learning Paths/i }));

        await waitFor(() => {
            expect(screen.getByText(/No learning paths yet/i)).toBeInTheDocument();
        });
    });

    it('should call onDeleteSkill when delete is clicked', async () => {
        const user = userEvent.setup();
        render(<SkillDevelopment skills={mockSkills} {...mockCallbacks} />);

        const deleteButtons = screen.getAllByRole('button').filter(btn =>
            btn.querySelector('svg[data-testid="DeleteIcon"]')
        );

        if (deleteButtons.length > 0) {
            await user.click(deleteButtons[0]);
            expect(mockCallbacks.onDeleteSkill).toHaveBeenCalled();
        }
    });
});
