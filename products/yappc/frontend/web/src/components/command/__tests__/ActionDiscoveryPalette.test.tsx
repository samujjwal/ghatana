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
});