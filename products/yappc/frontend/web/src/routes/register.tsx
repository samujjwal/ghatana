/**
 * Register Route
 *
 * New-user sign-up page. Calls AuthService.register() which posts to
 * POST /api/auth/register and auto-logs in on success.
 *
 * @doc.type route
 * @doc.purpose User registration / sign-up
 * @doc.layer routing
 */

import React from 'react';
import { Link, useNavigate } from 'react-router';
import { User, Mail, Lock, ArrowRight } from 'lucide-react';
import { authService, type RegisterData } from '../services/auth/AuthService';
import { RouteErrorBoundary } from '../components/route/ErrorBoundary';

// =============================================================================
// Helpers
// =============================================================================

function FieldError({ message }: { message: string }) {
  return (
    <p role="alert" className="mt-1 text-xs text-error-color">
      {message}
    </p>
  );
}

// =============================================================================
// Component
// =============================================================================

/**
 * Registration page — full sign-up form.
 */
export default function Component() {
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = React.useState(false);
  const [serverError, setServerError] = React.useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = React.useState<Partial<Record<keyof RegisterData, string>>>({});

  const validate = (data: RegisterData): boolean => {
    const errs: Partial<Record<keyof RegisterData, string>> = {};

    if (!data.username.trim()) errs.username = 'Username is required';
    else if (data.username.length < 3) errs.username = 'Username must be at least 3 characters';

    if (!data.email.trim()) errs.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) errs.email = 'Enter a valid email address';

    if (!data.firstName.trim()) errs.firstName = 'First name is required';
    if (!data.lastName.trim()) errs.lastName = 'Last name is required';

    if (!data.password) errs.password = 'Password is required';
    else if (data.password.length < 8) errs.password = 'Password must be at least 8 characters';

    setFieldErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setServerError(null);

    const form = e.currentTarget;
    const data: RegisterData = {
      username: (form.elements.namedItem('username') as HTMLInputElement).value,
      email: (form.elements.namedItem('email') as HTMLInputElement).value,
      firstName: (form.elements.namedItem('firstName') as HTMLInputElement).value,
      lastName: (form.elements.namedItem('lastName') as HTMLInputElement).value,
      password: (form.elements.namedItem('password') as HTMLInputElement).value,
    };

    if (!validate(data)) return;

    setIsLoading(true);
    try {
      const result = await authService.register(data);
      if (result.success) {
        navigate('/app/workspaces');
      } else {
        setServerError(result.error ?? 'Registration failed');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg-default flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-text-primary tracking-tight">YAPPC</h1>
          <p className="mt-2 text-text-secondary">Create your account</p>
        </div>

        {/* Card */}
        <div className="bg-bg-paper rounded-xl border border-divider p-6 shadow-sm">
          {serverError && (
            <div
              data-testid="register-error"
              role="alert"
              className="mb-6 p-4 rounded-lg bg-error-color/10 border border-error-color/30 text-error-color text-sm"
            >
              {serverError}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5" noValidate>
            {/* Name row */}
            <div className="flex gap-3">
              <div className="flex-1">
                <label htmlFor="firstName" className="block text-sm font-medium text-text-primary mb-1.5">
                  First name
                </label>
                <input
                  id="firstName"
                  name="firstName"
                  type="text"
                  required
                  autoComplete="given-name"
                  aria-describedby={fieldErrors.firstName ? 'firstName-error' : undefined}
                  className="w-full px-3 py-2.5 rounded-lg border border-divider bg-bg-surface text-text-primary focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-all outline-none"
                  placeholder="First"
                />
                {fieldErrors.firstName && (
                  <FieldError message={fieldErrors.firstName} />
                )}
              </div>
              <div className="flex-1">
                <label htmlFor="lastName" className="block text-sm font-medium text-text-primary mb-1.5">
                  Last name
                </label>
                <input
                  id="lastName"
                  name="lastName"
                  type="text"
                  required
                  autoComplete="family-name"
                  className="w-full px-3 py-2.5 rounded-lg border border-divider bg-bg-surface text-text-primary focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-all outline-none"
                  placeholder="Last"
                />
                {fieldErrors.lastName && (
                  <FieldError message={fieldErrors.lastName} />
                )}
              </div>
            </div>

            {/* Username */}
            <div>
              <label htmlFor="username" className="block text-sm font-medium text-text-primary mb-1.5">
                Username
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary pointer-events-none">
                  <User className="w-4 h-4" />
                </div>
                <input
                  id="username"
                  name="username"
                  type="text"
                  required
                  autoComplete="username"
                  className="w-full pl-9 pr-4 py-2.5 rounded-lg border border-divider bg-bg-surface text-text-primary focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-all outline-none"
                  placeholder="Choose a username"
                />
              </div>
              {fieldErrors.username && <FieldError message={fieldErrors.username} />}
            </div>

            {/* Email */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-text-primary mb-1.5">
                Email
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary pointer-events-none">
                  <Mail className="w-4 h-4" />
                </div>
                <input
                  id="email"
                  name="email"
                  type="email"
                  required
                  autoComplete="email"
                  className="w-full pl-9 pr-4 py-2.5 rounded-lg border border-divider bg-bg-surface text-text-primary focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-all outline-none"
                  placeholder="you@example.com"
                />
              </div>
              {fieldErrors.email && <FieldError message={fieldErrors.email} />}
            </div>

            {/* Password */}
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-text-primary mb-1.5">
                Password
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary pointer-events-none">
                  <Lock className="w-4 h-4" />
                </div>
                <input
                  id="password"
                  name="password"
                  type="password"
                  required
                  autoComplete="new-password"
                  className="w-full pl-9 pr-4 py-2.5 rounded-lg border border-divider bg-bg-surface text-text-primary focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-all outline-none"
                  placeholder="At least 8 characters"
                />
              </div>
              {fieldErrors.password && <FieldError message={fieldErrors.password} />}
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="w-full inline-flex items-center justify-center gap-2 px-5 py-3.5 bg-primary-600 hover:bg-primary-700 text-white rounded-lg font-semibold transition-all shadow-md hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 cursor-pointer border-none disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoading ? (
                <span>Creating account…</span>
              ) : (
                <>
                  <span>Create account</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          <div className="mt-6 pt-6 border-t border-divider text-center text-sm text-text-secondary">
            Already have an account?{' '}
            <Link to="/login" className="font-medium text-primary-600 hover:text-primary-700 transition-colors">
              Sign in
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

export function ErrorBoundary() {
  return (
    <RouteErrorBoundary
      title="Registration Error"
      message="Unable to load the registration page. Please check your connection and try again."
    />
  );
}
