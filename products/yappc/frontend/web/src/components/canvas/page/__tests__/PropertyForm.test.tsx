import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { PropertyForm } from '../PropertyForm';

vi.mock('../registry', () => ({
  getConfiguratorGroups: () => [],
  getRegistryFields: () => [
    {
      name: 'ariaLabel',
      label: 'ariaLabel',
      control: 'text',
      type: 'text',
      required: false,
    },
    {
      name: 'variant',
      label: 'variant',
      control: 'text',
      type: 'text',
      required: false,
    },
  ],
  getContractGovernanceProfile: () => ({
    reviewRequiredProps: ['variant'],
    telemetryEventNames: ['button.clicked'],
    observabilityMarks: [],
    requiredA11yProps: ['ariaLabel'],
  }),
}));

describe('PropertyForm governance enforcement', () => {
  it('blocks apply when required accessibility props are missing', () => {
    const onUpdate = vi.fn();
    render(
      <PropertyForm
        contractName="Button"
        initialProps={{ variant: 'solid' }}
        onUpdate={onUpdate}
      />,
    );

    expect(screen.getByTestId('property-policy-a11y-blocker')).toHaveTextContent('ariaLabel');
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
  });

  it('requires review acknowledgement before applying governed prop changes', () => {
    const onUpdate = vi.fn();
    render(
      <PropertyForm
        contractName="Button"
        initialProps={{ ariaLabel: 'Submit order', variant: 'solid' }}
        onUpdate={onUpdate}
      />,
    );

    fireEvent.change(screen.getByLabelText('variant (review required)'), {
      target: { value: 'outline' },
    });

    expect(screen.getByText(/Review required before applying governed prop changes/)).toBeTruthy();
    const applyButton = screen.getByRole('button', { name: 'Apply' });
    expect(applyButton).toBeDisabled();

    fireEvent.click(screen.getByLabelText('I have reviewed the governed change'));
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();

    fireEvent.click(screen.getByLabelText('Telemetry consent has been confirmed for this change'));
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    expect(onUpdate).toHaveBeenCalledWith({
      props: {
        ariaLabel: 'Submit order',
        variant: 'outline',
      },
      name: undefined,
    });
  });
});
