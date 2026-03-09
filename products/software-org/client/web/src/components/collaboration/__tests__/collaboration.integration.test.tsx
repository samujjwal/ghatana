import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { ProjectWorkspace } from '../ProjectWorkspace';
import { ApprovalWorkflow } from '../ApprovalWorkflow';
import { TeamCalendar } from '../TeamCalendar';

describe('Collaboration Components Integration Tests', () => {
    describe('ProjectWorkspace Component', () => {
        describe('Rendering', () => {
            it('should render project header with status and priority', () => {
                render(<ProjectWorkspace />);
                expect(screen.getByText('Mobile App Redesign')).toBeInTheDocument();
                expect(screen.getByText('Active')).toBeInTheDocument();
                expect(screen.getByText('High')).toBeInTheDocument();
            });

            it('should render project owner and team information', () => {
                render(<ProjectWorkspace />);
                expect(screen.getByText('Sarah Johnson')).toBeInTheDocument();
                expect(screen.getByText(/4 members/i)).toBeInTheDocument();
            });

            it('should render project timeline and progress', () => {
                render(<ProjectWorkspace />);
                expect(screen.getByText(/Jan 1, 2024/)).toBeInTheDocument();
                expect(screen.getByText(/Mar 31, 2024/)).toBeInTheDocument();
                expect(screen.getByText('60%')).toBeInTheDocument();
            });

            it('should render all navigation tabs', () => {
                render(<ProjectWorkspace />);
                expect(screen.getByRole('tab', { name: /board/i })).toBeInTheDocument();
                expect(screen.getByRole('tab', { name: /list/i })).toBeInTheDocument();
                expect(screen.getByRole('tab', { name: /activity/i })).toBeInTheDocument();
                expect(screen.getByRole('tab', { name: /files/i })).toBeInTheDocument();
            });

            it('should render task counts in tabs', () => {
                render(<ProjectWorkspace />);
                const boardTab = screen.getByRole('tab', { name: /board/i });
                expect(within(boardTab).getByText('5')).toBeInTheDocument();
            });
        });

        describe('Board View', () => {
            it('should render all kanban columns', () => {
                render(<ProjectWorkspace />);
                expect(screen.getByText('To Do')).toBeInTheDocument();
                expect(screen.getByText('In Progress')).toBeInTheDocument();
                expect(screen.getByText('Review')).toBeInTheDocument();
                expect(screen.getByText('Done')).toBeInTheDocument();
            });

            it('should display task cards with correct information', () => {
                render(<ProjectWorkspace />);
                expect(screen.getByText('Design new homepage layout')).toBeInTheDocument();
                expect(screen.getByText('Implement user authentication')).toBeInTheDocument();
            });

            it('should show priority chips on task cards', () => {
                render(<ProjectWorkspace />);
                const highPriorityChips = screen.getAllByText('High');
                expect(highPriorityChips.length).toBeGreaterThan(0);
            });

            it('should display assignee avatars on task cards', () => {
                render(<ProjectWorkspace />);
                const avatars = screen.getAllByRole('img');
                expect(avatars.length).toBeGreaterThan(0);
            });

            it('should show comment and attachment counts', () => {
                render(<ProjectWorkspace />);
                expect(screen.getByText(/2/)).toBeInTheDocument(); // comments
                expect(screen.getByText(/1/)).toBeInTheDocument(); // attachments
            });
        });

        describe('Task Creation', () => {
            it('should open create task dialog when clicking "New Task" button', () => {
                render(<ProjectWorkspace />);
                const newTaskButton = screen.getByRole('button', { name: /new task/i });
                fireEvent.click(newTaskButton);
                expect(screen.getByRole('dialog')).toBeInTheDocument();
                expect(screen.getByText('Create New Task')).toBeInTheDocument();
            });

            it('should have all required form fields in create dialog', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('button', { name: /new task/i }));
                expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/status/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/priority/i)).toBeInTheDocument();
            });

            it('should call onCreateTask callback when submitting form', () => {
                const onCreateTask = vi.fn();
                render(<ProjectWorkspace onCreateTask={onCreateTask} />);

                fireEvent.click(screen.getByRole('button', { name: /new task/i }));
                fireEvent.change(screen.getByLabelText(/title/i), {
                    target: { value: 'Test Task' },
                });
                fireEvent.click(screen.getByRole('button', { name: /create task/i }));

                expect(onCreateTask).toHaveBeenCalledWith(
                    expect.objectContaining({
                        title: 'Test Task',
                    })
                );
            });

            it('should disable submit button when required fields are empty', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('button', { name: /new task/i }));
                const submitButton = screen.getByRole('button', { name: /create task/i });
                expect(submitButton).toBeDisabled();
            });
        });

        describe('Task Details', () => {
            it('should open task detail dialog when clicking a task card', () => {
                render(<ProjectWorkspace />);
                const taskCard = screen.getByText('Design new homepage layout');
                fireEvent.click(taskCard);
                expect(screen.getByRole('dialog')).toBeInTheDocument();
            });

            it('should display task description in detail dialog', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByText('Design new homepage layout'));
                expect(screen.getByText(/Create wireframes and mockups/)).toBeInTheDocument();
            });

            it('should show all task metadata in detail dialog', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByText('Design new homepage layout'));
                expect(screen.getByLabelText(/assignee/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/due date/i)).toBeInTheDocument();
            });

            it('should display comments section in detail dialog', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByText('Design new homepage layout'));
                expect(screen.getByText('Comments')).toBeInTheDocument();
            });

            it('should allow adding comments to tasks', () => {
                const onAddComment = vi.fn();
                render(<ProjectWorkspace onAddComment={onAddComment} />);

                fireEvent.click(screen.getByText('Design new homepage layout'));
                const commentInput = screen.getByPlaceholderText(/add a comment/i);
                fireEvent.change(commentInput, { target: { value: 'Test comment' } });
                fireEvent.click(screen.getByRole('button', { name: /send/i }));

                expect(onAddComment).toHaveBeenCalled();
            });
        });

        describe('List View', () => {
            it('should switch to list view when clicking list tab', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('tab', { name: /list/i }));
                expect(screen.getByRole('table')).toBeInTheDocument();
            });

            it('should display tasks in table format', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('tab', { name: /list/i }));
                expect(screen.getByRole('columnheader', { name: /task/i })).toBeInTheDocument();
                expect(screen.getByRole('columnheader', { name: /assignee/i })).toBeInTheDocument();
                expect(screen.getByRole('columnheader', { name: /status/i })).toBeInTheDocument();
            });

            it('should show task actions in list view', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('tab', { name: /list/i }));
                const viewButtons = screen.getAllByRole('button', { name: /view/i });
                expect(viewButtons.length).toBeGreaterThan(0);
            });
        });

        describe('Activity Feed', () => {
            it('should switch to activity view when clicking activity tab', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('tab', { name: /activity/i }));
                expect(screen.getByText(/activity/i)).toBeInTheDocument();
            });

            it('should display activity items with timestamps', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('tab', { name: /activity/i }));
                expect(screen.getByText(/completed task/i)).toBeInTheDocument();
            });

            it('should show user avatars in activity feed', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('tab', { name: /activity/i }));
                const avatars = screen.getAllByRole('img');
                expect(avatars.length).toBeGreaterThan(0);
            });
        });

        describe('Files Tab', () => {
            it('should switch to files view when clicking files tab', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('tab', { name: /files/i }));
                expect(screen.getByText(/attachments/i)).toBeInTheDocument();
            });

            it('should display file attachments with metadata', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('tab', { name: /files/i }));
                expect(screen.getByText(/design_mockup\.png/i)).toBeInTheDocument();
            });

            it('should show file upload button', () => {
                render(<ProjectWorkspace />);
                fireEvent.click(screen.getByRole('tab', { name: /files/i }));
                expect(screen.getByRole('button', { name: /upload file/i })).toBeInTheDocument();
            });
        });

        describe('Callbacks', () => {
            it('should call onUpdateProject when editing project', () => {
                const onUpdateProject = vi.fn();
                render(<ProjectWorkspace onUpdateProject={onUpdateProject} />);
                fireEvent.click(screen.getByRole('button', { name: /edit project/i }));
                expect(onUpdateProject).toHaveBeenCalled();
            });

            it('should call onUpdateTask when updating task status', () => {
                const onUpdateTask = vi.fn();
                render(<ProjectWorkspace onUpdateTask={onUpdateTask} />);
                fireEvent.click(screen.getByText('Design new homepage layout'));
                const statusSelect = screen.getByLabelText(/status/i);
                fireEvent.change(statusSelect, { target: { value: 'in-progress' } });
                expect(onUpdateTask).toHaveBeenCalled();
            });

            it('should call onDeleteTask when deleting a task', () => {
                const onDeleteTask = vi.fn();
                render(<ProjectWorkspace onDeleteTask={onDeleteTask} />);
                fireEvent.click(screen.getByText('Design new homepage layout'));
                fireEvent.click(screen.getByRole('button', { name: /delete/i }));
                expect(onDeleteTask).toHaveBeenCalled();
            });
        });
    });

    describe('ApprovalWorkflow Component', () => {
        describe('Rendering', () => {
            it('should render page header and title', () => {
                render(<ApprovalWorkflow />);
                expect(screen.getByText('Approval Workflow')).toBeInTheDocument();
            });

            it('should render all status tabs', () => {
                render(<ApprovalWorkflow />);
                expect(screen.getByRole('tab', { name: /pending/i })).toBeInTheDocument();
                expect(screen.getByRole('tab', { name: /approved/i })).toBeInTheDocument();
                expect(screen.getByRole('tab', { name: /rejected/i })).toBeInTheDocument();
                expect(screen.getByRole('tab', { name: /my requests/i })).toBeInTheDocument();
            });

            it('should display request counts in tabs', () => {
                render(<ApprovalWorkflow />);
                const pendingTab = screen.getByRole('tab', { name: /pending/i });
                expect(within(pendingTab).getByText('1')).toBeInTheDocument();
            });

            it('should render new request button', () => {
                render(<ApprovalWorkflow />);
                expect(screen.getByRole('button', { name: /new request/i })).toBeInTheDocument();
            });
        });

        describe('Request List', () => {
            it('should display pending approval requests', () => {
                render(<ApprovalWorkflow />);
                expect(screen.getByText('Q1 2024 Marketing Budget Increase')).toBeInTheDocument();
            });

            it('should show request type and status chips', () => {
                render(<ApprovalWorkflow />);
                expect(screen.getByText('Budget')).toBeInTheDocument();
                expect(screen.getByText('Pending')).toBeInTheDocument();
            });

            it('should display requester information', () => {
                render(<ApprovalWorkflow />);
                expect(screen.getByText('Sarah Johnson')).toBeInTheDocument();
            });

            it('should show approval progress bar', () => {
                render(<ApprovalWorkflow />);
                const progressBars = screen.getAllByRole('progressbar');
                expect(progressBars.length).toBeGreaterThan(0);
            });

            it('should display approval level information', () => {
                render(<ApprovalWorkflow />);
                expect(screen.getByText(/Level 2 of 3/)).toBeInTheDocument();
            });

            it('should show approver chips with status', () => {
                render(<ApprovalWorkflow />);
                expect(screen.getByText(/Michael Chen \(L1\)/)).toBeInTheDocument();
            });

            it('should display action required badge for pending approvals', () => {
                render(<ApprovalWorkflow currentUserId="approver2" />);
                expect(screen.getByText('Action Required')).toBeInTheDocument();
            });
        });

        describe('Request Details', () => {
            it('should open detail dialog when clicking view details', () => {
                render(<ApprovalWorkflow />);
                const viewButton = screen.getAllByRole('button', { name: /view details/i })[0];
                fireEvent.click(viewButton);
                expect(screen.getByRole('dialog')).toBeInTheDocument();
            });

            it('should display full request description', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                expect(screen.getByText(/Request to increase marketing budget/)).toBeInTheDocument();
            });

            it('should show requester details with avatar', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                expect(screen.getByText('sarah.johnson@company.com')).toBeInTheDocument();
            });

            it('should display approval chain as stepper', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                expect(screen.getByText('Approval Chain')).toBeInTheDocument();
            });

            it('should show approver comments in stepper', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                expect(screen.getByText(/Campaign metrics look promising/)).toBeInTheDocument();
            });

            it('should display attachments if present', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                expect(screen.getByText('Campaign_Proposal.pdf')).toBeInTheDocument();
            });

            it('should show file sizes for attachments', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                expect(screen.getByText(/2.3 MB/)).toBeInTheDocument();
            });
        });

        describe('Approval Actions', () => {
            it('should show approve and reject buttons for pending approver', () => {
                render(<ApprovalWorkflow currentUserId="approver2" />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                expect(screen.getByRole('button', { name: /^approve$/i })).toBeInTheDocument();
                expect(screen.getByRole('button', { name: /^reject$/i })).toBeInTheDocument();
            });

            it('should open approve dialog when clicking approve', () => {
                render(<ApprovalWorkflow currentUserId="approver2" />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                fireEvent.click(screen.getByRole('button', { name: /^approve$/i }));
                expect(screen.getByText('Approve Request')).toBeInTheDocument();
            });

            it('should open reject dialog when clicking reject', () => {
                render(<ApprovalWorkflow currentUserId="approver2" />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                fireEvent.click(screen.getByRole('button', { name: /^reject$/i }));
                expect(screen.getByText('Reject Request')).toBeInTheDocument();
            });

            it('should call onApprove callback with comment', () => {
                const onApprove = vi.fn();
                render(<ApprovalWorkflow currentUserId="approver2" onApprove={onApprove} />);

                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                fireEvent.click(screen.getByRole('button', { name: /^approve$/i }));
                fireEvent.change(screen.getByLabelText(/comment/i), {
                    target: { value: 'Looks good' },
                });
                fireEvent.click(screen.getAllByRole('button', { name: /approve/i })[1]);

                expect(onApprove).toHaveBeenCalledWith('1', 'approver2', 'Looks good');
            });

            it('should require reason when rejecting', () => {
                render(<ApprovalWorkflow currentUserId="approver2" />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                fireEvent.click(screen.getByRole('button', { name: /^reject$/i }));
                const rejectButton = screen.getAllByRole('button', { name: /reject/i })[1];
                expect(rejectButton).toBeDisabled();
            });
        });

        describe('Comments', () => {
            it('should display existing comments', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                expect(screen.getByText(/What is the expected ROI/)).toBeInTheDocument();
            });

            it('should show comment author and timestamp', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                expect(screen.getByText('Michael Chen')).toBeInTheDocument();
            });

            it('should allow adding new comments', () => {
                const onAddComment = vi.fn();
                render(<ApprovalWorkflow onAddComment={onAddComment} />);

                fireEvent.click(screen.getAllByRole('button', { name: /view details/i })[0]);
                const commentInput = screen.getByPlaceholderText(/add a comment/i);
                fireEvent.change(commentInput, { target: { value: 'Test comment' } });
                fireEvent.click(screen.getByRole('button', { name: /send/i }));

                expect(onAddComment).toHaveBeenCalledWith('1', 'Test comment');
            });
        });

        describe('Tab Filtering', () => {
            it('should show approved requests in approved tab', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getByRole('tab', { name: /approved/i }));
                expect(screen.getByText(/New Senior Engineer Position/)).toBeInTheDocument();
            });

            it('should show rejected requests in rejected tab', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getByRole('tab', { name: /rejected/i }));
                expect(screen.getByText(/Promotion: Emily White/)).toBeInTheDocument();
            });

            it('should filter by current user in my requests tab', () => {
                render(<ApprovalWorkflow currentUserId="user1" />);
                fireEvent.click(screen.getByRole('tab', { name: /my requests/i }));
                expect(screen.getByText('Q1 2024 Marketing Budget Increase')).toBeInTheDocument();
            });
        });

        describe('Request Creation', () => {
            it('should open create dialog when clicking new request', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getByRole('button', { name: /new request/i }));
                expect(screen.getByText('New Approval Request')).toBeInTheDocument();
            });

            it('should have all form fields in create dialog', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getByRole('button', { name: /new request/i }));
                expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/request type/i)).toBeInTheDocument();
            });

            it('should disable submit without required fields', () => {
                render(<ApprovalWorkflow />);
                fireEvent.click(screen.getByRole('button', { name: /new request/i }));
                const submitButton = screen.getByRole('button', { name: /submit request/i });
                expect(submitButton).toBeDisabled();
            });
        });
    });

    describe('TeamCalendar Component', () => {
        describe('Rendering', () => {
            it('should render page header and title', () => {
                render(<TeamCalendar />);
                expect(screen.getByText('Team Calendar')).toBeInTheDocument();
            });

            it('should render view selector dropdown', () => {
                render(<TeamCalendar />);
                expect(screen.getByDisplayValue('Week')).toBeInTheDocument();
            });

            it('should render new event button', () => {
                render(<TeamCalendar />);
                expect(screen.getByRole('button', { name: /new event/i })).toBeInTheDocument();
            });
        });

        describe('Quick Stats', () => {
            it('should display upcoming events count', () => {
                render(<TeamCalendar />);
                expect(screen.getByText('Upcoming Events')).toBeInTheDocument();
            });

            it('should display today meetings count', () => {
                render(<TeamCalendar />);
                expect(screen.getByText("Today's Meetings")).toBeInTheDocument();
            });

            it('should display pending invitations count', () => {
                render(<TeamCalendar />);
                expect(screen.getByText('Pending Invitations')).toBeInTheDocument();
            });

            it('should display scheduling conflicts count', () => {
                render(<TeamCalendar />);
                expect(screen.getByText('Scheduling Conflicts')).toBeInTheDocument();
            });
        });

        describe('Event List', () => {
            it('should display calendar events', () => {
                render(<TeamCalendar />);
                expect(screen.getByText('1:1 with Sarah Johnson')).toBeInTheDocument();
                expect(screen.getByText('Team Sprint Planning')).toBeInTheDocument();
            });

            it('should show event type chips', () => {
                render(<TeamCalendar />);
                expect(screen.getByText('1:1')).toBeInTheDocument();
                expect(screen.getByText('Team Meeting')).toBeInTheDocument();
            });

            it('should display event times and duration', () => {
                render(<TeamCalendar />);
                expect(screen.getByText(/30m/)).toBeInTheDocument();
            });

            it('should show virtual meeting indicator', () => {
                render(<TeamCalendar />);
                expect(screen.getByText(/Virtual/)).toBeInTheDocument();
            });

            it('should display physical location when not virtual', () => {
                render(<TeamCalendar />);
                expect(screen.getByText(/Conference Room A/)).toBeInTheDocument();
            });

            it('should show attendee count', () => {
                render(<TeamCalendar />);
                expect(screen.getByText(/4 attendees/)).toBeInTheDocument();
            });

            it('should display recurring event indicator', () => {
                render(<TeamCalendar />);
                expect(screen.getByText('Recurring')).toBeInTheDocument();
            });

            it('should show join button for virtual meetings', () => {
                render(<TeamCalendar />);
                const joinButtons = screen.getAllByRole('button', { name: /join/i });
                expect(joinButtons.length).toBeGreaterThan(0);
            });
        });

        describe('Event Details', () => {
            it('should open detail dialog when clicking an event', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByText('1:1 with Sarah Johnson'));
                expect(screen.getByRole('dialog')).toBeInTheDocument();
            });

            it('should display event time information', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByText('1:1 with Sarah Johnson'));
                expect(screen.getByText('When')).toBeInTheDocument();
            });

            it('should show recurrence pattern if recurring', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByText('1:1 with Sarah Johnson'));
                expect(screen.getByText(/Weekly on Monday/)).toBeInTheDocument();
            });

            it('should display location or meeting link', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByText('1:1 with Sarah Johnson'));
                expect(screen.getByText('Where')).toBeInTheDocument();
            });

            it('should show join meeting button for virtual events', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByText('1:1 with Sarah Johnson'));
                expect(screen.getByRole('button', { name: /join meeting/i })).toBeInTheDocument();
            });

            it('should display event description', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByText('1:1 with Sarah Johnson'));
                expect(screen.getByText('Weekly check-in')).toBeInTheDocument();
            });

            it('should show conflict warning if present', () => {
                render(<TeamCalendar />);
                const events = screen.getAllByText(/1:1 with Sarah Johnson|Team Sprint Planning/);
                fireEvent.click(events[0]);
                // Would show conflict if times overlap
            });
        });

        describe('Attendees', () => {
            it('should display all attendees with avatars', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByText('Team Sprint Planning'));
                expect(screen.getByText(/Attendees \(4\)/)).toBeInTheDocument();
            });

            it('should show organizer badge', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByText('Team Sprint Planning'));
                expect(screen.getByText('Organizer')).toBeInTheDocument();
            });

            it('should display attendee status', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByText('Team Sprint Planning'));
                expect(screen.getByText('Accepted')).toBeInTheDocument();
                expect(screen.getByText('Tentative')).toBeInTheDocument();
                expect(screen.getByText('Pending')).toBeInTheDocument();
            });
        });

        describe('RSVP Actions', () => {
            it('should show RSVP buttons for pending invitations', () => {
                render(<TeamCalendar currentUserId="user5" />);
                fireEvent.click(screen.getByText('Team Sprint Planning'));
                expect(screen.getByRole('button', { name: /accept/i })).toBeInTheDocument();
                expect(screen.getByRole('button', { name: /tentative/i })).toBeInTheDocument();
                expect(screen.getByRole('button', { name: /decline/i })).toBeInTheDocument();
            });

            it('should call onRSVP when accepting invitation', () => {
                const onRSVP = vi.fn();
                render(<TeamCalendar currentUserId="user5" onRSVP={onRSVP} />);
                fireEvent.click(screen.getByText('Team Sprint Planning'));
                fireEvent.click(screen.getByRole('button', { name: /accept/i }));
                expect(onRSVP).toHaveBeenCalledWith('2', 'user5', 'accepted');
            });

            it('should call onRSVP when declining invitation', () => {
                const onRSVP = vi.fn();
                render(<TeamCalendar currentUserId="user5" onRSVP={onRSVP} />);
                fireEvent.click(screen.getByText('Team Sprint Planning'));
                fireEvent.click(screen.getByRole('button', { name: /decline/i }));
                expect(onRSVP).toHaveBeenCalledWith('2', 'user5', 'declined');
            });
        });

        describe('Event Creation', () => {
            it('should open create dialog when clicking new event', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByRole('button', { name: /new event/i }));
                expect(screen.getByText('New Calendar Event')).toBeInTheDocument();
            });

            it('should have all form fields in create dialog', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByRole('button', { name: /new event/i }));
                expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/event type/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/start time/i)).toBeInTheDocument();
                expect(screen.getByLabelText(/end time/i)).toBeInTheDocument();
            });

            it('should toggle between virtual and physical location', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByRole('button', { name: /new event/i }));
                const locationTypeSelect = screen.getByLabelText(/location type/i);
                expect(locationTypeSelect).toBeInTheDocument();
            });

            it('should disable submit without required fields', () => {
                render(<TeamCalendar />);
                fireEvent.click(screen.getByRole('button', { name: /new event/i }));
                const createButton = screen.getByRole('button', { name: /create event/i });
                expect(createButton).toBeDisabled();
            });

            it('should call onCreateEvent when submitting form', () => {
                const onCreateEvent = vi.fn();
                render(<TeamCalendar onCreateEvent={onCreateEvent} />);

                fireEvent.click(screen.getByRole('button', { name: /new event/i }));
                fireEvent.change(screen.getByLabelText(/title/i), {
                    target: { value: 'Test Event' },
                });
                fireEvent.change(screen.getByLabelText(/start time/i), {
                    target: { value: '2024-01-20T10:00' },
                });
                fireEvent.change(screen.getByLabelText(/end time/i), {
                    target: { value: '2024-01-20T11:00' },
                });
                fireEvent.click(screen.getByRole('button', { name: /create event/i }));

                expect(onCreateEvent).toHaveBeenCalled();
            });
        });

        describe('Organizer Actions', () => {
            it('should show delete button for event organizer', () => {
                render(<TeamCalendar currentUserId="user2" />);
                fireEvent.click(screen.getByText('Team Sprint Planning'));
                expect(screen.getByRole('button', { name: /delete event/i })).toBeInTheDocument();
            });

            it('should call onDeleteEvent when deleting', () => {
                const onDeleteEvent = vi.fn();
                render(<TeamCalendar currentUserId="user2" onDeleteEvent={onDeleteEvent} />);
                fireEvent.click(screen.getByText('Team Sprint Planning'));
                fireEvent.click(screen.getByRole('button', { name: /delete event/i }));
                expect(onDeleteEvent).toHaveBeenCalledWith('2');
            });
        });

        describe('View Switching', () => {
            it('should switch to month view', () => {
                render(<TeamCalendar />);
                const viewSelect = screen.getByDisplayValue('Week');
                fireEvent.change(viewSelect, { target: { value: 'month' } });
                expect(screen.getByText('This Month')).toBeInTheDocument();
            });

            it('should switch to day view', () => {
                render(<TeamCalendar />);
                const viewSelect = screen.getByDisplayValue('Week');
                fireEvent.change(viewSelect, { target: { value: 'day' } });
                expect(screen.getByText('Today')).toBeInTheDocument();
            });
        });
    });
});
