/**
 * Register Form Component
 * 
 * Production-grade registration form with validation,
 * password strength indicator, and error handling.
 * 
 * @module ui/components/Auth
 * @doc.type component
 * @doc.purpose User registration form
 * @doc.layer ui
 */

import { useState, type FormEvent } from 'react';
import { useAuth } from '@ghatana/yappc-canvas';

// ============================================================================
// Types
// ============================================================================

/**
 * Register form props
 */
export interface RegisterFormProps {
  /** Callback on successful registration */
  onSuccess?: () => void;
  
  /** Callback on registration error */
  onError?: (error: Error) => void;
  
  /** Whether to show terms and conditions checkbox */
  showTerms?: boolean;
  
  /** Whether to show sign in link */
  showSignIn?: boolean;
  
  /** Redirect URL after registration */
  redirectTo?: string;
  
  /** Custom submit button text */
  submitText?: string;
  
  /** API endpoint override */
  registerEndpoint?: string;
  
  /** Minimum password length */
  minPasswordLength?: number;
}

/**
 * Register form data
 */
export interface RegisterFormData {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
  acceptTerms: boolean;
}

/**
 * Password strength level
 */
type PasswordStrength = 'weak' | 'fair' | 'good' | 'strong';

// ============================================================================
// Register Form Component
// ============================================================================

/**
 * Register form component
 * 
 * @example
 * <RegisterForm
 *   onSuccess={() => navigate('/onboarding')}
 *   showTerms
 *   minPasswordLength={8}
 * />
 */
export function RegisterForm({
  onSuccess,
  onError,
  showTerms = true,
  showSignIn = true,
  redirectTo = '/onboarding',
  submitText = 'Create Account',
  registerEndpoint = '/api/auth/register',
  minPasswordLength = 8,
}: RegisterFormProps): React.JSX.Element {
  const { login, isLoading, error: authError } = useAuth();
  
  // Form state
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  
  // Validation errors
  const [errors, setErrors] = useState<Partial<Record<keyof RegisterFormData, string>>>({});
  
  /**
   * Calculate password strength
   */
  const getPasswordStrength = (pwd: string): PasswordStrength => {
    if (pwd.length < minPasswordLength) return 'weak';
    
    let strength = 0;
    if (pwd.length >= 12) strength++;
    if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) strength++;
    if (/\d/.test(pwd)) strength++;
    if (/[!@#$%^&*(),.?":{}|<>]/.test(pwd)) strength++;
    
    if (strength === 0) return 'weak';
    if (strength === 1) return 'fair';
    if (strength === 2) return 'good';
    return 'strong';
  };
  
  const passwordStrength = password ? getPasswordStrength(password) : null;
  
  /**
   * Validate form data
   */
  const validate = (): boolean => {
    const newErrors: Partial<Record<keyof RegisterFormData, string>> = {};
    
    // Name validation
    if (!name) {
      newErrors.name = 'Name is required';
    } else if (name.length < 2) {
      newErrors.name = 'Name must be at least 2 characters';
    }
    
    // Email validation
    if (!email) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = 'Invalid email format';
    }
    
    // Password validation
    if (!password) {
      newErrors.password = 'Password is required';
    } else if (password.length < minPasswordLength) {
      newErrors.password = `Password must be at least ${minPasswordLength} characters`;
    } else if (getPasswordStrength(password) === 'weak') {
      newErrors.password = 'Password is too weak. Add numbers, symbols, or make it longer.';
    }
    
    // Confirm password validation
    if (!confirmPassword) {
      newErrors.confirmPassword = 'Please confirm your password';
    } else if (password !== confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }
    
    // Terms validation
    if (showTerms && !acceptTerms) {
      newErrors.acceptTerms = 'You must accept the terms and conditions';
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
      const response = await fetch(registerEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ name, email, password }),
      });
      
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || 'Registration failed');
      }
      
      const data = await response.json();
      
      // Auto-login after registration
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
      const err = error instanceof Error ? error : new Error('Registration failed');
      
      // Call error callback
      if (onError) {
        onError(err);
      }
    }
  };
  
  return (
    <form
      onSubmit={handleSubmit}
      className="register-form"
      noValidate
      aria-label="Registration form"
    >
      {/* Name Field */}
      <div className="form-group">
        <label htmlFor="name" className="form-label">
          Full Name
        </label>
        <input
          id="name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={isLoading}
          required
          autoComplete="name"
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'name-error' : undefined}
          className={`form-input ${errors.name ? 'form-input-error' : ''}`}
          placeholder="John Doe"
        />
        {errors.name && (
          <p id="name-error" className="form-error" role="alert">
            {errors.name}
          </p>
        )}
      </div>
      
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
        <label htmlFor="password" className="form-label">
          Password
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
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? 'password-error password-strength' : 'password-strength'}
            className={`form-input ${errors.password ? 'form-input-error' : ''}`}
            placeholder="Create a strong password"
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
        
        {/* Password Strength Indicator */}
        {passwordStrength && (
          <div id="password-strength" className="password-strength" aria-live="polite">
            <div className={`strength-bar strength-${passwordStrength}`}>
              <div className="strength-fill" />
            </div>
            <span className="strength-text">
              Password strength: <strong>{passwordStrength}</strong>
            </span>
          </div>
        )}
        
        {errors.password && (
          <p id="password-error" className="form-error" role="alert">
            {errors.password}
          </p>
        )}
      </div>
      
      {/* Confirm Password Field */}
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
          placeholder="Re-enter your password"
        />
        {errors.confirmPassword && (
          <p id="confirm-password-error" className="form-error" role="alert">
            {errors.confirmPassword}
          </p>
        )}
      </div>
      
      {/* Terms and Conditions */}
      {showTerms && (
        <div className="form-group">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={acceptTerms}
              onChange={(e) => setAcceptTerms(e.target.checked)}
              disabled={isLoading}
              required
              aria-invalid={!!errors.acceptTerms}
              aria-describedby={errors.acceptTerms ? 'terms-error' : undefined}
              className="form-checkbox"
            />
            <span>
              I accept the{' '}
              <a href="/terms" target="_blank" rel="noopener noreferrer">
                Terms of Service
              </a>{' '}
              and{' '}
              <a href="/privacy" target="_blank" rel="noopener noreferrer">
                Privacy Policy
              </a>
            </span>
          </label>
          {errors.acceptTerms && (
            <p id="terms-error" className="form-error" role="alert">
              {errors.acceptTerms}
            </p>
          )}
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
        {isLoading ? 'Creating account...' : submitText}
      </button>
      
      {/* Sign In Link */}
      {showSignIn && (
        <p className="form-footer">
          Already have an account?{' '}
          <a href="/login" className="form-link">
            Sign in
          </a>
        </p>
      )}
    </form>
  );
}
