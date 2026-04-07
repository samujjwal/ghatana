import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockActionData, mockSearchParams, mockNavigationState, mockNavigate } = vi.hoisted(() => ({
  mockActionData: { current: undefined as { error?: string } | undefined },
  mockSearchParams: { current: '' },
  mockNavigationState: { current: 'idle' as 'idle' | 'submitting' | 'loading' },
  mockNavigate: vi.fn(),
}));

vi.mock('../../services/auth/AuthService', () => ({
  authService: {
    demoLogin: vi.fn(),
  },
  isDemoLoginEnabled: vi.fn(() => false),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useActionData: () => mockActionData.current,
    useNavigation: () => ({ state: mockNavigationState.current }),
    useNavigate: () => mockNavigate,
    useSearchParams: () => [new URLSearchParams(mockSearchParams.current), vi.fn()],
  };
});

import LoginComponent from '../../routes/login';

async function renderWithRouter(): Promise<void> {
  const { createMemoryRouter, RouterProvider } = await import('react-router');
  const router = createMemoryRouter([{ path: '/', element: <LoginComponent /> }], {
    initialEntries: ['/'],
  });

  render(<RouterProvider router={router} />);
}

describe('LoginPage', () => {
  beforeEach(() => {
    mockActionData.current = undefined;
    mockSearchParams.current = '';
    mockNavigationState.current = 'idle';
    mockNavigate.mockReset();
  });

  it('renders the email-first login form', async () => {
    await renderWithRouter();

    expect(screen.getByTestId('login-form')).toBeDefined();
    expect(screen.getByLabelText('Email')).toBeDefined();
    expect(screen.getByLabelText('Password')).toBeDefined();
    expect(screen.getByTestId('login-submit')).toBeDefined();
  });

  it('shows the session-expired banner from the search params', async () => {
    mockSearchParams.current = 'sessionExpired=true&redirectTo=%2Fworkspaces';

    await renderWithRouter();

    expect(screen.getByTestId('session-expired-message')).toBeDefined();
  });

  it('surfaces action errors from failed login attempts', async () => {
    mockActionData.current = { error: 'Invalid email or password' };

    await renderWithRouter();

    await waitFor(() => {
      expect(screen.getByTestId('login-error')).toHaveTextContent('Invalid email or password');
    });
  });

  it('keeps demo login hidden unless explicitly enabled', async () => {
    await renderWithRouter();

    expect(screen.queryByText('Continue as Demo User →')).toBeNull();
  });
});