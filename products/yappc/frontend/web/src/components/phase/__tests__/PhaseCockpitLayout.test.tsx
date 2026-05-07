import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { PhaseCockpitLayout } from '../PhaseCockpitLayout';

describe('PhaseCockpitLayout', () => {
  it('renders phase-specific supporting workspace copy and reveals hidden content on demand', async () => {
    const user = userEvent.setup();

    render(
      <PhaseCockpitLayout
        phaseName="Validate"
        phaseDescription="Review the current packet and approve the next step."
        primaryAction={<div>Primary action</div>}
        supportingTitle="Validation summary"
        advancedTools={<div>Advanced content</div>}
        advancedToolsLabel="Open validation evidence workspace"
        advancedToolsDescription="Use this when approval gates need deeper artifact, risk, or evidence review."
      >
        <div>Native phase summary</div>
      </PhaseCockpitLayout>,
    );

    expect(screen.getByText('Validation summary')).toBeInTheDocument();
    expect(screen.getByText(/open the supporting workspace only when you need deeper context/i)).toBeInTheDocument();
    expect(screen.getByTestId('advanced-tools-description')).toHaveTextContent(
      'Use this when approval gates need deeper artifact, risk, or evidence review.',
    );
    expect(screen.getByText('Native phase summary')).toBeInTheDocument();
    expect(screen.queryByText('Advanced content')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Open validation evidence workspace' }));

    expect(screen.getByText('Advanced content')).toBeInTheDocument();
  });
});
