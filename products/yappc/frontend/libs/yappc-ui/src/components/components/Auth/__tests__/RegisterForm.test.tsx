import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RegisterForm } from '../RegisterForm';
import { useAuth } from '../../../hooks/auth';

vi.mock('../../../hooks/auth', () => ({
  useAuth: vi.fn(),
}));

const mockedUseAuth = vi.mocked(useAuth);

function createAuthState(overrides: Partial<ReturnType<typeof useAuth>> = {}) {
  return {
    login: vi.fn(),
    hydrateSession: vi.fn(),
    isLoading: false,
    error: null,
    ...overrides,
  };
}

describe('RegisterForm', () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn());
    mockedUseAuth.mockReturnValue(createAuthState());
  });

  it('renders the default registration controls', () => {
    render(<RegisterForm />);

    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
    expect(screen.getByRole('checkbox')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /create account/i })
    ).toBeInTheDocument();
  });

  it('hides terms and sign-in affordances when disabled', () => {
    render(<RegisterForm showTerms={false} showSignIn={false} />);

    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.queryByText(/already have an account/i)).not.toBeInTheDocument();
  });

  it('shows password strength feedback', async () => {
    const user = userEvent.setup();
    render(<RegisterForm />);

    await user.type(screen.getByLabelText(/^password$/i), 'P@ssw0rd123!');

    expect(screen.getByText(/password strength:/i)).toBeInTheDocument();
    expect(screen.getByText(/strong/i)).toBeInTheDocument();
  });

  it('shows validation errors on submit', async () => {
    const user = userEvent.setup();
    render(<RegisterForm />);

    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(screen.getByText(/name is required/i)).toBeInTheDocument();
    expect(screen.getByText(/email is required/i)).toBeInTheDocument();
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();
    expect(screen.getByText(/please confirm your password/i)).toBeInTheDocument();
    expect(
      screen.getByText(/you must accept the terms and conditions/i)
    ).toBeInTheDocument();
  });

  it('toggles password visibility for both password fields', async () => {
    const user = userEvent.setup();
    render(<RegisterForm />);

    const passwordInput = screen.getByLabelText(/^password$/i) as HTMLInputElement;
    const confirmInput = screen.getByLabelText(/confirm password/i) as HTMLInputElement;

    expect(passwordInput.type).toBe('password');
    expect(confirmInput.type).toBe('password');

    await user.click(screen.getByRole('button', { name: /show password/i }));

    expect(passwordInput.type).toBe('text');
    expect(confirmInput.type).toBe('text');
  });

  it('posts registration data and auto-logins on success', async () => {
    const hydrateSession = vi.fn();
    const onSuccess = vi.fn();
    const mockFetch = vi.mocked(fetch);
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        user: { id: 'user-1', email: 'john@example.com' },
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        expiresIn: 3600,
      }),
    } as Response);
    mockedUseAuth.mockReturnValue(createAuthState({ hydrateSession }));

    const user = userEvent.setup();
    render(<RegisterForm onSuccess={onSuccess} />);

    await user.type(screen.getByLabelText(/full name/i), 'John Doe');
    await user.type(screen.getByLabelText(/email address/i), 'john@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'P@ssw0rd123!');
    await user.type(screen.getByLabelText(/confirm password/i), 'P@ssw0rd123!');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/auth/register',
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: 'John Doe',
            email: 'john@example.com',
            password: 'P@ssw0rd123!',
          }),
        })
      );
      expect(hydrateSession).toHaveBeenCalledWith({
        user: { id: 'user-1', email: 'john@example.com' },
        token: 'access-token',
        refreshToken: 'refresh-token',
        expiresIn: 3600,
      });
      expect(onSuccess).toHaveBeenCalled();
    });
  });

  it('blocks submission for weak passwords', async () => {
    const mockFetch = vi.mocked(fetch);
    const user = userEvent.setup();
    render(<RegisterForm showTerms={false} />);

    await user.type(screen.getByLabelText(/full name/i), 'John Doe');
    await user.type(screen.getByLabelText(/email address/i), 'john@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'password');
    await user.type(screen.getByLabelText(/confirm password/i), 'password');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/password is too weak/i)
      ).toBeInTheDocument();
    });
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('renders the auth error message from state', () => {
    mockedUseAuth.mockReturnValue(
      createAuthState({
        error: new Error('Email already exists'),
      })
    );

    render(<RegisterForm />);

    expect(screen.getByText(/email already exists/i)).toBeInTheDocument();
  });
});
