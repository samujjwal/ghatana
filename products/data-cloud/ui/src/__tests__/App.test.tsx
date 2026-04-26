import React from 'react';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

const routerProviderMock = vi.fn((_props: unknown) => <div data-testid="router-provider">router</div>);

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router');

  return {
    ...actual,
    createBrowserRouter: vi.fn(() => ({ routes: [] })),
    RouterProvider: (props: unknown) => routerProviderMock(props),
  };
});

import { App } from '../App';

describe('App onboarding gate', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('shows onboarding on first load and hides it after completion', () => {
    render(<App />);

    expect(screen.getByRole('dialog', { name: /getting started/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /skip setup/i }));

    expect(screen.queryByRole('dialog', { name: /getting started/i })).not.toBeInTheDocument();
    expect(screen.getByTestId('router-provider')).toBeInTheDocument();
    expect(localStorage.getItem('dc:onboarding:complete')).toBe('true');
  });

  it('keeps onboarding dismissed when completion state is already persisted without tenant bootstrap', () => {
    localStorage.setItem('dc:onboarding:complete', 'true');

    render(<App />);

    expect(screen.queryByRole('dialog', { name: /getting started/i })).not.toBeInTheDocument();
    expect(screen.getByTestId('router-provider')).toBeInTheDocument();
  });
});