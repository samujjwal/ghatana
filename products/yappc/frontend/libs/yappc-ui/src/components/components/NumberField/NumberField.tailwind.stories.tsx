/**
 * NumberField Component Stories (Tailwind CSS + Base UI)
 */

import { useState } from 'react';

import { NumberField } from './NumberField.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Forms/NumberField (Tailwind)',
  component: NumberField,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'A number input component built with Base UI NumberField primitives and styled with Tailwind CSS. Supports increment/decrement steppers, min/max validation, and keyboard controls.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
      description: 'Size variant',
    },
    colorScheme: {
      control: 'select',
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'grey'],
      description: 'Color scheme',
    },
    disabled: {
      control: 'boolean',
      description: 'Whether the field is disabled',
    },
    required: {
      control: 'boolean',
      description: 'Whether the field is required',
    },
    showSteppers: {
      control: 'boolean',
      description: 'Show increment/decrement buttons',
    },
  },
} satisfies Meta<typeof NumberField>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default number field
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = useState<number>(0);

    return (
      <NumberField
        label="Quantity"
        value={value}
        onChange={setValue}
        min={0}
        max={100}
      />
    );
  },
};

/**
 * All size variants
 */
export const Sizes: Story = {
  render: () => {
    const [value, setValue] = useState<number>(5);

    return (
      <div className="space-y-4">
        <NumberField
          label="Small"
          size="sm"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
        />

        <NumberField
          label="Medium (default)"
          size="md"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
        />

        <NumberField
          label="Large"
          size="lg"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
        />
      </div>
    );
  },
};

/**
 * Color scheme variants
 */
export const ColorSchemes: Story = {
  render: () => {
    const [value, setValue] = useState<number>(5);

    return (
      <div className="space-y-4">
        <NumberField
          label="Primary"
          colorScheme="primary"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
        />

        <NumberField
          label="Secondary"
          colorScheme="secondary"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
        />

        <NumberField
          label="Success"
          colorScheme="success"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
        />

        <NumberField
          label="Error"
          colorScheme="error"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
        />

        <NumberField
          label="Warning"
          colorScheme="warning"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
        />

        <NumberField
          label="Grey"
          colorScheme="grey"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
        />
      </div>
    );
  },
};

/**
 * With stepper buttons
 */
export const WithSteppers: Story = {
  render: () => {
    const [value, setValue] = useState<number>(1);

    return (
      <NumberField
        label="Items"
        value={value}
        onChange={setValue}
        min={1}
        max={99}
        step={1}
        showSteppers
        helperText="Use buttons or type a number"
      />
    );
  },
};

/**
 * Without stepper buttons
 */
export const WithoutSteppers: Story = {
  render: () => {
    const [value, setValue] = useState<number>(42);

    return (
      <NumberField
        label="Age"
        value={value}
        onChange={setValue}
        min={0}
        max={120}
        showSteppers={false}
        helperText="Clean input without steppers"
      />
    );
  },
};

/**
 * Min/Max validation
 */
export const MinMaxValidation: Story = {
  render: () => {
    const [value, setValue] = useState<number>(50);
    const min = 10;
    const max = 100;

    return (
      <NumberField
        label="Percentage"
        value={value}
        onChange={setValue}
        min={min}
        max={max}
        step={5}
        helperText={`Must be between ${min} and ${max}`}
      />
    );
  },
};

/**
 * With step increments
 */
export const StepIncrements: Story = {
  render: () => {
    const [value, setValue] = useState<number>(0);

    return (
      <div className="space-y-4">
        <NumberField
          label="Step by 1"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
          step={1}
        />

        <NumberField
          label="Step by 5"
          value={value}
          onChange={setValue}
          min={0}
          max={100}
          step={5}
        />

        <NumberField
          label="Step by 0.1 (decimals)"
          value={value}
          onChange={setValue}
          min={0}
          max={1}
          step={0.1}
        />
      </div>
    );
  },
};

/**
 * With helper text
 */
export const WithHelperText: Story = {
  render: () => {
    const [value, setValue] = useState<number>(1);

    return (
      <NumberField
        label="Ticket Quantity"
        value={value}
        onChange={setValue}
        min={1}
        max={10}
        helperText="Maximum 10 tickets per order"
      />
    );
  },
};

/**
 * Error state
 */
export const ErrorState: Story = {
  render: () => {
    const [value, setValue] = useState<number>(0);

    return (
      <NumberField
        label="Required Field"
        value={value}
        onChange={setValue}
        min={1}
        max={100}
        error="Value must be at least 1"
        required
      />
    );
  },
};

/**
 * Required field
 */
export const Required: Story = {
  render: () => {
    const [value, setValue] = useState<number>(0);

    return (
      <NumberField
        label="Guests"
        value={value}
        onChange={setValue}
        min={1}
        max={20}
        required
        helperText="This field is required"
      />
    );
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => (
    <NumberField
      label="Disabled field"
      defaultValue={5}
      min={0}
      max={10}
      disabled
      helperText="This field is disabled"
    />
  ),
};

/**
 * Read-only state
 */
export const ReadOnly: Story = {
  render: () => (
    <NumberField
      label="Total Items"
      defaultValue={42}
      readOnly
      helperText="Read-only value"
    />
  ),
};

/**
 * Form example with validation
 */
export const FormExample: Story = {
  render: () => {
    const [adults, setAdults] = useState<number>(2);
    const [children, setChildren] = useState<number>(0);
    const [rooms, setRooms] = useState<number>(1);
    const [error, setError] = useState('');

    const totalGuests = adults + children;

    const handleSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      if (adults < 1) {
        setError('At least 1 adult required');
        return;
      }
      if (totalGuests > rooms * 4) {
        setError('Too many guests for selected rooms');
        return;
      }
      setError('');
      alert(`Booking: ${adults} adults, ${children} children, ${rooms} room(s)`);
    };

    return (
      <form
        onSubmit={handleSubmit}
        className="mx-auto max-w-md space-y-4 rounded-lg border border-grey-200 bg-white p-6 shadow-sm"
      >
        <h3 className="text-lg font-semibold text-grey-900">Hotel Booking</h3>

        <NumberField
          label="Adults"
          value={adults}
          onChange={(val) => {
            setAdults(val);
            setError('');
          }}
          min={1}
          max={10}
          required
        />

        <NumberField
          label="Children"
          value={children}
          onChange={(val) => {
            setChildren(val);
            setError('');
          }}
          min={0}
          max={10}
          helperText="Under 18 years"
        />

        <NumberField
          label="Rooms"
          value={rooms}
          onChange={(val) => {
            setRooms(val);
            setError('');
          }}
          min={1}
          max={5}
          required
        />

        {error && (
          <div className="rounded-md bg-error-50 p-3 text-sm text-error-700">{error}</div>
        )}

        <div className="rounded-md bg-grey-50 p-3">
          <p className="text-sm text-grey-700">
            Total guests: <span className="font-semibold">{totalGuests}</span>
          </p>
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <button
            type="button"
            onClick={() => {
              setAdults(2);
              setChildren(0);
              setRooms(1);
              setError('');
            }}
            className="rounded-md border border-grey-300 px-4 py-2 text-sm font-medium text-grey-700 hover:bg-grey-50"
          >
            Reset
          </button>
          <button
            type="submit"
            className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
          >
            Book Now
          </button>
        </div>
      </form>
    );
  },
};

/**
 * Accessibility demonstration
 */
export const Accessibility: Story = {
  render: () => {
    const [value, setValue] = useState<number>(5);

    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-grey-200 bg-grey-50 p-4">
          <h3 className="mb-2 font-semibold text-grey-900">Accessibility Features:</h3>
          <ul className="space-y-1 text-sm text-grey-700">
            <li>• Keyboard navigation (Up/Down arrows to increment/decrement)</li>
            <li>• Proper label association</li>
            <li>• ARIA attributes for screen readers</li>
            <li>• Min/max boundaries announced</li>
            <li>• Error messages linked to input</li>
            <li>• Stepper buttons with ARIA labels</li>
          </ul>
        </div>

        <NumberField
          label="Accessible number field"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
          helperText="Use arrow keys to adjust value"
        />
      </div>
    );
  },
};

/**
 * Interactive playground
 */
export const Playground: Story = {
  args: {
    label: 'Playground NumberField',
    helperText: 'Enter a number',
    min: 0,
    max: 100,
    step: 1,
    size: 'md',
    colorScheme: 'primary',
    showSteppers: true,
    disabled: false,
    required: false,
  },
  render: (args) => {
    const [value, setValue] = useState<number>(50);

    return <NumberField {...args} value={value} onChange={setValue} />;
  },
};
