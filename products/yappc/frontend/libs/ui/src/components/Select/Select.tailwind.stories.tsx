/**
 * Select Component Stories (Tailwind CSS + Base UI)
 */

import { useState } from 'react';

import { Select, SelectOption, SelectGroup } from './Select.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Forms/Select (Tailwind)',
  component: Select,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'A dropdown select component built with Base UI Select primitives and styled with Tailwind CSS. Supports keyboard navigation, grouping, and full accessibility.',
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
      description: 'Color scheme for focus states',
    },
    disabled: {
      control: 'boolean',
      description: 'Whether the select is disabled',
    },
    required: {
      control: 'boolean',
      description: 'Whether the select is required',
    },
  },
} satisfies Meta<typeof Select>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default select with basic options
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = useState<string>('');

    return (
      <Select
        label="Country"
        placeholder="Select a country"
        value={value}
        onChange={(newValue) => setValue(newValue || '')}
      >
        <SelectOption value="us">United States</SelectOption>
        <SelectOption value="ca">Canada</SelectOption>
        <SelectOption value="mx">Mexico</SelectOption>
        <SelectOption value="uk">United Kingdom</SelectOption>
        <SelectOption value="de">Germany</SelectOption>
        <SelectOption value="fr">France</SelectOption>
        <SelectOption value="jp">Japan</SelectOption>
        <SelectOption value="au">Australia</SelectOption>
      </Select>
    );
  },
};

/**
 * All size variants
 */
export const Sizes: Story = {
  render: () => {
    const [valueSm, setValueSm] = useState<string>('');
    const [valueMd, setValueMd] = useState<string>('');
    const [valueLg, setValueLg] = useState<string>('');

    return (
      <div className="space-y-4">
        <Select
          label="Small"
          size="sm"
          placeholder="Select option"
          value={valueSm}
          onChange={(v) => setValueSm(v || '')}
        >
          <SelectOption value="1">Option 1</SelectOption>
          <SelectOption value="2">Option 2</SelectOption>
          <SelectOption value="3">Option 3</SelectOption>
        </Select>

        <Select
          label="Medium (default)"
          size="md"
          placeholder="Select option"
          value={valueMd}
          onChange={(v) => setValueMd(v || '')}
        >
          <SelectOption value="1">Option 1</SelectOption>
          <SelectOption value="2">Option 2</SelectOption>
          <SelectOption value="3">Option 3</SelectOption>
        </Select>

        <Select
          label="Large"
          size="lg"
          placeholder="Select option"
          value={valueLg}
          onChange={(v) => setValueLg(v || '')}
        >
          <SelectOption value="1">Option 1</SelectOption>
          <SelectOption value="2">Option 2</SelectOption>
          <SelectOption value="3">Option 3</SelectOption>
        </Select>
      </div>
    );
  },
};

/**
 * All color schemes
 */
export const ColorSchemes: Story = {
  render: () => {
    const [value, setValue] = useState<string>('');

    return (
      <div className="space-y-4">
        <Select
          label="Primary"
          colorScheme="primary"
          placeholder="Select option"
          value={value}
          onChange={(v) => setValue(v || '')}
        >
          <SelectOption value="1">Option 1</SelectOption>
          <SelectOption value="2">Option 2</SelectOption>
        </Select>

        <Select
          label="Secondary"
          colorScheme="secondary"
          placeholder="Select option"
          value={value}
          onChange={(v) => setValue(v || '')}
        >
          <SelectOption value="1">Option 1</SelectOption>
          <SelectOption value="2">Option 2</SelectOption>
        </Select>

        <Select
          label="Success"
          colorScheme="success"
          placeholder="Select option"
          value={value}
          onChange={(v) => setValue(v || '')}
        >
          <SelectOption value="1">Option 1</SelectOption>
          <SelectOption value="2">Option 2</SelectOption>
        </Select>

        <Select
          label="Error"
          colorScheme="error"
          placeholder="Select option"
          value={value}
          onChange={(v) => setValue(v || '')}
        >
          <SelectOption value="1">Option 1</SelectOption>
          <SelectOption value="2">Option 2</SelectOption>
        </Select>

        <Select
          label="Warning"
          colorScheme="warning"
          placeholder="Select option"
          value={value}
          onChange={(v) => setValue(v || '')}
        >
          <SelectOption value="1">Option 1</SelectOption>
          <SelectOption value="2">Option 2</SelectOption>
        </Select>

        <Select
          label="Grey"
          colorScheme="grey"
          placeholder="Select option"
          value={value}
          onChange={(v) => setValue(v || '')}
        >
          <SelectOption value="1">Option 1</SelectOption>
          <SelectOption value="2">Option 2</SelectOption>
        </Select>
      </div>
    );
  },
};

/**
 * Select with grouped options
 */
export const WithGroups: Story = {
  render: () => {
    const [value, setValue] = useState<string>('');

    return (
      <Select
        label="Choose a framework"
        placeholder="Select framework"
        value={value}
        onChange={(v) => setValue(v || '')}
      >
        <SelectGroup label="Frontend">
          <SelectOption value="react">React</SelectOption>
          <SelectOption value="vue">Vue</SelectOption>
          <SelectOption value="angular">Angular</SelectOption>
          <SelectOption value="svelte">Svelte</SelectOption>
        </SelectGroup>

        <SelectGroup label="Backend">
          <SelectOption value="express">Express</SelectOption>
          <SelectOption value="fastify">Fastify</SelectOption>
          <SelectOption value="nestjs">NestJS</SelectOption>
          <SelectOption value="hapi">Hapi</SelectOption>
        </SelectGroup>

        <SelectGroup label="Full Stack">
          <SelectOption value="nextjs">Next.js</SelectOption>
          <SelectOption value="nuxt">Nuxt</SelectOption>
          <SelectOption value="remix">Remix</SelectOption>
        </SelectGroup>
      </Select>
    );
  },
};

/**
 * Select with helper text
 */
export const WithHelperText: Story = {
  render: () => {
    const [value, setValue] = useState<string>('');

    return (
      <Select
        label="Timezone"
        placeholder="Select timezone"
        value={value}
        onChange={(v) => setValue(v || '')}
        helperText="Choose your local timezone for accurate event times"
      >
        <SelectOption value="utc">UTC (Coordinated Universal Time)</SelectOption>
        <SelectOption value="est">EST (Eastern Standard Time)</SelectOption>
        <SelectOption value="cst">CST (Central Standard Time)</SelectOption>
        <SelectOption value="mst">MST (Mountain Standard Time)</SelectOption>
        <SelectOption value="pst">PST (Pacific Standard Time)</SelectOption>
      </Select>
    );
  },
};

/**
 * Select with error state
 */
export const ErrorState: Story = {
  render: () => {
    const [value, setValue] = useState<string>('');

    return (
      <Select
        label="Payment method"
        placeholder="Select payment method"
        value={value}
        onChange={(v) => setValue(v || '')}
        error="Please select a payment method to continue"
        required
      >
        <SelectOption value="card">Credit Card</SelectOption>
        <SelectOption value="paypal">PayPal</SelectOption>
        <SelectOption value="bank">Bank Transfer</SelectOption>
      </Select>
    );
  },
};

/**
 * Required select field
 */
export const Required: Story = {
  render: () => {
    const [value, setValue] = useState<string>('');

    return (
      <Select
        label="Department"
        placeholder="Select department"
        value={value}
        onChange={(v) => setValue(v || '')}
        required
        helperText="This field is required"
      >
        <SelectOption value="eng">Engineering</SelectOption>
        <SelectOption value="design">Design</SelectOption>
        <SelectOption value="product">Product</SelectOption>
        <SelectOption value="sales">Sales</SelectOption>
        <SelectOption value="marketing">Marketing</SelectOption>
      </Select>
    );
  },
};

/**
 * Disabled select
 */
export const Disabled: Story = {
  render: () => (
    <Select
      label="Subscription plan"
      placeholder="Select plan"
      disabled
      defaultValue="pro"
    >
      <SelectOption value="free">Free</SelectOption>
      <SelectOption value="pro">Pro</SelectOption>
      <SelectOption value="enterprise">Enterprise</SelectOption>
    </Select>
  ),
};

/**
 * Select with disabled options
 */
export const DisabledOptions: Story = {
  render: () => {
    const [value, setValue] = useState<string>('');

    return (
      <Select
        label="Subscription tier"
        placeholder="Select tier"
        value={value}
        onChange={(v) => setValue(v || '')}
        helperText="Some options are currently unavailable"
      >
        <SelectOption value="free">Free (Available)</SelectOption>
        <SelectOption value="starter">Starter (Available)</SelectOption>
        <SelectOption value="pro" disabled>
          Pro (Coming Soon)
        </SelectOption>
        <SelectOption value="enterprise" disabled>
          Enterprise (Contact Sales)
        </SelectOption>
      </Select>
    );
  },
};

/**
 * Form example with multiple selects
 */
export const FormExample: Story = {
  render: () => {
    const [country, setCountry] = useState<string>('');
    const [state, setState] = useState<string>('');
    const [city, setCity] = useState<string>('');

    return (
      <div className="mx-auto max-w-md space-y-4 rounded-lg border border-grey-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-semibold text-grey-900">Location Information</h3>

        <Select
          label="Country"
          placeholder="Select country"
          value={country}
          onChange={(v) => setCountry(v || '')}
          required
        >
          <SelectOption value="us">United States</SelectOption>
          <SelectOption value="ca">Canada</SelectOption>
          <SelectOption value="uk">United Kingdom</SelectOption>
          <SelectOption value="au">Australia</SelectOption>
        </Select>

        <Select
          label="State/Province"
          placeholder="Select state"
          value={state}
          onChange={(v) => setState(v || '')}
          disabled={!country}
          helperText={!country ? 'Select a country first' : undefined}
        >
          <SelectOption value="ca">California</SelectOption>
          <SelectOption value="ny">New York</SelectOption>
          <SelectOption value="tx">Texas</SelectOption>
          <SelectOption value="fl">Florida</SelectOption>
        </Select>

        <Select
          label="City"
          placeholder="Select city"
          value={city}
          onChange={(v) => setCity(v || '')}
          disabled={!state}
          helperText={!state ? 'Select a state first' : undefined}
        >
          <SelectOption value="la">Los Angeles</SelectOption>
          <SelectOption value="sf">San Francisco</SelectOption>
          <SelectOption value="sd">San Diego</SelectOption>
          <SelectOption value="sj">San Jose</SelectOption>
        </Select>

        <div className="mt-6 flex justify-end">
          <button
            type="button"
            className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 disabled:opacity-50"
            disabled={!country || !state || !city}
          >
            Continue
          </button>
        </div>
      </div>
    );
  },
};

/**
 * Accessibility demonstration
 */
export const Accessibility: Story = {
  render: () => {
    const [value, setValue] = useState<string>('');

    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-grey-200 bg-grey-50 p-4">
          <h3 className="mb-2 font-semibold text-grey-900">Keyboard Navigation:</h3>
          <ul className="space-y-1 text-sm text-grey-700">
            <li>• Tab: Focus select trigger</li>
            <li>• Space/Enter: Open dropdown</li>
            <li>• Arrow Up/Down: Navigate options</li>
            <li>• Enter: Select highlighted option</li>
            <li>• Escape: Close dropdown</li>
            <li>• Type to search options</li>
          </ul>
        </div>

        <Select
          label="Preferred language"
          placeholder="Select language"
          value={value}
          onChange={(v) => setValue(v || '')}
          helperText="Use keyboard to navigate"
        >
          <SelectOption value="en">English</SelectOption>
          <SelectOption value="es">Spanish</SelectOption>
          <SelectOption value="fr">French</SelectOption>
          <SelectOption value="de">German</SelectOption>
          <SelectOption value="it">Italian</SelectOption>
          <SelectOption value="pt">Portuguese</SelectOption>
          <SelectOption value="ja">Japanese</SelectOption>
          <SelectOption value="zh">Chinese</SelectOption>
        </Select>
      </div>
    );
  },
};

/**
 * Interactive playground
 */
export const Playground: Story = {
  args: {
    label: 'Select an option',
    placeholder: 'Choose...',
    size: 'md',
    colorScheme: 'primary',
    disabled: false,
    required: false,
    helperText: 'This is helper text',
  },
  render: (args) => {
    const [value, setValue] = useState<string>('');

    return (
      <Select
        {...args}
        value={value}
        onChange={(v) => setValue(v || '')}
      >
        <SelectOption value="1">Option 1</SelectOption>
        <SelectOption value="2">Option 2</SelectOption>
        <SelectOption value="3">Option 3</SelectOption>
        <SelectOption value="4">Option 4</SelectOption>
        <SelectOption value="5">Option 5</SelectOption>
      </Select>
    );
  },
};
