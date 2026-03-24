import { useState } from 'react';

import { FormField } from './FormField';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof FormField> = {
  title: 'Components/FormField',
  component: FormField,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
  argTypes: {
    layout: {
      control: 'select',
      options: ['vertical', 'horizontal'],
    },
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof FormField>;

/**
 * Default vertical layout with label
 */
export const Default: Story = {
  args: {
    label: 'Email Address',
    children: <input type="email" className="px-3 py-2 border border-grey-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500" />,
  },
};

/**
 * All layout orientations
 */
export const Layouts: Story = {
  render: () => (
    <div className="space-y-6">
      <FormField label="Vertical Layout (default)">
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>

      <FormField label="Horizontal Layout" layout="horizontal" labelWidth="150px">
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>

      <FormField label="Horizontal Wide Label" layout="horizontal" labelWidth="200px">
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
    </div>
  ),
};

/**
 * All size variants
 */
export const Sizes: Story = {
  render: () => (
    <div className="space-y-4">
      <FormField label="Small Size" size="small">
        <input type="text" className="px-2 py-1 text-sm border border-grey-300 rounded w-full" />
      </FormField>

      <FormField label="Medium Size (default)" size="medium">
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>

      <FormField label="Large Size" size="large">
        <input type="text" className="px-4 py-3 text-lg border border-grey-300 rounded w-full" />
      </FormField>
    </div>
  ),
};

/**
 * With helper text
 */
export const WithHelperText: Story = {
  args: {
    label: 'Username',
    helperText: 'Must be 3-20 characters, letters and numbers only',
    children: <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />,
  },
};

/**
 * With error message
 */
export const WithError: Story = {
  args: {
    label: 'Email',
    error: 'Please enter a valid email address',
    children: <input type="email" className="px-3 py-2 border-2 border-red-500 rounded w-full" />,
  },
};

/**
 * Required field
 */
export const Required: Story = {
  args: {
    label: 'Full Name',
    required: true,
    helperText: 'This field is required',
    children: <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />,
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  args: {
    label: 'Email',
    disabled: true,
    children: <input type="email" value="user@example.com" readOnly className="px-3 py-2 border border-grey-300 rounded w-full bg-grey-100 cursor-not-allowed" />,
  },
};

/**
 * Hidden label (for screen readers only)
 */
export const HiddenLabel: Story = {
  args: {
    label: 'Search',
    hideLabel: true,
    children: <input type="search" placeholder="Search..." className="px-3 py-2 border border-grey-300 rounded w-full" />,
  },
};

/**
 * With textarea
 */
export const WithTextarea: Story = {
  args: {
    label: 'Description',
    helperText: 'Provide a detailed description',
    children: <textarea rows={4} className="px-3 py-2 border border-grey-300 rounded w-full resize-none" />,
  },
};

/**
 * With select
 */
export const WithSelect: Story = {
  args: {
    label: 'Country',
    required: true,
    children: (
      <select className="px-3 py-2 border border-grey-300 rounded w-full">
        <option value="">Select a country</option>
        <option value="us">United States</option>
        <option value="uk">United Kingdom</option>
        <option value="ca">Canada</option>
      </select>
    ),
  },
};

/**
 * Form validation example
 */
export const FormValidation: Story = {
  render: () => {
    const [values, setValues] = useState({ email: '', password: '' });
    const [errors, setErrors] = useState<Record<string, string>>({});

    const handleSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      const newErrors: Record<string, string> = {};

      if (!values.email) {
        newErrors.email = 'Email is required';
      } else if (!/\S+@\S+\.\S+/.test(values.email)) {
        newErrors.email = 'Invalid email format';
      }

      if (!values.password) {
        newErrors.password = 'Password is required';
      } else if (values.password.length < 8) {
        newErrors.password = 'Password must be at least 8 characters';
      }

      setErrors(newErrors);

      if (Object.keys(newErrors).length === 0) {
        alert('Form submitted successfully!');
      }
    };

    return (
      <form onSubmit={handleSubmit} className="space-y-4 max-w-md">
        <FormField
          label="Email"
          required
          error={errors.email}
          helperText={!errors.email ? 'Enter your email address' : undefined}
        >
          <input
            type="email"
            value={values.email}
            onChange={(e) => setValues({ ...values, email: e.target.value })}
            className={`px-3 py-2 border rounded w-full ${errors.email ? 'border-red-500' : 'border-grey-300'}`}
          />
        </FormField>

        <FormField
          label="Password"
          required
          error={errors.password}
          helperText={!errors.password ? 'Minimum 8 characters' : undefined}
        >
          <input
            type="password"
            value={values.password}
            onChange={(e) => setValues({ ...values, password: e.target.value })}
            className={`px-3 py-2 border rounded w-full ${errors.password ? 'border-red-500' : 'border-grey-300'}`}
          />
        </FormField>

        <button
          type="submit"
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Submit
        </button>
      </form>
    );
  },
};

/**
 * Horizontal form layout
 */
export const HorizontalForm: Story = {
  render: () => (
    <form className="space-y-4 max-w-2xl">
      <FormField label="First Name" layout="horizontal" labelWidth="150px" required>
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>

      <FormField label="Last Name" layout="horizontal" labelWidth="150px" required>
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>

      <FormField label="Email" layout="horizontal" labelWidth="150px" required helperText="We'll never share your email">
        <input type="email" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>

      <FormField label="Phone" layout="horizontal" labelWidth="150px">
        <input type="tel" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>

      <FormField label="Message" layout="horizontal" labelWidth="150px">
        <textarea rows={4} className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>

      <div className="flex justify-end gap-2" style={{ marginLeft: '150px' }}>
        <button type="button" className="px-4 py-2 border border-grey-300 rounded hover:bg-grey-50">
          Cancel
        </button>
        <button type="submit" className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600">
          Submit
        </button>
      </div>
    </form>
  ),
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  parameters: {
    backgrounds: { default: 'dark' },
  },
  render: () => (
    <div className="dark">
      <div className="space-y-4 max-w-md">
        <FormField label="Username" required helperText="Choose a unique username">
          <input type="text" className="px-3 py-2 border border-grey-600 rounded w-full bg-grey-800 text-white" />
        </FormField>

        <FormField label="Email" error="This email is already registered">
          <input type="email" className="px-3 py-2 border-2 border-red-500 rounded w-full bg-grey-800 text-white" />
        </FormField>

        <FormField label="Bio">
          <textarea rows={4} className="px-3 py-2 border border-grey-600 rounded w-full bg-grey-800 text-white" />
        </FormField>
      </div>
    </div>
  ),
};
