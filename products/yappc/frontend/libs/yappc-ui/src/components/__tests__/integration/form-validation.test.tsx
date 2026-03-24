/**
 * Integration Tests: Form + Validation
 *
 * Tests the integration between useForm hook and validation system,
 * ensuring forms work seamlessly with validation rules and error handling.
 */

import { renderHook, act } from '@testing-library/react';

import { useForm } from '../../hooks/useForm';
import { validators } from '../../utils/validation';

describe.skip('Form + Validation Integration', () => {
  describe('Field-Level Validation', () => {
    it('should validate fields on change when validateOnChange is enabled', () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { email: '', password: '' },
          validationRules: {
            email: [validators.required(), validators.email()],
            password: [validators.required(), validators.minLength(8)],
          },
          onSubmit: jest.fn(),
          validateOnChange: true,
        })
      );

      // Initially no errors
      expect(result.current.errors).toEqual({});

      // Invalid email
      act(() => {
        result.current.setFieldValue('email', 'invalid');
      });

      // Should show email error
      expect(result.current.errors.email).toBe('Please enter a valid email address');

      // Valid email
      act(() => {
        result.current.setFieldValue('email', 'test@example.com');
      });

      // Error should be cleared
      expect(result.current.errors.email).toBeFalsy();
    });

    it('should validate fields on blur when validateOnBlur is enabled', () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { email: '', password: '' },
          validationRules: {
            email: [validators.required(), validators.email()],
            password: [validators.required()],
          },
          onSubmit: jest.fn(),
          validateOnBlur: true,
          validateOnChange: false,
        })
      );

      // Set invalid email without triggering blur
      act(() => {
        result.current.setFieldValue('email', 'invalid');
      });

      // Should not show error yet (validateOnChange is false)
      expect(result.current.errors.email).toBeFalsy();

      // Simulate blur event
      act(() => {
        const mockEvent = {
          target: { name: 'email', value: 'invalid' },
        } as unknown;
        result.current.handleBlur(mockEvent);
      });

      // Now should show error
      expect(result.current.errors.email).toBe('Please enter a valid email address');
      expect(result.current.touched.email).toBe(true);
    });
  });

  describe('Form-Level Validation', () => {
    it('should validate entire form on submit', async () => {
      const onSubmit = jest.fn();

      const { result } = renderHook(() =>
        useForm({
          initialValues: { email: '', password: '', confirmPassword: '' },
          validationRules: {
            email: [validators.required(), validators.email()],
            password: [validators.required(), validators.minLength(8)],
            confirmPassword: [
              validators.required(),
              validators.match('password', (name) => result.current.values.password),
            ],
          },
          onSubmit,
        })
      );

      // Submit empty form
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      // Should not call onSubmit
      expect(onSubmit).not.toHaveBeenCalled();

      // Should show all errors
      expect(result.current.errors.email).toBe('This field is required');
      expect(result.current.errors.password).toBe('This field is required');
      expect(result.current.errors.confirmPassword).toBe('This field is required');

      // All fields should be marked as touched
      expect(result.current.touched.email).toBe(true);
      expect(result.current.touched.password).toBe(true);
      expect(result.current.touched.confirmPassword).toBe(true);

      // Fill form with valid data
      act(() => {
        result.current.setFieldValue('email', 'test@example.com');
        result.current.setFieldValue('password', 'password123');
        result.current.setFieldValue('confirmPassword', 'password123');
      });

      // Submit again
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      // Should call onSubmit with correct values
      expect(onSubmit).toHaveBeenCalledWith({
        email: 'test@example.com',
        password: 'password123',
        confirmPassword: 'password123',
      });
    });

    it('should handle async validation in onSubmit', async () => {
      const onSubmit = jest.fn().mockImplementation(async (values) => {
        // Simulate async operation
        await new Promise((resolve) => setTimeout(resolve, 100));

        // Simulate server-side validation error
        if (values.email === 'taken@example.com') {
          throw new Error('Email already taken');
        }
      });

      const { result } = renderHook(() =>
        useForm({
          initialValues: { email: '' },
          validationRules: {
            email: [validators.required(), validators.email()],
          },
          onSubmit,
        })
      );

      act(() => {
        result.current.setFieldValue('email', 'taken@example.com');
      });

      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      expect(onSubmit).toHaveBeenCalled();
      // Form should handle the error (logged to console by useForm)
    });
  });

  describe('Complex Validation Rules', () => {
    it('should handle multiple validation rules per field', () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { username: '' },
          validationRules: {
            username: [
              validators.required('Username is required'),
              validators.minLength(3, 'Username must be at least 3 characters'),
              validators.maxLength(20, 'Username must be at most 20 characters'),
              validators.pattern(/^[a-zA-Z0-9_]+$/, 'Username can only contain letters, numbers, and underscores'),
            ],
          },
          onSubmit: jest.fn(),
          validateOnChange: true,
        })
      );

      // Test required validation
      act(() => {
        result.current.setFieldValue('username', '');
      });
      expect(result.current.errors.username).toBe('Username is required');

      // Test minLength validation
      act(() => {
        result.current.setFieldValue('username', 'ab');
      });
      expect(result.current.errors.username).toBe('Username must be at least 3 characters');

      // Test maxLength validation
      act(() => {
        result.current.setFieldValue('username', 'a'.repeat(21));
      });
      expect(result.current.errors.username).toBe('Username must be at most 20 characters');

      // Test pattern validation
      act(() => {
        result.current.setFieldValue('username', 'invalid-username!');
      });
      expect(result.current.errors.username).toBe('Username can only contain letters, numbers, and underscores');

      // Test valid username
      act(() => {
        result.current.setFieldValue('username', 'valid_username123');
      });
      expect(result.current.errors.username).toBeFalsy();
    });

    it('should validate cross-field dependencies', () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { password: '', confirmPassword: '' },
          validationRules: {
            password: [validators.required(), validators.minLength(8)],
            confirmPassword: [
              validators.required(),
              validators.match('password', (name) => result.current.values.password, 'Passwords must match'),
            ],
          },
          onSubmit: jest.fn(),
          validateOnChange: true,
        })
      );

      // Set password
      act(() => {
        result.current.setFieldValue('password', 'password123');
      });

      // Set non-matching confirm password
      act(() => {
        result.current.setFieldValue('confirmPassword', 'different');
      });

      expect(result.current.errors.confirmPassword).toBe('Passwords must match');

      // Set matching confirm password
      act(() => {
        result.current.setFieldValue('confirmPassword', 'password123');
      });

      expect(result.current.errors.confirmPassword).toBeFalsy();
    });
  });

  describe('Custom Validation', () => {
    it('should support custom validation functions', () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { age: 0 },
          validationRules: {
            age: [
              validators.required(),
              validators.custom(
                (value) => value >= 18,
                'You must be at least 18 years old'
              ),
              validators.custom(
                (value) => value <= 120,
                'Age must be realistic'
              ),
            ],
          },
          onSubmit: jest.fn(),
          validateOnChange: true,
        })
      );

      // Test minimum age
      act(() => {
        result.current.setFieldValue('age', 15);
      });
      expect(result.current.errors.age).toBe('You must be at least 18 years old');

      // Test maximum age
      act(() => {
        result.current.setFieldValue('age', 150);
      });
      expect(result.current.errors.age).toBe('Age must be realistic');

      // Test valid age
      act(() => {
        result.current.setFieldValue('age', 25);
      });
      expect(result.current.errors.age).toBeFalsy();
    });
  });

  describe('Form State Management', () => {
    it('should properly manage form state through the lifecycle', async () => {
      const onSubmit = jest.fn();

      const { result } = renderHook(() =>
        useForm({
          initialValues: { email: 'initial@example.com' },
          validationRules: {
            email: [validators.required(), validators.email()],
          },
          onSubmit,
        })
      );

      // Initial state
      expect(result.current.values.email).toBe('initial@example.com');
      expect(result.current.isSubmitting).toBe(false);
      expect(result.current.isValid).toBe(true);

      // Modify field
      act(() => {
        result.current.setFieldValue('email', 'updated@example.com');
      });

      expect(result.current.values.email).toBe('updated@example.com');

      // Submit form
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      expect(onSubmit).toHaveBeenCalledWith({ email: 'updated@example.com' });

      // Reset form
      act(() => {
        result.current.resetForm();
      });

      expect(result.current.values.email).toBe('initial@example.com');
      expect(result.current.errors).toEqual({});
      expect(result.current.touched).toEqual({});
      expect(result.current.isSubmitting).toBe(false);
    });

    it('should handle form errors properly', () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { email: '' },
          validationRules: {
            email: [validators.required(), validators.email()],
          },
          onSubmit: jest.fn(),
        })
      );

      // Set custom error
      act(() => {
        result.current.setFieldError('email', 'Custom error message');
      });

      expect(result.current.errors.email).toBe('Custom error message');
      expect(result.current.isValid).toBe(false);

      // Clear error by setting valid value
      act(() => {
        result.current.setFieldValue('email', 'valid@example.com');
        result.current.setFieldError('email', '');
      });

      expect(result.current.errors.email).toBe('');
      expect(result.current.isValid).toBe(true);
    });
  });

  describe('Touched State Management', () => {
    it('should track touched fields correctly', () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { field1: '', field2: '', field3: '' },
          validationRules: {},
          onSubmit: jest.fn(),
        })
      );

      // Initially no fields are touched
      expect(result.current.touched).toEqual({});

      // Mark field as touched
      act(() => {
        result.current.setFieldTouched('field1', true);
      });

      expect(result.current.touched.field1).toBe(true);
      expect(result.current.touched.field2).toBeFalsy();

      // Simulate blur on multiple fields
      act(() => {
        const event1 = { target: { name: 'field2' } } as unknown;
        result.current.handleBlur(event1);

        const event2 = { target: { name: 'field3' } } as unknown;
        result.current.handleBlur(event2);
      });

      expect(result.current.touched.field2).toBe(true);
      expect(result.current.touched.field3).toBe(true);
    });

    it('should show errors only for touched fields', () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { email: '', password: '' },
          validationRules: {
            email: [validators.required()],
            password: [validators.required()],
          },
          onSubmit: jest.fn(),
          validateOnChange: true,
        })
      );

      // Set invalid values
      act(() => {
        result.current.setFieldValue('email', '');
        result.current.setFieldValue('password', '');
      });

      // Errors exist but fields not touched
      expect(result.current.errors.email).toBe('This field is required');
      expect(result.current.errors.password).toBe('This field is required');
      expect(result.current.touched.email).toBeFalsy();
      expect(result.current.touched.password).toBeFalsy();

      // Touch only email field
      act(() => {
        result.current.setFieldTouched('email', true);
      });

      // Only show error for touched field (UI pattern)
      const shouldShowEmailError = result.current.touched.email && result.current.errors.email;
      const shouldShowPasswordError = result.current.touched.password && result.current.errors.password;

      expect(shouldShowEmailError).toBeTruthy();
      expect(shouldShowPasswordError).toBeFalsy();
    });
  });

  describe('Edge Cases', () => {
    it('should handle validation with empty rules', () => {
      const onSubmit = jest.fn();

      const { result } = renderHook(() =>
        useForm({
          initialValues: { field: 'value' },
          validationRules: {},
          onSubmit,
        })
      );

      expect(result.current.isValid).toBe(true);

      // Should allow submission with no validation
      act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        result.current.handleSubmit(mockEvent);
      });

      expect(onSubmit).toHaveBeenCalledWith({ field: 'value' });
    });

    it('should handle checkbox fields', () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { agree: false },
          validationRules: {
            agree: [validators.custom((value) => value === true, 'You must agree to the terms')],
          },
          onSubmit: jest.fn(),
          validateOnChange: true,
        })
      );

      // Simulate checkbox change
      act(() => {
        const mockEvent = {
          target: { name: 'agree', type: 'checkbox', checked: false },
        } as unknown;
        result.current.handleChange(mockEvent);
      });

      expect(result.current.values.agree).toBe(false);
      expect(result.current.errors.agree).toBe('You must agree to the terms');

      // Check the checkbox
      act(() => {
        const mockEvent = {
          target: { name: 'agree', type: 'checkbox', checked: true },
        } as unknown;
        result.current.handleChange(mockEvent);
      });

      expect(result.current.values.agree).toBe(true);
      expect(result.current.errors.agree).toBeFalsy();
    });
  });
});
