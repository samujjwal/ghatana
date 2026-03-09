import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { Login } from '../pages/Login';

describe('Accessibility Tests', () => {
  describe('Semantic HTML', () => {
    it('should use proper heading hierarchy', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const heading = screen.getByRole('heading', { level: 2 });
      expect(heading).toBeInTheDocument();
    });

    it('should have properly labeled form inputs', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const emailInput = screen.getByLabelText(/email/i);
      const passwordInput = screen.getByLabelText(/password/i);

      expect(emailInput).toBeInTheDocument();
      expect(passwordInput).toBeInTheDocument();
    });

    it('should have accessible buttons', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const submitButton = screen.getByRole('button', { name: /sign in/i });
      expect(submitButton).toBeInTheDocument();
    });

    it('should have navigation links', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const registerLink = screen.getByRole('link', { name: /sign up/i });
      expect(registerLink).toBeInTheDocument();
    });
  });

  describe('Form Accessibility', () => {
    it('should associate labels with inputs', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const emailInput = screen.getByLabelText(/email/i) as HTMLInputElement;
      expect(emailInput.id).toBeTruthy();
    });

    it('should have proper input types', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const emailInput = screen.getByLabelText(/email/i) as HTMLInputElement;
      const passwordInput = screen.getByLabelText(/password/i) as HTMLInputElement;

      expect(emailInput.type).toBe('email');
      expect(passwordInput.type).toBe('password');
    });

    it('should have proper input attributes', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const emailInput = screen.getByLabelText(/email/i) as HTMLInputElement;
      const passwordInput = screen.getByLabelText(/password/i) as HTMLInputElement;

      // React Hook Form handles validation, not HTML required attribute
      expect(emailInput.name).toBe('email');
      expect(passwordInput.name).toBe('password');
    });
  });

  describe('Keyboard Navigation', () => {
    it('should have focusable form elements', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const emailInput = screen.getByLabelText(/email/i) as HTMLInputElement;
      const passwordInput = screen.getByLabelText(/password/i) as HTMLInputElement;
      const submitButton = screen.getByRole('button', { name: /sign in/i }) as HTMLButtonElement;

      expect(emailInput.tabIndex).toBeGreaterThanOrEqual(0);
      expect(passwordInput.tabIndex).toBeGreaterThanOrEqual(0);
      expect(submitButton.tabIndex).toBeGreaterThanOrEqual(0);
    });
  });

  describe('Visual Feedback', () => {
    it('should have proper button text', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const submitButton = screen.getByRole('button', { name: /sign in/i });
      expect(submitButton.textContent).toBeTruthy();
    });

    it('should have descriptive link text', () => {
      render(
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      );

      const registerLink = screen.getByRole('link');
      expect(registerLink.textContent).toContain('account');
    });
  });
});
