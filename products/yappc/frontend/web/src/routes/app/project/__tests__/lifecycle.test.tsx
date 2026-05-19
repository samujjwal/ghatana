import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

const { mockNavigate } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ projectId: 'proj-42' }),
    useNavigate: () => mockNavigate,
  };
});

import LifecycleRoute from '../lifecycle';

describe('Lifecycle route', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
  });

  it('redirects legacy lifecycle links to the intent phase cockpit', async () => {
    render(<LifecycleRoute />);

    expect(screen.getByText('Redirecting to Intent phase...')).toBeDefined();
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/p/proj-42/intent', { replace: true });
    });
  });
});
