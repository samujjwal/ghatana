/**
 * Storybook stories for Checkbox component
 */
import { useState } from 'react';

import { Checkbox } from './Checkbox.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Checkbox (Tailwind)',
  component: Checkbox,
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
      description: 'Size of checkbox',
    },
    label: {
      control: 'text',
      description: 'Label text',
    },
    indeterminate: {
      control: 'boolean',
      description: 'Indeterminate state',
    },
    disabled: {
      control: 'boolean',
      description: 'Disabled state',
    },
  },
} satisfies Meta<typeof Checkbox>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default checkbox
 */
export const Default: Story = {
  args: {
    label: 'Accept terms and conditions',
  },
};

/**
 * All color schemes
 */
export const ColorSchemes: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <Checkbox label="Primary" colorScheme="primary" checked />
      <Checkbox label="Secondary" colorScheme="secondary" checked />
      <Checkbox label="Success" colorScheme="success" checked />
      <Checkbox label="Error" colorScheme="error" checked />
      <Checkbox label="Warning" colorScheme="warning" checked />
      <Checkbox label="Grey" colorScheme="grey" checked />
    </div>
  ),
};

/**
 * All sizes
 */
export const Sizes: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <Checkbox size="sm" label="Small checkbox" checked />
      <Checkbox size="md" label="Medium checkbox" checked />
      <Checkbox size="lg" label="Large checkbox" checked />
    </div>
  ),
};

/**
 * Checkbox states
 */
export const States: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <Checkbox label="Unchecked" checked={false} />
      <Checkbox label="Checked" checked={true} />
      <Checkbox label="Indeterminate" indeterminate />
      <Checkbox label="Disabled unchecked" disabled />
      <Checkbox label="Disabled checked" disabled checked />
    </div>
  ),
};

/**
 * Indeterminate state (for "Select All")
 */
export const IndeterminateExample: Story = {
  render: () => {
    const [checkedItems, setCheckedItems] = useState([false, false, false]);

    const allChecked = checkedItems.every(Boolean);
    const isIndeterminate = checkedItems.some(Boolean) && !allChecked;

    return (
      <div className="flex flex-col gap-3 p-4 border border-grey-300 rounded-md">
        <Checkbox
          label="Select all"
          checked={allChecked}
          indeterminate={isIndeterminate}
          onChange={(e) => {
            const checked = (e.target as HTMLInputElement).checked;
            setCheckedItems([checked, checked, checked]);
          }}
        />
        <div className="ml-6 flex flex-col gap-2 border-l-2 border-grey-200 pl-4">
          <Checkbox
            label="Option 1"
            checked={checkedItems[0]}
            onChange={(e) => {
              const checked = (e.target as HTMLInputElement).checked;
              setCheckedItems([checked, checkedItems[1], checkedItems[2]]);
            }}
          />
          <Checkbox
            label="Option 2"
            checked={checkedItems[1]}
            onChange={(e) => {
              const checked = (e.target as HTMLInputElement).checked;
              setCheckedItems([checkedItems[0], checked, checkedItems[2]]);
            }}
          />
          <Checkbox
            label="Option 3"
            checked={checkedItems[2]}
            onChange={(e) => {
              const checked = (e.target as HTMLInputElement).checked;
              setCheckedItems([checkedItems[0], checkedItems[1], checked]);
            }}
          />
        </div>
      </div>
    );
  },
};

/**
 * Form example
 */
export const FormExample: Story = {
  render: () => {
    const [formData, setFormData] = useState({
      newsletter: false,
      terms: false,
      privacy: false,
    });

    return (
      <div className="w-96 p-6 bg-grey-50 rounded-lg">
        <h2 className="text-xl font-bold mb-4">Sign Up Preferences</h2>
        
        <div className="flex flex-col gap-4">
          <Checkbox
            label="Send me newsletter and updates"
            checked={formData.newsletter}
            onChange={(e) =>
              setFormData({ ...formData, newsletter: (e.target as HTMLInputElement).checked })
            }
          />
          
          <Checkbox
            label="I accept the terms and conditions"
            colorScheme="error"
            checked={formData.terms}
            onChange={(e) =>
              setFormData({ ...formData, terms: (e.target as HTMLInputElement).checked })
            }
          />
          
          <Checkbox
            label="I agree to the privacy policy"
            colorScheme="error"
            checked={formData.privacy}
            onChange={(e) =>
              setFormData({ ...formData, privacy: (e.target as HTMLInputElement).checked })
            }
          />
        </div>

        <button
          disabled={!formData.terms || !formData.privacy}
          className={cn(
            'mt-6 w-full px-4 py-2 rounded-md transition-colors',
            formData.terms && formData.privacy
              ? 'bg-primary-500 text-white hover:bg-primary-600'
              : 'bg-grey-300 text-grey-600 cursor-not-allowed'
          )}
        >
          Create Account
        </button>
      </div>
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
          Tab to focus, Space to toggle
        </p>
        <div className="flex flex-col gap-2">
          <Checkbox label="Press Tab to focus me" />
          <Checkbox label="Tab again for next checkbox" />
          <Checkbox label="Space to check/uncheck" />
        </div>
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-2">Screen Reader Support</h3>
        <Checkbox
          label="Accessible checkbox with ARIA"
          aria-label="Accept terms and conditions"
        />
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-2">Focus Indicators</h3>
        <p className="text-sm text-grey-600 mb-4">
          Click to focus and see the ring
        </p>
        <div className="flex flex-col gap-2">
          <Checkbox label="Primary focus ring" colorScheme="primary" />
          <Checkbox label="Success focus ring" colorScheme="success" />
          <Checkbox label="Error focus ring" colorScheme="error" />
        </div>
      </div>
    </div>
  ),
};

/**
 * Playground for testing
 */
export const Playground: Story = {
  args: {
    label: 'Checkbox label',
    colorScheme: 'primary',
    size: 'md',
  },
};

// Helper for cn utility in FormExample
/**
 *
 */
function cn(...classes: (string | boolean | undefined)[]) {
  return classes.filter(Boolean).join(' ');
}
