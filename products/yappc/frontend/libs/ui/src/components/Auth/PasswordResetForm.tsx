/**
 * Password Reset Form Component
 * 
 * Two-step password reset: request reset link, then set new password.
 * 
 * @module ui/components/Auth
 * @doc.type component
 * @doc.purpose Password reset flow
 * @doc.layer ui
 */

import { useState, type FormEvent } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Password reset request props
 */
export interface PasswordResetRequestProps {
  /** Callback on successful request */
  onSuccess?: () => void;
  
  /** Callback on error */
  onError?: (error: Error) => void;
  
  /** API endpoint override */
  requestEndpoint?: string;
  
  /** Show back to login link */
  showBackToLogin?: boolean;
}

/**
 * Password reset confirm props
 */
export interface PasswordResetConfirmProps {
  /** Reset token from URL */
  token: string;
  
  /** Callback on successful reset */
  onSuccess?: () => void;
  
  /** Callback on error */
  onError?: (error: Error) => void;
  
  /** API endpoint override */
  confirmEndpoint?: string;
  
  /** Minimum password length */
  minPasswordLength?: number;
}

// ============================================================================
// Password Reset Request Component
// ============================================================================

/**
 * Password reset request form
 * First step: Enter email to receive reset link
 * 
 * @example
 * <PasswordResetRequest
 *   onSuccess={() => setStep('check-email')}
 *   requestEndpoint="/api/auth/reset-password"
 * />
 */
export function PasswordResetRequest({
  onSuccess,
  onError,
  requestEndpoint = '/api/auth/reset-password',
  showBackToLogin = true,
}: PasswordResetRequestProps): React.JSX.Element {
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  
  /**
   * Handle form submission
   */
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    
    // Validate email
    if (!email) {
      setError('Email is required');
      return;
    }
    
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Invalid email format');
      return;
    }
    
    setIsLoading(true);
    
    try {
      const response = await fetch(requestEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email }),
      });
      
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || 'Failed to send reset email');
      }
      
      setSuccess(true);
      
      if (onSuccess) {
        onSuccess();
      }
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Failed to send reset email');
      setError(error.message);
      
      if (onError) {
        onError(error);
      }
    } finally {
      setIsLoading(false);
    }
  };
  
  if (success) {
    return (
      <div className="reset-success" role="status">
        <div className="success-icon">✉️</div>
        <h2 className="success-title">Check your email</h2>
        <p className="success-message">
          We've sent a password reset link to <strong>{email}</strong>.
          Please check your inbox and follow the instructions.
        </p>
        <p className="success-note">
          Didn't receive the email? Check your spam folder or{' '}
          <button
            type="button"
            onClick={() => setSuccess(false)}
            className="link-button"
          >
            try again
          </button>
          .
        </p>
        {showBackToLogin && (
          <a href="/login" className="btn btn-secondary">
            Back to Login
          </a>
        )}
      </div>
    );
  }
  
  return (
    <form
      onSubmit={handleSubmit}
      className="password-reset-form"
      noValidate
      aria-label="Password reset request form"
    >
      <h2 className="form-title">Reset your password</h2>
      <p className="form-description">
        Enter your email address and we'll send you a link to reset your password.
      </p>
      
      <div className="form-group">
        <label htmlFor="email" className="form-label">
          Email Address
        </label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={isLoading}
          required
          autoComplete="email"
          autoFocus
          className="form-input"
          placeholder="you@example.com"
        />
      </div>
      
      {error && (
        <div className="alert alert-error" role="alert">
          {error}
        </div>
      )}
      
      <button
        type="submit"
        disabled={isLoading}
        className="btn btn-primary btn-block"
        aria-busy={isLoading}
      >
        {isLoading ? 'Sending...' : 'Send Reset Link'}
      </button>
      
      {showBackToLogin && (
        <p className="form-footer">
          <a href="/login" className="form-link">
            ← Back to Login
          </a>
        </p>
      )}
    </form>
  );
}

// ============================================================================
// Password Reset Confirm Component
// ============================================================================

/**
 * Password reset confirm form
 * Second step: Set new password with reset token
 * 
 * @example
 * <PasswordResetConfirm
 *   token={searchParams.get('token')}
 *   onSuccess={() => navigate('/login')}
 * />
 */
export function PasswordResetConfirm({
  token,
  onSuccess,
  onError,
  confirmEndpoint = '/api/auth/reset-password/confirm',
  minPasswordLength = 8,
}: PasswordResetConfirmProps): React.JSX.Element {
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<{ password?: string; confirmPassword?: string }>({});
  
  /**
   * Validate form
   */
  const validate = (): boolean => {
    const newErrors: { password?: string; confirmPassword?: string } = {};
    
    if (!password) {
      newErrors.password = 'Password is required';
    } else if (password.length < minPasswordLength) {
      newErrors.password = `Password must be at least ${minPasswordLength} characters`;
    }
    
    if (!confirmPassword) {
      newErrors.confirmPassword = 'Please confirm your password';
    } else if (password !== confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };
  
  /**
   * Handle form submission
   */
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    
    if (!validate()) {
      return;
    }
    
    setIsLoading(true);
    
    try {
      const response = await fetch(confirmEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ token, password }),
      });
      
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || 'Failed to reset password');
      }
      
      if (onSuccess) {
        onSuccess();
      }
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Failed to reset password');
      setError(error.message);
      
      if (onError) {
        onError(error);
      }
    } finally {
      setIsLoading(false);
    }
  };
  
  return (
    <form
      onSubmit={handleSubmit}
      className="password-reset-confirm-form"
      noValidate
      aria-label="Set new password form"
    >
      <h2 className="form-title">Set new password</h2>
      <p className="form-description">
        Choose a strong password for your account.
      </p>
      
      <div className="form-group">
        <label htmlFor="password" className="form-label">
          New Password
        </label>
        <div className="password-input-wrapper">
          <input
            id="password"
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={isLoading}
            required
            autoComplete="new-password"
            autoFocus
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? 'password-error' : undefined}
            className={`form-input ${errors.password ? 'form-input-error' : ''}`}
            placeholder="Enter new password"
          />
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            className="password-toggle"
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            tabIndex={-1}
          >
            {showPassword ? '👁️' : '👁️‍🗨️'}
          </button>
        </div>
        {errors.password && (
          <p id="password-error" className="form-error" role="alert">
            {errors.password}
          </p>
        )}
      </div>
      
      <div className="form-group">
        <label htmlFor="confirmPassword" className="form-label">
          Confirm Password
        </label>
        <input
          id="confirmPassword"
          type={showPassword ? 'text' : 'password'}
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          disabled={isLoading}
          required
          autoComplete="new-password"
          aria-invalid={!!errors.confirmPassword}
          aria-describedby={errors.confirmPassword ? 'confirm-password-error' : undefined}
          className={`form-input ${errors.confirmPassword ? 'form-input-error' : ''}`}
          placeholder="Re-enter new password"
        />
        {errors.confirmPassword && (
          <p id="confirm-password-error" className="form-error" role="alert">
            {errors.confirmPassword}
          </p>
        )}
      </div>
      
      {error && (
        <div className="alert alert-error" role="alert">
          {error}
        </div>
      )}
      
      <button
        type="submit"
        disabled={isLoading}
        className="btn btn-primary btn-block"
        aria-busy={isLoading}
      >
        {isLoading ? 'Resetting...' : 'Reset Password'}
      </button>
      
      <p className="form-footer">
        <a href="/login" className="form-link">
          ← Back to Login
        </a>
      </p>
    </form>
  );
}
