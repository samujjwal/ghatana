/**
 * Tests for YAPPC auth routes: Register and ForgotPassword.
 *
 * @doc.type test
 * @doc.purpose RTL tests for register and forgot-password routes
 * @doc.layer frontend
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import React from 'react';

// =============================================================================
// Mocks
// =============================================================================

vi.mock('../../services/auth/AuthService', () => ({
  authService: {
    register: vi.fn(),
    forgotPassword: vi.fn(),
  },
}));

// Mock react-router navigate
const mockNavigate = vi.fn();
vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// =============================================================================
// Imports (after mocks are set up)
// =============================================================================

import RegisterComponent from '../../routes/register';
import ForgotPasswordComponent from '../../routes/forgot-password';
import { authService } from '../../services/auth/AuthService';

// =============================================================================
// Helpers
// =============================================================================

function renderInRouter(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

// =============================================================================
// Register
// =============================================================================

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.mocked(authService.register).mockResolvedValue({ success: true, token: 'tok123' });
    mockNavigate.mockClear();
  });

  it('renders the heading', () => {
    renderInRouter(<RegisterComponent />);
    expect(screen.getByText('Create your account')).toBeDefined();
  });

  it('renders all form fields', () => {
    renderInRouter(<RegisterComponent />);
    expect(screen.getByLabelText('First name')).toBeDefined();
    expect(screen.getByLabelText('Last name')).toBeDefined();
    expect(screen.getByLabelText('Username')).toBeDefined();
    expect(screen.getByLabelText('Email')).toBeDefined();
    expect(screen.getByLabelText('Password')).toBeDefined();
  });

  it('shows a link to sign in', () => {
    renderInRouter(<RegisterComponent />);
    expect(screen.getByText('Sign in')).toBeDefined();
  });

  it('shows validation errors when form is submitted empty', async () => {
    renderInRouter(<RegisterComponent />);
    fireEvent.submit(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByText('First name is required')).toBeDefined();
      expect(screen.getByText('Last name is required')).toBeDefined();
      expect(screen.getByText('Username is required')).toBeDefined();
      expect(screen.getByText('Email is required')).toBeDefined();
      expect(screen.getByText('Password is required')).toBeDefined();
    });
    expect(authService.register).not.toHaveBeenCalled();
  });

  it('shows validation error for short username', async () => {
    renderInRouter(<RegisterComponent />);

    fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Jane' } });
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Doe' } });
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'ab' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });

    fireEvent.submit(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByText('Username must be at least 3 characters')).toBeDefined();
    });
  });

  it('shows validation error for invalid email', async () => {
    renderInRouter(<RegisterComponent />);

    fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Jane' } });
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Doe' } });
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'janedoe' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'notanemail' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });

    fireEvent.submit(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByText('Enter a valid email address')).toBeDefined();
    });
  });

  it('calls authService.register with valid data', async () => {
    renderInRouter(<RegisterComponent />);

    fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Jane' } });
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Doe' } });
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'janedoe' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });

    fireEvent.submit(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(authService.register).toHaveBeenCalledWith({
        firstName: 'Jane',
        lastName: 'Doe',
        username: 'janedoe',
        email: 'jane@example.com',
        password: 'password123',
      });
    });
  });

  it('navigates to workspaces on successful registration', async () => {
    renderInRouter(<RegisterComponent />);

    fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Jane' } });
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Doe' } });
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'janedoe' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });

    fireEvent.submit(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/app/workspaces');
    });
  });

  it('shows server error when registration fails', async () => {
    vi.mocked(authService.register).mockResolvedValue({
      success: false,
      error: 'Username or email already exists',
    });
    renderInRouter(<RegisterComponent />);

    fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Jane' } });
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Doe' } });
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'janedoe' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });

    fireEvent.submit(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByTestId('register-error')).toBeDefined();
      expect(screen.getByText('Username or email already exists')).toBeDefined();
    });
  });

  it('button shows loading state during submission', async () => {
    vi.mocked(authService.register).mockImplementation(
      () => new Promise((r) => setTimeout(() => r({ success: true }), 100)),
    );
    renderInRouter(<RegisterComponent />);

    fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Jane' } });
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Doe' } });
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'janedoe' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });

    fireEvent.submit(screen.getByRole('button', { name: /create account/i }));
    expect(screen.getByText('Creating account…')).toBeDefined();
  });
});

// =============================================================================
// ForgotPassword
// =============================================================================

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.mocked(authService.forgotPassword).mockResolvedValue({ success: true });
    mockNavigate.mockClear();
  });

  it('renders the heading', () => {
    renderInRouter(<ForgotPasswordComponent />);
    expect(screen.getByText('Reset your password')).toBeDefined();
  });

  it('renders the email field', () => {
    renderInRouter(<ForgotPasswordComponent />);
    expect(screen.getByLabelText('Email address')).toBeDefined();
  });

  it('shows a back to login link', () => {
    renderInRouter(<ForgotPasswordComponent />);
    expect(screen.getAllByText(/back to login/i).length).toBeGreaterThan(0);
  });

  it('shows validation error when email is empty', async () => {
    renderInRouter(<ForgotPasswordComponent />);
    fireEvent.submit(screen.getByRole('button', { name: /send reset link/i }));
    await waitFor(() => {
      expect(screen.getByText('Email is required')).toBeDefined();
    });
    expect(authService.forgotPassword).not.toHaveBeenCalled();
  });

  it('shows validation error for invalid email', async () => {
    renderInRouter(<ForgotPasswordComponent />);
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'bademail' } });
    fireEvent.submit(screen.getByRole('button', { name: /send reset link/i }));
    await waitFor(() => {
      expect(screen.getByText('Enter a valid email address')).toBeDefined();
    });
  });

  it('calls authService.forgotPassword with valid email', async () => {
    renderInRouter(<ForgotPasswordComponent />);
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'user@example.com' } });
    fireEvent.submit(screen.getByRole('button', { name: /send reset link/i }));
    await waitFor(() => {
      expect(authService.forgotPassword).toHaveBeenCalledWith('user@example.com');
    });
  });

  it('shows success state after request', async () => {
    renderInRouter(<ForgotPasswordComponent />);
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'user@example.com' } });
    fireEvent.submit(screen.getByRole('button', { name: /send reset link/i }));
    await waitFor(() => {
      expect(screen.getByTestId('forgot-password-success')).toBeDefined();
      expect(screen.getByText(/check your inbox/i)).toBeDefined();
    });
  });

  it('shows server error when request fails', async () => {
    vi.mocked(authService.forgotPassword).mockResolvedValue({
      success: false,
      error: 'Failed to send reset email',
    });
    renderInRouter(<ForgotPasswordComponent />);
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'user@example.com' } });
    fireEvent.submit(screen.getByRole('button', { name: /send reset link/i }));
    await waitFor(() => {
      expect(screen.getByTestId('forgot-password-error')).toBeDefined();
      expect(screen.getByText('Failed to send reset email')).toBeDefined();
    });
  });

  it('button shows loading state during submission', async () => {
    vi.mocked(authService.forgotPassword).mockImplementation(
      () => new Promise((r) => setTimeout(() => r({ success: true }), 100)),
    );
    renderInRouter(<ForgotPasswordComponent />);
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'user@example.com' } });
    fireEvent.submit(screen.getByRole('button', { name: /send reset link/i }));
    expect(screen.getByText('Sending…')).toBeDefined();
  });
});
