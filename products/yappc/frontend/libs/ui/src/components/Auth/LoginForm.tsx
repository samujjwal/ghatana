/**
 * Login Form Component
 * 
 * Production-grade login form with validation,
 * error handling, and accessibility.
 * 
 * @module ui/components/Auth
 * @doc.type component
 * @doc.purpose User authentication login form
 * @doc.layer ui
 */

import { useState, type FormEvent } from 'react';
import { useAuth } from '@ghatana/yappc-canvas';

// ============================================================================
// Types
// ============================================================================

/**
 * Login form props
 */
export interface LoginFormProps {
  /** Callback on successful login */
  onSuccess?: () => void;
  
  /** Callback on login error */
  onError?: (error: Error) => void;
  
  /** Whether to show remember me checkbox */
  showRememberMe?: boolean;
  
  /** Whether to show forgot password link */
  showForgotPassword?: boolean;
  
  /** Whether to show sign up link */
  showSignUp?: boolean;
  
  /** Redirect URL after login */
  redirectTo?: string;
  
  /** Custom submit button text */
  submitText?: string;
  
  /** API endpoint override */
  loginEndpoint?: string;
}

/**
 * Login form data
 */
export interface LoginFormData {
  email: string;
  password: string;
  rememberMe: boolean;
}

// ============================================================================
// Login Form Component
// ============================================================================

/**
 * Login form component
 * 
 * @example
 * <LoginForm
 *   onSuccess={() => navigate('/dashboard')}
 *   showRememberMe
 *   showForgotPassword
 * />
 */
export function LoginForm({
  onSuccess,
  onError,
  showRememberMe = true,
  showForgotPassword = true,
  showSignUp = true,
  redirectTo = '/dashboard',
  submitText = 'Sign In',
  loginEndpoint = '/api/auth/login',
}: LoginFormProps): React.JSX.Element {
  const { login, isLoading, error: authError } = useAuth();
  
  // Form state
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  
  // Validation errors
  const [errors, setErrors] = useState<Partial<Record<keyof LoginFormData, string>>>({});
  
  /**
   * Validate form data
   */
  const validate = (): boolean => {
    const newErrors: Partial<Record<keyof LoginFormData, string>> = {};
    
    // Email validation
    if (!email) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = 'Invalid email format';
    }
    
    // Password validation
    if (!password) {
      newErrors.password = 'Password is required';
    } else if (password.length < 8) {
      newErrors.password = 'Password must be at least 8 characters';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };
  
  /**
   * Handle form submission
   */
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    
    // Validate form
    if (!validate()) {
      return;
    }
    
    try {
      // Call backend API
      const response = await fetch(loginEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password, rememberMe }),
      });
      
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || 'Login failed');
      }
      
      const data = await response.json();
      
      // Update auth state
      login({
        user: data.user,
        token: data.accessToken,
        refreshToken: data.refreshToken,
        expiresIn: data.expiresIn,
      });
      
      // Call success callback
      if (onSuccess) {
        onSuccess();
      } else if (redirectTo) {
        window.location.href = redirectTo;
      }
    } catch (error) {
      const err = error instanceof Error ? error : new Error('Login failed');
      
      // Call error callback
      if (onError) {
        onError(err);
      }
    }
  };
  
  return (
    <form
      onSubmit={handleSubmit}
      className="login-form"
      noValidate
      aria-label="Login form"
    >
      {/* Email Field */}
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
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'email-error' : undefined}
          className={`form-input ${errors.email ? 'form-input-error' : ''}`}
          placeholder="you@example.com"
        />
        {errors.email && (
          <p id="email-error" className="form-error" role="alert">
            {errors.email}
          </p>
        )}
      </div>
      
      {/* Password Field */}
      <div className="form-group">
        <div className="form-label-row">
          <label htmlFor="password" className="form-label">
            Password
          </label>
          {showForgotPassword && (
            <a href="/forgot-password" className="form-link">
              Forgot password?
            </a>
          )}
        </div>
        <div className="password-input-wrapper">
          <input
            id="password"
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={isLoading}
            required
            autoComplete="current-password"
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? 'password-error' : undefined}
            className={`form-input ${errors.password ? 'form-input-error' : ''}`}
            placeholder="Enter your password"
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
      
      {/* Remember Me */}
      {showRememberMe && (
        <div className="form-group">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              disabled={isLoading}
              className="form-checkbox"
            />
            <span>Remember me for 30 days</span>
          </label>
        </div>
      )}
      
      {/* Error Message */}
      {authError && (
        <div className="alert alert-error" role="alert">
          {authError.message}
        </div>
      )}
      
      {/* Submit Button */}
      <button
        type="submit"
        disabled={isLoading}
        className="btn btn-primary btn-block"
        aria-busy={isLoading}
      >
        {isLoading ? 'Signing in...' : submitText}
      </button>
      
      {/* Sign Up Link */}
      {showSignUp && (
        <p className="form-footer">
          Don't have an account?{' '}
          <a href="/register" className="form-link">
            Sign up
          </a>
        </p>
      )}
    </form>
  );
}
