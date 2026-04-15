import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../../test-utils/wrapper';
import { AmbientIntelligenceBar } from '../../../components/core/AmbientIntelligenceBar';

describe('AmbientIntelligenceBar', () => {
  it('surfaces unsupported observability summaries explicitly', async () => {
    render(<AmbientIntelligenceBar />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(
        screen.getByText(/Execution summary unavailable in the standalone launcher/i),
      ).toBeInTheDocument();
    });

    expect(
      screen.getByText(/System health summary unavailable in the standalone launcher/i),
    ).toBeInTheDocument();
  });
});