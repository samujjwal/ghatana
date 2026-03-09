import React from 'react';

type BaseProps = {
  className?: string;
  children?: React.ReactNode;
};

type CardProps = BaseProps & {
  title?: string;
  description?: string;
};

export const Card: React.FC<CardProps> = ({ className = '', children, title, description }) => (
  <div className={`rounded-lg border border-slate-200 bg-white p-4 ${className}`.trim()}>
    {title ? <h3 className="text-base font-semibold text-slate-900">{title}</h3> : null}
    {description ? <p className="text-sm text-slate-500">{description}</p> : null}
    {children}
  </div>
);

type StatusBadgeProps = BaseProps & {
  status?: string;
};

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, className = '', children }) => (
  <span className={`inline-flex items-center rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700 ${className}`.trim()}>
    {status ?? children}
  </span>
);

type SkeletonProps = {
  count?: number;
  height?: string;
  className?: string;
};

export const Skeleton: React.FC<SkeletonProps> = ({ count = 1, height = 'h-4', className = '' }) => (
  <>
    {Array.from({ length: count }).map((_, index) => (
      <div
        // eslint-disable-next-line react/no-array-index-key
        key={index}
        className={`animate-pulse rounded bg-slate-200 ${height} ${className}`.trim()}
      />
    ))}
  </>
);

type ToggleProps = {
  checked?: boolean;
  onChange?: (checked: boolean) => void;
  label?: string;
} & BaseProps;

export const Toggle: React.FC<ToggleProps> = ({ checked, onChange, label, className = '' }) => (
  <label className={`inline-flex cursor-pointer items-center gap-2 ${className}`.trim()}>
    <input
      type="checkbox"
      className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
      checked={checked}
      onChange={(event) => onChange?.(event.target.checked)}
    />
    {label ? <span className="text-sm text-slate-600">{label}</span> : null}
  </label>
);

type SelectOption = {
  label: string;
  value: string;
};

type SelectProps = {
  value?: string;
  onChange?: (value: string) => void;
  options?: SelectOption[];
} & BaseProps;

export const Select: React.FC<SelectProps> = ({ value, onChange, options: optionsProp, className = '' }) => {
  const options: SelectOption[] = optionsProp ?? [];

  return (
    <select
      className={`rounded border border-slate-300 px-3 py-2 text-sm text-slate-700 focus:border-blue-500 focus:outline-none ${className}`.trim()}
      value={value}
      onChange={(event) => onChange?.(event.target.value)}
    >
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
};

type ButtonProps = BaseProps & {
  onClick?: () => void;
  variant?: 'primary' | 'secondary' | 'ghost';
  type?: 'button' | 'submit' | 'reset';
};

export const Button: React.FC<ButtonProps> = ({
  className = '',
  children,
  onClick,
  variant = 'secondary',
  type = 'button',
}) => {
  const variantClass =
    variant === 'primary'
      ? 'bg-blue-600 text-white hover:bg-blue-700'
      : variant === 'ghost'
        ? 'text-slate-600 hover:bg-slate-100'
        : 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50';

  return (
    <button
      type={type}
      className={`rounded-md px-3 py-2 text-sm font-medium transition ${variantClass} ${className}`.trim()}
      onClick={onClick}
    >
      {children}
    </button>
  );
};

type BadgeProps = BaseProps & {
  variant?: 'info' | 'success' | 'warning' | 'danger';
};

export const Badge: React.FC<BadgeProps> = ({ variant = 'info', className = '', children }) => {
  const styles: Record<string, string> = {
    info: 'bg-blue-100 text-blue-700',
    success: 'bg-emerald-100 text-emerald-700',
    warning: 'bg-amber-100 text-amber-700',
    danger: 'bg-rose-100 text-rose-700',
  };

  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${styles[variant]} ${className}`.trim()}>
      {children}
    </span>
  );
};

type InputProps = BaseProps & {
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  type?: string;
};

export const Input: React.FC<InputProps> = ({
  value,
  onChange,
  placeholder,
  type = 'text',
  className = '',
}) => (
  <input
    type={type}
    value={value}
    placeholder={placeholder}
    className={`w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-700 focus:border-blue-500 focus:outline-none ${className}`.trim()}
    onChange={(event) => onChange?.(event.target.value)}
  />
);
