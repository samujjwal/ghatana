import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { Analytics } from '../components/Analytics';
import { renderWithDashboardProviders } from './utils/renderWithProviders';

// NOTE: Do NOT mock Jotai - use renderWithDashboardProviders which properly wraps in JotaiProvider

describe('Analytics Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render analytics title', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('Analytics & Insights')).toBeInTheDocument();
  });

  it('should display time range selector', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    const select = screen.getByRole('combobox');
    expect(select).toBeInTheDocument();
  });

  it('should display usage overview section', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('Usage Overview')).toBeInTheDocument();
  });

  it('should display three usage statistics cards', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('Total Usage')).toBeInTheDocument();
    expect(screen.getByText('Active Devices')).toBeInTheDocument();
    expect(screen.getByText('Avg. Per Device')).toBeInTheDocument();
  });

  it('should display block activity section', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('Block Activity')).toBeInTheDocument();
  });

  it('should display two block statistics cards', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('Total Blocks')).toBeInTheDocument();
    expect(screen.getByText('Unique Items Blocked')).toBeInTheDocument();
  });

  it('should display top apps section', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('Top Apps by Usage')).toBeInTheDocument();
  });

  it('should display most blocked items section', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('Most Blocked Items')).toBeInTheDocument();
  });

  it('should display block reasons section', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('Block Reasons')).toBeInTheDocument();
  });

  it('should display summary insights section', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('📊 Summary Insights')).toBeInTheDocument();
  });
});
