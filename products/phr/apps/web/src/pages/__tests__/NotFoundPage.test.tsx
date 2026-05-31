/**
 * Tests for NotFoundPage — verifies title, message, and back navigation.
 */
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { NotFoundPage } from '../NotFoundPage';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

vi.mock('@ghatana/design-system', () => ({
  Button: ({ children, onClick, type }: { children: React.ReactNode; onClick?: () => void; type?: 'button' }) =>
    React.createElement('button', { onClick, type }, children),
  Card: ({ children }: { children: React.ReactNode }) => React.createElement('div', null, children),
  CardHeader: ({ title }: { title: string }) => React.createElement('h1', null, title),
  CardContent: ({ children }: { children: React.ReactNode }) => React.createElement('div', null, children),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

describe('NotFoundPage', () => {
  it('renders the not found title', () => {
    render(<NotFoundPage />);
    expect(screen.getByText('notFound.title')).toBeTruthy();
  });

  it('renders the not found message', () => {
    render(<NotFoundPage />);
    expect(screen.getByText('notFound.message')).toBeTruthy();
  });

  it('renders a back button', () => {
    render(<NotFoundPage />);
    expect(screen.getByRole('button', { name: 'notFound.back' })).toBeTruthy();
  });

  it('navigates to /dashboard when back is clicked', () => {
    render(<NotFoundPage />);
    fireEvent.click(screen.getByRole('button', { name: 'notFound.back' }));
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
  });
});
