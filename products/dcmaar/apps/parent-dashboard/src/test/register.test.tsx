import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { Register } from '../pages/Register';

// Mock the services
vi.mock('../services/auth.service', () => ({
  authService: {
    register: vi.fn(),
  },
}));

vi.mock('../services/websocket.service', () => ({
  websocketService: {
    connect: vi.fn(),
  },
}));

const { authService } = await import('../services/auth.service');

describe('Register Form Validation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should validate password confirmation match', async () => {
    const user = userEvent.setup();
    
    render(
      <BrowserRouter>
        <Register />
      </BrowserRouter>
    );

    const emailInput = screen.getByLabelText(/email/i);
    const passwordInput = screen.getByLabelText(/^password$/i);
    const confirmInput = screen.getByLabelText(/confirm password/i);
    const submitButton = screen.getByRole('button', { name: /sign up/i });

    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'password123');
    await user.type(confirmInput, 'different123');
    await act(async () => {
      await user.click(submitButton);
    });

    await waitFor(() => {
      expect(screen.getByText(/passwords don't match/i)).toBeInTheDocument();
    });
  });

  it('should show error message when registration fails', async () => {
    const user = userEvent.setup();
    
    render(
      <BrowserRouter>
        <Register />
      </BrowserRouter>
    );

    const emailInput = screen.getByLabelText(/email/i);
    const passwordInput = screen.getByLabelText(/^password$/i);
    const confirmInput = screen.getByLabelText(/confirm password/i);
    const submitButton = screen.getByRole('button', { name: /sign up/i });

    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'password123');
    await user.type(confirmInput, 'password123');

    // Mock API failure with proper error
    const error = new Error('Email already exists');
    vi.mocked(authService.register).mockRejectedValueOnce(error);

    await act(async () => {
      await user.click(submitButton);
    });

    await waitFor(() => {
      expect(screen.getByText(/email already exists/i)).toBeInTheDocument();
    });
  });
});
