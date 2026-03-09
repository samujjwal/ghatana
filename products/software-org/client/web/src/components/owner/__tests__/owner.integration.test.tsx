import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExecutiveOnboarding, type OrganizationInfo } from '../ExecutiveOnboarding';
import { BillingDashboard, type SubscriptionPlan, type CurrentSubscription, type Invoice } from '../BillingDashboard';
import { BudgetPlanningDashboard, type DepartmentBudget } from '../BudgetPlanningDashboard';

// ==================== EXECUTIVE ONBOARDING TESTS ====================

describe('ExecutiveOnboarding', () => {
    const mockUser = {
        name: 'John Smith',
        email: 'john.smith@acme.com',
    };

    const mockCallbacks = {
        onComplete: vi.fn(),
        onSkip: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render welcome step initially', () => {
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        expect(screen.getByText(/Welcome, John Smith!/i)).toBeInTheDocument();
        expect(screen.getByText(mockUser.email)).toBeInTheDocument();
        expect(screen.getByText(/about to create your organization/i)).toBeInTheDocument();
    });

    it('should display all 5 steps in stepper', () => {
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        expect(screen.getByText('Welcome')).toBeInTheDocument();
        expect(screen.getByText('Organization')).toBeInTheDocument();
        expect(screen.getByText('Invite Team')).toBeInTheDocument();
        expect(screen.getByText('Integrations')).toBeInTheDocument();
        expect(screen.getByText('Complete')).toBeInTheDocument();
    });

    it('should show progress bar', () => {
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        expect(screen.getByText(/Step 1 of 5/i)).toBeInTheDocument();
    });

    it('should advance to organization step when Get Started is clicked', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        const getStartedButton = screen.getByRole('button', { name: /get started/i });
        await user.click(getStartedButton);

        await waitFor(() => {
            expect(screen.getByText(/Organization Details/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/Organization Name/i)).toBeInTheDocument();
        });
    });

    it('should validate organization fields', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        // Go to organization step
        await user.click(screen.getByRole('button', { name: /get started/i }));

        // Try to continue without filling required fields
        await waitFor(() => {
            expect(screen.getByLabelText(/Organization Name/i)).toBeInTheDocument();
        });

        const continueButton = screen.getByRole('button', { name: /continue/i });
        await user.click(continueButton);

        await waitFor(() => {
            expect(screen.getByText(/Organization name is required/i)).toBeInTheDocument();
            expect(screen.getByText(/Organization slug is required/i)).toBeInTheDocument();
            expect(screen.getByText(/Industry is required/i)).toBeInTheDocument();
            expect(screen.getByText(/Organization size is required/i)).toBeInTheDocument();
        });
    });

    it('should auto-generate slug from organization name', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        await user.click(screen.getByRole('button', { name: /get started/i }));

        await waitFor(() => {
            expect(screen.getByLabelText(/Organization Name/i)).toBeInTheDocument();
        });

        const nameInput = screen.getByLabelText(/Organization Name/i);
        await user.type(nameInput, 'Acme Corporation');

        await waitFor(() => {
            const slugInput = screen.getByLabelText(/Organization Slug/i) as HTMLInputElement;
            expect(slugInput.value).toBe('acme-corporation');
        });
    });

    it('should allow filling all organization fields', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        await user.click(screen.getByRole('button', { name: /get started/i }));

        await waitFor(() => {
            expect(screen.getByLabelText(/Organization Name/i)).toBeInTheDocument();
        });

        await user.type(screen.getByLabelText(/Organization Name/i), 'Acme Corporation');

        const industrySelect = screen.getByLabelText(/Industry/i);
        fireEvent.change(industrySelect, { target: { value: 'technology' } });

        const sizeSelect = screen.getByLabelText(/Organization Size/i);
        fireEvent.change(sizeSelect, { target: { value: '51-200' } });

        await user.click(screen.getByRole('button', { name: /continue/i }));

        await waitFor(() => {
            expect(screen.getByText(/Invite Your Team/i)).toBeInTheDocument();
        });
    });

    it('should allow adding multiple team invitations', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        // Navigate to team invitation step
        await user.click(screen.getByRole('button', { name: /get started/i }));
        await waitFor(() => screen.getByLabelText(/Organization Name/i));
        await user.type(screen.getByLabelText(/Organization Name/i), 'Acme');
        fireEvent.change(screen.getByLabelText(/Industry/i), { target: { value: 'technology' } });
        fireEvent.change(screen.getByLabelText(/Organization Size/i), { target: { value: '51-200' } });
        await user.click(screen.getByRole('button', { name: /continue/i }));

        await waitFor(() => {
            expect(screen.getByText(/Invite Your Team/i)).toBeInTheDocument();
        });

        // Should start with 1 invitation
        expect(screen.getAllByLabelText(/Email Address/i)).toHaveLength(1);

        // Add another team member
        const addButton = screen.getByRole('button', { name: /Add Another Team Member/i });
        await user.click(addButton);

        await waitFor(() => {
            expect(screen.getAllByLabelText(/Email Address/i)).toHaveLength(2);
        });
    });

    it('should allow removing team invitations', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        // Navigate to team step and add member
        await user.click(screen.getByRole('button', { name: /get started/i }));
        await waitFor(() => screen.getByLabelText(/Organization Name/i));
        await user.type(screen.getByLabelText(/Organization Name/i), 'Acme');
        fireEvent.change(screen.getByLabelText(/Industry/i), { target: { value: 'technology' } });
        fireEvent.change(screen.getByLabelText(/Organization Size/i), { target: { value: '51-200' } });
        await user.click(screen.getByRole('button', { name: /continue/i }));

        await waitFor(() => screen.getByText(/Invite Your Team/i));
        await user.click(screen.getByRole('button', { name: /Add Another Team Member/i }));

        await waitFor(() => {
            expect(screen.getAllByLabelText(/Email Address/i)).toHaveLength(2);
        });

        // Remove the second invitation
        const deleteButtons = screen.getAllByRole('button', { name: '' }).filter(btn =>
            btn.querySelector('svg')?.getAttribute('data-testid') === 'DeleteIcon'
        );
        if (deleteButtons.length > 0) {
            await user.click(deleteButtons[0]);
            await waitFor(() => {
                expect(screen.getAllByLabelText(/Email Address/i)).toHaveLength(1);
            });
        }
    });

    it('should allow toggling integrations', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        // Navigate to integrations step
        await user.click(screen.getByRole('button', { name: /get started/i }));
        await waitFor(() => screen.getByLabelText(/Organization Name/i));
        await user.type(screen.getByLabelText(/Organization Name/i), 'Acme');
        fireEvent.change(screen.getByLabelText(/Industry/i), { target: { value: 'technology' } });
        fireEvent.change(screen.getByLabelText(/Organization Size/i), { target: { value: '51-200' } });
        await user.click(screen.getByRole('button', { name: /continue/i }));
        await waitFor(() => screen.getByText(/Invite Your Team/i));
        await user.click(screen.getByRole('button', { name: /continue/i }));

        await waitFor(() => {
            expect(screen.getByText(/Connect Your Tools/i)).toBeInTheDocument();
        });

        // Should show integration options
        expect(screen.getByText('Slack')).toBeInTheDocument();
        expect(screen.getByText('GitHub')).toBeInTheDocument();
        expect(screen.getByText('Jira')).toBeInTheDocument();
    });

    it('should show completion step with summary', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        // Complete all steps
        await user.click(screen.getByRole('button', { name: /get started/i }));
        await waitFor(() => screen.getByLabelText(/Organization Name/i));
        await user.type(screen.getByLabelText(/Organization Name/i), 'Acme Corporation');
        fireEvent.change(screen.getByLabelText(/Industry/i), { target: { value: 'technology' } });
        fireEvent.change(screen.getByLabelText(/Organization Size/i), { target: { value: '51-200' } });
        await user.click(screen.getByRole('button', { name: /continue/i }));

        await waitFor(() => screen.getByText(/Invite Your Team/i));
        await user.click(screen.getByRole('button', { name: /continue/i }));

        await waitFor(() => screen.getByText(/Connect Your Tools/i));
        await user.click(screen.getByRole('button', { name: /continue/i }));

        await waitFor(() => {
            expect(screen.getByText(/You're All Set!/i)).toBeInTheDocument();
            expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
            expect(screen.getByText(/acme-corporation/i)).toBeInTheDocument();
        });
    });

    it('should call onComplete when Go to Dashboard is clicked', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} {...mockCallbacks} />);

        // Navigate to completion
        await user.click(screen.getByRole('button', { name: /get started/i }));
        await waitFor(() => screen.getByLabelText(/Organization Name/i));
        await user.type(screen.getByLabelText(/Organization Name/i), 'Acme');
        fireEvent.change(screen.getByLabelText(/Industry/i), { target: { value: 'technology' } });
        fireEvent.change(screen.getByLabelText(/Organization Size/i), { target: { value: '51-200' } });
        await user.click(screen.getByRole('button', { name: /continue/i }));
        await waitFor(() => screen.getByText(/Invite Your Team/i));
        await user.click(screen.getByRole('button', { name: /continue/i }));
        await waitFor(() => screen.getByText(/Connect Your Tools/i));
        await user.click(screen.getByRole('button', { name: /continue/i }));

        await waitFor(() => screen.getByRole('button', { name: /Go to Dashboard/i }));
        await user.click(screen.getByRole('button', { name: /Go to Dashboard/i }));

        expect(mockCallbacks.onComplete).toHaveBeenCalled();
    });

    it('should allow skipping optional steps', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        // Navigate to team step
        await user.click(screen.getByRole('button', { name: /get started/i }));
        await waitFor(() => screen.getByLabelText(/Organization Name/i));
        await user.type(screen.getByLabelText(/Organization Name/i), 'Acme');
        fireEvent.change(screen.getByLabelText(/Industry/i), { target: { value: 'technology' } });
        fireEvent.change(screen.getByLabelText(/Organization Size/i), { target: { value: '51-200' } });
        await user.click(screen.getByRole('button', { name: /continue/i }));

        await waitFor(() => screen.getByText(/Invite Your Team/i));

        // Skip button should be available for optional step
        const skipButton = screen.getByRole('button', { name: /skip/i });
        await user.click(skipButton);

        await waitFor(() => {
            expect(screen.getByText(/Connect Your Tools/i)).toBeInTheDocument();
        });
    });

    it('should allow going back to previous steps', async () => {
        const user = userEvent.setup();
        render(<ExecutiveOnboarding currentUser={mockUser} />);

        await user.click(screen.getByRole('button', { name: /get started/i }));
        await waitFor(() => screen.getByLabelText(/Organization Name/i));

        const backButton = screen.getByRole('button', { name: /back/i });
        await user.click(backButton);

        await waitFor(() => {
            expect(screen.getByText(/Welcome, John Smith!/i)).toBeInTheDocument();
        });
    });
});

// ==================== BILLING DASHBOARD TESTS ====================

describe('BillingDashboard', () => {
    const mockPlan: SubscriptionPlan = {
        id: 'professional',
        name: 'Professional',
        tier: 'professional',
        price: 199,
        billingCycle: 'monthly',
        features: [
            'Up to 250 users',
            '250 GB storage',
            '500,000 API calls/month',
            'Priority support',
        ],
        limits: {
            users: 250,
            storage: 250,
            apiCalls: 500000,
        },
    };

    const mockSubscription: CurrentSubscription = {
        plan: mockPlan,
        status: 'active',
        currentPeriodStart: '2025-11-11T00:00:00Z',
        currentPeriodEnd: '2025-12-11T00:00:00Z',
        usage: {
            users: 185,
            storage: 142,
            apiCalls: 325000,
        },
    };

    const mockInvoices: Invoice[] = [
        {
            id: 'inv-1',
            number: 'INV-2025-001',
            date: '2025-12-01T00:00:00Z',
            dueDate: '2025-12-11T00:00:00Z',
            amount: 199,
            status: 'paid',
            items: [
                {
                    description: 'Professional Plan - December 2025',
                    quantity: 1,
                    unitPrice: 199,
                    amount: 199,
                },
            ],
        },
    ];

    const mockCallbacks = {
        onUpgrade: vi.fn(),
        onDowngrade: vi.fn(),
        onCancelSubscription: vi.fn(),
        onDownloadInvoice: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render current subscription details', () => {
        render(<BillingDashboard currentSubscription={mockSubscription} />);

        expect(screen.getByText(/Current Plan: Professional/i)).toBeInTheDocument();
        expect(screen.getByText(/ACTIVE/i)).toBeInTheDocument();
        expect(screen.getByText(/\$199\/month/i)).toBeInTheDocument();
    });

    it('should display usage metrics with progress bars', () => {
        render(<BillingDashboard currentSubscription={mockSubscription} />);

        expect(screen.getByText(/185 \/ 250/i)).toBeInTheDocument(); // Users
        expect(screen.getByText(/142 GB \/ 250 GB/i)).toBeInTheDocument(); // Storage
        expect(screen.getByText(/325K \/ 500K/i)).toBeInTheDocument(); // API calls
    });

    it('should display all available plans', () => {
        render(<BillingDashboard currentSubscription={mockSubscription} />);

        expect(screen.getByText('Free')).toBeInTheDocument();
        expect(screen.getByText('Starter')).toBeInTheDocument();
        expect(screen.getByText('Professional')).toBeInTheDocument();
        expect(screen.getByText('Enterprise')).toBeInTheDocument();
    });

    it('should show current plan indicator', () => {
        render(<BillingDashboard currentSubscription={mockSubscription} />);

        const currentPlanChip = screen.getByText('Current Plan');
        expect(currentPlanChip).toBeInTheDocument();
    });

    it('should display invoice history', () => {
        render(<BillingDashboard invoices={mockInvoices} />);

        expect(screen.getByText('INV-2025-001')).toBeInTheDocument();
        expect(screen.getByText('$199')).toBeInTheDocument();
        expect(screen.getByText('PAID')).toBeInTheDocument();
    });

    it('should open upgrade dialog when upgrade button is clicked', async () => {
        const user = userEvent.setup();
        render(<BillingDashboard currentSubscription={mockSubscription} />);

        const upgradeButtons = screen.getAllByRole('button', { name: /upgrade/i });
        await user.click(upgradeButtons[0]);

        await waitFor(() => {
            expect(screen.getByText(/Upgrade to Enterprise/i)).toBeInTheDocument();
        });
    });

    it('should show plan comparison in upgrade dialog', async () => {
        const user = userEvent.setup();
        render(<BillingDashboard currentSubscription={mockSubscription} />);

        const upgradeButtons = screen.getAllByRole('button', { name: /upgrade/i });
        await user.click(upgradeButtons[0]);

        await waitFor(() => {
            const dialog = screen.getByRole('dialog');
            expect(within(dialog).getByText(/Current Plan/i)).toBeInTheDocument();
            expect(within(dialog).getByText(/New Plan/i)).toBeInTheDocument();
            expect(within(dialog).getByText(/Difference/i)).toBeInTheDocument();
        });
    });

    it('should call onUpgrade when upgrade is confirmed', async () => {
        const user = userEvent.setup();
        render(<BillingDashboard currentSubscription={mockSubscription} {...mockCallbacks} />);

        const upgradeButtons = screen.getAllByRole('button', { name: /upgrade/i });
        await user.click(upgradeButtons[0]);

        await waitFor(() => screen.getByRole('dialog'));

        const confirmButton = screen.getByRole('button', { name: /Confirm Upgrade/i });
        await user.click(confirmButton);

        expect(mockCallbacks.onUpgrade).toHaveBeenCalled();
    });

    it('should open cancel subscription dialog', async () => {
        const user = userEvent.setup();
        render(<BillingDashboard currentSubscription={mockSubscription} />);

        const cancelButton = screen.getByRole('button', { name: /Cancel Subscription/i });
        await user.click(cancelButton);

        await waitFor(() => {
            expect(screen.getByText(/Are you sure you want to cancel\?/i)).toBeInTheDocument();
        });
    });

    it('should call onCancelSubscription when cancellation is confirmed', async () => {
        const user = userEvent.setup();
        render(<BillingDashboard currentSubscription={mockSubscription} {...mockCallbacks} />);

        await user.click(screen.getByRole('button', { name: /Cancel Subscription/i }));

        await waitFor(() => screen.getByRole('dialog'));

        const confirmButton = screen.getByRole('button', { name: /Cancel Subscription/i });
        await user.click(confirmButton);

        expect(mockCallbacks.onCancelSubscription).toHaveBeenCalled();
    });

    it('should open invoice detail dialog when View is clicked', async () => {
        const user = userEvent.setup();
        render(<BillingDashboard invoices={mockInvoices} />);

        const viewButton = screen.getByRole('button', { name: /view/i });
        await user.click(viewButton);

        await waitFor(() => {
            expect(screen.getByText(/Invoice INV-2025-001/i)).toBeInTheDocument();
        });
    });

    it('should call onDownloadInvoice when download is clicked', async () => {
        const user = userEvent.setup();
        render(<BillingDashboard invoices={mockInvoices} {...mockCallbacks} />);

        const downloadButtons = screen.getAllByRole('button', { name: '' }).filter(btn =>
            btn.querySelector('svg')?.getAttribute('data-testid') === 'DownloadIcon'
        );

        if (downloadButtons.length > 0) {
            await user.click(downloadButtons[0]);
            expect(mockCallbacks.onDownloadInvoice).toHaveBeenCalledWith('inv-1');
        }
    });
});

// ==================== BUDGET PLANNING DASHBOARD TESTS ====================

describe('BudgetPlanningDashboard', () => {
    const mockBudgets: DepartmentBudget[] = [
        {
            id: 'budget-1',
            departmentName: 'Engineering',
            departmentId: 'dept-1',
            fiscalYear: 2025,
            allocated: 2000000,
            spent: 1650000,
            forecast: 1950000,
            lastYearSpent: 1800000,
            category: 'engineering',
            status: 'approved',
            approvedBy: 'CFO',
            approvedAt: '2024-12-01T00:00:00Z',
        },
        {
            id: 'budget-2',
            departmentName: 'Operations',
            departmentId: 'dept-2',
            fiscalYear: 2025,
            allocated: 600000,
            spent: 480000,
            forecast: 590000,
            lastYearSpent: 550000,
            category: 'operations',
            status: 'pending_approval',
        },
        {
            id: 'budget-3',
            departmentName: 'Human Resources',
            departmentId: 'dept-3',
            fiscalYear: 2025,
            allocated: 400000,
            spent: 310000,
            forecast: 380000,
            lastYearSpent: 350000,
            category: 'hr',
            status: 'draft',
        },
    ];

    const mockCallbacks = {
        onUpdateBudget: vi.fn(),
        onApproveBudget: vi.fn(),
        onRejectBudget: vi.fn(),
        onSubmitForApproval: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render budget summary cards', () => {
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} totalBudget={5000000} />);

        expect(screen.getByText(/Total Budget/i)).toBeInTheDocument();
        expect(screen.getByText('$5,000,000')).toBeInTheDocument();
        expect(screen.getByText(/Allocated/i)).toBeInTheDocument();
        expect(screen.getByText(/Spent \(YTD\)/i)).toBeInTheDocument();
        expect(screen.getByText(/Forecast/i)).toBeInTheDocument();
    });

    it('should display all department budgets in table', () => {
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} />);

        expect(screen.getByText('Engineering')).toBeInTheDocument();
        expect(screen.getByText('Operations')).toBeInTheDocument();
        expect(screen.getByText('Human Resources')).toBeInTheDocument();
    });

    it('should show budget status chips', () => {
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} />);

        expect(screen.getByText(/APPROVED/i)).toBeInTheDocument();
        expect(screen.getByText(/PENDING APPROVAL/i)).toBeInTheDocument();
        expect(screen.getByText(/DRAFT/i)).toBeInTheDocument();
    });

    it('should display category tabs', () => {
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} />);

        expect(screen.getByRole('tab', { name: /By Department/i })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: /By Category/i })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: /Pending Approvals/i })).toBeInTheDocument();
    });

    it('should switch between tabs', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} />);

        const categoryTab = screen.getByRole('tab', { name: /By Category/i });
        await user.click(categoryTab);

        await waitFor(() => {
            expect(screen.getByText('Engineering')).toBeInTheDocument(); // Category name
        });
    });

    it('should show pending approvals in dedicated tab', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} />);

        const approvalsTab = screen.getByRole('tab', { name: /Pending Approvals/i });
        await user.click(approvalsTab);

        await waitFor(() => {
            expect(screen.getByText('Operations')).toBeInTheDocument(); // Only pending approval
            expect(screen.queryByText('Engineering')).not.toBeInTheDocument(); // Not pending
        });
    });

    it('should open edit dialog when edit button is clicked', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} />);

        const editButtons = screen.getAllByRole('button', { name: '' }).filter(btn =>
            btn.querySelector('svg')?.getAttribute('data-testid') === 'EditIcon'
        );

        if (editButtons.length > 0) {
            await user.click(editButtons[0]);

            await waitFor(() => {
                expect(screen.getByText(/Edit Budget/i)).toBeInTheDocument();
            });
        }
    });

    it('should call onUpdateBudget when budget is saved', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} {...mockCallbacks} />);

        const editButtons = screen.getAllByRole('button', { name: '' }).filter(btn =>
            btn.querySelector('svg')?.getAttribute('data-testid') === 'EditIcon'
        );

        if (editButtons.length > 0) {
            await user.click(editButtons[0]);

            await waitFor(() => screen.getByRole('dialog'));

            const allocationInput = screen.getByLabelText(/Budget Allocation/i) as HTMLInputElement;
            await user.clear(allocationInput);
            await user.type(allocationInput, '2500000');

            const saveButton = screen.getByRole('button', { name: /Save Changes/i });
            await user.click(saveButton);

            expect(mockCallbacks.onUpdateBudget).toHaveBeenCalled();
        }
    });

    it('should open approve dialog when approve button is clicked', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} />);

        // Switch to pending approvals tab
        await user.click(screen.getByRole('tab', { name: /Pending Approvals/i }));

        await waitFor(() => {
            const approveButton = screen.getByRole('button', { name: /Approve/i });
            expect(approveButton).toBeInTheDocument();
        });

        const approveButton = screen.getByRole('button', { name: /Approve/i });
        await user.click(approveButton);

        await waitFor(() => {
            expect(screen.getByText(/Approve Budget - Operations/i)).toBeInTheDocument();
        });
    });

    it('should call onApproveBudget when approval is confirmed', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} {...mockCallbacks} />);

        await user.click(screen.getByRole('tab', { name: /Pending Approvals/i }));
        await waitFor(() => screen.getByRole('button', { name: /Approve/i }));
        await user.click(screen.getByRole('button', { name: /Approve/i }));

        await waitFor(() => screen.getByRole('dialog'));

        const confirmButton = screen.getByRole('button', { name: /Approve Budget/i });
        await user.click(confirmButton);

        expect(mockCallbacks.onApproveBudget).toHaveBeenCalled();
    });

    it('should open reject dialog when reject button is clicked', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} />);

        await user.click(screen.getByRole('tab', { name: /Pending Approvals/i }));
        await waitFor(() => screen.getByRole('button', { name: /Reject/i }));

        const rejectButton = screen.getByRole('button', { name: /Reject/i });
        await user.click(rejectButton);

        await waitFor(() => {
            expect(screen.getByText(/Reject Budget - Operations/i)).toBeInTheDocument();
        });
    });

    it('should require reason when rejecting budget', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} {...mockCallbacks} />);

        await user.click(screen.getByRole('tab', { name: /Pending Approvals/i }));
        await waitFor(() => screen.getByRole('button', { name: /Reject/i }));
        await user.click(screen.getByRole('button', { name: /Reject/i }));

        await waitFor(() => screen.getByRole('dialog'));

        const rejectButton = screen.getByRole('button', { name: /Reject Budget/i });
        expect(rejectButton).toBeDisabled();

        const reasonInput = screen.getByLabelText(/Reason for Rejection/i);
        await user.type(reasonInput, 'Budget exceeds available funds');

        expect(rejectButton).not.toBeDisabled();
    });

    it('should call onRejectBudget with reason when rejection is confirmed', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} {...mockCallbacks} />);

        await user.click(screen.getByRole('tab', { name: /Pending Approvals/i }));
        await waitFor(() => screen.getByRole('button', { name: /Reject/i }));
        await user.click(screen.getByRole('button', { name: /Reject/i }));

        await waitFor(() => screen.getByRole('dialog'));

        const reasonInput = screen.getByLabelText(/Reason for Rejection/i);
        await user.type(reasonInput, 'Budget exceeds available funds');

        const confirmButton = screen.getByRole('button', { name: /Reject Budget/i });
        await user.click(confirmButton);

        expect(mockCallbacks.onRejectBudget).toHaveBeenCalledWith(
            'budget-2',
            'Budget exceeds available funds'
        );
    });

    it('should call onSubmitForApproval when submit button is clicked', async () => {
        const user = userEvent.setup();
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} {...mockCallbacks} />);

        const submitButton = screen.getByRole('button', { name: /Submit/i });
        await user.click(submitButton);

        expect(mockCallbacks.onSubmitForApproval).toHaveBeenCalledWith('budget-3');
    });

    it('should display variance indicators', () => {
        render(<BudgetPlanningDashboard departmentBudgets={mockBudgets} />);

        // Should show variance for forecast vs allocated
        const trendIcons = screen.getAllByTestId(/TrendingUpIcon|TrendingDownIcon/);
        expect(trendIcons.length).toBeGreaterThan(0);
    });
});
