/**
 * Storybook stories for Radio component
 */
import { useState } from 'react';

import { Radio, RadioGroup } from './Radio.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Radio (Tailwind)',
  component: Radio,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    colorScheme: {
      control: 'select',
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'grey'],
      description: 'Color scheme',
    },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
      description: 'Size of radio button',
    },
    label: {
      control: 'text',
      description: 'Label text',
    },
    disabled: {
      control: 'boolean',
      description: 'Disabled state',
    },
  },
} satisfies Meta<typeof Radio>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default radio group
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = useState('option1');
    return (
      <RadioGroup name="default" value={value} onChange={setValue}>
        <Radio value="option1" label="Option 1" />
        <Radio value="option2" label="Option 2" />
        <Radio value="option3" label="Option 3" />
      </RadioGroup>
    );
  },
};

/**
 * All color schemes
 */
export const ColorSchemes: Story = {
  render: () => (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-grey-700">Primary</h3>
        <RadioGroup name="primary" defaultValue="1">
          <Radio value="1" label="Primary color" colorScheme="primary" />
        </RadioGroup>
      </div>
      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-grey-700">Secondary</h3>
        <RadioGroup name="secondary" defaultValue="1">
          <Radio value="1" label="Secondary color" colorScheme="secondary" />
        </RadioGroup>
      </div>
      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-grey-700">Success</h3>
        <RadioGroup name="success" defaultValue="1">
          <Radio value="1" label="Success color" colorScheme="success" />
        </RadioGroup>
      </div>
      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-grey-700">Error</h3>
        <RadioGroup name="error" defaultValue="1">
          <Radio value="1" label="Error color" colorScheme="error" />
        </RadioGroup>
      </div>
      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-grey-700">Warning</h3>
        <RadioGroup name="warning" defaultValue="1">
          <Radio value="1" label="Warning color" colorScheme="warning" />
        </RadioGroup>
      </div>
      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-grey-700">Grey</h3>
        <RadioGroup name="grey" defaultValue="1">
          <Radio value="1" label="Grey color" colorScheme="grey" />
        </RadioGroup>
      </div>
    </div>
  ),
};

/**
 * All sizes
 */
export const Sizes: Story = {
  render: () => (
    <div className="flex flex-col gap-4">
      <RadioGroup name="sizes" defaultValue="md">
        <Radio value="sm" size="sm" label="Small radio button" />
        <Radio value="md" size="md" label="Medium radio button" />
        <Radio value="lg" size="lg" label="Large radio button" />
      </RadioGroup>
    </div>
  ),
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => (
    <div className="flex flex-col gap-4">
      <RadioGroup name="disabled" defaultValue="1">
        <Radio value="1" label="Enabled radio" />
        <Radio value="2" label="Disabled unchecked" disabled />
        <Radio value="3" label="Disabled checked" disabled checked />
      </RadioGroup>
    </div>
  ),
};

/**
 * Form example
 */
export const FormExample: Story = {
  render: () => {
    const [plan, setPlan] = useState('basic');
    const [billing, setBilling] = useState('monthly');

    return (
      <div className="w-96 p-6 bg-grey-50 rounded-lg">
        <h2 className="text-xl font-bold mb-6">Choose Your Plan</h2>
        
        <div className="mb-6">
          <h3 className="text-sm font-semibold text-grey-700 mb-3">Plan Type</h3>
          <RadioGroup name="plan" value={plan} onChange={setPlan} className="gap-3">
            <div className="p-4 bg-white rounded-md border-2 border-grey-200 hover:border-primary-300 transition-colors">
              <Radio value="basic" label="Basic" colorScheme="primary" />
              <p className="ml-7 text-sm text-grey-600 mt-1">
                Perfect for individuals - $9/month
              </p>
            </div>
            <div className="p-4 bg-white rounded-md border-2 border-grey-200 hover:border-primary-300 transition-colors">
              <Radio value="pro" label="Pro" colorScheme="primary" />
              <p className="ml-7 text-sm text-grey-600 mt-1">
                Great for teams - $29/month
              </p>
            </div>
            <div className="p-4 bg-white rounded-md border-2 border-grey-200 hover:border-primary-300 transition-colors">
              <Radio value="enterprise" label="Enterprise" colorScheme="primary" />
              <p className="ml-7 text-sm text-grey-600 mt-1">
                For large organizations - Custom pricing
              </p>
            </div>
          </RadioGroup>
        </div>

        <div className="mb-6">
          <h3 className="text-sm font-semibold text-grey-700 mb-3">Billing Cycle</h3>
          <RadioGroup name="billing" value={billing} onChange={setBilling}>
            <Radio value="monthly" label="Monthly" colorScheme="success" />
            <Radio value="yearly" label="Yearly (Save 20%)" colorScheme="success" />
          </RadioGroup>
        </div>

        <button className="w-full px-4 py-2 bg-primary-500 text-white rounded-md hover:bg-primary-600 transition-colors">
          Continue to Payment
        </button>
      </div>
    );
  },
};

/**
 * Horizontal layout
 */
export const HorizontalLayout: Story = {
  render: () => {
    const [value, setValue] = useState('option2');
    return (
      <RadioGroup
        name="horizontal"
        value={value}
        onChange={setValue}
        className="flex-row gap-4"
      >
        <Radio value="option1" label="Option 1" />
        <Radio value="option2" label="Option 2" />
        <Radio value="option3" label="Option 3" />
      </RadioGroup>
    );
  },
};

/**
 * Accessibility features
 */
export const Accessibility: Story = {
  render: () => (
    <div className="flex flex-col gap-6 w-96">
      <div>
        <h3 className="text-lg font-semibold mb-2">Keyboard Navigation</h3>
        <p className="text-sm text-grey-600 mb-4">
          Tab to focus group, Arrow keys to select, Space to confirm
        </p>
        <RadioGroup name="keyboard" defaultValue="1">
          <Radio value="1" label="Press Tab to focus" />
          <Radio value="2" label="Arrow keys to navigate" />
          <Radio value="3" label="Space to select" />
        </RadioGroup>
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-2">Screen Reader Support</h3>
        <RadioGroup
          name="accessibility"
          defaultValue="1"
          className="border border-grey-300 p-4 rounded-md"
        >
          <Radio
            value="1"
            label="Option with ARIA support"
            aria-describedby="option1-desc"
          />
          <p id="option1-desc" className="ml-7 text-sm text-grey-600 mt-1">
            This description is announced by screen readers
          </p>
          <Radio value="2" label="Another accessible option" />
        </RadioGroup>
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-2">Focus Indicators</h3>
        <p className="text-sm text-grey-600 mb-4">
          Click to focus and see colored focus rings
        </p>
        <div className="flex flex-col gap-4">
          <RadioGroup name="focus-primary" defaultValue="1">
            <Radio value="1" label="Primary focus ring" colorScheme="primary" />
          </RadioGroup>
          <RadioGroup name="focus-success" defaultValue="1">
            <Radio value="1" label="Success focus ring" colorScheme="success" />
          </RadioGroup>
          <RadioGroup name="focus-error" defaultValue="1">
            <Radio value="1" label="Error focus ring" colorScheme="error" />
          </RadioGroup>
        </div>
      </div>
    </div>
  ),
};

/**
 * Playground for testing
 */
export const Playground: Story = {
  render: (args) => {
    const [value, setValue] = useState('option1');
    return (
      <RadioGroup name="playground" value={value} onChange={setValue}>
        <Radio value="option1" {...args} />
        <Radio value="option2" {...args} label="Option 2" />
        <Radio value="option3" {...args} label="Option 3" />
      </RadioGroup>
    );
  },
  args: {
    label: 'Option 1',
    colorScheme: 'primary',
    size: 'md',
  },
};
