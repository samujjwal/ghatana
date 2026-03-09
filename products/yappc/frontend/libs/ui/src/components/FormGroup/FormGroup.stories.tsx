import { FormField } from '../FormField';
import { FormGroup } from './FormGroup';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof FormGroup> = {
  title: 'Components/FormGroup',
  component: FormGroup,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof FormGroup>;

/**
 * Default fieldset with title
 */
export const Default: Story = {
  render: () => (
    <FormGroup title="Personal Information">
      <FormField label="First Name" required>
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="Last Name" required>
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="Date of Birth">
        <input type="date" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
    </FormGroup>
  ),
};

/**
 * With description
 */
export const WithDescription: Story = {
  render: () => (
    <FormGroup
      title="Contact Details"
      description="Please provide at least one method to contact you"
    >
      <FormField label="Email" required helperText="Primary contact method">
        <input type="email" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="Phone" helperText="Include country code">
        <input type="tel" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="Preferred Contact Method">
        <select className="px-3 py-2 border border-grey-300 rounded w-full">
          <option value="email">Email</option>
          <option value="phone">Phone</option>
          <option value="both">Both</option>
        </select>
      </FormField>
    </FormGroup>
  ),
};

/**
 * Multiple groups in a form
 */
export const MultipleGroups: Story = {
  render: () => (
    <form className="space-y-6 max-w-2xl">
      <FormGroup title="Account Information">
        <FormField label="Username" required helperText="3-20 characters, letters and numbers only">
          <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
        </FormField>
        <FormField label="Email" required>
          <input type="email" className="px-3 py-2 border border-grey-300 rounded w-full" />
        </FormField>
        <FormField label="Password" required helperText="Minimum 8 characters">
          <input type="password" className="px-3 py-2 border border-grey-300 rounded w-full" />
        </FormField>
      </FormGroup>

      <FormGroup title="Profile Details" description="Tell us about yourself">
        <FormField label="Display Name">
          <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
        </FormField>
        <FormField label="Bio">
          <textarea rows={4} className="px-3 py-2 border border-grey-300 rounded w-full" />
        </FormField>
        <FormField label="Website">
          <input type="url" className="px-3 py-2 border border-grey-300 rounded w-full" />
        </FormField>
      </FormGroup>

      <FormGroup title="Preferences">
        <FormField label="Email Notifications">
          <div className="flex items-center gap-2">
            <input type="checkbox" id="email-notif" className="w-4 h-4" />
            <label htmlFor="email-notif" className="text-sm">Receive email notifications</label>
          </div>
        </FormField>
        <FormField label="Newsletter">
          <div className="flex items-center gap-2">
            <input type="checkbox" id="newsletter" className="w-4 h-4" />
            <label htmlFor="newsletter" className="text-sm">Subscribe to newsletter</label>
          </div>
        </FormField>
      </FormGroup>

      <div className="flex gap-2">
        <button type="button" className="px-4 py-2 border border-grey-300 rounded hover:bg-grey-50">
          Cancel
        </button>
        <button type="submit" className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600">
          Create Account
        </button>
      </div>
    </form>
  ),
};

/**
 * Disabled group
 */
export const Disabled: Story = {
  render: () => (
    <FormGroup title="Billing Information" disabled>
      <FormField label="Card Number">
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="Expiry Date">
        <input type="text" placeholder="MM/YY" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="CVV">
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
    </FormGroup>
  ),
};

/**
 * As div instead of fieldset
 */
export const AsDiv: Story = {
  render: () => (
    <FormGroup title="Settings" asFieldset={false}>
      <FormField label="Language">
        <select className="px-3 py-2 border border-grey-300 rounded w-full">
          <option value="en">English</option>
          <option value="es">Spanish</option>
          <option value="fr">French</option>
        </select>
      </FormField>
      <FormField label="Timezone">
        <select className="px-3 py-2 border border-grey-300 rounded w-full">
          <option value="utc">UTC</option>
          <option value="est">EST</option>
          <option value="pst">PST</option>
        </select>
      </FormField>
    </FormGroup>
  ),
};

/**
 * Horizontal layout within group
 */
export const HorizontalLayout: Story = {
  render: () => (
    <FormGroup title="Shipping Address" description="Where should we send your order?">
      <FormField label="Street Address" layout="horizontal" labelWidth="150px" required>
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="Apartment/Suite" layout="horizontal" labelWidth="150px">
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="City" layout="horizontal" labelWidth="150px" required>
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="State/Province" layout="horizontal" labelWidth="150px" required>
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
      <FormField label="Postal Code" layout="horizontal" labelWidth="150px" required>
        <input type="text" className="px-3 py-2 border border-grey-300 rounded w-full" />
      </FormField>
    </FormGroup>
  ),
};

/**
 * Without border
 */
export const NoBorder: Story = {
  render: () => (
    <FormGroup title="Quick Settings" className="border-0 p-0">
      <FormField label="Theme">
        <select className="px-3 py-2 border border-grey-300 rounded w-full">
          <option value="light">Light</option>
          <option value="dark">Dark</option>
          <option value="auto">Auto</option>
        </select>
      </FormField>
      <FormField label="Font Size">
        <select className="px-3 py-2 border border-grey-300 rounded w-full">
          <option value="small">Small</option>
          <option value="medium">Medium</option>
          <option value="large">Large</option>
        </select>
      </FormField>
    </FormGroup>
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
      <FormGroup
        title="Account Settings"
        description="Manage your account preferences"
      >
        <FormField label="Email" required>
          <input type="email" className="px-3 py-2 border border-grey-600 rounded w-full bg-grey-800 text-white" />
        </FormField>
        <FormField label="Current Password">
          <input type="password" className="px-3 py-2 border border-grey-600 rounded w-full bg-grey-800 text-white" />
        </FormField>
        <FormField label="New Password">
          <input type="password" className="px-3 py-2 border border-grey-600 rounded w-full bg-grey-800 text-white" />
        </FormField>
      </FormGroup>
    </div>
  ),
};
