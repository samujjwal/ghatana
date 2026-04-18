import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

const { mockCapabilities } = vi.hoisted(() => ({
  mockCapabilities: {
    useCapabilityRegistry: vi.fn(),
  },
}));

vi.mock('../../api/capabilities.service', () => ({
  useCapabilityRegistry: mockCapabilities.useCapabilityRegistry,
  getCapabilitySignal: (capabilities: Array<{ key: string }> | undefined, aliases: string[]) =>
    capabilities?.find((capability) => aliases.includes(capability.key)),
}));

import { SmartWorkflowBuilder } from '../../pages/SmartWorkflowBuilder';

describe('SmartWorkflowBuilder', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows an unavailable state when ai assist is not configured', () => {
    mockCapabilities.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-builder',
        tenantId: 'tenant-alpha',
        capabilities: [
          {
            key: 'ai.assist',
            label: 'AI Assist',
            status: 'unavailable',
            summary: 'NOT_CONFIGURED',
            detail: 'AI assist is not configured in this environment.',
            rawValue: 'NOT_CONFIGURED',
          },
        ],
      },
    });

    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });

    expect(screen.getByText(/AI assist unavailable/i)).toBeInTheDocument();
    expect(screen.getByText(/AI assist is not configured in this environment./i)).toBeInTheDocument();
  });

  it('shows a degraded warning when ai assist is partially available', () => {
    mockCapabilities.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-builder',
        tenantId: 'tenant-alpha',
        capabilities: [
          {
            key: 'ai.assist',
            label: 'AI Assist',
            status: 'degraded',
            summary: 'DEGRADED',
            detail: 'LLM service is responding intermittently.',
            rawValue: 'DEGRADED',
          },
        ],
      },
    });

    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });

    expect(screen.getByText(/AI assist degraded/i)).toBeInTheDocument();
    expect(screen.getByText(/LLM service is responding intermittently./i)).toBeInTheDocument();
  });
});