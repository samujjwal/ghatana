import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { LoginForm } from '../LoginForm';
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

describe('LoginForm', () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn());
    mockedUseAuth.mockReturnValue(createAuthState());
  });

  it('renders the default login controls', () => {
    render(<LoginForm />);

    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/remember me for 30 days/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('hides optional controls when disabled', () => {
    render(
      <LoginForm
        showRememberMe={false}
        showForgotPassword={false}
        showSignUp={false}
      />
    );

    expect(
      screen.queryByLabelText(/remember me for 30 days/i)
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/forgot password/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/sign up/i)).not.toBeInTheDocument();
  });

  it('shows validation errors on submit and marks invalid fields', async () => {
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(screen.getByText(/email is required/i)).toBeInTheDocument();
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toHaveAttribute(
      'aria-invalid',
      'true'
    );
    expect(screen.getByLabelText(/^password$/i)).toHaveAttribute(
      'aria-invalid',
      'true'
    );
  });

  it('toggles password visibility', async () => {
    const user = userEvent.setup();
    render(<LoginForm />);

    const passwordInput = screen.getByLabelText(/^password$/i) as HTMLInputElement;

    expect(passwordInput.type).toBe('password');

    await user.click(screen.getByRole('button', { name: /show password/i }));
    expect(passwordInput.type).toBe('text');

    await user.click(screen.getByRole('button', { name: /hide password/i }));
    expect(passwordInput.type).toBe('password');
  });

  it('posts credentials and hydrates auth state on success', async () => {
    const hydrateSession = vi.fn();
    const onSuccess = vi.fn();
    const mockFetch = vi.mocked(fetch);
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        user: { id: 'user-1', email: 'test@example.com' },
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        expiresIn: 3600,
      }),
    } as Response);
    mockedUseAuth.mockReturnValue(createAuthState({ hydrateSession }));

    const user = userEvent.setup();
    render(<LoginForm onSuccess={onSuccess} />);

    await user.type(screen.getByLabelText(/email address/i), 'test@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'password123');
    await user.click(screen.getByLabelText(/remember me for 30 days/i));
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/auth/login',
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: 'test@example.com',
            password: 'password123',
            rememberMe: true,
          }),
        })
      );
      expect(hydrateSession).toHaveBeenCalledWith({
        user: { id: 'user-1', email: 'test@example.com' },
        token: 'access-token',
        refreshToken: 'refresh-token',
        expiresIn: 3600,
      });
      expect(onSuccess).toHaveBeenCalled();
    });
  });

  it('surfaces submit failures through onError', async () => {
    const onError = vi.fn();
    const mockFetch = vi.mocked(fetch);
    mockFetch.mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'Invalid credentials' }),
    } as Response);

    const user = userEvent.setup();
    render(<LoginForm onError={onError} />);

    await user.type(screen.getByLabelText(/email address/i), 'test@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'password123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(onError).toHaveBeenCalledWith(expect.any(Error));
    });
    expect(onError.mock.calls[0]?.[0]).toMatchObject({
      message: 'Invalid credentials',
    });
  });

  it('renders the auth error message from state', () => {
    mockedUseAuth.mockReturnValue(
      createAuthState({
        error: new Error('Invalid email or password'),
      })
    );

    render(<LoginForm />);

    expect(
      screen.getByText(/invalid email or password/i)
    ).toBeInTheDocument();
  });
});
