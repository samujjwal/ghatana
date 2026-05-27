import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ArtifactHealthPanel, type KernelArtifactHealth } from '../ArtifactHealthPanel';

describe('ArtifactHealthPanel', () => {
  it('renders an explicit empty state when Kernel artifacts are unavailable', () => {
    render(<ArtifactHealthPanel productUnitId="unit-1" artifacts={[]} />);

    expect(screen.getByText('No artifacts available')).toBeInTheDocument();
    expect(screen.getByText(/no kernel artifact metadata has been recorded for unit-1 yet/i)).toBeInTheDocument();
  });

  it('renders artifact metadata when present', () => {
    const artifact: KernelArtifactHealth = {
      id: 'artifact-1',
      type: 'docker-image',
      surface: 'web',
      path: 'registry.example/yappc:1',
      fingerprint: 'sha256:abc',
      producedBy: 'generate-run-1',
      producedAt: '2026-05-01T10:00:00Z',
      healthCheckStatus: 'healthy',
      lastVerified: '2026-05-01T10:05:00Z',
    };

    render(<ArtifactHealthPanel productUnitId="unit-1" artifacts={[artifact]} />);

    expect(screen.getByText('Artifact Health')).toBeInTheDocument();
    expect(screen.getByText('registry.example/yappc:1')).toBeInTheDocument();
    expect(screen.getByText('sha256:abc')).toBeInTheDocument();
  });
});
