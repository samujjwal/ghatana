import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from '@/test-utils/test-utils';
import { PageDesigner } from '../PageDesigner';

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

  it('renders a real built-in component when added from the palette', async () => {
    render(
      <PageDesigner
        initialComponents={[]}
        onDocumentChange={vi.fn()}
        onImportArtifacts={vi.fn()}
        onAIChangeRecord={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByTestId('page-component-button'));

    await waitFor(() => {
      const designArea = screen.getByTestId('page-design-area');
      expect(within(designArea).getByRole('button', { name: /button/i })).toBeInTheDocument();
    });
  });

  it('deletes a selected canvas component through real toolbar interaction', async () => {
    const onDocumentChange = vi.fn();

    render(
      <PageDesigner
        initialComponents={[]}
        onDocumentChange={onDocumentChange}
        onImportArtifacts={vi.fn()}
        onAIChangeRecord={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByTestId('page-component-button'));

    await waitFor(() => {
      const [document] = onDocumentChange.mock.calls.at(-1) as [{ rootNodes: string[] }];
      expect(document.rootNodes).toHaveLength(1);
    });

    const designArea = screen.getByTestId('page-design-area');
    fireEvent.click(within(designArea).getByRole('button', { name: /button/i }));

    await waitFor(() => {
      expect(screen.getByTitle('Delete')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTitle('Delete'));

    await waitFor(() => {
      const [document] = onDocumentChange.mock.calls.at(-1) as [{ rootNodes: string[] }];
      expect(document.rootNodes).toHaveLength(0);
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

    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    await waitFor(() => {
      expect(screen.getByText(/Invalid JSON - could not parse semantic model\./i)).toBeInTheDocument();
    });
    expect(onImportArtifacts).not.toHaveBeenCalled();
  });

  it('imports artifacts through the real import workflow and records governance evidence', async () => {
    const onImportArtifacts = vi.fn();
    const onAIChangeRecord = vi.fn();

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
      target: {
        value: JSON.stringify({
          id: 'artifact-home',
          name: 'Imported Home',
          pages: [
            {
              id: 'artifact-home',
              name: 'Imported Home',
              residualIslands: [{ id: 'legacy-chart' }],
              confidence: 0.82,
              canRoundTrip: false,
              serializedBuilderDocument: {
                id: 'doc-home-v2',
                version: '1',
                name: 'Imported Home',
                designSystem: {
                  id: 'ghatana-ds-v1',
                  name: 'Ghatana Design System',
                  version: '1.0.0',
                  tokenSetIds: [],
                  componentContracts: [],
                  themeId: 'default',
                },
                rootNodes: ['button-1'],
                nodes: {
                  'button-1': {
                    id: 'button-1',
                    contractName: 'Button',
                    props: { children: 'Launch' },
                    slots: {},
                    metadata: { name: 'Launch Button' },
                  },
                },
                metadata: {
                  createdAt: '2026-05-04T00:00:00.000Z',
                  updatedAt: '2026-05-04T00:00:00.000Z',
                  author: 'import',
                  dataClassification: 'INTERNAL',
                  trustLevel: 'GENERATED_TRUSTED',
                },
              },
            },
          ],
        }),
      },
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
