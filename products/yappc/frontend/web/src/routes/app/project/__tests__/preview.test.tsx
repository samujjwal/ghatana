import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ projectId: 'proj-42' }),
  };
});

import PreviewRoute from '../preview';

describe('preview route', () => {
  beforeEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
    vi.stubGlobal('fetch', vi.fn());
  });

  it('shows a truthful unavailable state when no external preview host is configured', () => {
    vi.stubEnv('VITE_FEATURE_PROJECT_PREVIEW', 'false');
    vi.stubEnv('VITE_PREVIEW_BASE_URL', '');

    render(<PreviewRoute />);

    expect(screen.getByTestId('preview-status-badge')).toHaveTextContent('Preview unavailable');
    expect(screen.getByText('Preview Not Available')).toBeDefined();
    expect(screen.getByText('A preview host must be configured before this screen can expose a live preview.')).toBeDefined();
  });

  it('shows external preview status and opens the configured host in a new tab', async () => {
    vi.stubEnv('VITE_FEATURE_PROJECT_PREVIEW', 'true');
    vi.stubEnv('VITE_PREVIEW_BASE_URL', 'https://preview.example.test');
    const windowOpenSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 200 }));

    render(<PreviewRoute />);

    await waitFor(() => {
      expect(screen.getByTestId('preview-status-badge')).toHaveTextContent('External preview ready');
    });
    expect(screen.getByText('Preview via external host')).toBeDefined();
    expect(screen.getByTitle('Project Preview')).toHaveAttribute('src', 'https://preview.example.test/preview/proj-42');

    fireEvent.click(screen.getByTitle('Open in New Tab'));

    expect(windowOpenSpy).toHaveBeenCalledWith('https://preview.example.test/preview/proj-42', '_blank');
  });
});