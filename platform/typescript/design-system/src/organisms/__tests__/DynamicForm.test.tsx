import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DynamicForm, type FieldConfig } from '../DynamicForm';

/**
 * Unit tests for Form component.
 *
 * Tests validate:
 * - Field rendering (text, number, select, textarea, time)
 * - Validation (required, custom validators)
 * - Conditional visibility
 * - Data transformation
 * - Submit handling
 * - Cancel handling
 * - Error display
 * - Edit mode with initial data
 * - Accessibility (ARIA attributes)
 *
 * @see Form
 */
describe('DynamicForm Component', () => {
  interface TestFormData {
    name: string;
    email: string;
    age: number;
    type: 'personal' | 'business';
    companyName?: string;
    message?: string;
  }

  /**
   * Verifies basic form rendering with text and email fields.
   *
   * GIVEN: Simple form with name and email fields
   * WHEN: Component renders
   * THEN: All fields and buttons are visible
   */
  it('should render basic form fields', () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name', required: true },
      { name: 'email', type: 'email', label: 'Email', required: true },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Email/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Submit/ })).toBeInTheDocument();
  });

  /**
   * Verifies required field indicators are displayed.
   *
   * GIVEN: Form with required fields
   * WHEN: Component renders
   * THEN: Required asterisks are visible
   */
  it('should display required field indicators', () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name', required: true },
      { name: 'message', type: 'textarea', label: 'Message', required: false },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    // Required field has asterisk
    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('*')).toBeInTheDocument();
  });

  /**
   * Verifies select field rendering with options.
   *
   * GIVEN: Form with select field
   * WHEN: Component renders
   * THEN: Select element with options is visible
   */
  it('should render select field with options', () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
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
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const select = screen.getByLabelText(/Type/) as HTMLSelectElement;
    expect(select).toBeInTheDocument();
    expect(select.options).toHaveLength(2);
    expect(select.options[0].textContent).toBe('Personal');
    expect(select.options[1].textContent).toBe('Business');
  });

  /**
   * Verifies textarea field rendering.
   *
   * GIVEN: Form with textarea field
   * WHEN: Component renders
   * THEN: Textarea element is visible with correct rows
   */
  it('should render textarea field', () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'message', type: 'textarea', label: 'Message', rows: 5 },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const textarea = screen.getByLabelText(/Message/) as HTMLTextAreaElement;
    expect(textarea).toBeInTheDocument();
    expect(textarea.rows).toBe(5);
  });

  /**
   * Verifies number field rendering.
   *
   * GIVEN: Form with number field
   * WHEN: Component renders
   * THEN: Number input is visible with min/max attributes
   */
  it('should render number field with min/max', () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'age', type: 'number', label: 'Age', min: 18, max: 100, step: 1 },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const input = screen.getByLabelText(/Age/) as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(input.type).toBe('number');
    expect(input.min).toBe('18');
    expect(input.max).toBe('100');
    expect(input.step).toBe('1');
  });

  /**
   * Verifies required field validation on submit.
   *
   * GIVEN: Form with required name field
   * WHEN: Submit clicked with empty field
   * THEN: Error message displayed, submit not called
   */
  it('should validate required fields on submit', async () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name', required: true },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const submitButton = screen.getByRole('button', { name: /Submit/ });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Name is required')).toBeInTheDocument();
    });

    expect(mockSubmit).not.toHaveBeenCalled();
  });

  /**
   * Verifies custom validation function.
   *
   * GIVEN: Field with custom validation
   * WHEN: Invalid value entered and submitted
   * THEN: Custom error message displayed
   */
  it('should execute custom validation', async () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      {
        name: 'email',
        type: 'email',
        label: 'Email',
        validation: (value) => {
          if (!value?.includes('@')) {
            return 'Invalid email format';
          }
        },
      },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const emailInput = screen.getByLabelText(/Email/);
    await userEvent.type(emailInput, 'invalid-email');

    const submitButton = screen.getByRole('button', { name: /Submit/ });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Invalid email format')).toBeInTheDocument();
    });

    expect(mockSubmit).not.toHaveBeenCalled();
  });

  /**
   * Verifies conditional field visibility.
   *
   * GIVEN: Field with visible condition
   * WHEN: Condition becomes true
   * THEN: Field appears
   */
  it('should show/hide fields based on visibility condition', async () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      {
        name: 'type',
        type: 'select',
        label: 'Type',
        options: [
          { label: 'Personal', value: 'personal' },
          { label: 'Business', value: 'business' },
        ],
      },
      {
        name: 'companyName',
        type: 'text',
        label: 'Company Name',
        visible: (data) => data.type === 'business',
      },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    // Initially should not show company name (default is personal)
    expect(screen.queryByLabelText(/Company Name/)).not.toBeInTheDocument();

    // Change type to business
    const typeSelect = screen.getByLabelText(/Type/);
    await userEvent.selectOptions(typeSelect, 'business');

    // Now company name should be visible
    await waitFor(() => {
      expect(screen.getByLabelText(/Company Name/)).toBeInTheDocument();
    });
  });

  /**
   * Verifies field validation on blur.
   *
   * GIVEN: Required field
   * WHEN: Field focused then blurred empty
   * THEN: Error message displayed
   */
  it('should validate field on blur', async () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name', required: true },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const nameInput = screen.getByLabelText(/Name/);
    
    // Focus then blur
    fireEvent.focus(nameInput);
    fireEvent.blur(nameInput);

    await waitFor(() => {
      expect(screen.getByText('Name is required')).toBeInTheDocument();
    });
  });

  /**
   * Verifies successful form submission with valid data.
   *
   * GIVEN: Form with valid data
   * WHEN: Submit clicked
   * THEN: onSubmit called with form data
   */
  it('should call onSubmit with form data when valid', async () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name', required: true },
      { name: 'email', type: 'email', label: 'Email', required: true },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const nameInput = screen.getByLabelText(/Name/);
    const emailInput = screen.getByLabelText(/Email/);

    await userEvent.type(nameInput, 'John Doe');
    await userEvent.type(emailInput, 'john@example.com');

    const submitButton = screen.getByRole('button', { name: /Submit/ });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(mockSubmit).toHaveBeenCalledWith({
        name: 'John Doe',
        email: 'john@example.com',
      });
    });
  });

  /**
   * Verifies data transformation from form to submit.
   *
   * GIVEN: Field with transform.fromForm
   * WHEN: Form submitted
   * THEN: Transformed value passed to onSubmit
   */
  it('should transform data on submit', async () => {
    interface FormWithArray {
      tags: string[];
    }

    const mockSubmit = vi.fn();
    const fields: FieldConfig<FormWithArray>[] = [
      {
        name: 'tags',
        type: 'text',
        label: 'Tags',
        placeholder: 'Comma-separated',
        transform: {
          toForm: (value: string[]) => value?.join(', ') || '',
          fromForm: (value: string) =>
            value.split(',').map((s) => s.trim()).filter(Boolean),
        },
      },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const tagsInput = screen.getByLabelText(/Tags/);
    await userEvent.type(tagsInput, 'react, typescript, testing');

    const submitButton = screen.getByRole('button', { name: /Submit/ });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(mockSubmit).toHaveBeenCalledWith({
        tags: ['react', 'typescript', 'testing'],
      });
    });
  });

  /**
   * Verifies edit mode with initial data.
   *
   * GIVEN: Form with initialData
   * WHEN: Component renders
   * THEN: Fields pre-populated with initial values
   */
  it('should populate fields with initial data', () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name' },
      { name: 'email', type: 'email', label: 'Email' },
    ];

    const initialData: Partial<TestFormData> = {
      name: 'Jane Doe',
      email: 'jane@example.com',
    };

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} initialData={initialData} />);

    const nameInput = screen.getByLabelText(/Name/) as HTMLInputElement;
    const emailInput = screen.getByLabelText(/Email/) as HTMLInputElement;

    expect(nameInput.value).toBe('Jane Doe');
    expect(emailInput.value).toBe('jane@example.com');
  });

  /**
   * Verifies initial data transformation using toForm.
   *
   * GIVEN: Initial data with array value and toForm transformer
   * WHEN: Component renders
   * THEN: Array transformed to comma-separated string
   */
  it('should transform initial data with toForm', () => {
    interface FormWithArray {
      tags: string[];
    }

    const mockSubmit = vi.fn();
    const fields: FieldConfig<FormWithArray>[] = [
      {
        name: 'tags',
        type: 'text',
        label: 'Tags',
        transform: {
          toForm: (value: string[]) => value?.join(', ') || '',
          fromForm: (value: string) =>
            value.split(',').map((s) => s.trim()).filter(Boolean),
        },
      },
    ];

    const initialData: Partial<FormWithArray> = {
      tags: ['react', 'typescript', 'testing'],
    };

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} initialData={initialData} />);

    const tagsInput = screen.getByLabelText(/Tags/) as HTMLInputElement;
    expect(tagsInput.value).toBe('react, typescript, testing');
  });

  /**
   * Verifies cancel button functionality.
   *
   * GIVEN: Form with onCancel callback
   * WHEN: Cancel button clicked
   * THEN: onCancel called
   */
  it('should call onCancel when cancel button clicked', async () => {
    const mockSubmit = vi.fn();
    const mockCancel = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name' },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} onCancel={mockCancel} />);

    const cancelButton = screen.getByRole('button', { name: /Cancel/ });
    fireEvent.click(cancelButton);

    expect(mockCancel).toHaveBeenCalled();
    expect(mockSubmit).not.toHaveBeenCalled();
  });

  /**
   * Verifies form disables during submission.
   *
   * GIVEN: Form with async onSubmit
   * WHEN: Submit clicked
   * THEN: Submit button disabled and shows "Submitting..."
   */
  it('should disable form during submission', async () => {
    const mockSubmit = vi.fn(() => new Promise((resolve) => setTimeout(resolve, 100)));
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name', required: true },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const nameInput = screen.getByLabelText(/Name/);
    await userEvent.type(nameInput, 'Test');

    const submitButton = screen.getByRole('button', { name: /Submit/ });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Submitting...')).toBeInTheDocument();
      expect(submitButton).toBeDisabled();
    });
  });

  /**
   * Verifies help text display.
   *
   * GIVEN: Field with helpText
   * WHEN: Component renders
   * THEN: Help text visible below field
   */
  it('should display help text', () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      {
        name: 'email',
        type: 'email',
        label: 'Email',
        helpText: 'We will never share your email',
      },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    expect(screen.getByText('We will never share your email')).toBeInTheDocument();
  });

  /**
   * Verifies error clears help text.
   *
   * GIVEN: Field with helpText and validation error
   * WHEN: Validation error appears
   * THEN: Help text replaced by error message
   */
  it('should replace help text with error when validation fails', async () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      {
        name: 'name',
        type: 'text',
        label: 'Name',
        required: true,
        helpText: 'Enter your full name',
      },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    // Help text visible initially
    expect(screen.getByText('Enter your full name')).toBeInTheDocument();

    // Trigger validation
    const nameInput = screen.getByLabelText(/Name/);
    fireEvent.focus(nameInput);
    fireEvent.blur(nameInput);

    await waitFor(() => {
      expect(screen.getByText('Name is required')).toBeInTheDocument();
      expect(screen.queryByText('Enter your full name')).not.toBeInTheDocument();
    });
  });

  /**
   * Verifies ARIA attributes for accessibility.
   *
   * GIVEN: Form with required field
   * WHEN: Component renders
   * THEN: Proper ARIA attributes present
   */
  it('should have proper ARIA attributes', () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name', required: true },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const nameInput = screen.getByLabelText(/Name/);
    expect(nameInput).toHaveAttribute('aria-required', 'true');
    expect(nameInput).toHaveAttribute('aria-invalid', 'false');
  });

  /**
   * Verifies ARIA attributes update on validation error.
   *
   * GIVEN: Field with validation error
   * WHEN: Error appears
   * THEN: aria-invalid set to true, aria-describedby references error
   */
  it('should update ARIA attributes on validation error', async () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name', required: true },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const nameInput = screen.getByLabelText(/Name/);
    
    // Trigger validation
    fireEvent.focus(nameInput);
    fireEvent.blur(nameInput);

    await waitFor(() => {
      expect(nameInput).toHaveAttribute('aria-invalid', 'true');
      expect(nameInput).toHaveAttribute('aria-describedby');
    });
  });

  /**
   * Verifies custom CSS classes applied.
   *
   * GIVEN: Form with custom className
   * WHEN: Component renders
   * THEN: Custom classes applied to form element
   */
  it('should apply custom CSS classes', () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      { name: 'name', type: 'text', label: 'Name' },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} formClassName="custom-form" />);

    const form = screen.getByRole('button', { name: /Submit/ }).closest('form');
    expect(form).toHaveClass('custom-form');
  });

  /**
   * Verifies conditional field disabled state.
   *
   * GIVEN: Field with disabled condition
   * WHEN: Condition becomes true
   * THEN: Field is disabled
   */
  it('should disable field based on condition', async () => {
    const mockSubmit = vi.fn();
    const fields: FieldConfig<TestFormData>[] = [
      {
        name: 'type',
        type: 'select',
        label: 'Type',
        options: [
          { label: 'Personal', value: 'personal' },
          { label: 'Business', value: 'business' },
        ],
      },
      {
        name: 'companyName',
        type: 'text',
        label: 'Company Name',
        disabled: (data) => data.type === 'personal',
      },
    ];

    render(<DynamicForm fields={fields} onSubmit={mockSubmit} />);

    const companyInput = screen.getByLabelText(/Company Name/) as HTMLInputElement;
    
    // Should be enabled initially (no type selected)
    expect(companyInput).not.toBeDisabled();

    // Change to personal - should disable
    const typeSelect = screen.getByLabelText(/Type/);
    await userEvent.selectOptions(typeSelect, 'personal');

    await waitFor(() => {
      expect(companyInput).toBeDisabled();
    });
  });
});
