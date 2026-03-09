import React from 'react';

import { cn } from '../../utils/cn';
import { DatePicker } from '../DatePicker/DatePicker';

/**
 * Date range
 */
export interface DateRange {
  start: Date | null;
  end: Date | null;
}

/**
 * DateRangePicker component props
 */
export interface DateRangePickerProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /**
   * Current selected range
   */
  value?: DateRange;

  /**
   * Callback when range changes
   */
  onChange?: (range: DateRange) => void;

  /**
   * Minimum selectable date
   */
  minDate?: Date;

  /**
   * Maximum selectable date
   */
  maxDate?: Date;

  /**
   * Placeholder text for start date
   */
  startPlaceholder?: string;

  /**
   * Placeholder text for end date
   */
  endPlaceholder?: string;

  /**
   * Whether the picker is disabled
   */
  disabled?: boolean;

  /**
   * Whether the picker is read-only
   */
  readOnly?: boolean;

  /**
   * Size variant
   */
  size?: 'small' | 'medium' | 'large';

  /**
   * Date format
   */
  format?: string;
}

/**
 * DateRangePicker component - Select a date range
 */
export const DateRangePicker = React.forwardRef<HTMLDivElement, DateRangePickerProps>((props, _ref) => {
  const {
    value = { start: null, end: null },
    onChange,
    minDate,
    maxDate,
    startPlaceholder = 'Start date',
    endPlaceholder = 'End date',
    disabled = false,
    readOnly = false,
    size = 'medium',
    format = 'MM/DD/YYYY',
    className,
    ...rest
  } = props;

  // Note: We accept the forwarded ref parameter to satisfy React's forwardRef
  // signature even though this wrapper doesn't attach it directly.

  const handleStartChange = (date: Date | null) => {
    const newRange: DateRange = { start: date, end: value.end };
    
    // If end date is before start date, clear end date
    if (date && value.end && date > value.end) {
      newRange.end = null;
    }
    
    onChange?.(newRange);
  };

  const handleEndChange = (date: Date | null) => {
    const newRange: DateRange = { start: value.start, end: date };
    
    // If start date is after end date, swap them
    if (date && value.start && date < value.start) {
      newRange.start = date;
      newRange.end = value.start;
    }
    
    onChange?.(newRange);
  };

  // Disable dates before start date for end picker
  const shouldDisableEndDate = (date: Date) => {
    if (!value.start) return false;
    return date < value.start;
  };

  // Disable dates after end date for start picker
  const shouldDisableStartDate = (date: Date) => {
    if (!value.end) return false;
    return date > value.end;
  };

  return (
    <div className={cn('flex items-center gap-2', className)} {...rest}>
      <DatePicker
        value={value.start}
        onChange={handleStartChange}
        minDate={minDate}
        maxDate={value.end || maxDate}
        shouldDisableDate={shouldDisableStartDate}
        placeholder={startPlaceholder}
        disabled={disabled}
        readOnly={readOnly}
        size={size}
        format={format}
      />
      
      <span className="text-grey-400">→</span>
      
      <DatePicker
        value={value.end}
        onChange={handleEndChange}
        minDate={value.start || minDate}
        maxDate={maxDate}
        shouldDisableDate={shouldDisableEndDate}
        placeholder={endPlaceholder}
        disabled={disabled}
        readOnly={readOnly}
        size={size}
        format={format}
      />
    </div>
  );
});

DateRangePicker.displayName = 'DateRangePicker';
