import { renderHook, act, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { useFormValidation, validators } from '../useFormValidation';

type LoginForm = {
  email: string;
  password: string;
  confirmPassword: string;
};

describe('useFormValidation', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('initializes values and field state for all fields', () => {
    const { result } = renderHook(() =>
      useFormValidation<LoginForm>({
        schema: {},
        initialValues: { email: '', password: '', confirmPassword: '' },
      })
    );

    expect(result.current.values).toEqual({ email: '', password: '', confirmPassword: '' });
    expect(result.current.fieldStates.email.error).toBeNull();
    expect(result.current.fieldStates.email.isValid).toBe(true);
    expect(result.current.isValid).toBe(true);
  });

  it('validates on blur by default', async () => {
    const { result } = renderHook(() =>
      useFormValidation<LoginForm>({
        schema: { email: [validators.required('Email required')] },
        initialValues: { email: '', password: '', confirmPassword: '' },
      })
    );

    await act(async () => {
      result.current.touchField('email');
    });

    await waitFor(() => {
      expect(result.current.fieldStates.email.touched).toBe(true);
      expect(result.current.fieldStates.email.error).toBe('Email required');
      expect(result.current.isValid).toBe(false);
    });
  });

  it('validates using the updated value on change', async () => {
    const { result } = renderHook(() =>
      useFormValidation<LoginForm>({
        schema: { email: [validators.email('Invalid email')] },
        initialValues: { email: '', password: '', confirmPassword: '' },
        validateOnChange: true,
      })
    );

    await act(async () => {
      result.current.setValue('email', 'valid@example.com');
    });

    await waitFor(() => {
      expect(result.current.values.email).toBe('valid@example.com');
      expect(result.current.fieldStates.email.error).toBeNull();
      expect(result.current.fieldStates.email.isValid).toBe(true);
    });
  });

  it('supports async validation rules and exposes validating state', async () => {
    let resolveValidation: ((isValid: boolean) => void) | undefined;
    const rule = validators.custom<string>(
      async () => {
        return new Promise<boolean>((resolve) => {
          resolveValidation = resolve;
        });
      },
      'Email already taken',
      true
    );

    const { result } = renderHook(() =>
      useFormValidation<LoginForm>({
        schema: { email: [rule] },
        initialValues: { email: '', password: '', confirmPassword: '' },
        validateOnChange: false,
      })
    );

    act(() => {
      result.current.setValue('email', 'taken@example.com');
    });

    let validationPromise: Promise<boolean>;
    act(() => {
      validationPromise = result.current.validateField('email');
    });

    expect(result.current.fieldStates.email.validating).toBe(true);

    act(() => {
      resolveValidation?.(false);
    });

    await act(async () => {
      await validationPromise;
    });

    expect(result.current.fieldStates.email.validating).toBe(false);
    expect(result.current.fieldStates.email.error).toBe('Email already taken');
  });

  it('submits only when all fields are valid', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() =>
      useFormValidation<LoginForm>({
        schema: {
          email: [validators.required('Email required'), validators.email()],
          password: [validators.minLength(8)],
          confirmPassword: [validators.match('password', 'Passwords must match')],
        },
        initialValues: {
          email: 'valid@example.com',
          password: 'strong-pass',
          confirmPassword: 'strong-pass',
        },
      })
    );

    await act(async () => {
      await result.current.handleSubmit(onSubmit)();
    });

    expect(result.current.isSubmitted).toBe(true);
    expect(onSubmit).toHaveBeenCalledWith({
      email: 'valid@example.com',
      password: 'strong-pass',
      confirmPassword: 'strong-pass',
    });
  });

  it('does not submit invalid forms and can clear/reset errors', async () => {
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useFormValidation<LoginForm>({
        schema: {
          email: [validators.required('Email required')],
          password: [validators.required('Password required')],
        },
        initialValues: { email: '', password: '', confirmPassword: '' },
      })
    );

    await act(async () => {
      await result.current.handleSubmit(onSubmit)();
    });

    expect(onSubmit).not.toHaveBeenCalled();
    expect(result.current.fieldStates.email.error).toBe('Email required');
    expect(result.current.fieldStates.password.error).toBe('Password required');

    await act(async () => {
      result.current.clearErrors();
    });
    expect(result.current.fieldStates.email.error).toBeNull();
    expect(result.current.fieldStates.password.error).toBeNull();

    await act(async () => {
      result.current.setValue('email', 'reset@example.com');
      result.current.reset();
    });

    expect(result.current.values.email).toBe('');
    expect(result.current.isSubmitted).toBe(false);
    expect(result.current.fieldStates.email.error).toBeNull();
  });
});