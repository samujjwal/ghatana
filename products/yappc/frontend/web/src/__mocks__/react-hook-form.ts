/**
 * Minimal react-hook-form mock for the web app test environment.
 * Only used in tests that load components with react-hook-form (PropertyForm.tsx).
 */
import React from 'react';

export const useForm = () => ({
  register: () => ({}),
  handleSubmit: (fn: (data: unknown) => void) => (e: React.BaseSyntheticEvent) => {
    e?.preventDefault?.();
    fn({});
  },
  formState: { errors: {} },
  reset: () => {},
  watch: () => undefined,
  setValue: () => {},
  getValues: () => ({}),
  control: {},
});

export const Controller = ({
  render,
}: {
  render: (props: { field: Record<string, unknown> }) => React.ReactElement;
}) => render({ field: {} });
