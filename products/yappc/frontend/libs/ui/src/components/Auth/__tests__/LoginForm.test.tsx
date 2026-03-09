/**
 * LoginForm Component Tests
 * 
 * Unit tests for LoginForm component
 * 
 * @module ui/components/Auth/__tests__/LoginForm.test
 */

import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginForm } from '../LoginForm';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the useAuth hook
vi.mock('@ghatana/yappc-canvas', () => ({
  useAuth: () => ({
    login: vi.fn(),
    isLoading: false,
    error: null,
  }),
}));

describe('LoginForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render login form with all fields', () => {
      render(<LoginForm />);

      expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
    });

    it('should render remember me checkbox when enabled', () => {
      render(<LoginForm showRememberMe={true} />);

      expect(screen.getByLabelText(/remember me/i)).toBeInTheDocument();
    });

    it('should not render remember me checkbox when disabled', () => {
      render(<LoginForm showRememberMe={false} />);

      expect(screen.queryByLabelText(/remember me/i)).not.toBeInTheDocument();
    });

    it('should render forgot password link when enabled', () => {
      render(<LoginForm showForgotPassword={true} />);

      expect(screen.getByText(/forgot password/i)).toBeInTheDocument();
    });

    it('should render sign up link when enabled', () => {
      render(<LoginForm showSignUp={true} />);

      expect(screen.getByText(/sign up/i)).toBeInTheDocument();
    });

    it('should render custom submit text', () => {
      render(<LoginForm submitText="Log In" />);

      expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument();
    });
  });

  describe('Validation', () => {
    it('should show error for invalid email', async () => {
      const user = userEvent.setup();
      render(<LoginForm />);

      const emailInput = screen.getByLabelText(/email/i);
      await user.type(emailInput, 'invalid-email');
      await user.tab(); // Trigger blur

      await waitFor(() => {
        expect(screen.getByText(/valid email/i)).toBeInTheDocument();
      });
    });

    it('should show error for empty password', async () => {
      const user = userEvent.setup();
      render(<LoginForm />);

      const passwordInput = screen.getByLabelText(/password/i);
      await user.click(passwordInput);
      await user.tab(); // Trigger blur

      await waitFor(() => {
        expect(screen.getByText(/password is required/i)).toBeInTheDocument();
      });
    });

    it('should clear errors when valid input is entered', async () => {
      const user = userEvent.setup();
      render(<LoginForm />);

      const emailInput = screen.getByLabelText(/email/i);
      
      // Enter invalid email
      await user.type(emailInput, 'invalid');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/valid email/i)).toBeInTheDocument();
      });

      // Fix email
      await user.clear(emailInput);
      await user.type(emailInput, 'test@example.com');

      await waitFor(() => {
        expect(screen.queryByText(/valid email/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('Interactions', () => {
    it('should toggle password visibility', async () => {
      const user = userEvent.setup();
      render(<LoginForm />);

      const passwordInput = screen.getByLabelText(/password/i) as HTMLInputElement;
      const toggleButton = screen.getByRole('button', { name: /show password/i });

      expect(passwordInput.type).toBe('password');

      await user.click(toggleButton);

      expect(passwordInput.type).toBe('text');

      await user.click(toggleButton);

      expect(passwordInput.type).toBe('password');
    });

    it('should toggle remember me checkbox', async () => {
      const user = userEvent.setup();
      render(<LoginForm showRememberMe={true} />);

      const checkbox = screen.getByLabelText(/remember me/i) as HTMLInputElement;

      expect(checkbox.checked).toBe(false);

      await user.click(checkbox);

      expect(checkbox.checked).toBe(true);
    });

    it('should disable submit button when loading', () => {
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        login: vi.fn(),
        isLoading: true,
        error: null,
      });

      render(<LoginForm />);

      const submitButton = screen.getByRole('button', { name: /signing in/i });
      expect(submitButton).toBeDisabled();
    });
  });

  describe('Form Submission', () => {
    it('should call login with correct credentials', async () => {
      const mockLogin = vi.fn().mockResolvedValue({});
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        login: mockLogin,
        isLoading: false,
        error: null,
      });

      const user = userEvent.setup();
      render(<LoginForm />);

      await user.type(screen.getByLabelText(/email/i), 'test@example.com');
      await user.type(screen.getByLabelText(/password/i), 'password123');
      await user.click(screen.getByRole('button', { name: /sign in/i }));

      await waitFor(() => {
        expect(mockLogin).toHaveBeenCalledWith('test@example.com', 'password123', false);
      });
    });

    it('should call login with rememberMe when checked', async () => {
      const mockLogin = vi.fn().mockResolvedValue({});
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        login: mockLogin,
        isLoading: false,
        error: null,
      });

      const user = userEvent.setup();
      render(<LoginForm showRememberMe={true} />);

      await user.type(screen.getByLabelText(/email/i), 'test@example.com');
      await user.type(screen.getByLabelText(/password/i), 'password123');
      await user.click(screen.getByLabelText(/remember me/i));
      await user.click(screen.getByRole('button', { name: /sign in/i }));

      await waitFor(() => {
        expect(mockLogin).toHaveBeenCalledWith('test@example.com', 'password123', true);
      });
    });

    it('should call onSuccess callback on successful login', async () => {
      const mockLogin = vi.fn().mockResolvedValue({});
      const mockOnSuccess = vi.fn();
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        login: mockLogin,
        isLoading: false,
        error: null,
      });

      const user = userEvent.setup();
      render(<LoginForm onSuccess={mockOnSuccess} />);

      await user.type(screen.getByLabelText(/email/i), 'test@example.com');
      await user.type(screen.getByLabelText(/password/i), 'password123');
      await user.click(screen.getByRole('button', { name: /sign in/i }));

      await waitFor(() => {
        expect(mockOnSuccess).toHaveBeenCalled();
      });
    });

    it('should call onError callback on failed login', async () => {
      const mockError = new Error('Invalid credentials');
      const mockLogin = vi.fn().mockRejectedValue(mockError);
      const mockOnError = vi.fn();
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        login: mockLogin,
        isLoading: false,
        error: 'Invalid credentials',
      });

      const user = userEvent.setup();
      render(<LoginForm onError={mockOnError} />);

      await user.type(screen.getByLabelText(/email/i), 'test@example.com');
      await user.type(screen.getByLabelText(/password/i), 'wrongpassword');
      await user.click(screen.getByRole('button', { name: /sign in/i }));

      await waitFor(() => {
        expect(mockOnError).toHaveBeenCalledWith('Invalid credentials');
      });
    });

    it('should not submit with invalid email', async () => {
      const mockLogin = vi.fn();
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        login: mockLogin,
        isLoading: false,
        error: null,
      });

      const user = userEvent.setup();
      render(<LoginForm />);

      await user.type(screen.getByLabelText(/email/i), 'invalid-email');
      await user.type(screen.getByLabelText(/password/i), 'password123');
      await user.click(screen.getByRole('button', { name: /sign in/i }));

      await waitFor(() => {
        expect(mockLogin).not.toHaveBeenCalled();
      });
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA labels', () => {
      render(<LoginForm />);

      expect(screen.getByLabelText(/email/i)).toHaveAttribute('aria-label');
      expect(screen.getByLabelText(/password/i)).toHaveAttribute('aria-label');
    });

    it('should mark invalid fields with aria-invalid', async () => {
      const user = userEvent.setup();
      render(<LoginForm />);

      const emailInput = screen.getByLabelText(/email/i);
      await user.type(emailInput, 'invalid');
      await user.tab();

      await waitFor(() => {
        expect(emailInput).toHaveAttribute('aria-invalid', 'true');
      });
    });

    it('should associate errors with aria-describedby', async () => {
      const user = userEvent.setup();
      render(<LoginForm />);

      const emailInput = screen.getByLabelText(/email/i);
      await user.type(emailInput, 'invalid');
      await user.tab();

      await waitFor(() => {
        const ariaDescribedBy = emailInput.getAttribute('aria-describedby');
        expect(ariaDescribedBy).toBeTruthy();
        if (ariaDescribedBy) {
          expect(document.getElementById(ariaDescribedBy)).toBeInTheDocument();
        }
      });
    });

    it('should be keyboard navigable', async () => {
      const user = userEvent.setup();
      render(<LoginForm showRememberMe={true} />);

      const emailInput = screen.getByLabelText(/email/i);
      const passwordInput = screen.getByLabelText(/password/i);
      const rememberMeCheckbox = screen.getByLabelText(/remember me/i);
      const submitButton = screen.getByRole('button', { name: /sign in/i });

      // Tab through form
      await user.tab();
      expect(emailInput).toHaveFocus();

      await user.tab();
      expect(passwordInput).toHaveFocus();

      await user.tab();
      // Skip show password button
      
      await user.tab();
      expect(rememberMeCheckbox).toHaveFocus();

      await user.tab();
      expect(submitButton).toHaveFocus();
    });
  });

  describe('Error Display', () => {
    it('should display authentication error', () => {
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        login: vi.fn(),
        isLoading: false,
        error: 'Invalid email or password',
      });

      render(<LoginForm />);

      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
    });

    it('should display field-specific errors', async () => {
      const user = userEvent.setup();
      render(<LoginForm />);

      const emailInput = screen.getByLabelText(/email/i);
      await user.type(emailInput, 'invalid');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/valid email/i)).toBeInTheDocument();
      });
    });
  });
});
