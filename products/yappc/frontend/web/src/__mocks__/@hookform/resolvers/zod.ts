/**
 * Minimal @hookform/resolvers/zod mock for the web app test environment.
 * Only used in tests that load components with react-hook-form (PropertyForm.tsx).
 */
export const zodResolver = () => async (values: unknown) => ({
  values: values ?? {},
  errors: {},
});
