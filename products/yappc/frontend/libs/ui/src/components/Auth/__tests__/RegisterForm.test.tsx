/**
 * RegisterForm Component Tests
 * 
 * Unit tests for RegisterForm component
 * 
 * @module ui/components/Auth/__tests__/RegisterForm.test
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegisterForm } from '../RegisterForm';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the useAuth hook
vi.mock('@ghatana/yappc-canvas', () => ({
  useAuth: () => ({
    register: vi.fn(),
    login: vi.fn(),
    isLoading: false,
    error: null,
  }),
}));

describe('RegisterForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render registration form with all fields', () => {
      render(<RegisterForm />);

      expect(screen.getByLabelText(/^name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
    });

    it('should render terms checkbox when enabled', () => {
      render(<RegisterForm showTerms={true} />);

      expect(screen.getByLabelText(/agree to.*terms/i)).toBeInTheDocument();
    });

    it('should not render terms checkbox when disabled', () => {
      render(<RegisterForm showTerms={false} />);

      expect(screen.queryByLabelText(/agree to.*terms/i)).not.toBeInTheDocument();
    });

    it('should render sign in link when enabled', () => {
      render(<RegisterForm showSignIn={true} />);

      expect(screen.getByText(/sign in/i)).toBeInTheDocument();
    });

    it('should render custom submit text', () => {
      render(<RegisterForm submitText="Register Now" />);

      expect(screen.getByRole('button', { name: /register now/i })).toBeInTheDocument();
    });
  });

  describe('Password Strength Indicator', () => {
    it('should show weak password strength', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const passwordInput = screen.getByLabelText(/^password$/i);
      await user.type(passwordInput, '12345');

      await waitFor(() => {
        expect(screen.getByText(/weak/i)).toBeInTheDocument();
      });
    });

    it('should show fair password strength', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const passwordInput = screen.getByLabelText(/^password$/i);
      await user.type(passwordInput, 'password123');

      await waitFor(() => {
        expect(screen.getByText(/fair/i)).toBeInTheDocument();
      });
    });

    it('should show good password strength', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const passwordInput = screen.getByLabelText(/^password$/i);
      await user.type(passwordInput, 'Password123');

      await waitFor(() => {
        expect(screen.getByText(/good/i)).toBeInTheDocument();
      });
    });

    it('should show strong password strength', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const passwordInput = screen.getByLabelText(/^password$/i);
      await user.type(passwordInput, 'P@ssw0rd123!');

      await waitFor(() => {
        expect(screen.getByText(/strong/i)).toBeInTheDocument();
      });
    });
  });

  describe('Validation', () => {
    it('should show error for short name', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const nameInput = screen.getByLabelText(/^name/i);
      await user.type(nameInput, 'A');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/at least 2 characters/i)).toBeInTheDocument();
      });
    });

    it('should show error for invalid email', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const emailInput = screen.getByLabelText(/email/i);
      await user.type(emailInput, 'invalid-email');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/valid email/i)).toBeInTheDocument();
      });
    });

    it('should show error for short password', async () => {
      const user = userEvent.setup();
      render(<RegisterForm minPasswordLength={8} />);

      const passwordInput = screen.getByLabelText(/^password$/i);
      await user.type(passwordInput, '123');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument();
      });
    });

    it('should show error for password mismatch', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const passwordInput = screen.getByLabelText(/^password$/i);
      const confirmInput = screen.getByLabelText(/confirm password/i);

      await user.type(passwordInput, 'Password123!');
      await user.type(confirmInput, 'DifferentPassword123!');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
      });
    });

    it('should show error when terms not accepted', async () => {
      const user = userEvent.setup();
      render(<RegisterForm showTerms={true} />);

      await user.type(screen.getByLabelText(/^name/i), 'John Doe');
      await user.type(screen.getByLabelText(/email/i), 'john@example.com');
      await user.type(screen.getByLabelText(/^password$/i), 'Password123!');
      await user.type(screen.getByLabelText(/confirm password/i), 'Password123!');
      await user.click(screen.getByRole('button', { name: /create account/i }));

      await waitFor(() => {
        expect(screen.getByText(/accept.*terms/i)).toBeInTheDocument();
      });
    });
  });

  describe('Interactions', () => {
    it('should toggle password visibility', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const passwordInput = screen.getByLabelText(/^password$/i) as HTMLInputElement;
      const toggleButtons = screen.getAllByRole('button', { name: /show password/i });

      expect(passwordInput.type).toBe('password');

      await user.click(toggleButtons[0]);

      expect(passwordInput.type).toBe('text');
    });

    it('should toggle confirm password visibility', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const confirmInput = screen.getByLabelText(/confirm password/i) as HTMLInputElement;
      const toggleButtons = screen.getAllByRole('button', { name: /show password/i });

      expect(confirmInput.type).toBe('password');

      await user.click(toggleButtons[1]);

      expect(confirmInput.type).toBe('text');
    });

    it('should toggle terms checkbox', async () => {
      const user = userEvent.setup();
      render(<RegisterForm showTerms={true} />);

      const checkbox = screen.getByLabelText(/agree to.*terms/i) as HTMLInputElement;

      expect(checkbox.checked).toBe(false);

      await user.click(checkbox);

      expect(checkbox.checked).toBe(true);
    });
  });

  describe('Form Submission', () => {
    it('should call register with correct data', async () => {
      const mockRegister = vi.fn().mockResolvedValue({});
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        register: mockRegister,
        login: vi.fn(),
        isLoading: false,
        error: null,
      });

      const user = userEvent.setup();
      render(<RegisterForm showTerms={false} />);

      await user.type(screen.getByLabelText(/^name/i), 'John Doe');
      await user.type(screen.getByLabelText(/email/i), 'john@example.com');
      await user.type(screen.getByLabelText(/^password$/i), 'Password123!');
      await user.type(screen.getByLabelText(/confirm password/i), 'Password123!');
      await user.click(screen.getByRole('button', { name: /create account/i }));

      await waitFor(() => {
        expect(mockRegister).toHaveBeenCalledWith({
          name: 'John Doe',
          email: 'john@example.com',
          password: 'Password123!',
        });
      });
    });

    it('should auto-login after successful registration', async () => {
      const mockRegister = vi.fn().mockResolvedValue({});
      const mockLogin = vi.fn().mockResolvedValue({});
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        register: mockRegister,
        login: mockLogin,
        isLoading: false,
        error: null,
      });

      const user = userEvent.setup();
      render(<RegisterForm showTerms={false} />);

      const password = 'Password123!';

      await user.type(screen.getByLabelText(/^name/i), 'John Doe');
      await user.type(screen.getByLabelText(/email/i), 'john@example.com');
      await user.type(screen.getByLabelText(/^password$/i), password);
      await user.type(screen.getByLabelText(/confirm password/i), password);
      await user.click(screen.getByRole('button', { name: /create account/i }));

      await waitFor(() => {
        expect(mockLogin).toHaveBeenCalledWith('john@example.com', password);
      });
    });

    it('should call onSuccess callback on successful registration', async () => {
      const mockRegister = vi.fn().mockResolvedValue({});
      const mockOnSuccess = vi.fn();
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        register: mockRegister,
        login: vi.fn().mockResolvedValue({}),
        isLoading: false,
        error: null,
      });

      const user = userEvent.setup();
      render(<RegisterForm onSuccess={mockOnSuccess} showTerms={false} />);

      await user.type(screen.getByLabelText(/^name/i), 'John Doe');
      await user.type(screen.getByLabelText(/email/i), 'john@example.com');
      await user.type(screen.getByLabelText(/^password$/i), 'Password123!');
      await user.type(screen.getByLabelText(/confirm password/i), 'Password123!');
      await user.click(screen.getByRole('button', { name: /create account/i }));

      await waitFor(() => {
        expect(mockOnSuccess).toHaveBeenCalled();
      });
    });

    it('should call onError callback on failed registration', async () => {
      const mockError = new Error('Email already exists');
      const mockRegister = vi.fn().mockRejectedValue(mockError);
      const mockOnError = vi.fn();
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        register: mockRegister,
        login: vi.fn(),
        isLoading: false,
        error: 'Email already exists',
      });

      const user = userEvent.setup();
      render(<RegisterForm onError={mockOnError} showTerms={false} />);

      await user.type(screen.getByLabelText(/^name/i), 'John Doe');
      await user.type(screen.getByLabelText(/email/i), 'existing@example.com');
      await user.type(screen.getByLabelText(/^password$/i), 'Password123!');
      await user.type(screen.getByLabelText(/confirm password/i), 'Password123!');
      await user.click(screen.getByRole('button', { name: /create account/i }));

      await waitFor(() => {
        expect(mockOnError).toHaveBeenCalledWith('Email already exists');
      });
    });

    it('should not submit with weak password', async () => {
      const mockRegister = vi.fn();
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        register: mockRegister,
        login: vi.fn(),
        isLoading: false,
        error: null,
      });

      const user = userEvent.setup();
      render(<RegisterForm showTerms={false} />);

      await user.type(screen.getByLabelText(/^name/i), 'John Doe');
      await user.type(screen.getByLabelText(/email/i), 'john@example.com');
      await user.type(screen.getByLabelText(/^password$/i), '12345');
      await user.type(screen.getByLabelText(/confirm password/i), '12345');
      await user.click(screen.getByRole('button', { name: /create account/i }));

      await waitFor(() => {
        expect(mockRegister).not.toHaveBeenCalled();
      });
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA labels', () => {
      render(<RegisterForm />);

      expect(screen.getByLabelText(/^name/i)).toHaveAttribute('aria-label');
      expect(screen.getByLabelText(/email/i)).toHaveAttribute('aria-label');
      expect(screen.getByLabelText(/^password$/i)).toHaveAttribute('aria-label');
      expect(screen.getByLabelText(/confirm password/i)).toHaveAttribute('aria-label');
    });

    it('should mark invalid fields with aria-invalid', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const emailInput = screen.getByLabelText(/email/i);
      await user.type(emailInput, 'invalid');
      await user.tab();

      await waitFor(() => {
        expect(emailInput).toHaveAttribute('aria-invalid', 'true');
      });
    });

    it('should be keyboard navigable', async () => {
      const user = userEvent.setup();
      render(<RegisterForm showTerms={true} />);

      const nameInput = screen.getByLabelText(/^name/i);
      const emailInput = screen.getByLabelText(/email/i);
      const passwordInput = screen.getByLabelText(/^password$/i);
      const confirmInput = screen.getByLabelText(/confirm password/i);
      const termsCheckbox = screen.getByLabelText(/agree to.*terms/i);
      const submitButton = screen.getByRole('button', { name: /create account/i });

      // Tab through form
      await user.tab();
      expect(nameInput).toHaveFocus();

      await user.tab();
      expect(emailInput).toHaveFocus();

      await user.tab();
      expect(passwordInput).toHaveFocus();

      // Skip show password buttons
      await user.tab();
      await user.tab();
      
      await user.tab();
      expect(confirmInput).toHaveFocus();

      // Skip show password button
      await user.tab();
      await user.tab();
      
      await user.tab();
      expect(termsCheckbox).toHaveFocus();

      await user.tab();
      expect(submitButton).toHaveFocus();
    });
  });

  describe('Error Display', () => {
    it('should display authentication error', () => {
      const { useAuth } = require('@ghatana/yappc-canvas');
      useAuth.mockReturnValue({
        register: vi.fn(),
        login: vi.fn(),
        isLoading: false,
        error: 'Email already exists',
      });

      render(<RegisterForm />);

      expect(screen.getByText(/email already exists/i)).toBeInTheDocument();
    });

    it('should display field-specific errors', async () => {
      const user = userEvent.setup();
      render(<RegisterForm />);

      const nameInput = screen.getByLabelText(/^name/i);
      await user.type(nameInput, 'A');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/at least 2 characters/i)).toBeInTheDocument();
      });
    });
  });
});
