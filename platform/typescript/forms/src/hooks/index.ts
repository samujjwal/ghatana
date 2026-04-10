import { useState, useCallback } from 'react';
import type { ChangeEvent } from 'react';

export interface FieldState<T> {
  value: T;
  error?: string;
  touched: boolean;
}

/**
 * @doc.type hook
 * @doc.purpose Simple uncontrolled field state hook for individual inputs.
 * @doc.layer platform
 * @doc.pattern Custom Hook
 */
export function useField<T>(initialValue: T) {
  const [state, setState] = useState<FieldState<T>>({
    value: initialValue,
    error: undefined,
    touched: false,
  });

  const onChange = useCallback((value: T) => {
    setState((prev) => ({ ...prev, value, touched: true }));
  }, []);

  const setError = useCallback((error: string | undefined) => {
    setState((prev) => ({ ...prev, error }));
  }, []);

  const onInputChange = useCallback((e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const raw = e.target.value;
    setState((prev) => ({ ...prev, value: raw as unknown as T, touched: true }));
  }, []);

  return { ...state, onChange, onInputChange, setError };
}

export interface FormState {
  isSubmitting: boolean;
  isSuccess: boolean;
  error?: string;
}

/**
 * @doc.type hook
 * @doc.purpose Tracks async form submission state.
 * @doc.layer platform
 * @doc.pattern Custom Hook
 */
export function useFormState() {
  const [state, setState] = useState<FormState>({
    isSubmitting: false,
    isSuccess: false,
    error: undefined,
  });

  const startSubmit = useCallback(() => {
    setState({ isSubmitting: true, isSuccess: false, error: undefined });
  }, []);

  const onSuccess = useCallback(() => {
    setState({ isSubmitting: false, isSuccess: true, error: undefined });
  }, []);

  const onError = useCallback((error: string) => {
    setState({ isSubmitting: false, isSuccess: false, error });
  }, []);

  const reset = useCallback(() => {
    setState({ isSubmitting: false, isSuccess: false, error: undefined });
  }, []);

  return { ...state, startSubmit, onSuccess, onError, reset };
}
