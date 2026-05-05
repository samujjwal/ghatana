import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { PhaseCockpitLayout } from '../PhaseCockpitLayout';

describe('PhaseCockpitLayout', () => {
  it('renders the configured native details title and reveals advanced details on demand', async () => {
    const user = userEvent.setup();

    render(
      <PhaseCockpitLayout
        phaseName="Validate"
        phaseDescription="Review the current packet and approve the next step."
        primaryAction={<div>Primary action</div>}
        supportingTitle="Validation summary"
        advancedTools={<div>Advanced content</div>}
      >
        <div>Native phase summary</div>
      </PhaseCockpitLayout>,
    );

    expect(screen.getByText('Validation summary')).toBeInTheDocument();
    expect(screen.getByText('Native phase summary')).toBeInTheDocument();
    expect(screen.queryByText('Advanced content')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Advanced details' }));

    expect(screen.getByText('Advanced content')).toBeInTheDocument();
  });
});