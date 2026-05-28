import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SavedQueries } from '../../components/sql/SavedQueries';

describe('SavedQueries', () => {
  it('exposes row favorite and actions controls with explicit accessible labels', async () => {
    render(
      <SavedQueries
        onSelect={vi.fn()}
        currentSql="SELECT * FROM events"
      />,
    );

    expect(
      await screen.findByRole('button', { name: /Toggle favorite for Revenue by Product/i }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', { name: /Query actions for Active Users Last 7 Days/i }),
    ).toBeInTheDocument();
  });

  it('supports keyboard activation of row action menu and duplicate flow', async () => {
    const user = userEvent.setup();

    render(
      <SavedQueries
        onSelect={vi.fn()}
        currentSql="SELECT * FROM events"
      />,
    );

    const actionButton = await screen.findByRole('button', {
      name: /Query actions for Active Users Last 7 Days/i,
    });

    actionButton.focus();
    await user.keyboard('{Enter}');

    const duplicateButton = await screen.findByRole('button', { name: 'Duplicate' });
    await user.click(duplicateButton);

    expect(await screen.findByText('Active Users Last 7 Days (Copy)')).toBeInTheDocument();
  });
});
