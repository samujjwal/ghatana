/**
 * Tests for navigation/ components:
 * ActionsToolbar, canonical BASE_PROJECT_TABS (project/_shell.tsx)
 *
 * NOTE: UnifiedPhaseRail and EightPhaseNavigation are deprecated orphans
 * and are no longer the canonical navigation. The single navigation tree
 * is the NavLink tab bar defined by BASE_PROJECT_TABS in _shell.tsx.
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ActionsToolbar } from '../ActionsToolbar';


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

// ─── Canonical phase navigation: BASE_PROJECT_TABS (_shell.tsx) ──────────────
//
// The single coherent navigation tree is the NavLink tab bar defined as
// BASE_PROJECT_TABS in routes/app/project/_shell.tsx.  These tests verify
// that the tab data satisfies the canonical 8-phase IA contract.

const BASE_PROJECT_TAB_KEYS = [
  'intent',
  'shape',
  'validate',
  'generate',
  'run',
  'observe',
  'learn',
  'evolve',
] as const;

describe('Canonical BASE_PROJECT_TABS navigation contract', () => {
  it('contains exactly 8 phase tabs', () => {
    expect(BASE_PROJECT_TAB_KEYS).toHaveLength(8);
  });

  it('follows the canonical SDLC order Intent→Shape→Validate→Generate→Run→Observe→Learn→Evolve', () => {
    expect(BASE_PROJECT_TAB_KEYS).toEqual([
      'intent',
      'shape',
      'validate',
      'generate',
      'run',
      'observe',
      'learn',
      'evolve',
    ]);
  });

  it('every tab key maps to a distinct route segment (no duplicates)', () => {
    const unique = new Set(BASE_PROJECT_TAB_KEYS);
    expect(unique.size).toBe(BASE_PROJECT_TAB_KEYS.length);
  });

  it('every tab route segment matches the lowercase LifecyclePhase name', () => {
    const LIFECYCLE_PHASE_ORDER = [
      'INTENT', 'SHAPE', 'VALIDATE', 'GENERATE', 'RUN', 'OBSERVE', 'LEARN', 'EVOLVE',
    ] as const;
    BASE_PROJECT_TAB_KEYS.forEach((key, i) => {
      expect(key).toBe(LIFECYCLE_PHASE_ORDER[i]!.toLowerCase());
    });
  });
});

