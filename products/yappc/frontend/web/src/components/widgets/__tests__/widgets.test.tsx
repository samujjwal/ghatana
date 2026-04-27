/**
 * Tests for widgets/ components:
 * KPICard, TableWidget
 * (ChartWidget skipped — requires Recharts canvas/SVG rendering in jsdom)
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { KPICard } from '../KPICard';
import { TableWidget } from '../TableWidget';

// ─── KPICard ──────────────────────────────────────────────────────────────────

describe('KPICard', () => {
  it('renders label', () => {
    render(<KPICard label="Active Users" value={1000} />);
    expect(screen.getByText('Active Users')).toBeTruthy();
  });

  it('renders formatted value (< 1000)', () => {
    render(<KPICard label="Count" value={42} />);
    expect(screen.getByText('42')).toBeTruthy();
  });

  it('renders formatted value (K)', () => {
    render(<KPICard label="Downloads" value={12345} />);
    expect(screen.getByText('12.3K')).toBeTruthy();
  });

  it('renders formatted value (M)', () => {
    render(<KPICard label="Views" value={1234567} />);
    expect(screen.getByText('1.2M')).toBeTruthy();
  });

  it('renders loading skeleton', () => {
    render(<KPICard label="Metric" value={0} isLoading />);
    expect(screen.getByTestId('kpi-skeleton')).toBeTruthy();
  });

  it('renders up trend icon', () => {
    render(<KPICard label="Revenue" value={500} trend="up" changePercent={10} />);
    expect(screen.getByTestId('trend-up-icon')).toBeTruthy();
    expect(screen.getByText('+10%')).toBeTruthy();
  });

  it('renders down trend icon', () => {
    render(<KPICard label="Churn" value={50} trend="down" changePercent={-5} />);
    expect(screen.getByTestId('trend-down-icon')).toBeTruthy();
    expect(screen.getByText('-5%')).toBeTruthy();
  });

  it('renders neutral trend without arrows', () => {
    render(<KPICard label="Stable" value={100} trend="neutral" changePercent={0} />);
    expect(screen.queryByTestId('trend-up-icon')).toBeNull();
    expect(screen.queryByTestId('trend-down-icon')).toBeNull();
  });

  it('renders with role=button when onClick provided', () => {
    const onClick = vi.fn();
    render(<KPICard label="Clickable" value={99} onClick={onClick} />);
    expect(screen.getByRole('button')).toBeTruthy();
  });

  it('calls onClick when clicked', () => {
    const onClick = vi.fn();
    render(<KPICard label="Metric" value={10} onClick={onClick} />);
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledOnce();
  });
});

// ─── TableWidget ──────────────────────────────────────────────────────────────

const columns = [
  { key: 'name', label: 'Name', sortable: true },
  { key: 'role', label: 'Role' },
  { key: 'status', label: 'Status' },
];

const data = [
  { id: '1', name: 'Alice', role: 'Admin', status: 'Active' },
  { id: '2', name: 'Bob', role: 'Editor', status: 'Inactive' },
  { id: '3', name: 'Carol', role: 'Viewer', status: 'Active' },
];

describe('TableWidget', () => {
  it('renders column headers', () => {
    render(<TableWidget data={data} columns={columns} />);
    expect(screen.getByText('Name')).toBeTruthy();
    expect(screen.getByText('Role')).toBeTruthy();
    expect(screen.getByText('Status')).toBeTruthy();
  });

  it('renders row data', () => {
    render(<TableWidget data={data} columns={columns} />);
    expect(screen.getByText('Alice')).toBeTruthy();
    expect(screen.getByText('Bob')).toBeTruthy();
    expect(screen.getByText('Carol')).toBeTruthy();
  });

  it('renders filter input when filterable=true', () => {
    render(<TableWidget data={data} columns={columns} filterable />);
    expect(screen.getByPlaceholderText(/filter/i)).toBeTruthy();
  });

  it('filters rows on input', () => {
    render(<TableWidget data={data} columns={columns} filterable />);
    const input = screen.getByPlaceholderText(/filter/i);
    fireEvent.change(input, { target: { value: 'Alice' } });
    expect(screen.getByText('Alice')).toBeTruthy();
    expect(screen.queryByText('Bob')).toBeNull();
  });

  it('renders export button when exportable=true', () => {
    render(<TableWidget data={data} columns={columns} exportable />);
    expect(screen.getByText(/export/i)).toBeTruthy();
  });

  it('calls onExport when export clicked', () => {
    const onExport = vi.fn();
    render(<TableWidget data={data} columns={columns} exportable onExport={onExport} />);
    fireEvent.click(screen.getByText(/export/i));
    expect(onExport).toHaveBeenCalledOnce();
  });

  it('renders empty state when data is empty', () => {
    render(<TableWidget data={[]} columns={columns} />);
    // No rows should render
    expect(screen.queryByText('Alice')).toBeNull();
  });

  it('renders checkboxes when selectable=true', () => {
    render(<TableWidget data={data} columns={columns} selectable />);
    const checkboxes = screen.getAllByRole('checkbox');
    // One per row + one header select-all
    expect(checkboxes.length).toBeGreaterThanOrEqual(data.length);
  });

  it('sorts on column header click', () => {
    render(<TableWidget data={data} columns={columns} />);
    // Click the Name header
    fireEvent.click(screen.getByText('Name'));
    // Should still render rows (no crash)
    expect(screen.getByText('Alice')).toBeTruthy();
  });
});
