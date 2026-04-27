/**
 * Tests for guidance/GuidancePanel
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GuidancePanel } from '../GuidancePanel';

// Mock the WorkflowContextProvider hooks
vi.mock('../../../context/WorkflowContextProvider', () => ({
  usePhaseContext: vi.fn(),
  useGuidanceContext: vi.fn(),
}));

import { usePhaseContext, useGuidanceContext } from '../../../context/WorkflowContextProvider';

const mockPhaseContext = {
  currentPhase: 'INTENT' as const,
  availablePhases: ['INTENT'],
  canTransitionTo: vi.fn().mockReturnValue(false),
  navigateToPhase: vi.fn(),
};

const mockGuidanceContext = {
  currentPhaseSteps: [
    { id: 'step-1', title: 'Define goals', description: 'Set project goals' },
    { id: 'step-2', title: 'Set scope', description: 'Define project scope' },
  ],
  tips: ['Start with a clear vision', 'Keep scope small'],
  completedSteps: ['step-1'],
  completeStep: vi.fn(),
  dismissTip: vi.fn(),
  resetGuidance: vi.fn(),
  toggleGuidancePanel: vi.fn(),
};

describe('GuidancePanel', () => {
  beforeEach(() => {
    vi.mocked(usePhaseContext).mockReturnValue(mockPhaseContext);
    vi.mocked(useGuidanceContext).mockReturnValue(mockGuidanceContext);
  });

  it('renders guidance panel with aria-label', () => {
    render(<GuidancePanel />);
    expect(screen.getByRole('complementary', { name: /guidance panel/i })).toBeTruthy();
  });

  it('renders Guidance heading', () => {
    render(<GuidancePanel />);
    expect(screen.getByText('Guidance')).toBeTruthy();
  });

  it('renders Current Phase section', () => {
    render(<GuidancePanel />);
    expect(screen.getByText('Current Phase')).toBeTruthy();
  });

  it('renders step titles from context', () => {
    render(<GuidancePanel />);
    expect(screen.getByText('Define goals')).toBeTruthy();
    expect(screen.getByText('Set scope')).toBeTruthy();
  });

  it('renders collapsed state when defaultCollapsed=true', () => {
    render(<GuidancePanel defaultCollapsed />);
    // Should NOT render the aside aria-label when collapsed
    expect(screen.queryByRole('complementary', { name: /guidance panel/i })).toBeNull();
  });

  it('calls onToggle when toggle button clicked', () => {
    const onToggle = vi.fn();
    render(<GuidancePanel onToggle={onToggle} />);
    // The header has two buttons: Reset (index 0) and Collapse (index 1)
    const buttons = screen.getAllByRole('button');
    // Collapse panel button is the second in the header
    fireEvent.click(buttons[1]);
    expect(onToggle).toHaveBeenCalledWith(true);
  });

  it('toggles from collapsed to expanded on button click', () => {
    render(<GuidancePanel defaultCollapsed />);
    // In collapsed state, there's an expand icon button
    const buttons = screen.getAllByRole('button');
    fireEvent.click(buttons[0]);
    // After expanding, should show the Guidance heading
    expect(screen.getByText('Guidance')).toBeTruthy();
  });
});
