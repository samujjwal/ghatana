/**
 * Register route — archived (not mounted in active router).
 *
 * @doc.type component
 * @doc.purpose Registration form for archived use
 * @doc.layer frontend
 */
import React, { useState, FormEvent } from 'react';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { authService } from '../../services/auth/AuthService';

interface FormFields {
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  password: string;
}

interface FormErrors {
  firstName?: string;
  lastName?: string;
  username?: string;
  email?: string;
  password?: string;
}

function validate(fields: FormFields): FormErrors {
  const errors: FormErrors = {};
  if (!fields.firstName.trim()) errors.firstName = 'First name is required';
  if (!fields.lastName.trim()) errors.lastName = 'Last name is required';
  if (!fields.username.trim()) {
    errors.username = 'Username is required';
  } else if (fields.username.trim().length < 3) {
    errors.username = 'Username must be at least 3 characters';
  }
  if (!fields.email.trim()) {
    errors.email = 'Email is required';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email)) {
    errors.email = 'Enter a valid email address';
  }
  if (!fields.password) errors.password = 'Password is required';
  return errors;
}

export default function RegisterComponent(): React.ReactElement {
  const [fields, setFields] = useState<FormFields>({
    firstName: '',
    lastName: '',
    username: '',
    email: '',
    password: '',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleChange(e: React.ChangeEvent<HTMLInputElement>): void {
    const { name, value } = e.target;
    setFields((prev) => ({ ...prev, [name]: value }));
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>): Promise<void> {
    e.preventDefault();
    const fieldErrors = validate(fields);
    if (Object.keys(fieldErrors).length > 0) {
      setErrors(fieldErrors);
      return;
    }
    setErrors({});
    setIsSubmitting(true);
    try {
      const result = await authService.register({
        firstName: fields.firstName,
        lastName: fields.lastName,
        username: fields.username,
        email: fields.email,
        password: fields.password,
      });
      if (!result.success && result.error) {
        setServerError(result.error);
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div>
      <h1>Create your account</h1>
      <form onSubmit={(e) => void handleSubmit(e)}>
        <div>
          <label htmlFor="register-firstName">First name</label>
          <Input id="register-firstName" aria-label="First name" name="firstName" value={fields.firstName} onChange={handleChange} />
          {errors.firstName && <span>{errors.firstName}</span>}
        </div>
        <div>
          <label htmlFor="register-lastName">Last name</label>
          <Input id="register-lastName" aria-label="Last name" name="lastName" value={fields.lastName} onChange={handleChange} />
          {errors.lastName && <span>{errors.lastName}</span>}
        </div>
        <div>
          <label htmlFor="register-username">Username</label>
          <Input id="register-username" aria-label="Username" name="username" value={fields.username} onChange={handleChange} />
          {errors.username && <span>{errors.username}</span>}
        </div>
        <div>
          <label htmlFor="register-email">Email</label>
          <Input id="register-email" aria-label="Email" name="email" type="email" value={fields.email} onChange={handleChange} />
          {errors.email && <span>{errors.email}</span>}
        </div>
        <div>
          <label htmlFor="register-password">Password</label>
          <Input id="register-password" aria-label="Password" name="password" type="password" value={fields.password} onChange={handleChange} />
          {errors.password && <span>{errors.password}</span>}
        </div>
        {serverError && (
          <div data-testid="register-error">{serverError}</div>
        )}
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
      <a href="/login">Sign in</a>
    </div>
  );
}
