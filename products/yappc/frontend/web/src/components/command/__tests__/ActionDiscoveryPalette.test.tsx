import { describe, expect, it, vi } from 'vitest';

import { render, screen } from '@/test-utils/test-utils';

import { ActionDiscoveryPalette, type ActionGroup } from '../ActionDiscoveryPalette';

describe('ActionDiscoveryPalette', () => {
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