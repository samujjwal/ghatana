import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

import { Form, useForm } from './Form';
import { FormInput } from './FormInput';

// Mock the FormField component to simplify tests
vi.mock('./FormField', () => ({
  FormField: ({ name, children }: { 
    name: string; 
    children: (props: unknown) => React.ReactNode 
  }) => {
    return children({
      value: '',
      onChange: vi.fn(),
      onBlur: vi.fn(),
      error: undefined,
      touched: false,
      name,
    });
  },
}));

// Mock the Input component with proper label association
vi.mock('../Input/Input', () => ({
  Input: ({ label, name, ...props }: unknown) => (
    <div>
      {label && <label htmlFor={name}>{label}</label>}
      <input id={name} name={name} {...props} />
    </div>
  ),
}));

describe('Form', () => {
  it('handles async form submission', async () => {
    const handleSubmit = vi.fn().mockImplementation(() => {
      return new Promise((resolve) => {
        setTimeout(() => {
          resolve(undefined);
        }, 10);
      });
    });

    function TestForm() {
      const { isSubmitting } = useForm();
      return (
        <>
          <FormInput name="name" label="Name" />
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Submitting...' : 'Submit'}
          </button>
        </>
      );
    }

    render(
      <Form initialValues={{ name: 'Test' }} onSubmit={handleSubmit}>
        <TestForm />
      </Form>
    );

    const submitButton = screen.getByRole('button', { name: /submit/i });
    
    // Initial state - button should be enabled
    expect(submitButton).not.toBeDisabled();
    
    // Click the button to submit the form
    fireEvent.click(submitButton);
    
    // Button should be disabled during submission
    expect(submitButton).toBeDisabled();
    expect(submitButton).toHaveTextContent('Submitting...');

    // Wait for the submission to complete and check the button state
    await waitFor(() => {
      expect(handleSubmit).toHaveBeenCalledWith({ name: 'Test' });
      // Check that the button is re-enabled after submission
      expect(submitButton).not.toBeDisabled();
      expect(submitButton).toHaveTextContent('Submit');
    });
  });
});
