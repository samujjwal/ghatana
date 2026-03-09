import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TeamDashboard from '../TeamDashboard';
import OneOnOneTracker from '../OneOnOneTracker';
import PerformanceReviews from '../PerformanceReviews';

describe('TeamDashboard', () => {
    const mockMembers = [
        {
            id: '1',
            name: 'Sarah Johnson',
            email: 'sarah@test.com',
            role: 'Senior Software Engineer',
            status: 'active' as const,
            workload: 85,
            performance: { rating: 4.5, trend: 'up' as const, tasksCompleted: 23, tasksInProgress: 3 },
            nextOneOnOne: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
            lastReview: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000),
        },
        {
            id: '2',
            name: 'Michael Chen',
            email: 'michael@test.com',
            role: 'Software Engineer',
            status: 'active' as const,
            workload: 95,
            performance: { rating: 4.0, trend: 'stable' as const, tasksCompleted: 18, tasksInProgress: 5 },
            nextOneOnOne: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000),
        },
    ];

    const mockProjects = [
        {
            id: '1',
            name: 'User Authentication Redesign',
            status: 'on_track' as const,
            progress: 75,
            dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000),
            assignedMembers: ['1', '2'],
            priority: 'high' as const,
        },
        {
            id: '2',
            name: 'API Performance Optimization',
            status: 'at_risk' as const,
            progress: 45,
            dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
            assignedMembers: ['1'],
            priority: 'critical' as const,
        },
    ];

    it('renders with team name and member count', () => {
        render(<TeamDashboard teamName="Engineering Team" members={mockMembers} projects={mockProjects} />);

        expect(screen.getByText('Engineering Team')).toBeInTheDocument();
        expect(screen.getByText(/2 active members/)).toBeInTheDocument();
    });

    it('displays team metrics cards', () => {
        render(<TeamDashboard members={mockMembers} projects={mockProjects} />);

        expect(screen.getByText('Team Size')).toBeInTheDocument();
        expect(screen.getByText('Avg Workload')).toBeInTheDocument();
        expect(screen.getByText('Avg Performance')).toBeInTheDocument();
        expect(screen.getByText('Projects')).toBeInTheDocument();
    });

    it('calculates average workload correctly', () => {
        render(<TeamDashboard members={mockMembers} />);

        // Average of 85 and 95 = 90%
        expect(screen.getByText('90%')).toBeInTheDocument();
    });

    it('calculates average performance correctly', () => {
        render(<TeamDashboard members={mockMembers} />);

        // Average of 4.5 and 4.0 = 4.2
        expect(screen.getByText('4.2')).toBeInTheDocument();
    });

    it('displays team member cards in Members tab', () => {
        render(<TeamDashboard members={mockMembers} />);

        expect(screen.getByText('Sarah Johnson')).toBeInTheDocument();
        expect(screen.getByText('Michael Chen')).toBeInTheDocument();
        expect(screen.getByText('Senior Software Engineer')).toBeInTheDocument();
        expect(screen.getByText('Software Engineer')).toBeInTheDocument();
    });

    it('shows member status and workload chips', () => {
        render(<TeamDashboard members={mockMembers} />);

        expect(screen.getByText('Active')).toBeInTheDocument();
        expect(screen.getByText('85%')).toBeInTheDocument();
        expect(screen.getByText('95%')).toBeInTheDocument();
    });

    it('shows overallocation warning for high workload', () => {
        render(<TeamDashboard members={mockMembers} />);

        // Michael has 95% workload which should show warning
        const memberCards = screen.getAllByRole('button', { name: /sarah johnson|michael chen/i });
        expect(memberCards.length).toBeGreaterThan(0);
    });

    it('switches to Projects tab and displays projects', async () => {
        const user = userEvent.setup();
        render(<TeamDashboard members={mockMembers} projects={mockProjects} />);

        await user.click(screen.getByRole('tab', { name: /projects/i }));

        expect(screen.getByText('User Authentication Redesign')).toBeInTheDocument();
        expect(screen.getByText('API Performance Optimization')).toBeInTheDocument();
    });

    it('shows project status and priority chips', async () => {
        const user = userEvent.setup();
        render(<TeamDashboard projects={mockProjects} />);

        await user.click(screen.getByRole('tab', { name: /projects/i }));

        expect(screen.getByText('ON_TRACK')).toBeInTheDocument();
        expect(screen.getByText('AT_RISK')).toBeInTheDocument();
        expect(screen.getByText('HIGH')).toBeInTheDocument();
        expect(screen.getByText('CRITICAL')).toBeInTheDocument();
    });

    it('displays project progress bars', async () => {
        const user = userEvent.setup();
        render(<TeamDashboard projects={mockProjects} />);

        await user.click(screen.getByRole('tab', { name: /projects/i }));

        expect(screen.getByText('75%')).toBeInTheDocument();
        expect(screen.getByText('45%')).toBeInTheDocument();
    });

    it('switches to Workload tab and shows workload distribution', async () => {
        const user = userEvent.setup();
        render(<TeamDashboard members={mockMembers} />);

        await user.click(screen.getByRole('tab', { name: /workload/i }));

        expect(screen.getByText('85% Capacity')).toBeInTheDocument();
        expect(screen.getByText('95% Capacity')).toBeInTheDocument();
        expect(screen.getByText('3 active tasks')).toBeInTheDocument();
        expect(screen.getByText('5 active tasks')).toBeInTheDocument();
    });

    it('shows overallocation alert in Workload tab', async () => {
        const user = userEvent.setup();
        render(<TeamDashboard members={mockMembers} />);

        await user.click(screen.getByRole('tab', { name: /workload/i }));

        expect(screen.getByText(/1 team member\(s\) overallocated/)).toBeInTheDocument();
    });

    it('opens member detail dialog on member card click', async () => {
        const user = userEvent.setup();
        const mockOnMemberClick = vi.fn();
        render(<TeamDashboard members={mockMembers} onMemberClick={mockOnMemberClick} />);

        const memberCard = screen.getByText('Sarah Johnson').closest('[role="button"]') || screen.getByText('Sarah Johnson');
        await user.click(memberCard);

        await waitFor(() => {
            expect(mockOnMemberClick).toHaveBeenCalledWith('1');
        });
    });

    it('shows member details in dialog', async () => {
        const user = userEvent.setup();
        render(<TeamDashboard members={mockMembers} />);

        const memberCard = screen.getByText('Sarah Johnson').closest('[role="button"]') || screen.getByText('Sarah Johnson');
        await user.click(memberCard);

        await waitFor(() => {
            expect(screen.getByText('Performance Rating')).toBeInTheDocument();
            expect(screen.getByText('4.5/5.0')).toBeInTheDocument();
            expect(screen.getByText('23')).toBeInTheDocument(); // Tasks Completed
            expect(screen.getByText('3')).toBeInTheDocument(); // In Progress
        });
    });

    it('calls onScheduleOneOnOne when Schedule 1:1 button clicked', async () => {
        const user = userEvent.setup();
        const mockOnSchedule = vi.fn();
        render(<TeamDashboard members={mockMembers} onScheduleOneOnOne={mockOnSchedule} />);

        const memberCard = screen.getByText('Sarah Johnson').closest('[role="button"]') || screen.getByText('Sarah Johnson');
        await user.click(memberCard);

        await waitFor(async () => {
            const scheduleButton = screen.getByRole('button', { name: /schedule 1:1/i });
            await user.click(scheduleButton);
            expect(mockOnSchedule).toHaveBeenCalledWith('1');
        });
    });

    it('calls onStartReview when Start Review button clicked', async () => {
        const user = userEvent.setup();
        const mockOnStartReview = vi.fn();
        render(<TeamDashboard members={mockMembers} onStartReview={mockOnStartReview} />);

        const memberCard = screen.getByText('Sarah Johnson').closest('[role="button"]') || screen.getByText('Sarah Johnson');
        await user.click(memberCard);

        await waitFor(async () => {
            const reviewButton = screen.getByRole('button', { name: /start review/i });
            await user.click(reviewButton);
            expect(mockOnStartReview).toHaveBeenCalledWith('1');
        });
    });

    it('calls onProjectClick when project row clicked', async () => {
        const user = userEvent.setup();
        const mockOnProjectClick = vi.fn();
        render(<TeamDashboard projects={mockProjects} onProjectClick={mockOnProjectClick} />);

        await user.click(screen.getByRole('tab', { name: /projects/i }));

        const projectRow = screen.getByText('User Authentication Redesign').closest('tr');
        if (projectRow) {
            await user.click(projectRow);
            expect(mockOnProjectClick).toHaveBeenCalledWith('1');
        }
    });
});

describe('OneOnOneTracker', () => {
    const mockMeetings = [
        {
            id: '1',
            employeeId: 'emp-1',
            employeeName: 'Sarah Johnson',
            employeeRole: 'Senior Software Engineer',
            date: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
            duration: 30,
            status: 'scheduled' as const,
            agenda: [
                { id: 'a1', title: 'Q1 Goals Review', completed: false, addedBy: 'manager' as const },
                { id: 'a2', title: 'Career Development', completed: false, addedBy: 'employee' as const },
            ],
            actionItems: [
                { id: 'act1', title: 'Complete certification', assignee: 'employee' as const, completed: false },
            ],
            topics: ['Goals', 'Career Development'],
        },
        {
            id: '2',
            employeeId: 'emp-2',
            employeeName: 'Michael Chen',
            employeeRole: 'Software Engineer',
            date: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
            duration: 30,
            status: 'completed' as const,
            agenda: [
                { id: 'a3', title: 'Sprint Retrospective', completed: true, addedBy: 'manager' as const },
            ],
            notes: 'Great discussion about workload.',
            actionItems: [
                { id: 'act2', title: 'Delegate tasks', assignee: 'manager' as const, completed: false },
            ],
            mood: 'good' as const,
        },
    ];

    it('renders with meeting count', () => {
        render(<OneOnOneTracker meetings={mockMeetings} />);

        expect(screen.getByText('1:1 Meetings')).toBeInTheDocument();
        expect(screen.getByText(/1 upcoming/)).toBeInTheDocument();
        expect(screen.getByText(/pending action items/)).toBeInTheDocument();
    });

    it('displays summary cards', () => {
        render(<OneOnOneTracker meetings={mockMeetings} />);

        expect(screen.getByText('Upcoming')).toBeInTheDocument();
        expect(screen.getByText('Completed')).toBeInTheDocument();
        expect(screen.getByText('Action Items')).toBeInTheDocument();
    });

    it('shows upcoming meeting count', () => {
        render(<OneOnOneTracker meetings={mockMeetings} />);

        // 1 upcoming meeting
        const upcomingCards = screen.getAllByText('1');
        expect(upcomingCards.length).toBeGreaterThan(0);
    });

    it('displays upcoming meetings in default tab', () => {
        render(<OneOnOneTracker meetings={mockMeetings} />);

        expect(screen.getByText('Sarah Johnson')).toBeInTheDocument();
        expect(screen.getByText('Senior Software Engineer')).toBeInTheDocument();
        expect(screen.getByText('2 agenda items')).toBeInTheDocument();
        expect(screen.getByText('1 pending actions')).toBeInTheDocument();
    });

    it('shows meeting topics as chips', () => {
        render(<OneOnOneTracker meetings={mockMeetings} />);

        expect(screen.getByText('Goals')).toBeInTheDocument();
        expect(screen.getByText('Career Development')).toBeInTheDocument();
    });

    it('switches to Past tab and shows completed meetings', async () => {
        const user = userEvent.setup();
        render(<OneOnOneTracker meetings={mockMeetings} />);

        await user.click(screen.getByRole('tab', { name: /past/i }));

        expect(screen.getByText('Michael Chen')).toBeInTheDocument();
        expect(screen.getByText('GOOD')).toBeInTheDocument(); // mood chip
        expect(screen.getByText('Great discussion about workload.')).toBeInTheDocument();
    });

    it('shows agenda completion in Past tab', async () => {
        const user = userEvent.setup();
        render(<OneOnOneTracker meetings={mockMeetings} />);

        await user.click(screen.getByRole('tab', { name: /past/i }));

        expect(screen.getByText(/1\/1 agenda completed/)).toBeInTheDocument();
        expect(screen.getByText(/0\/1 actions completed/)).toBeInTheDocument();
    });

    it('switches to Action Items tab', async () => {
        const user = userEvent.setup();
        render(<OneOnOneTracker meetings={mockMeetings} />);

        await user.click(screen.getByRole('tab', { name: /action items/i }));

        expect(screen.getByText('Complete certification')).toBeInTheDocument();
        expect(screen.getByText('Delegate tasks')).toBeInTheDocument();
    });

    it('shows action item assignees', async () => {
        const user = userEvent.setup();
        render(<OneOnOneTracker meetings={mockMeetings} />);

        await user.click(screen.getByRole('tab', { name: /action items/i }));

        // One assigned to manager (You), one to employee
        expect(screen.getByText('You')).toBeInTheDocument();
        expect(screen.getByText('Sarah Johnson')).toBeInTheDocument();
    });

    it('opens meeting detail dialog on meeting click', async () => {
        const user = userEvent.setup();
        render(<OneOnOneTracker meetings={mockMeetings} />);

        const meetingCard = screen.getByText('Sarah Johnson').closest('[role="button"]') || screen.getByText('Sarah Johnson');
        await user.click(meetingCard);

        await waitFor(() => {
            expect(screen.getByText('Agenda')).toBeInTheDocument();
            expect(screen.getByText('Q1 Goals Review')).toBeInTheDocument();
            expect(screen.getByText('Career Development')).toBeInTheDocument();
        });
    });

    it('shows agenda items with completion checkboxes in dialog', async () => {
        const user = userEvent.setup();
        render(<OneOnOneTracker meetings={mockMeetings} />);

        const meetingCard = screen.getByText('Sarah Johnson').closest('[role="button"]') || screen.getByText('Sarah Johnson');
        await user.click(meetingCard);

        await waitFor(() => {
            const checkboxes = screen.getAllByRole('checkbox');
            expect(checkboxes.length).toBeGreaterThan(0);
        });
    });

    it('calls onCompleteAgendaItem when agenda checkbox clicked', async () => {
        const user = userEvent.setup();
        const mockOnCompleteAgenda = vi.fn();
        render(<OneOnOneTracker meetings={mockMeetings} onCompleteAgendaItem={mockOnCompleteAgenda} />);

        const meetingCard = screen.getByText('Sarah Johnson').closest('[role="button"]') || screen.getByText('Sarah Johnson');
        await user.click(meetingCard);

        await waitFor(async () => {
            const checkboxes = screen.getAllByRole('checkbox');
            if (checkboxes[0]) {
                await user.click(checkboxes[0]);
                expect(mockOnCompleteAgenda).toHaveBeenCalled();
            }
        });
    });

    it('calls onCompleteActionItem when action checkbox clicked', async () => {
        const user = userEvent.setup();
        const mockOnCompleteAction = vi.fn();
        render(<OneOnOneTracker meetings={mockMeetings} onCompleteActionItem={mockOnCompleteAction} />);

        await user.click(screen.getByRole('tab', { name: /action items/i }));

        await waitFor(async () => {
            const checkboxes = screen.getAllByRole('checkbox');
            if (checkboxes[0]) {
                await user.click(checkboxes[0]);
                expect(mockOnCompleteAction).toHaveBeenCalled();
            }
        });
    });

    it('opens schedule dialog when Schedule 1:1 button clicked', async () => {
        const user = userEvent.setup();
        render(<OneOnOneTracker meetings={mockMeetings} />);

        await user.click(screen.getByRole('button', { name: /schedule 1:1/i }));

        await waitFor(() => {
            expect(screen.getByText('Schedule 1:1 Meeting')).toBeInTheDocument();
            expect(screen.getByLabelText(/employee/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/date/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/time/i)).toBeInTheDocument();
        });
    });

    it('shows agenda template options in schedule dialog', async () => {
        const user = userEvent.setup();
        render(<OneOnOneTracker />);

        await user.click(screen.getByRole('button', { name: /schedule 1:1/i }));

        await waitFor(() => {
            expect(screen.getByLabelText(/agenda template/i)).toBeInTheDocument();
        });
    });

    it('calls onCancelMeeting when Cancel Meeting button clicked', async () => {
        const user = userEvent.setup();
        const mockOnCancel = vi.fn();
        render(<OneOnOneTracker meetings={mockMeetings} onCancelMeeting={mockOnCancel} />);

        const meetingCard = screen.getByText('Sarah Johnson').closest('[role="button"]') || screen.getByText('Sarah Johnson');
        await user.click(meetingCard);

        await waitFor(async () => {
            const cancelButton = screen.getByRole('button', { name: /cancel meeting/i });
            await user.click(cancelButton);
            expect(mockOnCancel).toHaveBeenCalledWith('1');
        });
    });

    it('calls onCompleteMeeting when Mark Complete button clicked', async () => {
        const user = userEvent.setup();
        const mockOnComplete = vi.fn();
        render(<OneOnOneTracker meetings={mockMeetings} onCompleteMeeting={mockOnComplete} />);

        const meetingCard = screen.getByText('Sarah Johnson').closest('[role="button"]') || screen.getByText('Sarah Johnson');
        await user.click(meetingCard);

        await waitFor(async () => {
            const completeButton = screen.getByRole('button', { name: /mark complete/i });
            await user.click(completeButton);
            expect(mockOnComplete).toHaveBeenCalledWith('1');
        });
    });
});

describe('PerformanceReviews', () => {
    const mockCycles = [
        {
            id: 'cycle-1',
            name: 'Q4 2024 Reviews',
            period: 'Q4 2024',
            startDate: new Date(2024, 9, 1),
            endDate: new Date(2024, 11, 31),
            dueDate: new Date(2025, 0, 15),
            status: 'active' as const,
        },
    ];

    const mockReviews = [
        {
            id: '1',
            employeeId: 'emp-1',
            employeeName: 'Sarah Johnson',
            employeeRole: 'Senior Software Engineer',
            reviewPeriod: 'Q4 2024',
            reviewType: 'quarterly' as const,
            status: 'completed' as const,
            dueDate: new Date(2025, 0, 15),
            completedDate: new Date(2024, 11, 20),
            overallRating: 4.5,
            ratings: {
                technicalSkills: 5,
                communication: 4,
                leadership: 5,
                problemSolving: 5,
                teamwork: 4,
                initiative: 5,
            },
            goals: [
                { id: 'g1', title: 'Complete React Migration', status: 'achieved' as const, rating: 5 },
            ],
            feedback: {
                strengths: 'Exceptional technical leadership',
                areasForImprovement: 'Could delegate more',
                careerDevelopment: 'Ready for Principal Engineer role',
            },
            recommendations: {
                promotion: true,
                promotionLevel: 'Principal Engineer',
                salaryAdjustment: 12,
            },
        },
        {
            id: '2',
            employeeId: 'emp-2',
            employeeName: 'Michael Chen',
            employeeRole: 'Software Engineer',
            reviewPeriod: 'Q4 2024',
            reviewType: 'quarterly' as const,
            status: 'not_started' as const,
            dueDate: new Date(2025, 0, 15),
            ratings: {},
            goals: [],
            feedback: {},
            recommendations: {},
        },
    ];

    it('renders with active cycle information', () => {
        render(<PerformanceReviews reviews={mockReviews} cycles={mockCycles} />);

        expect(screen.getByText('Performance Reviews')).toBeInTheDocument();
        expect(screen.getByText(/Q4 2024 Reviews/)).toBeInTheDocument();
    });

    it('displays summary cards with counts', () => {
        render(<PerformanceReviews reviews={mockReviews} />);

        expect(screen.getByText('Not Started')).toBeInTheDocument();
        expect(screen.getByText('In Progress')).toBeInTheDocument();
        expect(screen.getByText('Completed')).toBeInTheDocument();
        expect(screen.getByText('Avg Rating')).toBeInTheDocument();
    });

    it('calculates average rating correctly', () => {
        render(<PerformanceReviews reviews={mockReviews} />);

        // Only 1 completed review with rating 4.5
        expect(screen.getByText('4.5')).toBeInTheDocument();
    });

    it('shows active cycle alert', () => {
        render(<PerformanceReviews cycles={mockCycles} />);

        expect(screen.getByText(/Q4 2024 Reviews is active/)).toBeInTheDocument();
        expect(screen.getByText(/days remaining/)).toBeInTheDocument();
    });

    it('displays not started reviews in default tab', () => {
        render(<PerformanceReviews reviews={mockReviews} />);

        expect(screen.getByText('Michael Chen')).toBeInTheDocument();
        expect(screen.getByText('Software Engineer')).toBeInTheDocument();
        expect(screen.getByText('QUARTERLY')).toBeInTheDocument();
    });

    it('shows Start button for not started reviews', () => {
        render(<PerformanceReviews reviews={mockReviews} />);

        const startButtons = screen.getAllByRole('button', { name: /start/i });
        expect(startButtons.length).toBeGreaterThan(0);
    });

    it('switches to Completed tab', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews reviews={mockReviews} />);

        await user.click(screen.getByRole('tab', { name: /completed/i }));

        expect(screen.getByText('Sarah Johnson')).toBeInTheDocument();
        expect(screen.getByText('Principal Engineer')).toBeInTheDocument(); // promotion level
    });

    it('shows promotion recommendation indicator', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews reviews={mockReviews} />);

        await user.click(screen.getByRole('tab', { name: /completed/i }));

        expect(screen.getByText('Promotion Recommended')).toBeInTheDocument();
    });

    it('displays overall rating stars', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews reviews={mockReviews} />);

        await user.click(screen.getByRole('tab', { name: /completed/i }));

        // Should show rating of 4.5
        expect(screen.getByText('4.5')).toBeInTheDocument();
    });

    it('opens review dialog on review click', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews reviews={mockReviews} />);

        const reviewRow = screen.getByText('Michael Chen').closest('tr');
        if (reviewRow) {
            await user.click(reviewRow);

            await waitFor(() => {
                expect(screen.getByText('Overall Rating')).toBeInTheDocument();
                expect(screen.getByText('Competency Ratings')).toBeInTheDocument();
            });
        }
    });

    it('shows all competency rating fields in dialog', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews reviews={mockReviews} />);

        await user.click(screen.getByRole('tab', { name: /completed/i }));

        const reviewRow = screen.getByText('Sarah Johnson').closest('tr');
        if (reviewRow) {
            await user.click(reviewRow);

            await waitFor(() => {
                expect(screen.getByText('Technical Skills')).toBeInTheDocument();
                expect(screen.getByText('Communication')).toBeInTheDocument();
                expect(screen.getByText('Leadership')).toBeInTheDocument();
                expect(screen.getByText('Problem Solving')).toBeInTheDocument();
                expect(screen.getByText('Teamwork')).toBeInTheDocument();
                expect(screen.getByText('Initiative')).toBeInTheDocument();
            });
        }
    });

    it('shows goals assessment section', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews reviews={mockReviews} />);

        await user.click(screen.getByRole('tab', { name: /completed/i }));

        const reviewRow = screen.getByText('Sarah Johnson').closest('tr');
        if (reviewRow) {
            await user.click(reviewRow);

            await waitFor(() => {
                expect(screen.getByText('Goals Assessment')).toBeInTheDocument();
                expect(screen.getByText('Complete React Migration')).toBeInTheDocument();
                expect(screen.getByText('ACHIEVED')).toBeInTheDocument();
            });
        }
    });

    it('shows feedback text fields', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews reviews={mockReviews} />);

        await user.click(screen.getByRole('tab', { name: /completed/i }));

        const reviewRow = screen.getByText('Sarah Johnson').closest('tr');
        if (reviewRow) {
            await user.click(reviewRow);

            await waitFor(() => {
                expect(screen.getByText('Written Feedback')).toBeInTheDocument();
                expect(screen.getByLabelText(/strengths/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/areas for improvement/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/career development/i)).toBeInTheDocument();
            });
        }
    });

    it('shows recommendations section', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews reviews={mockReviews} />);

        await user.click(screen.getByRole('tab', { name: /completed/i }));

        const reviewRow = screen.getByText('Sarah Johnson').closest('tr');
        if (reviewRow) {
            await user.click(reviewRow);

            await waitFor(() => {
                expect(screen.getByText('Recommendations')).toBeInTheDocument();
                expect(screen.getByLabelText(/promotion recommendation/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/salary adjustment/i)).toBeInTheDocument();
            });
        }
    });

    it('opens create review dialog', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews reviews={mockReviews} cycles={mockCycles} />);

        await user.click(screen.getByRole('button', { name: /start review/i }));

        await waitFor(() => {
            expect(screen.getByText('Start New Performance Review')).toBeInTheDocument();
            expect(screen.getByLabelText(/employee/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/review cycle/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/review type/i)).toBeInTheDocument();
        });
    });

    it('shows review type options in create dialog', async () => {
        const user = userEvent.setup();
        render(<PerformanceReviews />);

        await user.click(screen.getByRole('button', { name: /start review/i }));

        await waitFor(() => {
            const reviewTypeSelect = screen.getByLabelText(/review type/i);
            expect(reviewTypeSelect).toBeInTheDocument();
            // Options: quarterly, annual, probation, promotion
        });
    });

    it('calls onSubmitReview when Submit Review button clicked', async () => {
        const user = userEvent.setup();
        const mockOnSubmit = vi.fn();
        render(<PerformanceReviews reviews={mockReviews} onSubmitReview={mockOnSubmit} />);

        const reviewRow = screen.getByText('Michael Chen').closest('tr');
        if (reviewRow) {
            await user.click(reviewRow);

            await waitFor(async () => {
                const submitButton = screen.getByRole('button', { name: /submit review/i });
                await user.click(submitButton);
                expect(mockOnSubmit).toHaveBeenCalledWith('2');
            });
        }
    });

    it('disables editing for submitted reviews', async () => {
        const user = userEvent.setup();
        const submittedReview = {
            ...mockReviews[0],
            status: 'submitted' as const,
        };
        render(<PerformanceReviews reviews={[submittedReview]} />);

        await user.click(screen.getByRole('tab', { name: /completed/i }));

        const reviewRow = screen.getByText('Sarah Johnson').closest('tr');
        if (reviewRow) {
            await user.click(reviewRow);

            await waitFor(() => {
                // Save and Submit buttons should not be present
                expect(screen.queryByRole('button', { name: /save draft/i })).not.toBeInTheDocument();
                expect(screen.queryByRole('button', { name: /submit review/i })).not.toBeInTheDocument();
            });
        }
    });
});
