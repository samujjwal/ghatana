import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { createFallbackRenderer } from '../rendererManifest';
import type { ComponentInstance } from '@ghatana/ui-builder';

describe('createFallbackRenderer', () => {
  it('surfaces unknown components as reviewable residual islands', () => {
    const fallback = createFallbackRenderer();
    const instance = {
      id: 'legacy-chart',
      contractName: 'LegacyChart',
      props: {
        title: 'Revenue',
      },
      slots: {},
      bindings: [],
      metadata: {
        sourceLocation: 'src/pages/Dashboard.tsx:42',
        residualReason: 'Unsupported charting library detected during import.',
        confidence: 0.73,
        suggestedContractName: 'Chart',
      },
    } satisfies ComponentInstance;

    render(
      <>
        {fallback.render(instance, { default: null }, { mode: 'preview' })}
      </>,
    );

    expect(screen.getByTestId('fallback-renderer-LegacyChart')).toHaveAttribute(
      'aria-label',
      'Review unknown component LegacyChart',
    );
    expect(screen.getByText(/Unsupported charting library detected/)).toBeInTheDocument();
    expect(screen.getByText('Source: src/pages/Dashboard.tsx:42')).toBeInTheDocument();
    expect(screen.getByText('Confidence: 73%')).toBeInTheDocument();
    expect(screen.getByText('Suggested registry contract: Chart')).toBeInTheDocument();
    expect(screen.getByText(/Action needed: register a renderer/)).toBeInTheDocument();
  });
});
