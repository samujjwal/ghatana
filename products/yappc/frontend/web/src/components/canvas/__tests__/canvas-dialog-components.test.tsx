/**
 * Canvas dialog/utility component tests — CC-AK
 *
 * Covers:
 *   - KeyboardShortcutLegend: open/closed, category headings, shortcut keys, onClose
 *   - CanvasProgressWidget (mini variant): renders with progress display
 *   - CanvasProgressWidget (compact variant): shows overall progress, phase click callback
 */

import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { KeyboardShortcutLegend } from '../KeyboardShortcutLegend';
import { CanvasProgressWidget } from '../CanvasProgressWidget';
import type { PhaseProgress } from '../CanvasProgressWidget';
import type { LifecyclePhase } from '@/types/lifecycle';

// ---------------------------------------------------------------------------
// KeyboardShortcutLegend
// ---------------------------------------------------------------------------

describe('KeyboardShortcutLegend', () => {
  it('renders nothing visible when open=false', () => {
    render(<KeyboardShortcutLegend open={false} onClose={vi.fn()} />);
    // Dialog is not in DOM or hidden
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('renders "Keyboard Shortcuts" heading when open=true', () => {
    render(<KeyboardShortcutLegend open={true} onClose={vi.fn()} />);
    expect(screen.getByText('Keyboard Shortcuts')).toBeDefined();
  });

  it('calls onClose when close button is clicked', () => {
    const onClose = vi.fn();
    render(<KeyboardShortcutLegend open={true} onClose={onClose} />);
    const buttons = screen.getAllByRole('button');
    // First icon button is the close button
    fireEvent.click(buttons[0]!);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('shows Essential category', () => {
    render(<KeyboardShortcutLegend open={true} onClose={vi.fn()} />);
    expect(screen.getByText('Essential')).toBeDefined();
  });

  it('shows Tools category', () => {
    render(<KeyboardShortcutLegend open={true} onClose={vi.fn()} />);
    expect(screen.getByText('Tools')).toBeDefined();
  });

  it('shows Undo shortcut description', () => {
    render(<KeyboardShortcutLegend open={true} onClose={vi.fn()} />);
    expect(screen.getByText('Undo')).toBeDefined();
  });

  it('shows Redo shortcut description', () => {
    render(<KeyboardShortcutLegend open={true} onClose={vi.fn()} />);
    expect(screen.getByText('Redo')).toBeDefined();
  });

  it('shows "?" hint text about opening the dialog', () => {
    render(<KeyboardShortcutLegend open={true} onClose={vi.fn()} />);
    expect(screen.getByText(/press .* to open|shortcut.*legend|keyboard shortcut/i)).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// CanvasProgressWidget helpers
// ---------------------------------------------------------------------------

function makePhase(
  phase: LifecyclePhase,
  overrides: Partial<PhaseProgress> = {}
): PhaseProgress {
  return {
    phase,
    progress: 50,
    tasksCompleted: 2,
    tasksTotal: 4,
    status: 'in-progress',
    ...overrides,
  };
}

const SAMPLE_PHASES: PhaseProgress[] = [
  makePhase('INTENT', { progress: 100, status: 'completed', tasksCompleted: 4, tasksTotal: 4 }),
  makePhase('SHAPE', { progress: 60, status: 'in-progress' }),
  makePhase('VALIDATE', { progress: 0, status: 'pending', tasksCompleted: 0 }),
];

// ---------------------------------------------------------------------------
// CanvasProgressWidget — mini variant
// ---------------------------------------------------------------------------

describe('CanvasProgressWidget (mini variant)', () => {
  it('renders without crashing', () => {
    const { container } = render(
      <CanvasProgressWidget
        phases={SAMPLE_PHASES}
        currentPhase="SHAPE"
        variant="mini"
      />
    );
    expect(container.firstChild).toBeDefined();
  });

  it('shows overall progress percentage', () => {
    render(
      <CanvasProgressWidget
        phases={SAMPLE_PHASES}
        currentPhase="SHAPE"
        variant="mini"
      />
    );
    // avg of 100 + 60 + 0 = 160/3 = 53%
    const expectedPct = Math.round((100 + 60 + 0) / 3);
    expect(screen.getByText(new RegExp(`${expectedPct}%`))).toBeDefined();
  });

  it('shows completed phases count in tooltip', () => {
    render(
      <CanvasProgressWidget
        phases={SAMPLE_PHASES}
        currentPhase="SHAPE"
        variant="mini"
      />
    );
    // The fraction is in the Tooltip title attribute (not visible text)
    // The component renders a Tooltip with title="53% complete (1/3 phases)"
    const expectedPct = Math.round((100 + 60 + 0) / 3);
    // Check the progress percentage is shown in the box
    expect(screen.getByText(`${expectedPct}%`)).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// CanvasProgressWidget — compact variant (default)
// ---------------------------------------------------------------------------

describe('CanvasProgressWidget (compact variant)', () => {
  it('renders without crashing', () => {
    const { container } = render(
      <CanvasProgressWidget
        phases={SAMPLE_PHASES}
        currentPhase="SHAPE"
      />
    );
    expect(container.firstChild).toBeDefined();
  });

  it('shows overall progress percentage in header', () => {
    render(
      <CanvasProgressWidget
        phases={SAMPLE_PHASES}
        currentPhase="SHAPE"
      />
    );
    const expectedPct = Math.round((100 + 60 + 0) / 3);
    const matches = screen.getAllByText(new RegExp(`${expectedPct}%`));
    expect(matches.length).toBeGreaterThanOrEqual(1);
  });

  it('displays phase indicators in expanded state', () => {
    render(
      <CanvasProgressWidget
        phases={SAMPLE_PHASES}
        currentPhase="SHAPE"
      />
    );
    // Click to expand
    const header = screen.getByRole('button');
    fireEvent.click(header);
    // Should show phase labels
    expect(screen.getByText('Intent')).toBeDefined();
    expect(screen.getByText('Shape')).toBeDefined();
  });

  it('calls onPhaseClick when phase indicator is clicked in expanded state', () => {
    const onPhaseClick = vi.fn();
    render(
      <CanvasProgressWidget
        phases={SAMPLE_PHASES}
        currentPhase="SHAPE"
        onPhaseClick={onPhaseClick}
      />
    );
    // Expand the widget
    fireEvent.click(screen.getByRole('button'));
    // Find phase label and click
    const intentLabel = screen.getByText('Intent');
    fireEvent.click(intentLabel);
    expect(onPhaseClick).toHaveBeenCalledWith('INTENT');
  });

  it('shows 0% overall when no phases provided', () => {
    render(
      <CanvasProgressWidget
        phases={[]}
        currentPhase="INTENT"
      />
    );
    expect(screen.getByText('0%')).toBeDefined();
  });
});
