import { describe, expect, it, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import { render, screen } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import { SmartWorkflowBuilder } from '../../pages/SmartWorkflowBuilder';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';
import { smartWorkflowGenerationBoundary } from '@/components/common/unsupportedSurfaceRegistry';

vi.mock('../../api/surfaces.service', () => ({
  useSurfaceRegistry: () => ({
    data: {
      generatedAt: '2026-04-17T12:00:00Z',
      requestId: 'req-smart-workflow',
      tenantId: TEST_TENANT_ID,
      surfaces: [
        {
          key: 'ai_assist',
          label: 'AI Assist',
          status: 'UNAVAILABLE',
          summary: 'UNAVAILABLE',
          detail: smartWorkflowGenerationBoundary.details[1],
          rawValue: 'UNAVAILABLE',
        },
      ],
    },
  }),
  getSurfaceSignal: (surfaces: Array<{ key: string }> | undefined, aliases: string[]) =>
    surfaces?.find((surface) => aliases.includes(surface.key)),
}));

describe('SmartWorkflowBuilder boundary', () => {
  it('renders the shared unsupported boundary when AI draft generation is unavailable', async () => {
    const user = userEvent.setup();

    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });

    await user.type(
      screen.getByPlaceholderText(/Load data from S3/i),
      'Load data from S3, clean email addresses, save to PostgreSQL',
    );
    await user.click(screen.getByRole('button', { name: /Generate Draft/i }));

    expect(await screen.findByText(/Temporarily unavailable/i)).toBeInTheDocument();
    expect(screen.getByText(smartWorkflowGenerationBoundary.summary)).toBeInTheDocument();
    expect(screen.getAllByText(smartWorkflowGenerationBoundary.details[1]).length).toBeGreaterThan(0);
  });
});