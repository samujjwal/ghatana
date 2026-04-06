import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ConflictResolver } from '../ConflictResolver';
import type { Conflict, ResolutionSuggestion } from '@yappc/collab/crdt';

function makeConflict(): Conflict {
  return {
    id: 'conflict-1',
    type: 'concurrent-update',
    targetId: 'requirement-42',
    severity: 'high',
    timestamp: 1_710_000_000_000,
    operationA: {
      id: 'op-a',
      replicaId: 'replica-a',
      type: 'update',
      targetId: 'requirement-42',
      vectorClock: {
        id: 'vc-a',
        values: new Map([
          ['replica-a', 3],
          ['replica-b', 1],
        ]),
        timestamp: 1_710_000_000_000,
      },
      data: {
        field: 'title',
        value: 'Keep the billing requirement explicit',
      },
      timestamp: 1_710_000_000_000,
      parents: ['base-op'],
    },
    operationB: {
      id: 'op-b',
      replicaId: 'replica-b',
      type: 'update',
      targetId: 'requirement-42',
      vectorClock: {
        id: 'vc-b',
        values: new Map([
          ['replica-a', 2],
          ['replica-b', 4],
        ]),
        timestamp: 1_710_000_001_000,
      },
      data: {
        field: 'title',
        value: 'Rename the billing requirement to invoicing story',
      },
      timestamp: 1_710_000_001_000,
      parents: ['base-op'],
    },
  };
}

function makeSuggestions(): ResolutionSuggestion[] {
  return [
    {
      id: 'suggestion-merge',
      strategy: 'merge',
      description: 'Merge the descriptive intent while preserving the stronger title.',
      confidence: 0.91,
      resultingValue: {
        field: 'title',
        value: 'Billing requirement: invoicing story',
      },
      pros: ['Keeps both edits'],
      cons: ['Needs review'],
    },
  ];
}

describe('ConflictResolver', () => {
  it('renders both conflicting versions and engine suggestions', () => {
    render(
      <ConflictResolver
        conflict={makeConflict()}
        suggestedResolutions={makeSuggestions()}
        onResolve={vi.fn()}
      />
    );

    expect(screen.getByTestId('conflict-resolver')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Resolve concurrent-update' })).toBeInTheDocument();
    expect(screen.getByLabelText('Version A')).toBeInTheDocument();
    expect(screen.getByLabelText('Version B')).toBeInTheDocument();
    expect(screen.getByText('Merge the descriptive intent while preserving the stronger title.')).toBeInTheDocument();
    expect(screen.getByText('Keep the billing requirement explicit')).toBeInTheDocument();
    expect(screen.getByText('Rename the billing requirement to invoicing story')).toBeInTheDocument();
  });

  it('submits the selected version to the resolution callback', () => {
    const onResolve = vi.fn();

    render(
      <ConflictResolver
        conflict={makeConflict()}
        suggestedResolutions={makeSuggestions()}
        onResolve={onResolve}
      />
    );

    fireEvent.click(screen.getAllByRole('button', { name: 'Keep this version' })[0]);

    expect(onResolve).toHaveBeenCalledWith({
      conflictId: 'conflict-1',
      strategy: 'user-guided',
      selectedSource: 'operationA',
      resolvedValue: {
        field: 'title',
        value: 'Keep the billing requirement explicit',
      },
      notes: undefined,
    });
  });

  it('submits a manual merged payload and notes', () => {
    const onResolve = vi.fn();

    render(
      <ConflictResolver
        conflict={makeConflict()}
        suggestedResolutions={makeSuggestions()}
        onResolve={onResolve}
      />
    );

    fireEvent.change(screen.getByLabelText('Merged payload'), {
      target: {
        value: JSON.stringify(
          {
            field: 'title',
            value: 'Billing requirement: invoicing story',
            approvedBy: 'architect-review',
          },
          null,
          2
        ),
      },
    });
    fireEvent.change(screen.getByLabelText('Resolution notes'), {
      target: { value: 'Merged the stronger title with the clearer business context.' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Apply merged resolution' }));

    expect(onResolve).toHaveBeenCalledWith({
      conflictId: 'conflict-1',
      strategy: 'merge',
      resolvedValue: {
        field: 'title',
        value: 'Billing requirement: invoicing story',
        approvedBy: 'architect-review',
      },
      notes: 'Merged the stronger title with the clearer business context.',
    });
  });

  it('blocks manual submission when the merge payload is invalid', () => {
    const onResolve = vi.fn();

    render(
      <ConflictResolver
        conflict={makeConflict()}
        suggestedResolutions={makeSuggestions()}
        onResolve={onResolve}
      />
    );

    fireEvent.change(screen.getByLabelText('Merged payload'), {
      target: { value: '{invalid json' },
    });

    expect(screen.getByRole('alert')).toHaveTextContent('Merge payload is invalid JSON');
    expect(screen.getByRole('button', { name: 'Apply merged resolution' })).toBeDisabled();
    expect(onResolve).not.toHaveBeenCalled();
  });
});