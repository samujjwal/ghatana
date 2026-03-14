import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { Form, type FieldConfig } from './Form';

/**
 * Form component with configuration-driven field rendering.
 *
 * ## Features
 * - Configuration-driven field rendering
 * - Built-in validation with custom validators
 * - Conditional field visibility and disabled state
 * - Data transformation (form ↔ submit values)
 * - Edit mode with initial data
 * - Accessible with ARIA labels
 * - Responsive Tailwind styling
 *
 * ## Usage
 *
 * ```tsx
 * <DynamicForm
 *   fields={[
 *     { name: 'name', type: 'text', label: 'Name', required: true },
 *     { name: 'email', type: 'email', label: 'Email', required: true }
 *   ]}
 *   onSubmit={(data) => console.log(data)}
 * />
 * ```
 */
const meta = {
  title: 'Organisms/DynamicForm',
  component: DynamicForm,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'Generic form component that renders fields based on configuration. Eliminates form boilerplate with built-in validation, conditional fields, and data transformation.',
      },
    },
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Form>;

export default meta;
type Story = StoryObj<typeof meta>;

// ============================================================================
// Story 1: Simple Contact Form
// ============================================================================

interface ContactFormData {
  name: string;
  email: string;
  message: string;
}

const contactFields: FieldConfig<ContactFormData>[] = [
  {
    name: 'name',
    type: 'text',
    label: 'Name',
    required: true,
    placeholder: 'John Doe',
  },
  {
    name: 'email',
    type: 'email',
    label: 'Email',
    required: true,
    placeholder: 'john@example.com',
    validation: (value) => {
      if (value && !value.includes('@')) {
        return 'Please enter a valid email address';
      }
    },
  },
  {
    name: 'message',
    type: 'textarea',
    label: 'Message',
    required: true,
    rows: 4,
    placeholder: 'Your message here...',
  },
];

/**
 * Simple contact form with name, email, and message fields.
 * Demonstrates basic form usage with validation.
 */
export const SimpleContactForm: Story = {
  args: {
    fields: contactFields,
    onSubmit: fn((data) => console.log('Contact form submitted:', data)),
    submitText: 'Send Message',
  },
};

// ============================================================================
// Story 2: Registration Form with Validation
// ============================================================================

interface RegistrationData {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
  age: number;
  terms: string;
}

const registrationFields: FieldConfig<RegistrationData>[] = [
  {
    name: 'username',
    type: 'text',
    label: 'Username',
    required: true,
    placeholder: 'johndoe',
    validation: (value) => {
      if (value && value.length < 3) {
        return 'Username must be at least 3 characters';
      }
    },
  },
  {
    name: 'email',
    type: 'email',
    label: 'Email',
    required: true,
    placeholder: 'john@example.com',
    validation: (value) => {
      if (value && !value.includes('@')) {
        return 'Invalid email format';
      }
    },
  },
  {
    name: 'password',
    type: 'password',
    label: 'Password',
    required: true,
    validation: (value) => {
      if (value && value.length < 8) {
        return 'Password must be at least 8 characters';
      }
    },
  },
  {
    name: 'confirmPassword',
    type: 'password',
    label: 'Confirm Password',
    required: true,
    validation: (value, data) => {
      if (value && value !== data.password) {
        return 'Passwords do not match';
      }
    },
  },
  {
    name: 'age',
    type: 'number',
    label: 'Age',
    required: true,
    min: 18,
    max: 120,
    validation: (value) => {
      if (value && (value < 18 || value > 120)) {
        return 'Age must be between 18 and 120';
      }
    },
  },
];

/**
 * Registration form with multiple validation rules.
 * Demonstrates custom validation including cross-field validation.
 */
export const RegistrationForm: Story = {
  args: {
    fields: registrationFields,
    onSubmit: fn((data) => console.log('Registration submitted:', data)),
    submitText: 'Register',
  },
};

// ============================================================================
// Story 3: Survey Form with Conditional Fields
// ============================================================================

interface SurveyData {
  type: 'personal' | 'business';
  name: string;
  companyName?: string;
  employeeCount?: number;
  feedback: string;
}

const surveyFields: FieldConfig<SurveyData>[] = [
  {
    name: 'type',
    type: 'select',
    label: 'Type',
    required: true,
    options: [
      { label: 'Personal', value: 'personal' },
      { label: 'Business', value: 'business' },
    ],
  },
  {
    name: 'name',
    type: 'text',
    label: 'Name',
    required: true,
    placeholder: 'Your name',
  },
  {
    name: 'companyName',
    type: 'text',
    label: 'Company Name',
    required: true,
    visible: (data) => data.type === 'business',
    placeholder: 'Acme Inc.',
  },
  {
    name: 'employeeCount',
    type: 'number',
    label: 'Number of Employees',
    visible: (data) => data.type === 'business',
    min: 1,
    placeholder: '10',
  },
  {
    name: 'feedback',
    type: 'textarea',
    label: 'Feedback',
    required: true,
    rows: 4,
    placeholder: 'Your feedback...',
  },
];

/**
 * Survey form with conditional fields based on type selection.
 * Demonstrates dynamic field visibility.
 */
export const SurveyFormConditional: Story = {
  args: {
    fields: surveyFields,
    onSubmit: fn((data) => console.log('Survey submitted:', data)),
    submitText: 'Submit Survey',
  },
};

// ============================================================================
// Story 4: Edit Mode with Initial Data
// ============================================================================

/**
 * Edit mode with pre-populated form data.
 * Demonstrates form usage for editing existing records.
 */
export const EditMode: Story = {
  args: {
    fields: contactFields,
    initialData: {
      name: 'Jane Doe',
      email: 'jane@example.com',
      message: 'I would like to inquire about your services.',
    },
    onSubmit: fn((data) => console.log('Updated contact:', data)),
    submitText: 'Update',
    cancelText: 'Cancel',
    onCancel: fn(() => console.log('Edit cancelled')),
  },
};

// ============================================================================
// Story 5: Policy Form (Complex Example)
// ============================================================================

interface PolicyData {
  name: string;
  type: 'time-limit' | 'content-filter' | 'app-block' | 'schedule';
  maxUsageMinutes?: number;
  blockedCategories?: string[];
  blockedApps?: string[];
  allowedStart?: string;
  allowedEnd?: string;
  deviceIds: string[];
}

const policyFields: FieldConfig<PolicyData>[] = [
  {
    name: 'name',
    type: 'text',
    label: 'Policy Name',
    required: true,
    placeholder: 'e.g., School Time Limits',
  },
  {
    name: 'type',
    type: 'select',
    label: 'Policy Type',
    required: true,
    options: [
      { label: 'Time Limit', value: 'time-limit' },
      { label: 'Content Filter', value: 'content-filter' },
      { label: 'App Block', value: 'app-block' },
      { label: 'Schedule', value: 'schedule' },
    ],
  },
  {
    name: 'maxUsageMinutes',
    type: 'number',
    label: 'Maximum Usage (minutes)',
    visible: (data) => data.type === 'time-limit',
    required: true,
    placeholder: '60',
    validation: (value, data) => {
      if (data.type === 'time-limit' && (!value || value <= 0)) {
        return 'Valid max usage minutes required';
      }
    },
  },
  {
    name: 'blockedCategories',
    type: 'text',
    label: 'Blocked Categories (comma-separated)',
    visible: (data) => data.type === 'content-filter',
    required: true,
    placeholder: 'social media, gaming, adult content',
    transform: {
      toForm: (value: string[]) => value?.join(', ') || '',
      fromForm: (value: string) =>
        value.split(',').map((s) => s.trim()).filter(Boolean),
    },
    validation: (value, data) => {
      if (data.type === 'content-filter' && !value?.trim()) {
        return 'At least one blocked category required';
      }
    },
  },
  {
    name: 'blockedApps',
    type: 'text',
    label: 'Blocked Apps (comma-separated)',
    visible: (data) => data.type === 'app-block',
    required: true,
    placeholder: 'TikTok, Instagram, Snapchat',
    transform: {
      toForm: (value: string[]) => value?.join(', ') || '',
      fromForm: (value: string) =>
        value.split(',').map((s) => s.trim()).filter(Boolean),
    },
    validation: (value, data) => {
      if (data.type === 'app-block' && !value?.trim()) {
        return 'At least one blocked app required';
      }
    },
  },
  {
    name: 'allowedStart',
    type: 'time',
    label: 'Start Time',
    visible: (data) => data.type === 'schedule',
    required: true,
    validation: (value, data) => {
      if (data.type === 'schedule' && !value) {
        return 'Start time required';
      }
    },
  },
  {
    name: 'allowedEnd',
    type: 'time',
    label: 'End Time',
    visible: (data) => data.type === 'schedule',
    required: true,
    validation: (value, data) => {
      if (data.type === 'schedule' && !value) {
        return 'End time required';
      }
    },
  },
  {
    name: 'deviceIds',
    type: 'text',
    label: 'Device IDs (comma-separated)',
    required: true,
    placeholder: 'device-1, device-2',
    transform: {
      toForm: (value: string[]) => value?.join(', ') || '',
      fromForm: (value: string) =>
        value.split(',').map((s) => s.trim()).filter(Boolean),
    },
    validation: (value) => {
      if (!value?.trim()) {
        return 'At least one device ID required';
      }
    },
  },
];

/**
 * Complex policy form with multiple conditional fields and data transformations.
 * Demonstrates advanced form capabilities.
 */
export const PolicyForm: Story = {
  args: {
    fields: policyFields,
    onSubmit: fn((data) => console.log('Policy created:', data)),
    submitText: 'Create Policy',
    onCancel: fn(() => console.log('Cancelled')),
  },
};

// ============================================================================
// Story 6: Form with All Field Types
// ============================================================================

interface AllFieldsData {
  textField: string;
  emailField: string;
  passwordField: string;
  numberField: number;
  dateField: string;
  timeField: string;
  telField: string;
  selectField: string;
  textareaField: string;
}

const allFieldTypes: FieldConfig<AllFieldsData>[] = [
  {
    name: 'textField',
    type: 'text',
    label: 'Text Field',
    placeholder: 'Enter text',
  },
  {
    name: 'emailField',
    type: 'email',
    label: 'Email Field',
    placeholder: 'email@example.com',
  },
  {
    name: 'passwordField',
    type: 'password',
    label: 'Password Field',
    placeholder: 'Enter password',
  },
  {
    name: 'numberField',
    type: 'number',
    label: 'Number Field',
    min: 0,
    max: 100,
    step: 1,
    placeholder: '42',
  },
  {
    name: 'dateField',
    type: 'date',
    label: 'Date Field',
  },
  {
    name: 'timeField',
    type: 'time',
    label: 'Time Field',
  },
  {
    name: 'telField',
    type: 'tel',
    label: 'Phone Field',
    placeholder: '+1 (555) 123-4567',
  },
  {
    name: 'selectField',
    type: 'select',
    label: 'Select Field',
    options: [
      { label: 'Option 1', value: 'option1' },
      { label: 'Option 2', value: 'option2' },
      { label: 'Option 3', value: 'option3' },
    ],
  },
  {
    name: 'textareaField',
    type: 'textarea',
    label: 'Textarea Field',
    rows: 4,
    placeholder: 'Enter multiple lines...',
  },
];

/**
 * Showcase of all supported field types.
 * Useful for testing and documentation.
 */
export const AllFieldTypes: Story = {
  args: {
    fields: allFieldTypes,
    onSubmit: fn((data) => console.log('All fields submitted:', data)),
  },
};

// ============================================================================
// Story 7: Form with Help Text
// ============================================================================

interface FormWithHelp {
  username: string;
  password: string;
}

const fieldsWithHelp: FieldConfig<DynamicFormWithHelp>[] = [
  {
    name: 'username',
    type: 'text',
    label: 'Username',
    required: true,
    helpText: 'Choose a unique username (3-20 characters)',
    validation: (value) => {
      if (value && (value.length < 3 || value.length > 20)) {
        return 'Username must be 3-20 characters';
      }
    },
  },
  {
    name: 'password',
    type: 'password',
    label: 'Password',
    required: true,
    helpText: 'Use at least 8 characters with letters and numbers',
    validation: (value) => {
      if (value && value.length < 8) {
        return 'Password must be at least 8 characters';
      }
    },
  },
];

/**
 * Form with help text below fields.
 * Demonstrates field-level guidance for users.
 */
export const FormWithHelpText: Story = {
  args: {
    fields: fieldsWithHelp,
    onSubmit: fn((data) => console.log('Submitted:', data)),
    submitText: 'Sign In',
  },
};

// ============================================================================
// Story 8: Disabled Form
// ============================================================================

/**
 * Form with all fields disabled.
 * Demonstrates disabled state for read-only views.
 */
export const DisabledForm: Story = {
  args: {
    fields: contactFields.map((field) => ({
      ...field,
      disabled: () => true,
    })),
    initialData: {
      name: 'Jane Doe',
      email: 'jane@example.com',
      message: 'This is a read-only message.',
    },
    onSubmit: fn((data) => console.log('Should not submit:', data)),
    submitText: 'Submit',
  },
};

// ============================================================================
// Story 9: Form with Custom Styling
// ============================================================================

/**
 * Form with custom CSS classes.
 * Demonstrates styling customization.
 */
export const CustomStyling: Story = {
  args: {
    fields: contactFields,
    onSubmit: fn((data) => console.log('Custom styled:', data)),
    submitText: 'Send',
    submitButtonClassName:
      'flex-1 px-6 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 focus:ring-green-500 font-semibold',
    cancelButtonClassName:
      'flex-1 px-6 py-3 bg-red-500 text-white rounded-lg hover:bg-red-600 focus:ring-red-500 font-semibold',
    formClassName: 'space-y-6 p-6 bg-gray-50 rounded-lg shadow-lg',
    onCancel: fn(() => console.log('Cancelled')),
  },
};

// ============================================================================
// Story 10: Async Submission
// ============================================================================

/**
 * Form with async submission showing loading state.
 * Demonstrates disableOnSubmit behavior.
 */
export const AsyncSubmission: Story = {
  args: {
    fields: contactFields,
    onSubmit: fn(async (data) => {
      console.log('Submitting...', data);
      await new Promise((resolve) => setTimeout(resolve, 2000));
      console.log('Submitted successfully!');
    }),
    submitText: 'Submit',
    disableOnSubmit: true,
  },
};
