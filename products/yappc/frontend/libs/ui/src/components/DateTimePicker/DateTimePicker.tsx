import React from 'react';

import { cn } from '../../utils/cn';
import { DatePicker } from '../DatePicker/DatePicker';
import { TimePicker } from '../TimePicker/TimePicker';

/**
 * DateTimePicker component props
 */
export interface DateTimePickerProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /**
   * Current selected date-time
   */
  value?: Date | null;

  /**
   * Callback when date-time changes
   */
  onChange?: (dateTime: Date | null) => void;

  /**
   * Minimum selectable date
   */
  minDate?: Date;

  /**
   * Maximum selectable date
   */
  maxDate?: Date;

  /**
   * Time format (12-hour or 24-hour)
   * @default '12h'
   */
  timeFormat?: '12h' | '24h';

  /**
   * Date format
   * @default 'MM/DD/YYYY'
   */
  dateFormat?: string;

  /**
   * Minute step interval
   * @default 1
   */
  minuteStep?: number;

  /**
   * Placeholder text for date
   */
  datePlaceholder?: string;

  /**
   * Placeholder text for time
   */
  timePlaceholder?: string;

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
}

/**
 * DateTimePicker component - Combined date and time selection
 */
export const DateTimePicker = React.forwardRef<HTMLDivElement, DateTimePickerProps>((props, _ref) => {
  const {
    value,
    onChange,
    minDate,
    maxDate,
    timeFormat = '12h',
    dateFormat = 'MM/DD/YYYY',
    minuteStep = 1,
    datePlaceholder = 'Select date',
    timePlaceholder = 'Select time',
    disabled = false,
    readOnly = false,
    size = 'medium',
    className,
    ...rest
  } = props;

  const handleDateChange = (date: Date | null) => {
    if (!date) {
      onChange?.(null);
      return;
    }

    // Preserve time if exists
    if (value) {
      const combined = new Date(date);
      combined.setHours(value.getHours(), value.getMinutes(), 0, 0);
      onChange?.(combined);
    } else {
      // Set default time to current time
      const now = new Date();
      date.setHours(now.getHours(), now.getMinutes(), 0, 0);
      onChange?.(date);
    }
  };

  const handleTimeChange = (time: Date | null) => {
    if (!time) return;

    // Combine with existing date or use today
    const combined = value ? new Date(value) : new Date();
    combined.setHours(time.getHours(), time.getMinutes(), 0, 0);
    onChange?.(combined);
  };

  return (
    <div className={cn('flex items-center gap-2', className)} {...rest}>
      <DatePicker
        value={value}
        onChange={handleDateChange}
        minDate={minDate}
        maxDate={maxDate}
        placeholder={datePlaceholder}
        disabled={disabled}
        readOnly={readOnly}
        size={size}
        format={dateFormat}
      />

      <span className="text-grey-400">@</span>

      <TimePicker
        value={value}
        onChange={handleTimeChange}
        format={timeFormat}
        minuteStep={minuteStep}
        placeholder={timePlaceholder}
        disabled={disabled}
        readOnly={readOnly}
        size={size}
      />
    </div>
  );
});

DateTimePicker.displayName = 'DateTimePicker';
