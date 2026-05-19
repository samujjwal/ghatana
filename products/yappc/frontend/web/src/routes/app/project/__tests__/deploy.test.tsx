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
    useParams: () => ({ projectId: 'project-1' }),
    useNavigate: () => mockNavigate,
  };
});

import DeployRoute from '../deploy';

describe('deploy route', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
  });

  it('redirects legacy deploy links to the run phase cockpit', async () => {
    render(<DeployRoute />);

    expect(screen.getByText('Redirecting to Run phase...')).toBeDefined();
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/p/project-1/run', { replace: true });
    });
  });
});
