import { useState, useCallback, useEffect } from 'react';
import { useForm as useReactHookForm, FieldValues } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';

interface FormOptions<T extends FieldValues> {
  validationSchema?: yup.ObjectSchema<any>;
  defaultValues?: Partial<T>;
  onSubmit: (data: T) => Promise<void> | void;
  onSuccess?: (data: T) => void;
  onError?: (error: Error) => void;
  mode?: 'onChange' | 'onBlur' | 'onSubmit' | 'onTouched' | 'all';
}

interface FormState<T> {
  isSubmitting: boolean;
  isSubmitted: boolean;
  isDirty: boolean;
  isValid: boolean;
  submitCount: number;
  errors: Record<string, { message?: string }>;
  values: T;
  reset: (values?: T) => void;
  handleSubmit: (e?: React.BaseSyntheticEvent) => Promise<void>;
  setValue: (name: string, value: unknown, config?: { shouldValidate?: boolean; shouldDirty?: boolean }) => void;
  setError: (name: string, error: { type: string; message: string }) => void;
  clearErrors: (name?: string | string[]) => void;
  trigger: (name?: string | string[]) => Promise<boolean>;
}

export default function useForm<T extends FieldValues>({
  validationSchema,
  defaultValues,
  onSubmit,
  onSuccess,
  onError,
  ...formOptions
}: FormOptions<T>): FormState<T> {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [submitCount, setSubmitCount] = useState(0);

  const {
    handleSubmit: rhfHandleSubmit,
    formState: { isDirty, isValid, errors },
    reset: rhfReset,
    setValue: rhfSetValue,
    setError: rhfSetError,
    clearErrors: rhfClearErrors,
    trigger: rhfTrigger,
    watch,
    getValues: _getValues,
  } = useReactHookForm<T>({
    resolver: validationSchema ? yupResolver(validationSchema) as any : undefined,
    defaultValues: defaultValues as any,
    mode: formOptions.mode || 'onChange', // Validate on change for better UX
  });

  // Reset form when defaultValues changes
  useEffect(() => {
    if (defaultValues) {
      rhfReset(defaultValues as any);
    }
  }, [defaultValues, rhfReset]);

  const handleFormSubmit = useCallback(
    async (data: T) => {
      try {
        setIsSubmitting(true);
        await onSubmit(data);
        setIsSubmitted(true);
        setSubmitCount((prev) => prev + 1);
        onSuccess?.(data);
      } catch (error) {
        console.error('Form submission error:', error);
        onError?.(error as Error);
      } finally {
        setIsSubmitting(false);
      }
    },
    [onSubmit, onSuccess, onError]
  );

  const reset = useCallback(
    (values?: T) => {
      rhfReset(values as any);
      setIsSubmitted(false);
      setSubmitCount(0);
    },
    [rhfReset]
  );

  // mark _getValues as used to avoid lint warning in this wrapper
  void _getValues;

  const setValue = useCallback(
    (name: string, value: unknown, config?: { shouldValidate?: boolean; shouldDirty?: boolean }) => {
      rhfSetValue(name as any, value as any, config);
    },
    [rhfSetValue]
  );

  const setError = useCallback(
    (name: string, error: { type: string; message: string }) => {
      rhfSetError(name as any, error);
    },
    [rhfSetError]
  );

  const clearErrors = useCallback(
    (name?: string | string[]) => {
      rhfClearErrors(name as any);
    },
    [rhfClearErrors]
  );

  const trigger = useCallback(
    (name?: string | string[]) => {
      return rhfTrigger(name as any);
    },
    [rhfTrigger]
  );

  return {
    isSubmitting,
    isSubmitted,
    isDirty,
    isValid,
    submitCount,
    errors: errors as Record<string, { message?: string }>,
    values: watch(),
    reset,
    handleSubmit: rhfHandleSubmit(handleFormSubmit) as any,
    setValue,
    setError,
    clearErrors,
    trigger,
  };
}
