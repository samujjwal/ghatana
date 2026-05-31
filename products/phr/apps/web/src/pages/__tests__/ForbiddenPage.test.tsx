/**
 * Tests for ForbiddenPage — verifies title, message, and back navigation.
 */
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ForbiddenPage } from '../ForbiddenPage';

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

describe('ForbiddenPage', () => {
  it('renders the forbidden title', () => {
    render(<ForbiddenPage />);
    expect(screen.getByText('forbidden.title')).toBeTruthy();
  });

  it('renders the forbidden message', () => {
    render(<ForbiddenPage />);
    expect(screen.getByText('forbidden.message')).toBeTruthy();
  });

  it('renders a back button', () => {
    render(<ForbiddenPage />);
    expect(screen.getByRole('button', { name: 'forbidden.back' })).toBeTruthy();
  });

  it('calls navigate(-1) when back is clicked', () => {
    render(<ForbiddenPage />);
    fireEvent.click(screen.getByRole('button', { name: 'forbidden.back' }));
    expect(mockNavigate).toHaveBeenCalledWith(-1);
  });
});
