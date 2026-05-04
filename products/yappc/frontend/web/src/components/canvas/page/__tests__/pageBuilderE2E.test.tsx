import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PageDesigner } from '../PageDesigner';

const { mockImportPageArtifactsFromCode } = vi.hoisted(() => ({
  mockImportPageArtifactsFromCode: vi.fn(),
}));

vi.mock('../artifactCompilerBridge', () => ({
  importPageArtifactsFromCode: mockImportPageArtifactsFromCode,
}));

vi.mock('../rendererManifest', () => ({
  rendererManifestRegistry: {
    register: vi.fn(),
  },
}));

vi.mock('../ComponentRenderer', () => ({
  ComponentRenderer: () => <div data-testid="component-renderer" />,
}));

describe('Page Builder interactions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('publishes document validation on mount so the parent can react to the real builder state', async () => {
    const onDocumentChange = vi.fn();

    render(
      <PageDesigner
        initialComponents={[]}
        onDocumentChange={onDocumentChange}
        onImportArtifacts={vi.fn()}
        onAIChangeRecord={vi.fn()}
      />,
    );

    expect(screen.getByTestId('page-designer')).toBeInTheDocument();

    await waitFor(() => {
      expect(onDocumentChange).toHaveBeenCalled();
      const [document, validation] = onDocumentChange.mock.calls.at(-1) as [
        { rootNodes: string[] },
        { valid: boolean },
      ];
      expect(document.rootNodes).toEqual([]);
      expect(validation.valid).toBe(true);
    });
  });

  it('surfaces parse errors from the import panel instead of pretending import succeeded', async () => {
    const onImportArtifacts = vi.fn();

    render(
      <PageDesigner
        initialComponents={[]}
        onDocumentChange={vi.fn()}
        onImportArtifacts={onImportArtifacts}
        onAIChangeRecord={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    fireEvent.change(screen.getByTestId('page-designer-import-textarea'), {
      target: { value: '{invalid json' },
    });
    mockImportPageArtifactsFromCode.mockImplementation(() => {
      throw new Error('Invalid JSON - could not parse semantic model.');
    });

    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    await waitFor(() => {
      expect(screen.getByText(/Invalid JSON - could not parse semantic model\./i)).toBeInTheDocument();
    });
    expect(onImportArtifacts).not.toHaveBeenCalled();
  });

  it('imports artifacts through the real import workflow and records governance evidence', async () => {
    const onImportArtifacts = vi.fn();
    const onAIChangeRecord = vi.fn();

    mockImportPageArtifactsFromCode.mockReturnValue([
      {
        artifactId: 'artifact-home',
        documentId: 'doc-home-v2',
        source: 'import',
        serializedBuilderDocument: {
          rootNodes: ['button-1'],
          nodes: {
            'button-1': {
              id: 'button-1',
              contractName: 'Button',
              props: { children: 'Launch' },
              slots: { default: [] },
              metadata: { name: 'Launch Button' },
            },
          },
          metadata: { name: 'Imported Home' },
        },
        residualIslandIds: ['legacy-chart'],
        roundTripFidelity: {
          confidence: 0.82,
          canRoundTrip: false,
          lossPoints: ['legacy-chart'],
        },
      },
    ]);

    render(
      <PageDesigner
        initialComponents={[]}
        onDocumentChange={vi.fn()}
        onImportArtifacts={onImportArtifacts}
        onAIChangeRecord={onAIChangeRecord}
      />,
    );

    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    fireEvent.change(screen.getByTestId('page-designer-import-textarea'), {
      target: { value: '{"pages":[{"name":"Home"}]}' },
    });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    await waitFor(() => {
      expect(onImportArtifacts).toHaveBeenCalledTimes(1);
      expect(onAIChangeRecord).toHaveBeenCalledTimes(1);
      expect(screen.getByTestId('page-designer-residuals')).toBeInTheDocument();
      expect(screen.getByTestId('page-designer-roundtrip-fidelity')).toBeInTheDocument();
      expect(screen.getByTestId('governance-panel')).toBeInTheDocument();
    });
  });
});
