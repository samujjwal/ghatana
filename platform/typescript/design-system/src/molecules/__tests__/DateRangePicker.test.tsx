import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DateRangePicker, type DateRange, type DateRangePreset } from '../DateRangePicker';

describe('DateRangePicker', () => {
  const mockOnChange = vi.fn();

  beforeEach(() => {
    mockOnChange.mockClear();
  });

  describe('Initial Rendering', () => {
    it('should render with default date range when no initialRange provided', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      expect(screen.getByLabelText('Start date')).toBeInTheDocument();
      expect(screen.getByLabelText('End date')).toBeInTheDocument();
    });

    it('should render with provided initialRange', () => {
      const initialRange: DateRange = {
        startDate: '2025-01-01',
        endDate: '2025-01-31',
      };

      render(<DateRangePicker onDateRangeChange={mockOnChange} initialRange={initialRange} />);

      const startInput = screen.getByLabelText('Start date') as HTMLInputElement;
      const endInput = screen.getByLabelText('End date') as HTMLInputElement;

      expect(startInput.value).toBe('2025-01-01');
      expect(endInput.value).toBe('2025-01-31');
    });

    it('should display all default preset buttons', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      expect(screen.getByRole('button', { name: 'Today' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Yesterday' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Last 7 Days' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Last 30 Days' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'This Month' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Last Month' })).toBeInTheDocument();
    });

    it('should show selected range display by default', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      expect(screen.getByText(/Selected:/)).toBeInTheDocument();
    });
  });

  describe('Preset Selection', () => {
    it('should call onDateRangeChange when Today preset is clicked', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      const todayButton = screen.getByRole('button', { name: 'Today' });
      fireEvent.click(todayButton);

      expect(mockOnChange).toHaveBeenCalledTimes(1);
      const call = mockOnChange.mock.calls[0][0] as DateRange;
      expect(call.startDate).toBe(call.endDate);
    });

    it('should call onDateRangeChange when Last 7 Days preset is clicked', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      const last7DaysButton = screen.getByRole('button', { name: 'Last 7 Days' });
      fireEvent.click(last7DaysButton);

      expect(mockOnChange).toHaveBeenCalledTimes(1);
      const call = mockOnChange.mock.calls[0][0] as DateRange;
      expect(call.startDate).toBeTruthy();
      expect(call.endDate).toBeTruthy();
    });

    it('should highlight selected preset button', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      const todayButton = screen.getByRole('button', { name: 'Today' });
      fireEvent.click(todayButton);

      expect(todayButton).toHaveAttribute('aria-pressed', 'true');
      expect(todayButton).toHaveClass('bg-blue-600', 'text-white');
    });

    it('should update date inputs when preset is selected', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      const todayButton = screen.getByRole('button', { name: 'Today' });
      fireEvent.click(todayButton);

      const startInput = screen.getByLabelText('Start date') as HTMLInputElement;
      const endInput = screen.getByLabelText('End date') as HTMLInputElement;

      expect(startInput.value).toBe(endInput.value);
      expect(startInput.value).toBeTruthy();
    });
  });

  describe('Custom Date Selection', () => {
    it('should update start date when changed', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      const startInput = screen.getByLabelText('Start date') as HTMLInputElement;
      fireEvent.change(startInput, { target: { value: '2025-01-15' } });

      expect(startInput.value).toBe('2025-01-15');
    });

    it('should update end date when changed', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      const endInput = screen.getByLabelText('End date') as HTMLInputElement;
      fireEvent.change(endInput, { target: { value: '2025-01-31' } });

      expect(endInput.value).toBe('2025-01-31');
    });

    it('should call onDateRangeChange when both dates are set', () => {
      render(
        <DateRangePicker
          onDateRangeChange={mockOnChange}
          initialRange={{ startDate: '2025-01-01', endDate: '2025-01-01' }}
        />
      );

      const startInput = screen.getByLabelText('Start date');
      fireEvent.change(startInput, { target: { value: '2025-01-15' } });

      expect(mockOnChange).toHaveBeenCalledWith({
        startDate: '2025-01-15',
        endDate: '2025-01-01',
      });
    });

    it('should show Apply button when custom dates are entered', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      const startInput = screen.getByLabelText('Start date');
      fireEvent.change(startInput, { target: { value: '2025-01-01' } });

      expect(screen.getByRole('button', { name: 'Apply custom date range' })).toBeInTheDocument();
    });

    it('should call onDateRangeChange when Apply button is clicked', () => {
      render(
        <DateRangePicker
          onDateRangeChange={mockOnChange}
          initialRange={{ startDate: '2025-01-01', endDate: '2025-01-31' }}
        />
      );

      // Trigger custom mode
      const startInput = screen.getByLabelText('Start date');
      fireEvent.change(startInput, { target: { value: '2025-01-15' } });
      mockOnChange.mockClear();

      const applyButton = screen.getByRole('button', { name: 'Apply custom date range' });
      fireEvent.click(applyButton);

      expect(mockOnChange).toHaveBeenCalledWith({
        startDate: '2025-01-15',
        endDate: '2025-01-31',
      });
    });

    it('should disable Apply button when dates are invalid', () => {
      render(
        <DateRangePicker
          onDateRangeChange={mockOnChange}
          initialRange={{ startDate: '', endDate: '' }}
          showApplyButton={true}
        />
      );

      // Trigger custom mode
      const startInput = screen.getByLabelText('Start date');
      fireEvent.change(startInput, { target: { value: '2025-01-01' } });

      const applyButton = screen.getByRole('button', { name: 'Apply custom date range' });
      expect(applyButton).toBeDisabled();
    });
  });

  describe('Date Validation', () => {
    it('should respect minDate constraint', () => {
      const minDate = '2025-01-01';
      render(<DateRangePicker onDateRangeChange={mockOnChange} minDate={minDate} />);

      const startInput = screen.getByLabelText('Start date') as HTMLInputElement;
      expect(startInput.min).toBe(minDate);
    });

    it('should respect maxDate constraint', () => {
      const maxDate = '2025-12-31';
      render(<DateRangePicker onDateRangeChange={mockOnChange} maxDate={maxDate} />);

      const endInput = screen.getByLabelText('End date') as HTMLInputElement;
      expect(endInput.max).toBe(maxDate);
    });

    it('should set end date max to start date for validation', () => {
      render(
        <DateRangePicker
          onDateRangeChange={mockOnChange}
          initialRange={{ startDate: '2025-01-15', endDate: '2025-01-31' }}
        />
      );

      const startInput = screen.getByLabelText('Start date') as HTMLInputElement;
      const endInput = screen.getByLabelText('End date') as HTMLInputElement;

      expect(endInput.min).toBe(startInput.value);
    });
  });

  describe('Custom Presets', () => {
    it('should render custom presets when provided', () => {
      const customPresets: DateRangePreset[] = [
        {
          id: 'q1',
          label: 'Q1 2025',
          getValue: () => ({ startDate: '2025-01-01', endDate: '2025-03-31' }),
        },
        {
          id: 'q2',
          label: 'Q2 2025',
          getValue: () => ({ startDate: '2025-04-01', endDate: '2025-06-30' }),
        },
      ];

      render(<DateRangePicker onDateRangeChange={mockOnChange} presets={customPresets} />);

      expect(screen.getByRole('button', { name: 'Q1 2025' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Q2 2025' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Today' })).not.toBeInTheDocument();
    });

    it('should call preset getValue when custom preset is clicked', () => {
      const customPresets: DateRangePreset[] = [
        {
          id: 'custom',
          label: 'Custom Range',
          getValue: () => ({ startDate: '2025-01-01', endDate: '2025-12-31' }),
        },
      ];

      render(<DateRangePicker onDateRangeChange={mockOnChange} presets={customPresets} />);

      const customButton = screen.getByRole('button', { name: 'Custom Range' });
      fireEvent.click(customButton);

      expect(mockOnChange).toHaveBeenCalledWith({
        startDate: '2025-01-01',
        endDate: '2025-12-31',
      });
    });
  });

  describe('Disabled State', () => {
    it('should disable all controls when disabled prop is true', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} disabled={true} />);

      const todayButton = screen.getByRole('button', { name: 'Today' });
      const startInput = screen.getByLabelText('Start date');
      const endInput = screen.getByLabelText('End date');

      expect(todayButton).toBeDisabled();
      expect(startInput).toBeDisabled();
      expect(endInput).toBeDisabled();
    });

    it('should not trigger onChange when disabled', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} disabled={true} />);

      const todayButton = screen.getByRole('button', { name: 'Today' });
      fireEvent.click(todayButton);

      expect(mockOnChange).not.toHaveBeenCalled();
    });
  });

  describe('Optional Display Features', () => {
    it('should hide Apply button when showApplyButton is false', () => {
      render(
        <DateRangePicker
          onDateRangeChange={mockOnChange}
          showApplyButton={false}
          initialRange={{ startDate: '2025-01-01', endDate: '2025-01-31' }}
        />
      );

      // Trigger custom mode
      const startInput = screen.getByLabelText('Start date');
      fireEvent.change(startInput, { target: { value: '2025-01-15' } });

      expect(screen.queryByRole('button', { name: 'Apply custom date range' })).not.toBeInTheDocument();
    });

    it('should hide selected range display when showSelectedRange is false', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} showSelectedRange={false} />);

      expect(screen.queryByText(/Selected:/)).not.toBeInTheDocument();
    });
  });

  describe('Custom ClassName', () => {
    it('should apply custom className', () => {
      const { container } = render(
        <DateRangePicker onDateRangeChange={mockOnChange} className="custom-class" />
      );

      const picker = container.querySelector('.custom-class');
      expect(picker).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA labels for date inputs', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      expect(screen.getByLabelText('Start date')).toBeInTheDocument();
      expect(screen.getByLabelText('End date')).toBeInTheDocument();
    });

    it('should have aria-pressed on preset buttons', () => {
      render(<DateRangePicker onDateRangeChange={mockOnChange} />);

      const todayButton = screen.getByRole('button', { name: 'Today' });
      fireEvent.click(todayButton);

      expect(todayButton).toHaveAttribute('aria-pressed', 'true');
    });

    it('should have aria-label on Apply button', () => {
      render(
        <DateRangePicker
          onDateRangeChange={mockOnChange}
          initialRange={{ startDate: '2025-01-01', endDate: '2025-01-31' }}
        />
      );

      // Trigger custom mode
      const startInput = screen.getByLabelText('Start date');
      fireEvent.change(startInput, { target: { value: '2025-01-15' } });

      const applyButton = screen.getByRole('button', { name: 'Apply custom date range' });
      expect(applyButton).toHaveAttribute('aria-label', 'Apply custom date range');
    });
  });
});
