/**
 * Zod-based Form Validation Schemas
 * 
 * Production-grade validation schemas using Zod for type-safe form validation
 * 
 * @module ui/utils/zodValidation
 * @doc.type utility
 * @doc.purpose Type-safe form validation with Zod
 * @doc.layer ui
 */

import { z } from 'zod';

// ============================================================================
// Common Validation Schemas
// ============================================================================

/**
 * Email validation schema
 * - Must be valid email format
 * - Case-insensitive
 * - Trimmed whitespace
 */
export const emailSchema = z
  .string()
  .trim()
  .toLowerCase()
  .email('Please enter a valid email address')
  .min(5, 'Email must be at least 5 characters')
  .max(255, 'Email must not exceed 255 characters');

/**
 * Password validation schema
 * - Minimum 8 characters (configurable)
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one number
 * - At least one special character
 */
export const passwordSchema = (minLength = 8) =>
  z
    .string()
    .min(minLength, `Password must be at least ${minLength} characters`)
    .max(128, 'Password must not exceed 128 characters')
    .regex(
      /[a-z]/,
      'Password must contain at least one lowercase letter'
    )
    .regex(
      /[A-Z]/,
      'Password must contain at least one uppercase letter'
    )
    .regex(/[0-9]/, 'Password must contain at least one number')
    .regex(
      /[^a-zA-Z0-9]/,
      'Password must contain at least one special character'
    );

/**
 * Simple password schema (less strict, for basic validation)
 * - Minimum length only
 */
export const simplePasswordSchema = (minLength = 8) =>
  z
    .string()
    .min(minLength, `Password must be at least ${minLength} characters`)
    .max(128, 'Password must not exceed 128 characters');

/**
 * Name validation schema
 * - 2-100 characters
 * - Letters, spaces, hyphens, apostrophes only
 * - Trimmed whitespace
 */
export const nameSchema = z
  .string()
  .trim()
  .min(2, 'Name must be at least 2 characters')
  .max(100, 'Name must not exceed 100 characters')
  .regex(
    /^[a-zA-Z\s'-]+$/,
    'Name can only contain letters, spaces, hyphens, and apostrophes'
  );

/**
 * Username validation schema
 * - 3-30 characters
 * - Alphanumeric, underscores, hyphens only
 * - Must start with letter or number
 * - Case-insensitive
 */
export const usernameSchema = z
  .string()
  .trim()
  .toLowerCase()
  .min(3, 'Username must be at least 3 characters')
  .max(30, 'Username must not exceed 30 characters')
  .regex(
    /^[a-z0-9][a-z0-9_-]*$/,
    'Username must start with a letter or number and can only contain letters, numbers, underscores, and hyphens'
  );

/**
 * Phone number validation schema (international format)
 * - Optional country code
 * - 10-15 digits
 * - Allows spaces, hyphens, parentheses
 */
export const phoneSchema = z
  .string()
  .trim()
  .regex(
    /^\+?[1-9]\d{0,3}[-.\s]?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{1,9}$/,
    'Please enter a valid phone number'
  )
  .min(10, 'Phone number must be at least 10 digits')
  .max(20, 'Phone number must not exceed 20 characters');

/**
 * URL validation schema
 * - Must be valid URL format
 * - HTTP/HTTPS protocols
 */
export const urlSchema = z
  .string()
  .trim()
  .url('Please enter a valid URL')
  .refine(
    (url) => url.startsWith('http://') || url.startsWith('https://'),
    'URL must start with http:// or https://'
  );

/**
 * Date validation schema
 * - Must be valid ISO date string
 * - Optional min/max date constraints
 */
export const dateSchema = (options?: {
  min?: Date;
  max?: Date;
  message?: string;
}) => {
  let schema = z.string().datetime(options?.message ?? 'Please enter a valid date');

  if (options?.min) {
    schema = schema.refine(
      (date) => new Date(date) >= options.min!,
      `Date must be on or after ${options.min.toLocaleDateString()}`
    );
  }

  if (options?.max) {
    schema = schema.refine(
      (date) => new Date(date) <= options.max!,
      `Date must be on or before ${options.max.toLocaleDateString()}`
    );
  }

  return schema;
};

// ============================================================================
// Authentication Validation Schemas
// ============================================================================

/**
 * Login form validation schema
 */
export const loginSchema = z.object({
  email: emailSchema,
  password: simplePasswordSchema(6), // Less strict for login
  rememberMe: z.boolean().optional(),
});

export type LoginFormData = z.infer<typeof loginSchema>;

/**
 * Registration form validation schema
 */
export const registerSchema = z
  .object({
    name: nameSchema,
    email: emailSchema,
    password: passwordSchema(8),
    confirmPassword: z.string(),
    terms: z.boolean().refine((val) => val === true, {
      message: 'You must accept the terms and conditions',
    }),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

export type RegisterFormData = z.infer<typeof registerSchema>;

/**
 * Password reset request validation schema
 */
export const passwordResetRequestSchema = z.object({
  email: emailSchema,
});

export type PasswordResetRequestFormData = z.infer<
  typeof passwordResetRequestSchema
>;

/**
 * Password reset confirmation validation schema
 */
export const passwordResetConfirmSchema = z
  .object({
    token: z.string().min(1, 'Reset token is required'),
    password: passwordSchema(8),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

export type PasswordResetConfirmFormData = z.infer<
  typeof passwordResetConfirmSchema
>;

/**
 * Change password validation schema
 */
export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: passwordSchema(8),
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword !== data.currentPassword, {
    message: 'New password must be different from current password',
    path: ['newPassword'],
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

export type ChangePasswordFormData = z.infer<typeof changePasswordSchema>;

/**
 * Update profile validation schema
 */
export const updateProfileSchema = z.object({
  name: nameSchema,
  email: emailSchema,
  phone: phoneSchema.optional(),
  bio: z.string().max(500, 'Bio must not exceed 500 characters').optional(),
  website: urlSchema.optional(),
});

export type UpdateProfileFormData = z.infer<typeof updateProfileSchema>;

// ============================================================================
// Validation Helper Functions
// ============================================================================

/**
 * Validate data against a Zod schema
 * Returns validation result with typed data and errors
 */
export function validate<T extends z.ZodTypeAny>(
  schema: T,
  data: unknown
): {
  success: boolean;
  data?: z.infer<T>;
  errors?: Record<string, string[]>;
} {
  const result = schema.safeParse(data);

  if (result.success) {
    return {
      success: true,
      data: result.data,
    };
  }

  // Format Zod errors into field-level error messages
  const errors: Record<string, string[]> = {};
  result.error.issues.forEach((issue) => {
    const path = issue.path.join('.');
    if (!errors[path]) {
      errors[path] = [];
    }
    errors[path].push(issue.message);
  });

  return {
    success: false,
    errors,
  };
}

/**
 * Get the first error message for a field
 */
export function getFieldError(
  errors: Record<string, string[]> | undefined,
  field: string
): string | undefined {
  return errors?.[field]?.[0];
}

/**
 * Check if a field has any errors
 */
export function hasFieldError(
  errors: Record<string, string[]> | undefined,
  field: string
): boolean {
  return !!errors?.[field]?.length;
}

/**
 * Get all error messages as a flat array
 */
export function getAllErrors(
  errors: Record<string, string[]> | undefined
): string[] {
  if (!errors) return [];
  return Object.values(errors).flat();
}

/**
 * Password strength calculator
 * Returns a score from 0 (weakest) to 4 (strongest)
 */
export function calculatePasswordStrength(password: string): {
  score: number;
  feedback: string;
  level: 'weak' | 'fair' | 'good' | 'strong';
} {
  if (!password) {
    return { score: 0, feedback: 'Enter a password', level: 'weak' };
  }

  let score = 0;
  const feedback: string[] = [];

  // Length check
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  else if (password.length < 8) {
    feedback.push('Use at least 8 characters');
  }

  // Character variety checks
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) {
    score++;
  } else {
    feedback.push('Use both uppercase and lowercase letters');
  }

  if (/[0-9]/.test(password)) {
    score++;
  } else {
    feedback.push('Include at least one number');
  }

  if (/[^a-zA-Z0-9]/.test(password)) {
    score++;
  } else {
    feedback.push('Include at least one special character');
  }

  // Common patterns to avoid
  const commonPatterns = [
    /^[0-9]+$/,
    /^[a-zA-Z]+$/,
    /password/i,
    /123456/,
    /qwerty/i,
  ];
  if (commonPatterns.some((pattern) => pattern.test(password))) {
    score = Math.max(0, score - 1);
    feedback.push('Avoid common patterns');
  }

  // Determine level
  const levels: Array<'weak' | 'fair' | 'good' | 'strong'> = [
    'weak',
    'weak',
    'fair',
    'good',
    'strong',
  ];
  const level = levels[Math.min(score, 4)];

  // Map score to feedback
  const strengthLabels = ['Very Weak', 'Weak', 'Fair', 'Good', 'Strong'];
  const strengthLabel = strengthLabels[Math.min(score, 4)];

  return {
    score: Math.min(score, 4),
    level,
    feedback:
      feedback.length > 0 ? feedback.join('. ') : `Password is ${strengthLabel}`,
  };
}

/**
 * Email validation helper (for quick checks without Zod)
 */
export function isValidEmail(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
}

/**
 * Phone number formatting helper
 * Formats phone number to (XXX) XXX-XXXX format for US numbers
 */
export function formatPhoneNumber(phone: string): string {
  const cleaned = phone.replace(/\D/g, '');

  if (cleaned.length === 10) {
    return `(${cleaned.slice(0, 3)}) ${cleaned.slice(3, 6)}-${cleaned.slice(6)}`;
  }

  if (cleaned.length === 11 && cleaned[0] === '1') {
    return `+1 (${cleaned.slice(1, 4)}) ${cleaned.slice(4, 7)}-${cleaned.slice(7)}`;
  }

  return phone;
}

/**
 * Sanitize string input
 * Removes leading/trailing whitespace and collapses multiple spaces
 */
export function sanitizeString(input: string): string {
  return input.trim().replace(/\s+/g, ' ');
}
