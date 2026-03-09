/**
 * Textarea Component Stories (Tailwind CSS + Base UI)
 */

import { useState } from 'react';

import { Textarea } from './Textarea.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Forms/Textarea (Tailwind)',
  component: Textarea,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'A multiline text input component built with Base UI Field primitives and styled with Tailwind CSS. Supports auto-resize, character counting, and full accessibility.',
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
    variant: {
      control: 'select',
      options: ['outline', 'filled'],
      description: 'Visual style variant',
    },
    colorScheme: {
      control: 'select',
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'grey'],
      description: 'Color scheme for focus states',
    },
    autoResize: {
      control: 'boolean',
      description: 'Auto-resize based on content',
    },
    showCounter: {
      control: 'boolean',
      description: 'Show character counter',
    },
    disabled: {
      control: 'boolean',
      description: 'Whether the textarea is disabled',
    },
    required: {
      control: 'boolean',
      description: 'Whether the textarea is required',
    },
  },
} satisfies Meta<typeof Textarea>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default textarea
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = useState('');

    return (
      <Textarea
        label="Description"
        placeholder="Enter your description..."
        value={value}
        onChange={(e) => setValue(e.target.value)}
        rows={4}
      />
    );
  },
};

/**
 * All size variants
 */
export const Sizes: Story = {
  render: () => {
    const [value, setValue] = useState('');

    return (
      <div className="space-y-4">
        <Textarea
          label="Small"
          size="sm"
          placeholder="Small textarea..."
          value={value}
          onChange={(e) => setValue(e.target.value)}
          rows={3}
        />

        <Textarea
          label="Medium (default)"
          size="md"
          placeholder="Medium textarea..."
          value={value}
          onChange={(e) => setValue(e.target.value)}
          rows={4}
        />

        <Textarea
          label="Large"
          size="lg"
          placeholder="Large textarea..."
          value={value}
          onChange={(e) => setValue(e.target.value)}
          rows={5}
        />
      </div>
    );
  },
};

/**
 * Variant styles
 */
export const Variants: Story = {
  render: () => {
    const [value, setValue] = useState('');

    return (
      <div className="space-y-4">
        <Textarea
          label="Outline (default)"
          variant="outline"
          placeholder="Outline variant..."
          value={value}
          onChange={(e) => setValue(e.target.value)}
          rows={4}
        />

        <Textarea
          label="Filled"
          variant="filled"
          placeholder="Filled variant..."
          value={value}
          onChange={(e) => setValue(e.target.value)}
          rows={4}
        />
      </div>
    );
  },
};

/**
 * With auto-resize enabled
 */
export const AutoResize: Story = {
  render: () => {
    const [value, setValue] = useState('Type multiple lines to see auto-resize in action...');

    return (
      <Textarea
        label="Auto-resizing textarea"
        placeholder="Start typing..."
        value={value}
        onChange={(e) => setValue(e.target.value)}
        autoResize
        rows={2}
        helperText="This textarea will automatically grow as you type"
      />
    );
  },
};

/**
 * With character counter
 */
export const WithCharacterCounter: Story = {
  render: () => {
    const [value, setValue] = useState('');

    return (
      <Textarea
        label="Tweet"
        placeholder="What's happening?"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        maxLength={280}
        showCounter
        rows={4}
        helperText="Share your thoughts"
      />
    );
  },
};

/**
 * With helper text
 */
export const WithHelperText: Story = {
  render: () => {
    const [value, setValue] = useState('');

    return (
      <Textarea
        label="Feedback"
        placeholder="Tell us what you think..."
        value={value}
        onChange={(e) => setValue(e.target.value)}
        helperText="Your feedback helps us improve our product"
        rows={5}
      />
    );
  },
};

/**
 * Error state
 */
export const ErrorState: Story = {
  render: () => {
    const [value, setValue] = useState('');

    return (
      <Textarea
        label="Comment"
        placeholder="Enter your comment..."
        value={value}
        onChange={(e) => setValue(e.target.value)}
        error="Comment must be at least 10 characters long"
        required
        rows={4}
      />
    );
  },
};

/**
 * Required field
 */
export const Required: Story = {
  render: () => {
    const [value, setValue] = useState('');

    return (
      <Textarea
        label="Message"
        placeholder="Enter your message..."
        value={value}
        onChange={(e) => setValue(e.target.value)}
        required
        helperText="This field is required"
        rows={4}
      />
    );
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => (
    <Textarea
      label="Disabled textarea"
      placeholder="This textarea is disabled"
      defaultValue="You cannot edit this content"
      disabled
      rows={4}
    />
  ),
};

/**
 * Read-only state
 */
export const ReadOnly: Story = {
  render: () => (
    <Textarea
      label="Terms and Conditions"
      defaultValue="By using this service, you agree to our terms and conditions. This is a read-only field that displays important information."
      readOnly
      rows={4}
      helperText="Read-only content"
    />
  ),
};

/**
 * Form example with validation
 */
export const FormExample: Story = {
  render: () => {
    const [description, setDescription] = useState('');
    const [notes, setNotes] = useState('');
    const [error, setError] = useState('');

    const handleSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      if (description.length < 20) {
        setError('Description must be at least 20 characters');
        return;
      }
      setError('');
      alert('Form submitted!');
    };

    return (
      <form
        onSubmit={handleSubmit}
        className="mx-auto max-w-md space-y-4 rounded-lg border border-grey-200 bg-white p-6 shadow-sm"
      >
        <h3 className="text-lg font-semibold text-grey-900">Product Review</h3>

        <Textarea
          label="Description"
          placeholder="Describe your experience..."
          value={description}
          onChange={(e) => {
            setDescription(e.target.value);
            setError('');
          }}
          error={error}
          required
          maxLength={500}
          showCounter
          rows={4}
        />

        <Textarea
          label="Additional Notes (Optional)"
          placeholder="Any other comments?"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          helperText="Optional feedback"
        />

        <div className="flex justify-end gap-2">
          <button
            type="button"
            className="rounded-md border border-grey-300 px-4 py-2 text-sm font-medium text-grey-700 hover:bg-grey-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
          >
            Submit Review
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
    const [value, setValue] = useState('');

    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-grey-200 bg-grey-50 p-4">
          <h3 className="mb-2 font-semibold text-grey-900">Accessibility Features:</h3>
          <ul className="space-y-1 text-sm text-grey-700">
            <li>• Proper label association</li>
            <li>• Error announcements for screen readers</li>
            <li>• Helper text linked via aria-describedby</li>
            <li>• Required field indication</li>
            <li>• Character counter for length limits</li>
          </ul>
        </div>

        <Textarea
          label="Accessible textarea"
          placeholder="This textarea follows WCAG 2.1 AA guidelines"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          required
          maxLength={200}
          showCounter
          helperText="Screen readers will announce all relevant information"
          rows={4}
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
    label: 'Playground Textarea',
    placeholder: 'Enter text here...',
    helperText: 'This is helper text',
    size: 'md',
    variant: 'outline',
    colorScheme: 'primary',
    rows: 4,
    autoResize: false,
    showCounter: false,
    disabled: false,
    required: false,
    maxLength: undefined,
  },
  render: (args) => {
    const [value, setValue] = useState('');

    return (
      <Textarea
        {...args}
        value={value}
        onChange={(e) => setValue(e.target.value)}
      />
    );
  },
};
