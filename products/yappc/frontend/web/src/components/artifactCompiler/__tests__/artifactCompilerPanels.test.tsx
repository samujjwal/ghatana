import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  importSource: vi.fn(),
  getSourceImportJob: vi.fn(),
  setScope: vi.fn(),
  approveBundle: vi.fn(),
  rejectBundle: vi.fn(),
  applyBundle: vi.fn(),
}));

vi.mock('@/clients/artifactCompiler/ArtifactCompilerClient', () => ({
  getArtifactCompilerClient: vi.fn(() => ({
    importSource: mocks.importSource,
    getSourceImportJob: mocks.getSourceImportJob,
    setScope: mocks.setScope,
  })),
}));

vi.mock('@/clients/artifactCompiler/LegacyArtifactPatchBundleClient', () => ({
  getLegacyArtifactPatchBundleClient: vi.fn(() => ({
    approveBundle: mocks.approveBundle,
    rejectBundle: mocks.rejectBundle,
    applyBundle: mocks.applyBundle,
  })),
}));

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    currentUser: { id: 'reviewer-1', username: 'reviewer', email: 'reviewer@example.com' },
  }),
}));

import { PatchReviewPanel } from '../PatchReviewPanel';
import { SourceImportPanel } from '../SourceImportPanel';

describe('Artifact compiler panels', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.importSource.mockResolvedValue({
      jobId: 'job-1',
      status: 'PENDING',
      message: 'Import job submitted',
    });
  });

  it('submits source imports through the Java artifact compiler client', async () => {
    render(
      <SourceImportPanel
        open
        onClose={vi.fn()}
        tenantId="tenant-1"
        workspaceId="workspace-1"
        projectId="project-1"
      />
    );

    fireEvent.change(screen.getByLabelText(/Repository URL/i), {
      target: { value: 'https://github.com/acme/app' },
    });
    fireEvent.change(screen.getByLabelText(/Branch/i), {
      target: { value: 'release' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Start Import/i }));

    await waitFor(() => {
      expect(mocks.setScope).toHaveBeenCalledWith({
        workspaceId: 'workspace-1',
        projectId: 'project-1',
      });
      expect(mocks.importSource).toHaveBeenCalledWith({
        sourceType: 'github',
        source: 'https://github.com/acme/app',
        sourceUrl: 'https://github.com/acme/app',
        projectId: 'project-1',
        workspaceId: 'workspace-1',
        options: { ref: 'release' },
      });
    });
    expect(await screen.findByText('Importing...')).toBeInTheDocument();
    expect(screen.getByText('Import job submitted')).toBeInTheDocument();
  });

  it('approves patch bundles through the legacy patch bundle client until generated APIs exist', async () => {
    mocks.approveBundle.mockResolvedValue({
      success: true,
      bundleId: 'bundle-1',
      status: 'APPROVED',
      reviewedBy: 'reviewer-1',
    });
    const onApprove = vi.fn();

    render(
      <PatchReviewPanel
        open
        bundleId="bundle-1"
        patches={[{
          filePath: 'src/App.tsx',
          oldContent: '',
          newContent: '',
          diff: '@@ -1 +1 @@',
        }]}
        validation={{ valid: true, errors: [] }}
        residualOverlaps={[]}
        onClose={vi.fn()}
        onApprove={onApprove}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /Approve/i }));

    await waitFor(() => {
      expect(mocks.approveBundle).toHaveBeenCalledWith('bundle-1', { reviewer: 'reviewer-1' });
      expect(onApprove).toHaveBeenCalledTimes(1);
    });
  });
});
