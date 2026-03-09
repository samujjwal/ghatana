/**
 * Storybook stories for TextField component
 */
import { TextField } from './TextField.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/TextField (Tailwind)',
  component: TextField,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    variant: {
      control: 'select',
      options: ['outline', 'filled'],
      description: 'Visual variant of the text field',
    },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
      description: 'Size of the text field',
    },
    label: {
      control: 'text',
      description: 'Label text',
    },
    helperText: {
      control: 'text',
      description: 'Helper text',
    },
    error: {
      control: 'text',
      description: 'Error message',
    },
    required: {
      control: 'boolean',
      description: 'Whether field is required',
    },
    disabled: {
      control: 'boolean',
      description: 'Whether field is disabled',
    },
  },
} satisfies Meta<typeof TextField>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default text field with outline variant
 */
export const Default: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
  },
};

/**
 * All available variants
 */
export const Variants: Story = {
  render: () => (
    <div className="flex flex-col gap-4 w-80">
      <TextField
        variant="outline"
        label="Outline Variant"
        placeholder="outline@example.com"
      />
      <TextField
        variant="filled"
        label="Filled Variant"
        placeholder="filled@example.com"
      />
    </div>
  ),
};

/**
 * All available sizes
 */
export const Sizes: Story = {
  render: () => (
    <div className="flex flex-col gap-4 w-80">
      <TextField
        size="sm"
        label="Small"
        placeholder="Small text field"
      />
      <TextField
        size="md"
        label="Medium"
        placeholder="Medium text field"
      />
      <TextField
        size="lg"
        label="Large"
        placeholder="Large text field"
      />
    </div>
  ),
};

/**
 * With helper text
 */
export const WithHelperText: Story = {
  args: {
    label: 'Username',
    placeholder: 'john_doe',
    helperText: 'Must be 3-20 characters, letters and underscores only',
  },
};

/**
 * Error state
 */
export const ErrorState: Story = {
  render: () => (
    <div className="flex flex-col gap-4 w-80">
      <TextField
        label="Email"
        placeholder="email@example.com"
        error="Email is required"
      />
      <TextField
        variant="filled"
        label="Password"
        type="password"
        error="Password must be at least 8 characters"
      />
    </div>
  ),
};

/**
 * Required field
 */
export const Required: Story = {
  args: {
    label: 'Full Name',
    placeholder: 'John Doe',
    required: true,
    helperText: 'This field is required',
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => (
    <div className="flex flex-col gap-4 w-80">
      <TextField
        label="Disabled Empty"
        placeholder="Cannot edit"
        disabled
      />
      <TextField
        label="Disabled with Value"
        value="Pre-filled value"
        disabled
        helperText="This field is locked"
      />
    </div>
  ),
};

/**
 * Different input types
 */
export const InputTypes: Story = {
  render: () => (
    <div className="flex flex-col gap-4 w-80">
      <TextField
        type="text"
        label="Text"
        placeholder="Enter text"
      />
      <TextField
        type="email"
        label="Email"
        placeholder="email@example.com"
      />
      <TextField
        type="password"
        label="Password"
        placeholder="Enter password"
      />
      <TextField
        type="number"
        label="Number"
        placeholder="42"
      />
      <TextField
        type="tel"
        label="Phone"
        placeholder="+1 (555) 123-4567"
      />
      <TextField
        type="url"
        label="Website"
        placeholder="https://example.com"
      />
    </div>
  ),
};

/**
 * Complex form example
 */
export const FormExample: Story = {
  render: () => (
    <form className="flex flex-col gap-4 w-96 p-6 bg-grey-50 rounded-lg">
      <h2 className="text-xl font-bold text-grey-900 mb-2">Sign Up</h2>
      
      <TextField
        label="Email"
        type="email"
        placeholder="john@example.com"
        required
        helperText="We'll never share your email"
      />
      
      <TextField
        label="Username"
        placeholder="john_doe"
        required
        helperText="3-20 characters, letters and underscores"
      />
      
      <TextField
        label="Password"
        type="password"
        required
        helperText="At least 8 characters"
      />
      
      <TextField
        label="Confirm Password"
        type="password"
        required
      />
      
      <button
        type="submit"
        className="mt-4 px-4 py-2 bg-primary-500 text-white rounded-md hover:bg-primary-600 transition-colors"
      >
        Create Account
      </button>
    </form>
  ),
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
          Tab through fields, Shift+Tab to go back
        </p>
        <div className="flex flex-col gap-3">
          <TextField label="First Field" placeholder="Tab to next" />
          <TextField label="Second Field" placeholder="Tab to next" />
          <TextField label="Third Field" placeholder="Shift+Tab to previous" />
        </div>
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-2">ARIA Labels</h3>
        <TextField
          label="Email Address"
          placeholder="email@example.com"
          required
          helperText="We use this for account recovery"
          aria-describedby="email-helper"
        />
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-2">Error Announcements</h3>
        <TextField
          label="Username"
          error="Username is already taken"
          aria-invalid="true"
        />
      </div>
    </div>
  ),
};

/**
 * Playground for testing
 */
export const Playground: Story = {
  args: {
    label: 'Label',
    placeholder: 'Placeholder text',
    variant: 'outline',
    size: 'md',
    helperText: 'Helper text goes here',
  },
};
