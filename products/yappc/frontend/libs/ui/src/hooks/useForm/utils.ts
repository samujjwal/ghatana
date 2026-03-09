/* eslint-disable security/detect-object-injection -- guarded access; callers validate keys where required */
import type { ValidationRules } from '../../utils/validation';

/**
 * Mark all keys in the values object as touched
 */
export function markAllTouched(values: Record<string, unknown>) {
  return Object.keys(values).reduce(
    (acc, key) => ({ ...acc, [key]: true }),
    {} as Record<string, boolean>
  );
}

/**
 * Small helper to validate a single field when needed. Creates a wrapper around
 * validateField to adapt its signature for use with different validation configurations.
 */
export function runFieldValidationIfNeeded(
  name: string,
  values: Record<string, unknown>,
  validationRules: ValidationRules | Record<string, unknown>,
  setErrors: (
    updater: (prev: Record<string, string>) => Record<string, string>
  ) => void,
  shouldValidate = true,
  validateField: (value: unknown, rule: unknown) => string | undefined
) {
  if (!shouldValidate) return;
  if (!Object.prototype.hasOwnProperty.call(validationRules, name)) return;
  const maybeRule = (validationRules as Record<string, unknown>)[name];
  if (maybeRule == null) return;
  const isCallable =
    typeof maybeRule === 'function' || Array.isArray(maybeRule);
  if (!isCallable) return;
  const error = validateField(values[name], maybeRule as unknown);
  setErrors((prev) => ({ ...prev, [name]: error || '' }));
}
