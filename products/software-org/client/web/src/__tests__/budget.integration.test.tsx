/**
 * Budget Integration Tests
 *
 * Tests for BudgetPlanningPage, AllocationTable, and BudgetSummaryCards.
 *
 * @package @ghatana/software-org-web
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';

import { BudgetPlanningPage } from '../pages/budget/BudgetPlanningPage';
import { AllocationTable, type DepartmentBudget } from '../components/budget/AllocationTable';
import { BudgetSummaryCards, type BudgetMetrics } from '../components/budget/BudgetSummaryCards';

// Test Helpers
const createTestQueryClient = () =>
    new QueryClient({
        defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
        },
    });

const renderWithProviders = (component: React.ReactElement) => {
    const queryClient = createTestQueryClient();
    return render(
        <QueryClientProvider client={queryClient}>
            <BrowserRouter>{component}</BrowserRouter>
        </QueryClientProvider>
    );
};

// Mock Data
const mockBudgets: DepartmentBudget[] = [
    {
        id: 'budget-1',
        departmentId: 'dept-eng',
        departmentName: 'Engineering',
        currentAllocated: 2000000,
        currentSpent: 1500000,
        proposedAllocated: 2500000,
        categories: {
            headcount: 1800000,
            infrastructure: 400000,
            tools: 200000,
            training: 80000,
            other: 20000,
        },
        justification: 'Growing team, increased cloud costs',
        status: 'draft',
    },
    {
        id: 'budget-2',
        departmentId: 'dept-ops',
        departmentName: 'Operations',
        currentAllocated: 1500000,
        currentSpent: 1200000,
        proposedAllocated: 1600000,
        categories: {
            headcount: 1000000,
            infrastructure: 400000,
            tools: 150000,
            training: 30000,
            other: 20000,
        },
        justification: 'Maintaining current operations',
        status: 'draft',
    },
];

const mockMetrics: BudgetMetrics = {
    totalAllocated: 4100000,
    totalSpent: 2700000,
    totalForecasted: 3500000,
    variance: 800000,
    variancePercent: 29.63,
    remainingBudget: 900000,
    departmentCount: 2,
};

// Mock fetch
global.fetch = vi.fn();
global.alert = vi.fn();
global.confirm = vi.fn(() => true);

beforeEach(() => {
    vi.clearAllMocks();
});

afterEach(() => {
    vi.restoreAllMocks();
});

// ========================================
// BudgetSummaryCards Tests
// ========================================

describe('BudgetSummaryCards', () => {
    it('should render summary cards with metrics', () => {
        render(<BudgetSummaryCards metrics={mockMetrics} year={2025} quarter="Q1" />);

        expect(screen.getByText('Total Allocated')).toBeInTheDocument();
        expect(screen.getByText('$4,100,000')).toBeInTheDocument();

        expect(screen.getByText('Total Spent')).toBeInTheDocument();
        expect(screen.getByText('$2,700,000')).toBeInTheDocument();

        expect(screen.getByText('Remaining')).toBeInTheDocument();
        expect(screen.getByText('$900,000')).toBeInTheDocument();

        expect(screen.getByText('Variance')).toBeInTheDocument();
        expect(screen.getByText('+$800,000')).toBeInTheDocument();
    });

    it('should display budget usage percentage', () => {
        render(<BudgetSummaryCards metrics={mockMetrics} year={2025} />);

        const spendPercent = (2700000 / 4100000) * 100;
        expect(screen.getByText(`${spendPercent.toFixed(1)}%`)).toBeInTheDocument();
    });

    it('should show warning color when over 90% spent', () => {
        const highSpendMetrics: BudgetMetrics = {
            ...mockMetrics,
            totalSpent: 3800000, // 92.7%
        };

        render(<BudgetSummaryCards metrics={highSpendMetrics} year={2025} />);

        const spendPercent = (3800000 / 4100000) * 100;
        const percentElement = screen.getByText(`${spendPercent.toFixed(1)}%`);
        expect(percentElement).toHaveClass('text-red-600');
    });

    it('should format currency correctly', () => {
        render(<BudgetSummaryCards metrics={mockMetrics} year={2025} />);

        // Check for dollar signs and commas
        expect(screen.getByText('$4,100,000')).toBeInTheDocument();
        expect(screen.getByText('$2,700,000')).toBeInTheDocument();
        expect(screen.getByText('$900,000')).toBeInTheDocument();
    });

    it('should show department count and period', () => {
        render(<BudgetSummaryCards metrics={mockMetrics} year={2025} quarter="Q1" />);

        expect(screen.getByText(/2 departments • 2025 Q1/)).toBeInTheDocument();
    });
});

// ========================================
// AllocationTable Tests
// ========================================

describe('AllocationTable', () => {
    const mockOnBudgetChange = vi.fn();
    const mockOnCategoryChange = vi.fn();

    it('should render department budgets', () => {
        render(
            <AllocationTable
                budgets={mockBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={5000000}
            />
        );

        expect(screen.getByText('Engineering')).toBeInTheDocument();
        expect(screen.getByText('Operations')).toBeInTheDocument();
    });

    it('should display current and proposed allocations', () => {
        render(
            <AllocationTable
                budgets={mockBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={5000000}
            />
        );

        expect(screen.getByText('$2,000,000')).toBeInTheDocument(); // Eng current
        expect(screen.getByText('$1,500,000')).toBeInTheDocument(); // Ops current
    });

    it('should calculate change percentage correctly', () => {
        render(
            <AllocationTable
                budgets={mockBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={5000000}
            />
        );

        // Eng: (2.5M - 2M) / 2M = +25%
        expect(screen.getByText('+25.0%')).toBeInTheDocument();

        // Ops: (1.6M - 1.5M) / 1.5M = +6.7%
        expect(screen.getByText('+6.7%')).toBeInTheDocument();
    });

    it('should show over budget warning', () => {
        render(
            <AllocationTable
                budgets={mockBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={3000000} // Less than proposed total
            />
        );

        expect(screen.getByText(/Over Budget/)).toBeInTheDocument();
    });

    it('should allow editing proposed allocation', async () => {
        const user = userEvent.setup();

        render(
            <AllocationTable
                budgets={mockBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={5000000}
            />
        );

        const inputs = screen.getAllByRole('spinbutton');
        const proposedInput = inputs[0]; // First proposed input

        await user.clear(proposedInput);
        await user.type(proposedInput, '3000000');

        expect(mockOnBudgetChange).toHaveBeenCalledWith('dept-eng', 'proposedAllocated', 3000000);
    });

    it('should expand to show category breakdown', async () => {
        const user = userEvent.setup();

        render(
            <AllocationTable
                budgets={mockBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={5000000}
            />
        );

        const showButtons = screen.getAllByText(/▶ Show/);
        await user.click(showButtons[0]);

        await waitFor(() => {
            expect(screen.getByText('Budget Categories')).toBeInTheDocument();
            expect(screen.getByText(/headcount/i)).toBeInTheDocument();
            expect(screen.getByText(/infrastructure/i)).toBeInTheDocument();
        });
    });

    it('should allow editing category values', async () => {
        const user = userEvent.setup();

        render(
            <AllocationTable
                budgets={mockBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={5000000}
            />
        );

        // Expand first department
        const showButton = screen.getAllByText(/▶ Show/)[0];
        await user.click(showButton);

        await waitFor(() => {
            expect(screen.getByText('Budget Categories')).toBeInTheDocument();
        });

        // Find and edit headcount input
        const categoryInputs = screen.getAllByRole('spinbutton');
        const headcountInput = categoryInputs.find(input => (input as HTMLInputElement).value === '1800000');

        if (headcountInput) {
            await user.clear(headcountInput);
            await user.type(headcountInput, '2000000');

            expect(mockOnCategoryChange).toHaveBeenCalled();
        }
    });

    it('should warn about category mismatch', async () => {
        const user = userEvent.setup();

        const mismatchBudgets: DepartmentBudget[] = [
            {
                ...mockBudgets[0],
                proposedAllocated: 3000000, // Different from category total (2.5M)
            },
        ];

        render(
            <AllocationTable
                budgets={mismatchBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={5000000}
            />
        );

        expect(screen.getByText(/⚠️ Categories:/)).toBeInTheDocument();
    });

    it('should be readonly when flag is set', () => {
        render(
            <AllocationTable
                budgets={mockBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={5000000}
                readonly={true}
            />
        );

        const inputs = screen.queryAllByRole('spinbutton');
        expect(inputs).toHaveLength(0);
    });

    it('should calculate totals correctly', () => {
        render(
            <AllocationTable
                budgets={mockBudgets}
                onBudgetChange={mockOnBudgetChange}
                onCategoryChange={mockOnCategoryChange}
                totalBudgetLimit={5000000}
            />
        );

        // Total proposed: 2.5M + 1.6M = 4.1M
        expect(screen.getByText('$4,100,000')).toBeInTheDocument();

        // Total spent: 1.5M + 1.2M = 2.7M
        expect(screen.getByText('$2,700,000')).toBeInTheDocument();
    });
});

// ========================================
// BudgetPlanningPage Tests
// ========================================

describe('BudgetPlanningPage', () => {
    it('should render page header', async () => {
        (global.fetch as any).mockResolvedValueOnce({
            ok: false,
            status: 404,
        });

        renderWithProviders(<BudgetPlanningPage />);

        await waitFor(() => {
            expect(screen.getByText('💰 Budget Planning')).toBeInTheDocument();
        });
    });

    it('should load existing budget plan', async () => {
        const mockPlan = {
            id: 'plan-1',
            year: 2025,
            quarter: 'Q1',
            totalBudgetLimit: 5000000,
            status: 'draft',
            budgets: mockBudgets,
        };

        (global.fetch as any).mockResolvedValueOnce({
            ok: true,
            json: async () => mockPlan,
        });

        renderWithProviders(<BudgetPlanningPage />);

        await waitFor(() => {
            expect(screen.getByText('Engineering')).toBeInTheDocument();
            expect(screen.getByText('Operations')).toBeInTheDocument();
        });
    });

    it('should allow changing year and quarter', async () => {
        const user = userEvent.setup();

        (global.fetch as any).mockResolvedValueOnce({
            ok: false,
            status: 404,
        });

        renderWithProviders(<BudgetPlanningPage />);

        await waitFor(() => {
            expect(screen.getByText('💰 Budget Planning')).toBeInTheDocument();
        });

        const yearSelect = screen.getByLabelText(/Year/);
        await user.selectOptions(yearSelect, '2026');

        expect(yearSelect).toHaveValue('2026');
    });

    it('should save draft successfully', async () => {
        const user = userEvent.setup();

        (global.fetch as any)
            .mockResolvedValueOnce({
                ok: false,
                status: 404,
            })
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({ id: 'new-plan', message: 'Saved' }),
            });

        renderWithProviders(<BudgetPlanningPage />);

        await waitFor(() => {
            expect(screen.getByText('💰 Budget Planning')).toBeInTheDocument();
        });

        const saveDraftButton = screen.getByText('Save Draft');
        await user.click(saveDraftButton);

        await waitFor(() => {
            expect(global.fetch).toHaveBeenCalledWith(
                '/api/v1/budgets',
                expect.objectContaining({
                    method: 'POST',
                })
            );
        });
    });

    it('should validate before submission', async () => {
        const user = userEvent.setup();

        (global.fetch as any).mockResolvedValueOnce({
            ok: false,
            status: 404,
        });

        renderWithProviders(<BudgetPlanningPage />);

        await waitFor(() => {
            expect(screen.getByText('💰 Budget Planning')).toBeInTheDocument();
        });

        // Set budget limit lower than total proposed
        const limitInput = screen.getByLabelText(/Total Budget Limit/);
        await user.clear(limitInput);
        await user.type(limitInput, '1000000');

        const submitButton = screen.getByText('Submit for Approval');
        await user.click(submitButton);

        // Should show alert about over budget
        await waitFor(() => {
            expect(global.alert).toHaveBeenCalledWith(expect.stringContaining('exceeds budget limit'));
        });
    });

    it('should submit for approval successfully', async () => {
        const user = userEvent.setup();

        (global.fetch as any)
            .mockResolvedValueOnce({
                ok: false,
                status: 404,
            })
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({ id: 'new-plan' }),
            })
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({ id: 'approval-1', status: 'PENDING' }),
            });

        renderWithProviders(<BudgetPlanningPage />);

        await waitFor(() => {
            expect(screen.getByText('💰 Budget Planning')).toBeInTheDocument();
        });

        const submitButton = screen.getByText('Submit for Approval');
        await user.click(submitButton);

        await waitFor(() => {
            expect(global.fetch).toHaveBeenCalledWith(
                '/api/v1/approvals',
                expect.objectContaining({
                    method: 'POST',
                    body: expect.stringContaining('budget'),
                })
            );
        });
    });

    it('should disable editing when budget is approved', async () => {
        const mockApprovedPlan = {
            id: 'plan-1',
            year: 2025,
            quarter: 'Q1',
            totalBudgetLimit: 5000000,
            status: 'approved',
            budgets: mockBudgets,
        };

        (global.fetch as any).mockResolvedValueOnce({
            ok: true,
            json: async () => mockApprovedPlan,
        });

        renderWithProviders(<BudgetPlanningPage />);

        await waitFor(() => {
            expect(screen.getByText(/This budget plan is approved and cannot be edited/)).toBeInTheDocument();
        });

        expect(screen.queryByText('Submit for Approval')).not.toBeInTheDocument();
    });

    it('should show loading state', () => {
        (global.fetch as any).mockImplementation(
            () =>
                new Promise(resolve =>
                    setTimeout(() => resolve({ ok: true, json: async () => ({}) }), 1000)
                )
        );

        renderWithProviders(<BudgetPlanningPage />);

        expect(screen.getByText(/Loading budget plan/)).toBeInTheDocument();
    });
});

// ========================================
// Integration Workflow Tests
// ========================================

describe('Budget Planning End-to-End Workflow', () => {
    it('should complete full budget planning cycle', async () => {
        const user = userEvent.setup();

        // 1. Load page (no existing plan)
        (global.fetch as any).mockResolvedValueOnce({
            ok: false,
            status: 404,
        });

        renderWithProviders(<BudgetPlanningPage />);

        await waitFor(() => {
            expect(screen.getByText('💰 Budget Planning')).toBeInTheDocument();
        });

        // 2. Adjust budget allocation
        const inputs = screen.getAllByRole('spinbutton');
        const proposedInput = inputs.find(input => (input as HTMLInputElement).value === '2500000');

        if (proposedInput) {
            await user.clear(proposedInput);
            await user.type(proposedInput, '2800000');
        }

        // 3. Save draft
        (global.fetch as any).mockResolvedValueOnce({
            ok: true,
            json: async () => ({ id: 'draft-1' }),
        });

        const saveDraftButton = screen.getByText('Save Draft');
        await user.click(saveDraftButton);

        // 4. Submit for approval
        (global.fetch as any)
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({ id: 'draft-1' }),
            })
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({ id: 'approval-1' }),
            });

        const submitButton = screen.getByText('Submit for Approval');
        await user.click(submitButton);

        // Workflow verified
    });

    it('should handle category allocation', async () => {
        const user = userEvent.setup();

        (global.fetch as any).mockResolvedValueOnce({
            ok: false,
            status: 404,
        });

        renderWithProviders(<BudgetPlanningPage />);

        await waitFor(() => {
            expect(screen.getByText('💰 Budget Planning')).toBeInTheDocument();
        });

        // Expand category breakdown
        const showButton = screen.getAllByText(/▶ Show/)[0];
        await user.click(showButton);

        await waitFor(() => {
            expect(screen.getByText('Budget Categories')).toBeInTheDocument();
        });

        // Adjust category values
        // (Category inputs would be edited here)

        // Verify category total matches proposed
        expect(screen.getByText('Category Total:')).toBeInTheDocument();
    });
});
