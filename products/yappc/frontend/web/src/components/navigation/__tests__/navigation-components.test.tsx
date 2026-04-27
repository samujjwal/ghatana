/**
 * Tests for navigation/ components:
 * ActionsToolbar, UnifiedPhaseRail
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ActionsToolbar } from '../ActionsToolbar';
import { UnifiedPhaseRail } from '../UnifiedPhaseRail';

// Mock WorkflowContextProvider for UnifiedPhaseRail
vi.mock('../../../context/WorkflowContextProvider', () => ({
  usePhaseContext: vi.fn(),
}));

// Mock design-tokens to provide all phase color keys (including remapped enum values)
const mockColors = {
  bg: 'bg-blue-50',
  text: 'text-blue-700',
  border: 'border-blue-200',
  activeBg: 'bg-blue-600',
  gradient: 'from-blue-500 to-blue-600',
  icon: '💡',
};
vi.mock('../../../styles/design-tokens', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../styles/design-tokens')>();
  const colorsForAll: Record<string, typeof mockColors> = {};
  // Cover all enum values (INTENT, OBSERVE, CONTEXT, PLAN, EXECUTE, VERIFY, LEARN, SHAPE, VALIDATE, GENERATE, RUN, IMPROVE)
  ['INTENT', 'OBSERVE', 'SHAPE', 'VALIDATE', 'GENERATE', 'RUN', 'IMPROVE', 'CONTEXT', 'PLAN', 'EXECUTE', 'VERIFY', 'LEARN'].forEach(k => {
    colorsForAll[k] = {
      bg: 'bg-blue-50',
      text: 'text-blue-700',
      border: 'border-blue-200',
      activeBg: 'bg-blue-600',
      gradient: 'from-blue-500 to-blue-600',
      icon: '💡',
    };
  });
  return {
    ...actual,
    PHASE_COLORS: colorsForAll,
    PHASE_LABELS: {
      INTENT: 'Intent', OBSERVE: 'Observe', SHAPE: 'Shape', VALIDATE: 'Validate',
      GENERATE: 'Generate', RUN: 'Run', IMPROVE: 'Improve',
      CONTEXT: 'Context', PLAN: 'Plan', EXECUTE: 'Execute', VERIFY: 'Verify', LEARN: 'Learn',
    },
    PHASE_DESCRIPTIONS: {
      INTENT: 'Intent phase', OBSERVE: 'Observe phase', SHAPE: 'Shape phase', VALIDATE: 'Validate phase',
      GENERATE: 'Generate phase', RUN: 'Run phase', IMPROVE: 'Improve phase',
      CONTEXT: 'Context phase', PLAN: 'Plan phase', EXECUTE: 'Execute phase', VERIFY: 'Verify phase', LEARN: 'Learn phase',
    },
    PHASE_ICONS: {
      INTENT: 'lightbulb', OBSERVE: 'eye', SHAPE: 'shape', VALIDATE: 'check',
      GENERATE: 'code', RUN: 'play', IMPROVE: 'trend',
      CONTEXT: 'ctx', PLAN: 'plan', EXECUTE: 'exec', VERIFY: 'verify', LEARN: 'learn',
    },
  };
});

import { usePhaseContext } from '../../../context/WorkflowContextProvider';

const mockPhaseContext = {
  currentPhase: 'OBSERVE' as const,
  availablePhases: ['INTENT', 'OBSERVE'],
  canTransitionTo: vi.fn().mockReturnValue(true),
  navigateToPhase: vi.fn(),
};

// ─── ActionsToolbar ──────────────────────────────────────────────────────────

const MockIcon: React.FC = () => <span data-testid="mock-icon" />;

describe('ActionsToolbar', () => {
  const makeAction = (overrides: Partial<import('../ActionsToolbar').Action> = {}): import('../ActionsToolbar').Action => ({
    id: 'save',
    label: 'Save',
    icon: MockIcon,
    onClick: vi.fn(),
    ...overrides,
  });

  it('renders toolbar with role=toolbar', () => {
    render(<ActionsToolbar context="global" contextActions={[makeAction()]} />);
    expect(screen.getByRole('toolbar')).toBeTruthy();
  });

  it('renders action button with aria-label', () => {
    render(<ActionsToolbar context="project" contextActions={[makeAction({ label: 'Save' })]} />);
    expect(screen.getByRole('button', { name: 'Save' })).toBeTruthy();
  });

  it('renders multiple action buttons', () => {
    const actions = [
      makeAction({ id: 'save', label: 'Save' }),
      makeAction({ id: 'publish', label: 'Publish' }),
    ];
    render(<ActionsToolbar context="project" contextActions={actions} />);
    expect(screen.getByRole('button', { name: 'Save' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Publish' })).toBeTruthy();
  });

  it('calls action onClick when button clicked', () => {
    const onClick = vi.fn();
    render(<ActionsToolbar context="project" contextActions={[makeAction({ label: 'Export', onClick })]} />);
    fireEvent.click(screen.getByRole('button', { name: 'Export' }));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('renders disabled action', () => {
    render(<ActionsToolbar context="project" contextActions={[makeAction({ label: 'Save', disabled: true })]} />);
    const btn = screen.getByRole('button', { name: 'Save' });
    expect((btn as HTMLButtonElement).disabled).toBe(true);
  });

  it('renders empty toolbar when no actions', () => {
    render(<ActionsToolbar context="global" contextActions={[]} />);
    expect(screen.getByRole('toolbar')).toBeTruthy();
  });
});

// ─── UnifiedPhaseRail ────────────────────────────────────────────────────────

describe('UnifiedPhaseRail', () => {
  beforeEach(() => {
    vi.mocked(usePhaseContext).mockReturnValue(mockPhaseContext);
  });

  it('renders navigation with aria-label="Lifecycle phases"', () => {
    render(<UnifiedPhaseRail />);
    expect(screen.getByRole('navigation', { name: /lifecycle phases/i })).toBeTruthy();
  });

  it('marks current phase with aria-current=step', () => {
    render(<UnifiedPhaseRail />);
    const currentItem = document.querySelector('[aria-current="step"]');
    expect(currentItem).toBeTruthy();
  });

  it('calls onPhaseClick when phase clicked', () => {
    const onPhaseClick = vi.fn();
    render(<UnifiedPhaseRail onPhaseClick={onPhaseClick} />);
    // "Intent" is the INTENT phase label from our mock PHASE_LABELS
    const intentBtn = screen.getByRole('button', { name: /intent/i });
    fireEvent.click(intentBtn);
    expect(onPhaseClick).toHaveBeenCalled();
  });

  it('renders full variant without crashing', () => {
    render(<UnifiedPhaseRail variant="full" showDescriptions />);
    expect(screen.getByRole('navigation')).toBeTruthy();
  });

  it('renders horizontal orientation', () => {
    render(<UnifiedPhaseRail orientation="horizontal" />);
    expect(screen.getByRole('navigation')).toBeTruthy();
  });

  it('renders vertical orientation', () => {
    render(<UnifiedPhaseRail orientation="vertical" />);
    expect(screen.getByRole('navigation')).toBeTruthy();
  });
});

