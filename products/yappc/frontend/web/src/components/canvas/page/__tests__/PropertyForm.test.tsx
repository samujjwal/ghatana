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
    privacyLevel: 'SENSITIVE',
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
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();

    fireEvent.click(screen.getByLabelText('Privacy classification has been reviewed for this change'));
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    expect(onUpdate).toHaveBeenCalledWith({
      props: {
        ariaLabel: 'Submit order',
        variant: 'outline',
      },
      name: undefined,
    });
  });

  it('requires privacy classification acknowledgement for sensitive contracts', () => {
    const onUpdate = vi.fn();
    render(
      <PropertyForm
        contractName="Button"
        initialProps={{ ariaLabel: 'Submit order', variant: 'solid' }}
        onUpdate={onUpdate}
      />,
    );

    fireEvent.change(screen.getByLabelText('Node Name'), {
      target: { value: 'Primary Submit' },
    });
    fireEvent.click(screen.getByLabelText('Telemetry consent has been confirmed for this change'));

    expect(screen.getByText(/handles SENSITIVE data/i)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();

    fireEvent.click(screen.getByLabelText('Privacy classification has been reviewed for this change'));
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    expect(onUpdate).toHaveBeenCalledWith({
      props: {
        ariaLabel: 'Submit order',
        variant: 'solid',
      },
      name: 'Primary Submit',
    });
  });

  it('submits responsive variants, state variants, and bindings from the inspector', () => {
    const onUpdate = vi.fn();
    render(
      <PropertyForm
        contractName="Button"
        initialProps={{ ariaLabel: 'Submit order', variant: 'solid' }}
        onUpdate={onUpdate}
      />,
    );

    fireEvent.change(screen.getByLabelText('Responsive breakpoint'), {
      target: { value: 'md' },
    });
    fireEvent.change(screen.getByLabelText('Responsive props JSON'), {
      target: { value: '{"variant":"outline"}' },
    });
    fireEvent.change(screen.getByLabelText('State variant'), {
      target: { value: 'hover' },
    });
    fireEvent.change(screen.getByLabelText('State props JSON'), {
      target: { value: '{"variant":"ghost"}' },
    });
    fireEvent.change(screen.getByLabelText('Data source'), {
      target: { value: 'dataSource.orders' },
    });
    fireEvent.change(screen.getByLabelText('Data target prop'), {
      target: { value: 'children' },
    });
    fireEvent.change(screen.getByLabelText('Data transform'), {
      target: { value: 'latestOrder.label' },
    });
    fireEvent.click(screen.getByLabelText('Bidirectional data binding'));
    fireEvent.change(screen.getByLabelText('Action event'), {
      target: { value: 'onClick' },
    });
    fireEvent.change(screen.getByLabelText('Action target'), {
      target: { value: 'navigate:/orders' },
    });
    fireEvent.change(screen.getByLabelText('Action payload'), {
      target: { value: 'latestOrder.id' },
    });

    fireEvent.click(screen.getByLabelText('Telemetry consent has been confirmed for this change'));
    fireEvent.click(screen.getByLabelText('Privacy classification has been reviewed for this change'));
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    expect(onUpdate).toHaveBeenCalledWith({
      props: {
        ariaLabel: 'Submit order',
        variant: 'solid',
      },
      name: undefined,
      responsiveVariant: {
        breakpoint: 'md',
        props: {
          variant: 'outline',
        },
      },
      stateVariant: {
        state: 'hover',
        props: {
          variant: 'ghost',
        },
      },
      dataBinding: {
        id: 'inspector-data-datasource-orders-children',
        type: 'data',
        source: 'dataSource.orders',
        target: 'children',
        transform: 'latestOrder.label',
        bidirectional: true,
      },
      actionBinding: {
        id: 'inspector-event-onclick-navigate-orders',
        type: 'event',
        source: 'onClick',
        target: 'navigate:/orders',
        transform: 'latestOrder.id',
      },
    });
  });
});
