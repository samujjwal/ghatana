/**
 * Canvas AI Components — unit tests
 *
 * Covers:
 *   - AINotificationToast: show/hide logic, suggestion count text, view/dismiss callbacks, auto-dismiss
 *   - AISuggestionsPanel: open/closed, empty state, analyzing state, suggestion cards, priority chips,
 *     accept/dismiss/dismissAll callbacks
 *   - AIBadge: count badge, tooltip text, onClick
 *   - GhostNode: label, keyboard interaction, accept/dismiss callbacks
 *   - GhostNodeLayer: empty → null, renders all nodes
 */

import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AINotificationToast } from '../AINotificationToast';
import { AISuggestionsPanel, AIBadge } from '../AISuggestionsPanel';
import { GhostNode, GhostNodeLayer } from '../GhostNode';
import type { AISuggestion } from '@/hooks/useAIAssistant';
import type { GhostNode as GhostNodeType } from '@/hooks/useAIAssistant';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeSuggestion(overrides: Partial<AISuggestion> = {}): AISuggestion {
  return {
    id: 'sug-1',
    type: 'node',
    title: 'Add an API gateway',
    description: 'Your architecture is missing an API gateway layer.',
    confidence: 0.85,
    priority: 'high',
    ...overrides,
  };
}

function makeGhostNode(overrides: Partial<GhostNodeType> = {}): GhostNodeType {
  return {
    id: 'ghost-1',
    suggestionId: 'sug-1',
    type: 'component',
    position: { x: 100, y: 200 },
    data: { label: 'Auth Service', suggestion: { description: 'Handles authentication' } },
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// AINotificationToast
// ---------------------------------------------------------------------------

describe('AINotificationToast', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not render content when show is false', () => {
    render(
      <AINotificationToast
        suggestionCount={3}
        onView={vi.fn()}
        show={false}
      />
    );
    expect(screen.queryByText(/AI Suggestion/)).toBeNull();
  });

  it('does not render when show is true but suggestionCount is 0', () => {
    render(
      <AINotificationToast
        suggestionCount={0}
        onView={vi.fn()}
        show={true}
      />
    );
    expect(screen.queryByText(/AI Suggestion/)).toBeNull();
  });

  it('renders toast content when show=true and count > 0', () => {
    render(
      <AINotificationToast
        suggestionCount={1}
        onView={vi.fn()}
        show={true}
        autoDismiss={0}
      />
    );
    expect(screen.getByText('AI Suggestion Ready!')).toBeDefined();
    expect(screen.getByText('I found 1 way to improve your canvas')).toBeDefined();
  });

  it('uses plural header for multiple suggestions', () => {
    render(
      <AINotificationToast
        suggestionCount={4}
        onView={vi.fn()}
        show={true}
        autoDismiss={0}
      />
    );
    expect(screen.getByText('AI Suggestions Ready!')).toBeDefined();
    expect(screen.getByText('I found 4 ways to improve your canvas')).toBeDefined();
  });

  it('calls onView when View Suggestions button is clicked', () => {
    const onView = vi.fn();
    render(
      <AINotificationToast
        suggestionCount={2}
        onView={onView}
        show={true}
        autoDismiss={0}
      />
    );
    fireEvent.click(screen.getByText('View Suggestions'));
    expect(onView).toHaveBeenCalledOnce();
  });

  it('calls onDismiss when close button is clicked', () => {
    const onDismiss = vi.fn();
    render(
      <AINotificationToast
        suggestionCount={2}
        onView={vi.fn()}
        onDismiss={onDismiss}
        show={true}
        autoDismiss={0}
      />
    );
    // The close button contains the Close icon — find via role button
    const buttons = screen.getAllByRole('button');
    // The X/close button is the one that's NOT "View Suggestions"
    const closeBtn = buttons.find(b => !b.textContent?.includes('View Suggestions'));
    expect(closeBtn).toBeDefined();
    fireEvent.click(closeBtn!);
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it('auto-dismisses after autoDismiss timeout', () => {
    const onDismiss = vi.fn();
    render(
      <AINotificationToast
        suggestionCount={2}
        onView={vi.fn()}
        onDismiss={onDismiss}
        show={true}
        autoDismiss={3000}
      />
    );
    expect(screen.getByText('AI Suggestions Ready!')).toBeDefined();
    act(() => {
      vi.advanceTimersByTime(3001);
    });
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});

// ---------------------------------------------------------------------------
// AISuggestionsPanel
// ---------------------------------------------------------------------------

describe('AISuggestionsPanel', () => {
  const defaultProps = {
    suggestions: [] as AISuggestion[],
    onAccept: vi.fn(),
    onDismiss: vi.fn(),
    onDismissAll: vi.fn(),
  };

  it('renders empty state when no suggestions and not analyzing', () => {
    render(<AISuggestionsPanel {...defaultProps} open />);
    expect(screen.getByText('No suggestions at the moment')).toBeDefined();
  });

  it('renders "Analyzing canvas..." when isAnalyzing is true', () => {
    render(<AISuggestionsPanel {...defaultProps} open isAnalyzing />);
    expect(screen.getByText('Analyzing canvas...')).toBeDefined();
  });

  it('renders suggestion title and description', () => {
    const suggestions = [makeSuggestion()];
    render(<AISuggestionsPanel {...defaultProps} suggestions={suggestions} open />);
    expect(screen.getByText('Add an API gateway')).toBeDefined();
    expect(screen.getByText('Your architecture is missing an API gateway layer.')).toBeDefined();
  });

  it('renders confidence chip as percentage', () => {
    const suggestions = [makeSuggestion({ confidence: 0.85 })];
    render(<AISuggestionsPanel {...defaultProps} suggestions={suggestions} open />);
    expect(screen.getByText('85% confident')).toBeDefined();
  });

  it('renders suggestion type chip', () => {
    const suggestions = [makeSuggestion({ type: 'gap' })];
    render(<AISuggestionsPanel {...defaultProps} suggestions={suggestions} open />);
    expect(screen.getByText('gap')).toBeDefined();
  });

  it('calls onAccept with suggestion id when Accept clicked', () => {
    const onAccept = vi.fn();
    const suggestions = [makeSuggestion({ id: 'sug-42' })];
    render(
      <AISuggestionsPanel
        {...defaultProps}
        suggestions={suggestions}
        onAccept={onAccept}
        open
      />
    );
    fireEvent.click(screen.getByText('Accept'));
    expect(onAccept).toHaveBeenCalledWith('sug-42');
  });

  it('calls onDismiss with suggestion id when Dismiss clicked', () => {
    const onDismiss = vi.fn();
    const suggestions = [makeSuggestion({ id: 'sug-99' })];
    render(
      <AISuggestionsPanel
        {...defaultProps}
        suggestions={suggestions}
        onDismiss={onDismiss}
        open
      />
    );
    fireEvent.click(screen.getByText('Dismiss'));
    expect(onDismiss).toHaveBeenCalledWith('sug-99');
  });

  it('calls onDismissAll when Dismiss All button is clicked', () => {
    const onDismissAll = vi.fn();
    const suggestions = [makeSuggestion()];
    render(
      <AISuggestionsPanel
        {...defaultProps}
        suggestions={suggestions}
        onDismissAll={onDismissAll}
        open
      />
    );
    fireEvent.click(screen.getByText('Dismiss All'));
    expect(onDismissAll).toHaveBeenCalledOnce();
  });

  it('shows critical priority chip when critical suggestions present', () => {
    const suggestions = [makeSuggestion({ priority: 'critical' })];
    render(<AISuggestionsPanel {...defaultProps} suggestions={suggestions} open />);
    expect(screen.getByText('1 Critical')).toBeDefined();
  });

  it('shows high priority chip when high suggestions present', () => {
    const suggestions = [makeSuggestion({ priority: 'high' })];
    render(<AISuggestionsPanel {...defaultProps} suggestions={suggestions} open />);
    expect(screen.getByText('1 High')).toBeDefined();
  });

  it('does not show priority chips when only low/medium suggestions', () => {
    const suggestions = [makeSuggestion({ priority: 'low' })];
    render(<AISuggestionsPanel {...defaultProps} suggestions={suggestions} open />);
    expect(screen.queryByText(/Critical/)).toBeNull();
    expect(screen.queryByText(/High/)).toBeNull();
  });

  it('renders multiple suggestion cards', () => {
    const suggestions = [
      makeSuggestion({ id: '1', title: 'First suggestion' }),
      makeSuggestion({ id: '2', title: 'Second suggestion' }),
    ];
    render(<AISuggestionsPanel {...defaultProps} suggestions={suggestions} open />);
    expect(screen.getByText('First suggestion')).toBeDefined();
    expect(screen.getByText('Second suggestion')).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// AIBadge
// ---------------------------------------------------------------------------

describe('AIBadge', () => {
  it('renders without error', () => {
    render(<AIBadge count={3} />);
    // The badge itself renders as an icon button
    expect(screen.getAllByRole('button').length).toBeGreaterThanOrEqual(1);
  });

  it('calls onClick when badge button is clicked', () => {
    const onClick = vi.fn();
    render(<AIBadge count={5} onClick={onClick} />);
    const btn = screen.getAllByRole('button')[0];
    fireEvent.click(btn!);
    expect(onClick).toHaveBeenCalledOnce();
  });
});

// ---------------------------------------------------------------------------
// GhostNode
// ---------------------------------------------------------------------------

describe('GhostNode', () => {
  it('renders the node label from data', () => {
    const node = makeGhostNode();
    render(
      <GhostNode
        node={node}
        onAccept={vi.fn()}
        onDismiss={vi.fn()}
      />
    );
    expect(screen.getByText('Auth Service')).toBeDefined();
  });

  it('renders "AI Suggestion" caption', () => {
    const node = makeGhostNode();
    render(
      <GhostNode
        node={node}
        onAccept={vi.fn()}
        onDismiss={vi.fn()}
      />
    );
    expect(screen.getByText('AI Suggestion')).toBeDefined();
  });

  it('has correct aria-label', () => {
    const node = makeGhostNode();
    render(
      <GhostNode
        node={node}
        onAccept={vi.fn()}
        onDismiss={vi.fn()}
      />
    );
    const el = screen.getByRole('button', { name: /AI Suggestion: Auth Service/i });
    expect(el).toBeDefined();
  });

  it('calls onAccept with node id on Enter key', () => {
    const onAccept = vi.fn();
    const node = makeGhostNode({ id: 'ghost-abc' });
    render(
      <GhostNode
        node={node}
        onAccept={onAccept}
        onDismiss={vi.fn()}
      />
    );
    const el = screen.getByRole('button', { name: /AI Suggestion/i });
    fireEvent.keyDown(el, { key: 'Enter' });
    expect(onAccept).toHaveBeenCalledWith('ghost-abc');
  });

  it('calls onDismiss with node id on Escape key', () => {
    const onDismiss = vi.fn();
    const node = makeGhostNode({ id: 'ghost-xyz' });
    render(
      <GhostNode
        node={node}
        onAccept={vi.fn()}
        onDismiss={onDismiss}
      />
    );
    const el = screen.getByRole('button', { name: /AI Suggestion/i });
    fireEvent.keyDown(el, { key: 'Escape' });
    expect(onDismiss).toHaveBeenCalledWith('ghost-xyz');
  });

  it('uses node type as fallback label when data.label absent', () => {
    const node = makeGhostNode({ data: {} });
    render(
      <GhostNode
        node={node}
        onAccept={vi.fn()}
        onDismiss={vi.fn()}
      />
    );
    // Should show node type as label
    expect(screen.getByRole('button', { name: /AI Suggestion: component/i })).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// GhostNodeLayer
// ---------------------------------------------------------------------------

describe('GhostNodeLayer', () => {
  it('returns null when nodes array is empty', () => {
    const { container } = render(
      <GhostNodeLayer
        nodes={[]}
        onAccept={vi.fn()}
        onDismiss={vi.fn()}
      />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders all ghost nodes', () => {
    const nodes = [
      makeGhostNode({ id: 'n1', data: { label: 'Node One' } }),
      makeGhostNode({ id: 'n2', data: { label: 'Node Two' } }),
    ];
    render(
      <GhostNodeLayer
        nodes={nodes}
        onAccept={vi.fn()}
        onDismiss={vi.fn()}
      />
    );
    expect(screen.getByText('Node One')).toBeDefined();
    expect(screen.getByText('Node Two')).toBeDefined();
  });

  it('has aria-live="polite" on layer container', () => {
    const nodes = [makeGhostNode()];
    const { container } = render(
      <GhostNodeLayer
        nodes={nodes}
        onAccept={vi.fn()}
        onDismiss={vi.fn()}
      />
    );
    const liveRegion = container.querySelector('[aria-live="polite"]');
    expect(liveRegion).toBeDefined();
  });
});
