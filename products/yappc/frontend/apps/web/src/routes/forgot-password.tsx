/**
 * Forgot Password Route
 *
 * Allows users to request a password-reset email.
 * Calls AuthService.forgotPassword() which posts to
 * POST /api/auth/forgot-password.
 *
 * @doc.type route
 * @doc.purpose Password reset request form
 * @doc.layer routing
 */

import React from 'react';
import { Link } from 'react-router';
import { Mail, ArrowLeft, CheckCircle } from 'lucide-react';
import { authService } from '../services/auth/AuthService';
import { RouteErrorBoundary } from '../components/route/ErrorBoundary';

// =============================================================================
// Component
// =============================================================================

/**
 * Forgot-password page — collects user email and triggers reset flow.
 */
export default function Component() {
  const [email, setEmail] = React.useState('');
  const [emailError, setEmailError] = React.useState<string | null>(null);
  const [isLoading, setIsLoading] = React.useState(false);
  const [serverError, setServerError] = React.useState<string | null>(null);
  const [submitted, setSubmitted] = React.useState(false);

  const validate = (): boolean => {
    if (!email.trim()) {
      setEmailError('Email is required');
      return false;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailError('Enter a valid email address');
      return false;
    }
    setEmailError(null);
    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);

    if (!validate()) return;

    setIsLoading(true);
    try {
      const result = await authService.forgotPassword(email);
      if (result.success) {
        setSubmitted(true);
      } else {
        setServerError(result.error ?? 'Failed to send reset email');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg-default flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-text-primary tracking-tight">YAPPC</h1>
          <p className="mt-2 text-text-secondary">Reset your password</p>
        </div>

        {/* Card */}
        <div className="bg-bg-paper rounded-xl border border-divider p-6 shadow-sm">
          {submitted ? (
            /* Success state */
            <div
              data-testid="forgot-password-success"
              className="text-center py-4"
            >
              <CheckCircle className="w-12 h-12 text-primary-600 mx-auto mb-4" aria-hidden="true" />
              <h2 className="text-lg font-semibold text-text-primary mb-2">Check your inbox</h2>
              <p className="text-sm text-text-secondary mb-6">
                If an account exists for <strong>{email}</strong>, we sent a
                password-reset link. Check your spam folder if you don't see it.
              </p>
              <Link
                to="/login"
                className="inline-flex items-center gap-2 text-sm font-medium text-primary-600 hover:text-primary-700 transition-colors"
              >
                <ArrowLeft className="w-4 h-4" aria-hidden="true" />
                Back to login
              </Link>
            </div>
          ) : (
            /* Form state */
            <>
              <p className="text-sm text-text-secondary mb-6">
                Enter the email address associated with your account. We'll send you
                a link to reset your password.
              </p>

              {serverError && (
                <div
                  data-testid="forgot-password-error"
                  role="alert"
                  className="mb-6 p-4 rounded-lg bg-error-color/10 border border-error-color/30 text-error-color text-sm"
                >
                  {serverError}
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-5" noValidate>
                <div>
                  <label htmlFor="email" className="block text-sm font-medium text-text-primary mb-1.5">
                    Email address
                  </label>
                  <div className="relative">
                    <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary pointer-events-none">
                      <Mail className="w-4 h-4" aria-hidden="true" />
                    </div>
                    <input
                      id="email"
                      name="email"
                      type="email"
                      required
                      autoComplete="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      aria-describedby={emailError ? 'email-error' : undefined}
                      aria-invalid={emailError ? 'true' : undefined}
                      className="w-full pl-9 pr-4 py-2.5 rounded-lg border border-divider bg-bg-surface text-text-primary focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-all outline-none"
                      placeholder="you@example.com"
                    />
                  </div>
                  {emailError && (
                    <p id="email-error" role="alert" className="mt-1 text-xs text-error-color">
                      {emailError}
                    </p>
                  )}
                </div>

                <button
                  type="submit"
                  disabled={isLoading}
                  className="w-full inline-flex items-center justify-center gap-2 px-5 py-3.5 bg-primary-600 hover:bg-primary-700 text-white rounded-lg font-semibold transition-all shadow-md hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 cursor-pointer border-none disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isLoading ? 'Sending…' : 'Send reset link'}
                </button>
              </form>

              <div className="mt-6 pt-6 border-t border-divider text-center">
                <Link
                  to="/login"
                  className="inline-flex items-center gap-1.5 text-sm font-medium text-primary-600 hover:text-primary-700 transition-colors"
                >
                  <ArrowLeft className="w-4 h-4" aria-hidden="true" />
                  Back to login
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export function ErrorBoundary() {
  return (
    <RouteErrorBoundary
      title="Password Reset Error"
      message="Unable to load the password reset page. Please try again."
    />
  );
}
