/**
 * Tests for LoginPage — verifies real credential submission (no demo link).
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { LoginPage } from '../../pages/LoginPage';
import { PhrSessionProvider } from '../../auth/PhrSessionContext';

vi.mock('../../api/authApi', () => ({
  loginWithCredentials: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => vi.fn(),
  };
});

import { loginWithCredentials } from '../../api/authApi';

const mockLogin = loginWithCredentials as ReturnType<typeof vi.fn>;

function renderLoginPage(): void {
  render(
    <MemoryRouter>
      <PhrSessionProvider>
        <LoginPage />
      </PhrSessionProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    mockLogin.mockReset();
  });

  it('does not contain a bypass demo link', () => {
    renderLoginPage();
    // There must be no anchor pointing to /dashboard that bypasses auth
    const links = document.querySelectorAll('a[href*="dashboard"]');
    expect(links).toHaveLength(0);
  });

  it('shows validation error when national ID is empty', async () => {
    renderLoginPage();
    fireEvent.click(screen.getByText('login.signIn'));

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(mockLogin).not.toHaveBeenCalled();
  });

  it('calls loginWithCredentials when credentials are provided', async () => {
    const futureDate = new Date(Date.now() + 3600_000).toISOString();
    mockLogin.mockResolvedValue({
      token: 'tok-123',
      principalId: 'user-1',
      role: 'patient',
      tenantId: 'tenant-1',
      expiresAt: futureDate,
    });

    renderLoginPage();

    const inputs = screen.getAllByRole('textbox');
    fireEvent.change(inputs[0]!, { target: { value: 'NP-12345' } });
    const passwordInput = document.querySelector('input[type="password"]') as HTMLInputElement;
    fireEvent.change(passwordInput, { target: { value: 'secret123' } });

    fireEvent.click(screen.getByText('login.signIn'));

    await waitFor(() => expect(mockLogin).toHaveBeenCalledOnce());
    expect(mockLogin).toHaveBeenCalledWith({ nationalId: 'NP-12345', password: 'secret123' });
  });

  it('shows error when login fails', async () => {
    mockLogin.mockRejectedValue(new Error('Invalid credentials'));

    renderLoginPage();

    const inputs = screen.getAllByRole('textbox');
    fireEvent.change(inputs[0]!, { target: { value: 'NP-99999' } });
    const passwordInput = document.querySelector('input[type="password"]') as HTMLInputElement;
    fireEvent.change(passwordInput, { target: { value: 'wrong' } });

    fireEvent.click(screen.getByText('login.signIn'));

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.getByRole('alert').textContent).toContain('Invalid credentials');
  });
});
