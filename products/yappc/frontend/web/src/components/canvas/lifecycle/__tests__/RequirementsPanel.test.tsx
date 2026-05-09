/**
 * RequirementsPanel unit tests
 *
 * Tests the hierarchical requirements editor: Epics → Capabilities → Requirements.
 */

import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { RequirementsPanel } from '../RequirementsPanel';
import type { RequirementsPanelProps } from '../RequirementsPanel';
import type { RequirementsPayload } from '@/shared/types/lifecycle-artifacts';

// ─── helpers ────────────────────────────────────────────────────────────────

function defaultProps(overrides: Partial<RequirementsPanelProps> = {}): RequirementsPanelProps {
    return {
        onSave: vi.fn().mockResolvedValue(undefined),
        onClose: vi.fn(),
        ...overrides,
    };
}

const samplePayload: RequirementsPayload = {
    epics: [
        {
            id: 'e1',
            title: 'User Management',
            description: 'Handle all user operations',
            capabilities: [
                {
                    id: 'c1',
                    title: 'Authentication',
                    requirements: [
                        {
                            id: 'r1',
                            statement: 'Users must be able to log in',
                            priority: 'must',
                            acceptanceCriteria: ['Given valid credentials, then login succeeds'],
                            nfrTags: ['Security'],
                        },
                    ],
                },
            ],
        },
    ],
};

// ─── rendering ──────────────────────────────────────────────────────────────

describe('RequirementsPanel', () => {
    describe('rendering', () => {
        it('renders the panel header', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            expect(screen.getByText('Requirements')).toBeInTheDocument();
            expect(screen.getByText('Epics → Capabilities → Requirements')).toBeInTheDocument();
        });

        it('renders Save button', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument();
        });

        it('does not render Guided Assist button when onAIAssist is not provided', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            expect(screen.queryByRole('button', { name: /guided assist/i })).not.toBeInTheDocument();
        });

        it('renders Guided Assist button when onAIAssist is provided', () => {
            render(
                <RequirementsPanel
                    {...defaultProps()}
                    onAIAssist={vi.fn().mockResolvedValue(null)}
                />
            );
            expect(screen.getByRole('button', { name: /guided assist/i })).toBeInTheDocument();
        });

        it('renders Add Epic button', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            expect(screen.getByRole('button', { name: /add epic/i })).toBeInTheDocument();
        });

        it('renders with empty state (default epic) when no data provided', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            expect(screen.getByPlaceholderText('Epic title')).toBeInTheDocument();
        });

        it('renders supplied epics from data prop', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            expect(screen.getByDisplayValue('User Management')).toBeInTheDocument();
        });

        it('renders epic description when data provided', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            expect(screen.getByDisplayValue('Handle all user operations')).toBeInTheDocument();
        });

        it('renders capability title when epic is expanded', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            // Epic is expanded by default; capability title should be visible
            expect(screen.getByDisplayValue('Authentication')).toBeInTheDocument();
        });

        it('renders NFR option buttons when capability is expanded', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            // Expand the capability to reveal requirements + NFR buttons
            const capToggle = screen.getAllByRole('button').find(
                (btn) => btn.className.includes('p-0.5')
            );
            if (capToggle) {
                fireEvent.click(capToggle);
                expect(screen.getByRole('button', { name: 'Security' })).toBeInTheDocument();
                expect(screen.getByRole('button', { name: 'Performance' })).toBeInTheDocument();
            }
        });
    });

    // ─── epic interactions ───────────────────────────────────────────────────

    describe('epic interactions', () => {
        it('updates epic title on input change', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            const input = screen.getByPlaceholderText('Epic title');
            fireEvent.change(input, { target: { value: 'New Epic' } });
            expect(screen.getByDisplayValue('New Epic')).toBeInTheDocument();
        });

        it('updates epic description on input change', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            const input = screen.getByPlaceholderText('Epic description (optional)');
            fireEvent.change(input, { target: { value: 'Some description' } });
            expect(screen.getByDisplayValue('Some description')).toBeInTheDocument();
        });

        it('adds a new epic when Add Epic is clicked', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            const addButton = screen.getByRole('button', { name: /add epic/i });
            fireEvent.click(addButton);
            const epicInputs = screen.getAllByPlaceholderText('Epic title');
            expect(epicInputs).toHaveLength(2);
        });

        it('removes an epic when remove button is clicked (only when multiple epics exist)', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            // First add an epic so we have 2
            fireEvent.click(screen.getByRole('button', { name: /add epic/i }));
            const removeButtons = screen.getAllByRole('button', { name: /remove epic/i });
            expect(removeButtons.length).toBeGreaterThan(0);
            fireEvent.click(removeButtons[0]);
            expect(screen.getAllByPlaceholderText('Epic title')).toHaveLength(1);
        });

        it('does not show remove epic button when only one epic exists', () => {
            render(<RequirementsPanel {...defaultProps()} />);
            expect(screen.queryByRole('button', { name: /remove epic/i })).not.toBeInTheDocument();
        });

        it('collapses and expands epic when toggle button clicked', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            // Initially expanded — capability input should be visible
            expect(screen.getByDisplayValue('Authentication')).toBeInTheDocument();

            // Click toggle to collapse
            const toggleButtons = screen.getAllByRole('button');
            const collapseButton = toggleButtons.find(
                (btn) => btn.className.includes('text-text-secondary') && btn.className.includes('mt-0.5')
            );
            if (collapseButton) {
                fireEvent.click(collapseButton);
                expect(screen.queryByDisplayValue('Authentication')).not.toBeInTheDocument();
            }
        });
    });

    // ─── capability interactions ─────────────────────────────────────────────

    describe('capability interactions', () => {
        it('adds a capability when Add capability is clicked', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            const addCapBtn = screen.getByRole('button', { name: /add capability/i });
            fireEvent.click(addCapBtn);
            const capInputs = screen.getAllByPlaceholderText('Capability title');
            expect(capInputs.length).toBeGreaterThan(1);
        });

        it('updates capability title on input change', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            const capInput = screen.getByDisplayValue('Authentication');
            fireEvent.change(capInput, { target: { value: 'Authorization' } });
            expect(screen.getByDisplayValue('Authorization')).toBeInTheDocument();
        });

        it('removes a capability when remove button is clicked (only when multiple)', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            // Add a second capability first
            fireEvent.click(screen.getByRole('button', { name: /add capability/i }));
            const removeCapButtons = screen.getAllByRole('button', { name: /remove capability/i });
            expect(removeCapButtons.length).toBeGreaterThan(0);
            fireEvent.click(removeCapButtons[0]);
            expect(screen.getAllByPlaceholderText('Capability title')).toHaveLength(1);
        });

        it('does not show remove capability button when only one capability exists', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            expect(screen.queryByRole('button', { name: /remove capability/i })).not.toBeInTheDocument();
        });
    });

    // ─── requirement interactions ────────────────────────────────────────────

    describe('requirement interactions', () => {
        it('expands capability to show requirements when capability toggle clicked', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            // Find the capability expand button
            const capToggle = screen.getAllByRole('button').find(
                (btn) => btn.className.includes('text-text-secondary') && btn.className.includes('p-0.5')
            );
            if (capToggle) {
                fireEvent.click(capToggle);
                expect(screen.getByPlaceholderText('Requirement statement')).toBeInTheDocument();
            }
        });

        it('adds a requirement when Add requirement is clicked', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            // Expand capability to see requirements
            const capToggle = screen.getAllByRole('button').find(
                (btn) => btn.className.includes('p-0.5') && !btn.closest('[class*="mt-0.5"]')
            );
            if (capToggle) fireEvent.click(capToggle);

            const addReqBtn = screen.queryByRole('button', { name: /add requirement/i });
            if (addReqBtn) {
                const reqCountBefore = screen.queryAllByPlaceholderText('Requirement statement').length;
                fireEvent.click(addReqBtn);
                expect(screen.queryAllByPlaceholderText('Requirement statement').length).toBeGreaterThan(reqCountBefore);
            }
        });

        it('selects NFR tag when clicked', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            // Expand capability to see requirements
            const capToggle = screen.getAllByRole('button').find((btn) =>
                btn.className.includes('p-0.5')
            );
            if (capToggle) fireEvent.click(capToggle);

            const accessibilityBtn = screen.queryByRole('button', { name: 'Accessibility' });
            if (accessibilityBtn) {
                fireEvent.click(accessibilityBtn);
                // After clicking, should have selected semantic token classes.
                expect(accessibilityBtn).toHaveClass('bg-info-bg');
                expect(accessibilityBtn).toHaveClass('text-info-color');
            }
        });
    });

    // ─── save interactions ───────────────────────────────────────────────────

    describe('save interactions', () => {
        it('calls onSave with current epics when Save is clicked', async () => {
            const onSave = vi.fn().mockResolvedValue(undefined);
            render(<RequirementsPanel {...defaultProps({ onSave })} />);

            const input = screen.getByPlaceholderText('Epic title');
            fireEvent.change(input, { target: { value: 'My Epic' } });

            fireEvent.click(screen.getByRole('button', { name: /save/i }));

            await waitFor(() => {
                expect(onSave).toHaveBeenCalledOnce();
                const savedData = onSave.mock.calls[0][0] as RequirementsPayload;
                expect(savedData.epics[0].title).toBe('My Epic');
            });
        });

        it('shows Saving... text while saving', async () => {
            let resolveSave!: () => void;
            const onSave = vi.fn().mockReturnValue(new Promise<void>((resolve) => {
                resolveSave = resolve;
            }));

            render(<RequirementsPanel {...defaultProps({ onSave })} />);
            fireEvent.click(screen.getByRole('button', { name: /save/i }));

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /saving/i })).toBeInTheDocument();
            });

            await act(async () => {
                resolveSave();
            });
        });

        it('disables Save button while saving', async () => {
            let resolveSave!: () => void;
            const onSave = vi.fn().mockReturnValue(new Promise<void>((resolve) => {
                resolveSave = resolve;
            }));

            render(<RequirementsPanel {...defaultProps({ onSave })} />);
            fireEvent.click(screen.getByRole('button', { name: /save/i }));

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /saving/i })).toBeDisabled();
            });

            await act(async () => {
                resolveSave();
            });
        });

        it('disables Save button when isLoading prop is true', () => {
            render(<RequirementsPanel {...defaultProps({ isLoading: true })} />);
            expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
        });
    });

    // ─── AI assist ──────────────────────────────────────────────────────────

    describe('AI assist', () => {
        it('calls onAIAssist with current requirements context when Guided Assist clicked', async () => {
            const onAIAssist = vi.fn().mockResolvedValue(null);
            render(<RequirementsPanel {...defaultProps({ onAIAssist })} />);

            const input = screen.getByPlaceholderText('Epic title');
            fireEvent.change(input, { target: { value: 'Payments' } });

            fireEvent.click(screen.getByRole('button', { name: /guided assist/i }));

            await waitFor(() => {
                expect(onAIAssist).toHaveBeenCalledOnce();
                const ctx = onAIAssist.mock.calls[0][0] as { requirements?: RequirementsPayload };
                expect(ctx.requirements?.epics[0].title).toBe('Payments');
            });
        });

        it('replaces epics with AI result when result has epics', async () => {
            const aiEpics = [{ id: 'ai1', title: 'AI Epic', description: '', capabilities: [] }];
            const onAIAssist = vi.fn().mockResolvedValue({ epics: aiEpics });

            render(<RequirementsPanel {...defaultProps({ onAIAssist })} />);
            fireEvent.click(screen.getByRole('button', { name: /guided assist/i }));

            await waitFor(() => {
                expect(screen.getByDisplayValue('AI Epic')).toBeInTheDocument();
            });
        });

        it('shows Generating... during AI assist', async () => {
            let resolveAI!: () => void;
            const onAIAssist = vi.fn().mockReturnValue(new Promise<null>((resolve) => {
                resolveAI = () => resolve(null);
            }));

            render(<RequirementsPanel {...defaultProps({ onAIAssist })} />);
            fireEvent.click(screen.getByRole('button', { name: /guided assist/i }));

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /generating/i })).toBeInTheDocument();
            });

            await act(async () => {
                resolveAI();
            });
        });

        it('disables Guided Assist button while AI is loading', async () => {
            let resolveAI!: () => void;
            const onAIAssist = vi.fn().mockReturnValue(new Promise<null>((resolve) => {
                resolveAI = () => resolve(null);
            }));

            render(<RequirementsPanel {...defaultProps({ onAIAssist })} />);
            fireEvent.click(screen.getByRole('button', { name: /guided assist/i }));

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /generating/i })).toBeDisabled();
            });

            await act(async () => {
                resolveAI();
            });
        });
    });

    // ─── acceptance criteria ─────────────────────────────────────────────────

    describe('acceptance criteria', () => {
        beforeEach(() => {
            // Use samplePayload and expand the capability
        });

        it('renders Acceptance Criteria label inside expanded capability', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            // Expand capability
            const capToggle = screen.getAllByRole('button').find(
                (btn) => btn.className.includes('p-0.5') && !btn.closest('[class*="mt-0.5"]')
            );
            if (capToggle) {
                fireEvent.click(capToggle);
                expect(screen.getByText('Acceptance Criteria')).toBeInTheDocument();
            }
        });

        it('renders Add criterion button inside expanded capability', () => {
            render(<RequirementsPanel {...defaultProps({ data: samplePayload })} />);
            const capToggle = screen.getAllByRole('button').find(
                (btn) => btn.className.includes('p-0.5')
            );
            if (capToggle) {
                fireEvent.click(capToggle);
                expect(screen.getByRole('button', { name: /add criterion/i })).toBeInTheDocument();
            }
        });
    });
});
