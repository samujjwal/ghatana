/**
 * PersonasPage Component Tests
 *
 * Tests validate:
 * - Role selection and toggle behavior
 * - Save functionality with validation
 * - Reset functionality
 * - Sync status banner display
 * - Loading states
 * - Error handling
 * - 1-5 role validation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router';
import { PersonasPage } from '../PersonasPage';
import * as usePersonaQueries from '@/lib/hooks/usePersonaQueries';
import * as usePersonaSync from '@/lib/hooks/usePersonaSync';
import * as toast from '@/lib/toast';

// Mock dependencies
vi.mock('@/lib/hooks/usePersonaQueries');
vi.mock('@/lib/hooks/usePersonaSync');
vi.mock('@/lib/toast');

describe('PersonasPage', () => {
    let queryClient: QueryClient;
    const mockShowWarning = vi.fn();
    const mockShowError = vi.fn();
    const mockShowSuccess = vi.fn();

    const mockRoleDefinitions = [
        {
            id: 'admin',
            roleId: 'admin',
            name: 'Administrator',
            displayName: 'Administrator',
            type: 'BASE' as const,
            description: 'Full system access',
            permissions: ['all'],
            capabilities: ['manage.users', 'manage.system'],
        },
        {
            id: 'developer',
            roleId: 'developer',
            name: 'Developer',
            displayName: 'Developer',
            type: 'BASE' as const,
            description: 'Code development',
            permissions: ['code.read', 'code.write'],
            capabilities: ['code.review', 'code.deploy'],
        },
        {
            id: 'tech-lead',
            roleId: 'tech-lead',
            name: 'Tech Lead',
            displayName: 'Tech Lead',
            type: 'SPECIALIZED' as const,
            description: 'Technical leadership',
            permissions: ['review', 'architecture'],
            capabilities: ['team.lead', 'architecture.review'],
        },
        {
            id: 'role-3',
            roleId: 'role-3',
            name: 'Role 3',
            displayName: 'Role 3',
            type: 'BASE' as const,
            description: 'Role 3',
            permissions: ['perm3'],
            capabilities: ['cap3'],
        },
        {
            id: 'role-4',
            roleId: 'role-4',
            name: 'Role 4',
            displayName: 'Role 4',
            type: 'BASE' as const,
            description: 'Role 4',
            permissions: ['perm4'],
            capabilities: ['cap4'],
        },
        {
            id: 'role-5',
            roleId: 'role-5',
            name: 'Role 5',
            displayName: 'Role 5',
            type: 'BASE' as const,
            description: 'Role 5',
            permissions: ['perm5'],
            capabilities: ['cap5'],
        },
    ];

    const mockPreference = {
        id: 'pref-1',
        userId: 'user-1',
        workspaceId: 'default',
        activeRoles: ['admin', 'developer'],
        preferences: { dashboardLayout: 'grid' },
        createdAt: '2025-11-24T00:00:00Z',
        updatedAt: '2025-11-24T00:00:00Z',
    };

    beforeEach(() => {
        queryClient = new QueryClient({
            defaultOptions: {
                queries: { retry: false },
                mutations: { retry: false },
            },
        });

        // Default mocks - success state
        vi.mocked(usePersonaQueries.useRoleDefinitions).mockReturnValue({
            data: mockRoleDefinitions,
            isLoading: false,
            isError: false,
            error: null,
        } as any);

        vi.mocked(usePersonaQueries.usePersonaPreference).mockReturnValue({
            data: mockPreference,
            isLoading: false,
            isError: false,
            error: null,
        } as any);

        vi.mocked(usePersonaQueries.useUpdatePersonaPreference).mockReturnValue({
            mutateAsync: vi.fn().mockResolvedValue(mockPreference),
            isPending: false,
        } as any);

        vi.mocked(usePersonaSync.usePersonaSync).mockReturnValue({
            isConnected: true,
            error: null,
            reconnect: vi.fn(),
        });

        // Mock useToast
        vi.mocked(toast.useToast).mockReturnValue({
            showToast: vi.fn(),
            showWarning: mockShowWarning,
            showError: mockShowError,
            showSuccess: mockShowSuccess,
            showInfo: vi.fn(),
            dismiss: vi.fn(),
        });
    });

    afterEach(() => {
        vi.clearAllMocks();
        mockShowWarning.mockClear();
        mockShowError.mockClear();
        mockShowSuccess.mockClear();
    });

    const renderComponent = (workspaceId = 'default') => {
        return render(
            <QueryClientProvider client={queryClient}>
                <MemoryRouter initialEntries={[`/personas/${workspaceId}`]}>
                    <Routes>
                        <Route path="/personas/:workspaceId" element={<PersonasPage />} />
                    </Routes>
                </MemoryRouter>
            </QueryClientProvider>
        );
    };

    describe('Loading States', () => {
        it('should show loading skeletons when roles are loading', () => {
            vi.mocked(usePersonaQueries.useRoleDefinitions).mockReturnValue({
                data: undefined,
                isLoading: true,
                isError: false,
                error: null,
            } as any);

            renderComponent();

            // Verify skeleton cards are shown (6 cards with role="status")
            const skeletons = screen.getAllByRole('status', { name: /loading role card/i });
            expect(skeletons).toHaveLength(6);
        });

        it('should show loading skeletons when preference is loading', () => {
            vi.mocked(usePersonaQueries.usePersonaPreference).mockReturnValue({
                data: undefined,
                isLoading: true,
                isError: false,
                error: null,
            } as any);

            renderComponent();

            // Verify skeleton cards are shown
            const skeletons = screen.getAllByRole('status', { name: /loading role card/i });
            expect(skeletons).toHaveLength(6);
        });
    });

    describe('Role Selection', () => {
        it('should display all available roles grouped by type', () => {
            renderComponent();

            expect(screen.getByText('Base Roles')).toBeInTheDocument();
            expect(screen.getByText('Specialized Roles')).toBeInTheDocument();
            expect(screen.getAllByText('Administrator').length).toBeGreaterThan(0);
            expect(screen.getAllByText('Developer').length).toBeGreaterThan(0);
            expect(screen.getByText('Tech Lead')).toBeInTheDocument();
        });

        it('should pre-select active roles from preference', () => {
            renderComponent();

            const adminCheckbox = screen.getByRole('checkbox', { name: /Administrator/i });
            const developerCheckbox = screen.getByRole('checkbox', { name: /Developer/i });
            const techLeadCheckbox = screen.getByRole('checkbox', { name: /Tech Lead/i });

            expect(adminCheckbox).toBeChecked();
            expect(developerCheckbox).toBeChecked();
            expect(techLeadCheckbox).not.toBeChecked();
        });

        it('should toggle role selection when checkbox clicked', async () => {
            const user = userEvent.setup();
            renderComponent();

            const techLeadCheckbox = screen.getByRole('checkbox', { name: /Tech Lead/i });
            expect(techLeadCheckbox).not.toBeChecked();

            await user.click(techLeadCheckbox);
            expect(techLeadCheckbox).toBeChecked();

            await user.click(techLeadCheckbox);
            expect(techLeadCheckbox).not.toBeChecked();
        });

        it('should prevent selecting more than 5 roles', async () => {
            const user = userEvent.setup();
            const manyRoles = Array.from({ length: 10 }, (_, i) => ({
                id: `role-${i}`,
                roleId: `role-${i}`,
                name: `Role ${i}`,
                displayName: `Role ${i}`,
                type: 'BASE' as const,
                description: `Role ${i}`,
                permissions: [],
                capabilities: [],
                parentRoles: [],
            }));

            vi.mocked(usePersonaQueries.useRoleDefinitions).mockReturnValue({
                data: manyRoles,
                isLoading: false,
                isError: false,
                error: null,
            } as any);

            vi.mocked(usePersonaQueries.usePersonaPreference).mockReturnValue({
                data: { ...mockPreference, activeRoles: [] },
                isLoading: false,
                isError: false,
                error: null,
            } as any);

            renderComponent();

            // Select 5 roles
            for (let i = 0; i < 5; i++) {
                const checkbox = screen.getByRole('checkbox', { name: new RegExp(`Role ${i}`) });
                await user.click(checkbox);
            }

            // Try to select 6th role
            const sixthCheckbox = screen.getByRole('checkbox', { name: /Role 5/i });
            await user.click(sixthCheckbox);

            expect(mockShowWarning).toHaveBeenCalledWith('Maximum 5 roles allowed');
            expect(sixthCheckbox).not.toBeChecked();
        });

        it('should allow deselecting roles', async () => {
            const user = userEvent.setup();
            renderComponent();

            const adminCheckbox = screen.getByRole('checkbox', { name: /Administrator/i });
            expect(adminCheckbox).toBeChecked();

            await user.click(adminCheckbox);
            expect(adminCheckbox).not.toBeChecked();
        });
    });

    describe('Save Functionality', () => {
        it('should save selected roles when Save button clicked', async () => {
            const user = userEvent.setup();
            const mockMutate = vi.fn().mockResolvedValue(mockPreference);

            vi.mocked(usePersonaQueries.useUpdatePersonaPreference).mockReturnValue({
                mutateAsync: mockMutate,
                isPending: false,
            } as any);

            renderComponent();

            // Toggle tech-lead role
            const techLeadCheckbox = screen.getByRole('checkbox', { name: /Tech Lead/i });
            await user.click(techLeadCheckbox);

            // Click save
            const saveButton = screen.getByRole('button', { name: /Save/i });
            await user.click(saveButton);

            await waitFor(() => {
                expect(mockMutate).toHaveBeenCalledWith({
                    activeRoles: ['admin', 'developer', 'tech-lead'],
                    preferences: { dashboardLayout: 'grid' },
                });
            });

            expect(mockShowSuccess).toHaveBeenCalledWith('Persona preferences saved successfully!');
        });

        it('should prevent saving with 0 roles selected', async () => {
            const user = userEvent.setup();
            const mockMutate = vi.fn();

            vi.mocked(usePersonaQueries.useUpdatePersonaPreference).mockReturnValue({
                mutateAsync: mockMutate,
                isPending: false,
            } as any);

            renderComponent();

            // Deselect all roles
            const adminCheckbox = screen.getByRole('checkbox', { name: /Administrator/i });
            const developerCheckbox = screen.getByRole('checkbox', { name: /Developer/i });
            await user.click(adminCheckbox);
            await user.click(developerCheckbox);

            // Save button should be disabled
            const saveButton = screen.getByRole('button', { name: /Save/i });
            expect(saveButton).toBeDisabled();
            expect(mockMutate).not.toHaveBeenCalled();
        });

        it('should show error message when save fails', async () => {
            const user = userEvent.setup();
            const mockMutate = vi.fn().mockRejectedValue(new Error('Network error'));

            vi.mocked(usePersonaQueries.useUpdatePersonaPreference).mockReturnValue({
                mutateAsync: mockMutate,
                isPending: false,
            } as any);

            renderComponent();

            const saveButton = screen.getByRole('button', { name: /Save/i });
            await user.click(saveButton);

            await waitFor(() => {
                expect(mockShowError).toHaveBeenCalledWith('Failed to save: Network error');
            });
        });

        it('should disable save button while mutation is pending', async () => {
            vi.mocked(usePersonaQueries.useUpdatePersonaPreference).mockReturnValue({
                mutateAsync: vi.fn().mockImplementation(() => new Promise(() => { })),
                isPending: true,
            } as any);

            renderComponent();

            const saveButton = screen.getByRole('button', { name: /Saving/i });
            expect(saveButton).toBeDisabled();
        });
    });

    describe('Reset Functionality', () => {
        it('should reset selected roles to original preference', async () => {
            const user = userEvent.setup();
            renderComponent();

            // Toggle roles
            const techLeadCheckbox = screen.getByRole('checkbox', { name: /Tech Lead/i });
            await user.click(techLeadCheckbox);
            expect(techLeadCheckbox).toBeChecked();

            // Click reset
            const resetButton = screen.getByRole('button', { name: /Reset/i });
            await user.click(resetButton);

            // Should revert to original
            await waitFor(() => {
                expect(techLeadCheckbox).not.toBeChecked();
            });
        });
    });

    describe('Sync Status Banner', () => {
        it('should show connected status when WebSocket connected', () => {
            vi.mocked(usePersonaSync.usePersonaSync).mockReturnValue({
                isConnected: true,
                error: null,
                reconnect: vi.fn(),
            });

            renderComponent();

            expect(screen.getByText(/Real-time sync active/i)).toBeInTheDocument();
            expect(screen.getByText(/Real-time sync active/i).closest('div')).toHaveClass(
                'bg-green-50'
            );
        });

        it('should show connecting status when WebSocket connecting', () => {
            vi.mocked(usePersonaSync.usePersonaSync).mockReturnValue({
                isConnected: false,
                error: null,
                reconnect: vi.fn(),
            });

            renderComponent();

            expect(screen.getByText(/Connecting to real-time sync/i)).toBeInTheDocument();
        });

        it('should show error status when WebSocket disconnected', () => {
            vi.mocked(usePersonaSync.usePersonaSync).mockReturnValue({
                isConnected: false,
                error: new Error('Connection failed'),
                reconnect: vi.fn(),
            });

            renderComponent();

            expect(screen.getByText(/Real-time sync unavailable/i)).toBeInTheDocument();
            expect(screen.getByText(/Connection failed/i)).toBeInTheDocument();
        });
    });

    describe('Workspace Context', () => {
        it('should use workspace ID from URL params', () => {
            const mockUsePreference = vi.fn().mockReturnValue({
                data: mockPreference,
                isLoading: false,
                isError: false,
                error: null,
            });

            vi.mocked(usePersonaQueries.usePersonaPreference).mockImplementation(
                mockUsePreference as any
            );

            renderComponent('custom-workspace');

            expect(mockUsePreference).toHaveBeenCalledWith('custom-workspace');
        });

        it('should default to "default" workspace when not specified', () => {
            const mockUsePreference = vi.fn().mockReturnValue({
                data: mockPreference,
                isLoading: false,
                isError: false,
                error: null,
            });

            vi.mocked(usePersonaQueries.usePersonaPreference).mockImplementation(
                mockUsePreference as any
            );

            render(
                <QueryClientProvider client={queryClient}>
                    <MemoryRouter initialEntries={['/personas']}>
                        <Routes>
                            <Route path="/personas" element={<PersonasPage />} />
                        </Routes>
                    </MemoryRouter>
                </QueryClientProvider>
            );

            expect(mockUsePreference).toHaveBeenCalledWith('default');
        });
    });

    describe('Accessibility', () => {
        it('should have proper ARIA labels for checkboxes', () => {
            renderComponent();

            const adminCheckbox = screen.getByRole('checkbox', { name: /Administrator/i });
            expect(adminCheckbox).toHaveAccessibleName('Administrator');
        });

        it('should have proper button labels', () => {
            renderComponent();

            expect(screen.getByRole('button', { name: /Save/i })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /Reset/i })).toBeInTheDocument();
        });

        it('should support keyboard navigation', async () => {
            const user = userEvent.setup();
            renderComponent();

            const firstCheckbox = screen.getByRole('checkbox', { name: /Administrator/i });

            // Tab through view mode toggle buttons first (3 buttons: List/Tree/Audit)
            await user.tab(); // List View button
            await user.tab(); // Tree View button
            await user.tab(); // Audit Log button

            // Tab to first checkbox
            await user.tab();
            expect(firstCheckbox).toHaveFocus();

            // Space to toggle
            await user.keyboard(' ');
            expect(firstCheckbox).not.toBeChecked();
        });
    });

    describe('Edge Cases', () => {
        it('should handle undefined preference gracefully', () => {
            vi.mocked(usePersonaQueries.usePersonaPreference).mockReturnValue({
                data: undefined,
                isLoading: false,
                isError: false,
                error: null,
            } as any);

            renderComponent();

            // Should render without crashing
            expect(screen.getByText('Base Roles')).toBeInTheDocument();
        });

        it('should handle empty role definitions', () => {
            vi.mocked(usePersonaQueries.useRoleDefinitions).mockReturnValue({
                data: [],
                isLoading: false,
                isError: false,
                error: null,
            } as any);

            renderComponent();

            // Should render empty state
            expect(screen.queryByText('Administrator')).not.toBeInTheDocument();
        });

        it('should update selected roles when preference changes', async () => {
            const { rerender } = renderComponent();

            const adminCheckbox = screen.getByRole('checkbox', { name: /Administrator/i });
            expect(adminCheckbox).toBeChecked();

            // Simulate preference update from WebSocket
            vi.mocked(usePersonaQueries.usePersonaPreference).mockReturnValue({
                data: { ...mockPreference, activeRoles: ['developer'] },
                isLoading: false,
                isError: false,
                error: null,
            } as any);

            rerender(
                <QueryClientProvider client={queryClient}>
                    <MemoryRouter initialEntries={['/personas/default']}>
                        <Routes>
                            <Route path="/personas/:workspaceId" element={<PersonasPage />} />
                        </Routes>
                    </MemoryRouter>
                </QueryClientProvider>
            );

            await waitFor(() => {
                expect(adminCheckbox).not.toBeChecked();
            });
        });
    });

    /**
     * Accessibility Tests
     * 
     * Validates accessibility features and WCAG compliance
     * Note: Using @ghatana/yappc-accessibility-audit for automated axe-core audits
     * requires jsdom which conflicts with Vitest's browser environment.
     * These tests verify accessible structure manually.
     */
    describe('Accessibility', () => {
        it('should have proper accessible structure', () => {
            const { container } = renderComponent();

            // Verify all interactive elements have accessible labels
            const checkboxes = screen.getAllByRole('checkbox');
            checkboxes.forEach(checkbox => {
                expect(checkbox).toHaveAccessibleName();
            });

            // Verify buttons have proper labels
            const buttons = screen.getAllByRole('button');
            buttons.forEach(button => {
                expect(button).toHaveAccessibleName();
            });

            // Verify proper heading hierarchy
            const heading = screen.getByRole('heading', { name: /Persona Preferences/i });
            expect(heading).toBeInTheDocument();

            // Verify checkboxes have minimum touch target class (44x44px)
            checkboxes.forEach(checkbox => {
                expect(checkbox).toHaveClass('min-h-[44px]');
                expect(checkbox).toHaveClass('min-w-[44px]');
            });
        });

        it('should have accessible loading state', () => {
            // Mock loading state
            vi.mocked(usePersonaQueries.useRoleDefinitions).mockReturnValue({
                data: undefined,
                isLoading: true,
                isError: false,
                error: null,
            } as any);

            const { container } = renderComponent();

            // Verify skeleton has role="status" and aria-label
            const skeletons = screen.getAllByRole('status', { name: /loading/i });
            expect(skeletons.length).toBeGreaterThan(0);

            // Verify each skeleton has proper accessibility label
            skeletons.forEach(skeleton => {
                expect(skeleton).toHaveAccessibleName();
            });
        });
    });
});
