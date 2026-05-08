import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

  it('applies responsive variant edits through the real PageDesigner DOM and ui-builder command path', async () => {
    const user = userEvent.setup();
    const onDocumentChange = vi.fn();

    render(
      <PageDesigner
        initialComponents={[]}
        onDocumentChange={onDocumentChange}
        onImportArtifacts={vi.fn()}
        onAIChangeRecord={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByTestId('page-component-box'));

    const designArea = screen.getByTestId('page-design-area');
    fireEvent.click(await within(designArea).findByTestId('page-box'));
    fireEvent.click(await screen.findByTitle('Edit Properties'));

    fireEvent.change(await screen.findByLabelText('Responsive breakpoint'), {
      target: { value: 'md' },
    });
    fireEvent.change(screen.getByLabelText('Responsive props JSON'), {
      target: { value: '{ "display": "grid", "gap": 2 }' },
    });
    const reviewSwitch = screen.getByRole('switch', { name: 'I have reviewed the governed change' });
    await user.click(reviewSwitch);
    await waitFor(() => {
      expect(reviewSwitch).toHaveAttribute('aria-checked', 'true');
    });
    const telemetrySwitch = screen.queryByRole('switch', { name: 'Telemetry consent has been confirmed for this change' });
    if (telemetrySwitch) {
      await user.click(telemetrySwitch);
    }
    const privacySwitch = screen.queryByRole('switch', { name: 'Privacy classification has been reviewed for this change' });
    if (privacySwitch) {
      await user.click(privacySwitch);
    }
    const applyButton = screen.getByRole('button', { name: 'Apply' });
    await waitFor(() => {
      expect(applyButton).not.toBeDisabled();
    });
    fireEvent.click(applyButton);

    await waitFor(() => {
      const responsiveCall = onDocumentChange.mock.calls.findLast(([candidateDocument]) => {
        const document = candidateDocument as {
          rootNodes: string[];
          nodes: Map<string, { metadata: { responsiveVariants?: readonly { breakpoint: string; props: Record<string, unknown> }[] } }>;
        };
        const firstNode = document.nodes.get(document.rootNodes[0] ?? '');
        return Boolean(firstNode?.metadata.responsiveVariants);
      }) as
        | [
            {
              rootNodes: string[];
              nodes: Map<string, { metadata: { responsiveVariants?: readonly { breakpoint: string; props: Record<string, unknown> }[] } }>;
            },
          ]
        | undefined;
      const [document] = responsiveCall ?? [
        onDocumentChange.mock.calls.at(-1)?.[0] as {
          rootNodes: string[];
          nodes: Map<string, { metadata: { responsiveVariants?: readonly { breakpoint: string; props: Record<string, unknown> }[] } }>;
        },
      ];
      const firstNode = document.nodes.get(document.rootNodes[0] ?? '');
      expect(firstNode?.metadata.responsiveVariants).toEqual([
        {
          breakpoint: 'md',
          props: { display: 'grid', gap: 2 },
        },
      ]);
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
