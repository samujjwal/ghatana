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
    {
      name: 'options',
      label: 'options',
      control: 'json',
      type: 'text',
      valueType: 'array',
      required: false,
      description: 'Select options',
    },
    {
      name: 'analytics',
      label: 'analytics',
      control: 'json',
      type: 'text',
      valueType: 'object',
      required: false,
      description: 'Analytics payload',
    },
    {
      name: 'tokenRefs',
      label: 'tokenRefs',
      control: 'multiselect',
      type: 'text',
      valueType: 'array',
      required: false,
      tokenTypes: ['color'],
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

    expect(screen.getByText(/Review required before applying governed inspector changes/)).toBeTruthy();
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
      target: { value: 'ariaLabel' },
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
    fireEvent.change(screen.getByLabelText('Privacy classification'), {
      target: { value: 'REGULATED' },
    });

    fireEvent.click(screen.getByLabelText('Telemetry consent has been confirmed for this change'));
    fireEvent.click(screen.getByLabelText('Privacy classification has been reviewed for this change'));
    expect(screen.getByText(/responsive variant/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
    fireEvent.click(screen.getByLabelText('I have reviewed the governed change'));
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
        id: 'inspector-data-datasource-orders-arialabel',
        type: 'data',
        source: 'dataSource.orders',
        target: 'ariaLabel',
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
      privacyClassification: 'REGULATED',
    });
  });

  it('derives binding picker options from contract fields and existing action events', () => {
    render(
      <PropertyForm
        contractName="Button"
        initialProps={{ ariaLabel: 'Submit order', variant: 'solid' }}
        initialBindings={[
          {
            id: 'existing-action',
            type: 'event',
            source: 'onMouseEnter',
            target: 'track:hover',
          },
        ]}
        onUpdate={vi.fn()}
      />,
    );

    const dataTargetPicker = screen.getByLabelText('Data target prop');
    expect(dataTargetPicker).toHaveTextContent('ariaLabel');
    expect(dataTargetPicker).toHaveTextContent('variant');

    const actionEventPicker = screen.getByLabelText('Action event');
    expect(actionEventPicker).toHaveTextContent('onClick');
    expect(actionEventPicker).toHaveTextContent('onMouseEnter');
  });

  it('preserves complex object, array, and token collection props from rich editors', () => {
    const onUpdate = vi.fn();
    render(
      <PropertyForm
        contractName="Select"
        initialProps={{
          ariaLabel: 'Choose region',
          variant: 'solid',
          options: [{ label: 'North America', value: 'na' }],
          analytics: { eventName: 'region.changed' },
          tokenRefs: ['color.surface.default'],
        }}
        onUpdate={onUpdate}
      />,
    );

    fireEvent.change(screen.getByLabelText('options JSON'), {
      target: {
        value: JSON.stringify(
          [
            { label: 'North America', value: 'na' },
            { label: 'Europe', value: 'eu' },
          ],
          null,
          2,
        ),
      },
    });
    fireEvent.change(screen.getByLabelText('analytics JSON'), {
      target: { value: '{"eventName":"region.changed","sampleRate":0.5}' },
    });
    fireEvent.change(screen.getByLabelText(/tokenRefs/i), {
      target: { value: 'color.surface.default, color.text.strong' },
    });

    fireEvent.click(screen.getByLabelText('Telemetry consent has been confirmed for this change'));
    fireEvent.click(screen.getByLabelText('Privacy classification has been reviewed for this change'));
    const reviewAcknowledgement = screen.queryByLabelText('I have reviewed the governed change');
    if (reviewAcknowledgement) {
      fireEvent.click(reviewAcknowledgement);
    }
    expect(screen.getByRole('button', { name: 'Apply' })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    expect(onUpdate).toHaveBeenCalledWith({
      props: {
        ariaLabel: 'Choose region',
        variant: 'solid',
        options: [
          { label: 'North America', value: 'na' },
          { label: 'Europe', value: 'eu' },
        ],
        analytics: {
          eventName: 'region.changed',
          sampleRate: 0.5,
        },
        tokenRefs: ['color.surface.default', 'color.text.strong'],
      },
      name: undefined,
    });
  });

  it('blocks apply while complex field JSON is malformed or wrong-shaped', () => {
    const onUpdate = vi.fn();
    render(
      <PropertyForm
        contractName="Select"
        initialProps={{
          ariaLabel: 'Choose region',
          variant: 'solid',
          options: [],
          analytics: {},
          tokenRefs: [],
        }}
        onUpdate={onUpdate}
      />,
    );

    fireEvent.change(screen.getByLabelText('options JSON'), {
      target: { value: '{"label":"Not an array"}' },
    });

    expect(screen.getByTestId('property-complex-json-error-options')).toHaveTextContent(
      'options must be a JSON array.',
    );
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();

    fireEvent.change(screen.getByLabelText('options JSON'), {
      target: { value: '[{"label":"North America","value":"na"}]' },
    });
    fireEvent.change(screen.getByLabelText('analytics JSON'), {
      target: { value: '[{"not":"an object"}]' },
    });

    expect(screen.getByTestId('property-complex-json-error-analytics')).toHaveTextContent(
      'analytics must be a JSON object.',
    );
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
    expect(onUpdate).not.toHaveBeenCalled();
  });
});
