/**
 * ForgotPassword route — archived (not mounted in active router).
 *
 * @doc.type component
 * @doc.purpose Forgot password form for archived use
 * @doc.layer frontend
 */
import React, { useState, FormEvent } from 'react';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { authService } from '../../services/auth/AuthService';

interface FormErrors {
  email?: string;
}

function validate(email: string): FormErrors {
  const errors: FormErrors = {};
  if (!email.trim()) {
    errors.email = 'Email is required';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.email = 'Enter a valid email address';
  }
  return errors;
}

export default function ForgotPasswordComponent(): React.ReactElement {
  const [email, setEmail] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent<HTMLFormElement>): Promise<void> {
    e.preventDefault();
    const fieldErrors = validate(email);
    if (Object.keys(fieldErrors).length > 0) {
      setErrors(fieldErrors);
      return;
    }
    setErrors({});
    setIsSubmitting(true);
    try {
      const result = await authService.forgotPassword(email);
      if (!result.success && result.error) {
        setServerError(result.error);
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div>
      <h1>Reset your password</h1>
      <form onSubmit={(e) => void handleSubmit(e)}>
        <div>
          <label htmlFor="forgot-email">Email address</label>
          <Input
            id="forgot-email"
            aria-label="Email address"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          {errors.email && <span>{errors.email}</span>}
        </div>
        {serverError && (
          <div data-testid="forgot-password-error">{serverError}</div>
        )}
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Sending…' : 'Send reset link'}
        </Button>
      </form>
      <a href="/login">Back to login</a>
    </div>
  );
}
