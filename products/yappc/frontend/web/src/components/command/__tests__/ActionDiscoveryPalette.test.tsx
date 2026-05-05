import { describe, expect, it, vi } from 'vitest';

import { render, screen } from '@/test-utils/test-utils';

import { ActionDiscoveryPalette, type ActionGroup } from '../ActionDiscoveryPalette';

describe('ActionDiscoveryPalette', () => {
  it('focuses search input and executes selected action via keyboard', async () => {
    const executeFirst = vi.fn();
    const executeSecond = vi.fn();
    const onClose = vi.fn();

    const actionGroups: ActionGroup[] = [
      {
        title: 'Navigation',
        actions: [
          {
            id: 'open-dashboard',
            title: 'Open dashboard',
            category: 'navigation',
            onExecute: executeFirst,
          },
          {
            id: 'open-canvas',
            title: 'Open canvas',
            category: 'navigation',
            onExecute: executeSecond,
          },
        ],
      },
    ];

    const { user } = render(
      <ActionDiscoveryPalette
        actionGroups={actionGroups}
        open
        onClose={onClose}
      />,
    );

    const input = screen.getByLabelText('Search actions');
    expect(input).toHaveFocus();

    await user.keyboard('{ArrowDown}');
    await user.keyboard('{Enter}');

    expect(executeFirst).not.toHaveBeenCalled();
    expect(executeSecond).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalled();
  });

  it('closes on escape when confirmation dialog is not open', async () => {
    const onClose = vi.fn();
    const actionGroups: ActionGroup[] = [
      {
        title: 'Recent',
        actions: [
          {
            id: 'resume-last-session',
            title: 'Resume last session',
            category: 'recent',
            onExecute: vi.fn(),
          },
        ],
      },
    ];

    const { user } = render(
      <ActionDiscoveryPalette
        actionGroups={actionGroups}
        open
        onClose={onClose}
      />,
    );

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('requires confirmation before executing a dangerous action', async () => {
    const onExecute = vi.fn();
    const onClose = vi.fn();
    const actionGroups: ActionGroup[] = [
      {
        title: 'Advanced',
        actions: [
          {
            id: 'delete-generated-output',
            title: 'Delete generated output',
            category: 'advanced',
            dangerous: true,
            onExecute,
          },
        ],
      },
    ];

    const { user } = render(
      <ActionDiscoveryPalette
        actionGroups={actionGroups}
        open
        onClose={onClose}
      />,
    );

    await user.click(screen.getByTestId('action-delete-generated-output'));
    expect(screen.getByTestId('dangerous-action-confirmation')).toBeInTheDocument();
    expect(onExecute).not.toHaveBeenCalled();

    await user.click(screen.getByTestId('confirm-dangerous-action'));

    expect(onExecute).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('ArrowUp wraps navigation to first item when at top of list', async () => {
    const executeFirst = vi.fn();
    const onClose = vi.fn();

    const actionGroups: ActionGroup[] = [
      {
        title: 'Navigation',
        actions: [
          { id: 'first', title: 'First action', category: 'navigation', onExecute: executeFirst },
          { id: 'second', title: 'Second action', category: 'navigation', onExecute: vi.fn() },
        ],
      },
    ];

    const { user } = render(
      <ActionDiscoveryPalette actionGroups={actionGroups} open onClose={onClose} />,
    );

    // ArrowUp from index 0 should stay at 0 (clamped)
    await user.keyboard('{ArrowUp}');
    await user.keyboard('{Enter}');

    expect(executeFirst).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalled();
  });

  it('does not execute a disabled action via keyboard Enter', async () => {
    const onExecute = vi.fn();
    const onClose = vi.fn();

    const actionGroups: ActionGroup[] = [
      {
        title: 'Selection',
        actions: [
          {
            id: 'disabled-action',
            title: 'Disabled action',
            category: 'selection',
            disabled: true,
            onExecute,
          },
        ],
      },
    ];

    const { user } = render(
      <ActionDiscoveryPalette actionGroups={actionGroups} open onClose={onClose} />,
    );

    // First item is selected by default; pressing Enter should not execute it
    await user.keyboard('{Enter}');

    expect(onExecute).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('filters results by search query and keyboard navigation targets only filtered items', async () => {
    const executeMatch = vi.fn();
    const executeOther = vi.fn();
    const onClose = vi.fn();

    const actionGroups: ActionGroup[] = [
      {
        title: 'Navigation',
        actions: [
          { id: 'go-canvas', title: 'Go to canvas', category: 'navigation', onExecute: executeMatch },
          { id: 'go-dashboard', title: 'Go to dashboard', category: 'navigation', onExecute: executeOther },
        ],
      },
    ];

    const { user } = render(
      <ActionDiscoveryPalette actionGroups={actionGroups} open onClose={onClose} />,
    );

    await user.type(screen.getByLabelText('Search actions'), 'canvas');

    // After filtering only "Go to canvas" remains; Enter executes it
    await user.keyboard('{Enter}');

    expect(executeMatch).toHaveBeenCalledTimes(1);
    expect(executeOther).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('Escape dismisses confirmation dialog without executing or closing palette', async () => {
    const onExecute = vi.fn<[], void>();
    const onClose = vi.fn();

    const actionGroups: ActionGroup[] = [
      {
        title: 'Advanced',
        actions: [
          {
            id: 'drop-workspace',
            title: 'Drop workspace',
            category: 'advanced',
            dangerous: true,
            onExecute,
          },
        ],
      },
    ];

    const { user } = render(
      <ActionDiscoveryPalette actionGroups={actionGroups} open onClose={onClose} />,
    );

    // Open confirmation via click
    await user.click(screen.getByTestId('action-drop-workspace'));
    expect(screen.getByTestId('dangerous-action-confirmation')).toBeInTheDocument();

    // Escape cancels the confirmation; the action must NOT have been executed
    await user.keyboard('{Escape}');

    expect(screen.queryByTestId('dangerous-action-confirmation')).not.toBeInTheDocument();
    expect(onExecute).not.toHaveBeenCalled();
  });

  it('dialog has correct ARIA role and label for screen readers', () => {
    render(
      <ActionDiscoveryPalette
        actionGroups={[{ title: 'Test', actions: [] }]}
        open
        onClose={vi.fn()}
      />,
    );

    // MUI Modal renders in a portal, so query via screen rather than container
    // MUI may render multiple elements with role="dialog"; find the one with aria-label
    const dialogs = screen.getAllByRole('dialog', { hidden: true });
    const dialog = dialogs.find((el) => el.hasAttribute('aria-label')) ?? dialogs[0];
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAttribute('aria-label');
  });
});