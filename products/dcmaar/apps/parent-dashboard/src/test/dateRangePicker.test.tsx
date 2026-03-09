import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DateRangePicker } from '@ghatana/ui';

describe('DateRangePicker', () => {
  it('should render quick select buttons', () => {
    const mockOnChange = vi.fn();
    render(<DateRangePicker onDateRangeChange={mockOnChange} />);

    expect(screen.getByText('Today')).toBeInTheDocument();
    expect(screen.getByText('Yesterday')).toBeInTheDocument();
    expect(screen.getByText('Last 7 Days')).toBeInTheDocument();
    expect(screen.getByText('Last 30 Days')).toBeInTheDocument();
    expect(screen.getByText('This Month')).toBeInTheDocument();
    expect(screen.getByText('Last Month')).toBeInTheDocument();
  });

  it('should call onDateRangeChange when quick select clicked', () => {
    const mockOnChange = vi.fn();
    render(<DateRangePicker onDateRangeChange={mockOnChange} />);

    const todayButton = screen.getByText('Today');
    fireEvent.click(todayButton);

    expect(mockOnChange).toHaveBeenCalledTimes(1);
    expect(mockOnChange).toHaveBeenCalledWith(
      expect.objectContaining({
        startDate: expect.any(String),
        endDate: expect.any(String),
      })
    );
  });

  it('should update selected range display', () => {
    const mockOnChange = vi.fn();
    render(<DateRangePicker onDateRangeChange={mockOnChange} />);

    const last7DaysButton = screen.getByText('Last 7 Days');
    fireEvent.click(last7DaysButton);

    // Should show selected date range
    expect(screen.getByText(/Selected:/)).toBeInTheDocument();
  });

  it('should allow custom date input', () => {
    const mockOnChange = vi.fn();
    render(<DateRangePicker onDateRangeChange={mockOnChange} />);

    const startDateInput = screen.getByLabelText(/Start Date/i);
    const endDateInput = screen.getByLabelText(/End Date/i);

    expect(startDateInput).toBeInTheDocument();
    expect(endDateInput).toBeInTheDocument();

    // Should be date inputs
    expect(startDateInput).toHaveAttribute('type', 'date');
    expect(endDateInput).toHaveAttribute('type', 'date');
  });

  it('should call onDateRangeChange when apply clicked with custom dates', () => {
    const mockOnChange = vi.fn();
    render(<DateRangePicker onDateRangeChange={mockOnChange} />);

    const startDateInput = screen.getByLabelText(/Start Date/i);
    const endDateInput = screen.getByLabelText(/End Date/i);
    const applyButton = screen.getByText(/Apply Custom Range/i);

    // Set dates
    fireEvent.change(startDateInput, { target: { value: '2024-01-01' } });
    fireEvent.change(endDateInput, { target: { value: '2024-01-15' } });
    fireEvent.click(applyButton);

    expect(mockOnChange).toHaveBeenCalledWith({
      startDate: '2024-01-01',
      endDate: '2024-01-15',
    });
  });

  it('should highlight active quick select button', () => {
    const mockOnChange = vi.fn();
    render(<DateRangePicker onDateRangeChange={mockOnChange} />);

    const todayButton = screen.getByText('Today');
    fireEvent.click(todayButton);

    // Button should have active styling (blue background)
    expect(todayButton).toHaveClass('bg-blue-600');
  });
});
