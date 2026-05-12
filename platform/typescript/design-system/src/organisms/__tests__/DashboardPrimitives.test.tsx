import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import {
  ActivityLog,
  ChartPanel,
  DashboardGrid,
  DataCard,
  FilterBar,
  KpiStrip,
} from '../DashboardPrimitives';

describe('DashboardPrimitives', () => {
  it('renders a responsive dashboard grid and KPI strip', () => {
    render(
      <DashboardGrid data-testid="grid" density="compact">
        <KpiStrip columns={3}>
          <DataCard title="Revenue" value="$20k" />
          <DataCard title="Conversion" value="8%" />
          <DataCard title="Risk" value="Low" />
        </KpiStrip>
      </DashboardGrid>
    );

    expect(screen.getByTestId('grid')).toHaveClass('lg:grid-cols-12');
    expect(screen.getByText('Revenue')).toBeInTheDocument();
    expect(screen.getByText('$20k')).toBeInTheDocument();
  });

  it('renders filter bar summary and actions', () => {
    render(
      <FilterBar
        summary="3 filters active"
        actions={<button type="button">Reset</button>}
      >
        <label htmlFor="status-filter">Status</label>
        <select id="status-filter">
          <option>Open</option>
        </select>
      </FilterBar>
    );

    expect(screen.getByLabelText('Status')).toBeInTheDocument();
    expect(screen.getByText('3 filters active')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reset' })).toBeInTheDocument();
  });

  it('renders chart and activity panels with controls', () => {
    const { rerender } = render(
      <ChartPanel
        title="Pipeline"
        description="Qualified opportunities by stage"
        controls={<button type="button">Export</button>}
      >
        <div role="img" aria-label="Pipeline chart" />
      </ChartPanel>
    );

    expect(screen.getByRole('heading', { name: 'Pipeline' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Pipeline chart' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export' })).toBeInTheDocument();

    rerender(
      <ActivityLog title="Recent activity">
        <p>Budget approved</p>
      </ActivityLog>
    );

    expect(screen.getByRole('heading', { name: 'Recent activity' })).toBeInTheDocument();
    expect(screen.getByText('Budget approved')).toBeInTheDocument();
  });
});
