import React from 'react';

import { Avatar as BaseAvatar, AvatarGroup } from './Avatar';
import { Badge } from './Badge';
import { Box } from './Box';
import { Button } from './Button';
import { Card, CardContent } from './Card';
import { Checkbox } from './Checkbox';
import { Chip } from './Chip';
import { Dialog as BaseDialog, DialogTitle } from './Dialog';
import { Divider } from './Divider';
import { Drawer as BaseDrawer } from './Drawer';
import { Spinner } from './Spinner';
import { Stack } from './Stack';
import { TextField } from './TextField';
import { Typography } from './Typography';

type CompatElementProps = React.HTMLAttributes<HTMLElement> & {
  children?: React.ReactNode;
  className?: string;
};

type TriggerProps = CompatElementProps & {
  asChild?: boolean;
  onClick?: React.MouseEventHandler<HTMLElement>;
};

type TooltipContentProps = CompatElementProps & {
  side?: 'top' | 'right' | 'bottom' | 'left';
};

type DropdownMenuContentProps = CompatElementProps & {
  align?: 'start' | 'center' | 'end';
};

type DropdownMenuItemProps = CompatElementProps & {
  onClick?: React.MouseEventHandler<HTMLButtonElement>;
  disabled?: boolean;
};

type DropdownMenuCheckboxItemProps = DropdownMenuItemProps & {
  checked?: boolean;
  onCheckedChange?: (checked: boolean) => void;
};

const renderTrigger = ({
  asChild,
  children,
  className,
  onClick,
}: TriggerProps): React.ReactElement => {
  if (asChild && React.isValidElement(children)) {
    const child = children as React.ReactElement<{
      className?: string;
      onClick?: React.MouseEventHandler<HTMLElement>;
    }>;
    return React.cloneElement(children, {
      className: [child.props.className, className]
        .filter(Boolean)
        .join(' '),
      onClick: onClick ?? child.props.onClick,
    } as Partial<React.HTMLAttributes<HTMLElement>>);
  }

  return (
    <span className={className} onClick={onClick}>
      {children}
    </span>
  );
};

export function DropdownMenu({ children, className }: CompatElementProps) {
  return <div className={className}>{children}</div>;
}

export function DropdownMenuTrigger(props: TriggerProps) {
  return renderTrigger(props);
}

export function DropdownMenuContent({
  children,
  className,
  align,
}: DropdownMenuContentProps) {
  return (
    <div className={className} data-align={align} role="menu">
      {children}
    </div>
  );
}

export function DropdownMenuItem({
  children,
  className,
  onClick,
  disabled,
}: DropdownMenuItemProps) {
  return (
    <button
      className={className}
      disabled={disabled}
      onClick={onClick}
      role="menuitem"
      type="button"
    >
      {children}
    </button>
  );
}

export function DropdownMenuCheckboxItem({
  children,
  className,
  checked = false,
  onCheckedChange,
  disabled,
}: DropdownMenuCheckboxItemProps) {
  return (
    <button
      aria-checked={checked}
      className={className}
      disabled={disabled}
      onClick={() => onCheckedChange?.(!checked)}
      role="menuitemcheckbox"
      type="button"
    >
      {children}
    </button>
  );
}

export function DropdownMenuSeparator({ className }: CompatElementProps) {
  return <hr className={className} role="separator" />;
}

export function DropdownMenuLabel({ children, className }: CompatElementProps) {
  return <div className={className}>{children}</div>;
}

export function TooltipTrigger(props: TriggerProps) {
  return renderTrigger(props);
}

export function TooltipContent({
  children,
  className,
  side,
}: TooltipContentProps) {
  return (
    <span className={className} data-side={side} role="tooltip">
      {children}
    </span>
  );
}

export function DialogHeader({ children, className }: CompatElementProps) {
  return <div className={className}>{children}</div>;
}

export function DialogTrigger(props: TriggerProps) {
  return renderTrigger(props);
}

export function ScrollArea({ children, className }: CompatElementProps) {
  return <div className={className}>{children}</div>;
}

type AvatarSlotProps = CompatElementProps & {
  src?: string;
  alt?: string;
};

export function AvatarImage({ className, src, alt }: AvatarSlotProps) {
  if (!src) return null;
  return <img className={className} src={src} alt={alt ?? ''} />;
}

export function AvatarFallback({ children, className }: CompatElementProps) {
  return <span className={className}>{children}</span>;
}

export function CardTitle({ children, className }: CompatElementProps) {
  return <h3 className={className}>{children}</h3>;
}

export function CardDescription({ children, className }: CompatElementProps) {
  return <p className={className}>{children}</p>;
}

type TabsSlotProps = CompatElementProps & {
  value?: string;
  onClick?: React.MouseEventHandler<HTMLButtonElement>;
};

export function TabsContent({ children, className, value }: TabsSlotProps) {
  return (
    <div className={className} data-value={value}>
      {children}
    </div>
  );
}

export function TabsTrigger({
  children,
  className,
  value,
  onClick,
}: TabsSlotProps) {
  return (
    <button
      className={className}
      data-value={value}
      onClick={onClick}
      type="button"
    >
      {children}
    </button>
  );
}

type CollapsibleProps = CompatElementProps & {
  open?: boolean;
  defaultOpen?: boolean;
  onOpenChange?: (open: boolean) => void;
};

export function Collapsible({
  children,
  className,
  open,
  defaultOpen,
}: CollapsibleProps) {
  return (
    <div
      className={className}
      data-state={open ?? defaultOpen ? 'open' : 'closed'}
    >
      {children}
    </div>
  );
}

export function CollapsibleContent({ children, className }: CompatElementProps) {
  return <div className={className}>{children}</div>;
}

export function CollapsibleTrigger(props: TriggerProps) {
  return renderTrigger(props);
}

type IconButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  size?: 'sm' | 'md' | 'lg' | 'small' | 'medium' | 'large' | 'icon';
  color?: string;
  edge?: 'start' | 'end' | false | string;
  tone?: string;
};

export const IconButton = React.forwardRef<HTMLButtonElement, IconButtonProps>(
  ({ children, className, size = 'md', color: _color, type = 'button', ...props }, ref) => (
    <button
      ref={ref}
      type={type}
      className={[
        'inline-flex items-center justify-center rounded-md transition-colors hover:bg-grey-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
        size === 'sm' || size === 'small' ? 'h-8 w-8' : '',
        size === 'md' || size === 'medium' || size === 'icon' ? 'h-9 w-9' : '',
        size === 'lg' || size === 'large' ? 'h-10 w-10' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {children}
    </button>
  )
);
IconButton.displayName = 'IconButton';

type TooltipProps = CompatElementProps & {
  title?: React.ReactNode;
  arrow?: boolean;
  placement?: 'top' | 'right' | 'bottom' | 'left' | string;
};

export function Tooltip({ children, className, title }: TooltipProps) {
  return (
    <span className={className} title={typeof title === 'string' ? title : undefined}>
      {children}
    </span>
  );
}

type FormControlLabelProps = {
  control: React.ReactElement;
  label: React.ReactNode;
  className?: string;
};

export function FormControlLabel({
  control,
  label,
  className,
}: FormControlLabelProps) {
  return (
    <label className={['inline-flex items-center gap-2', className].filter(Boolean).join(' ')}>
      {control}
      <span>{label}</span>
    </label>
  );
}

type LinearProgressProps = React.HTMLAttributes<HTMLDivElement> & {
  value?: number;
  variant?: 'determinate' | 'indeterminate';
};

export function LinearProgress({
  value = 0,
  className,
  variant = 'indeterminate',
  ...props
}: LinearProgressProps) {
  const progressValue = Math.max(0, Math.min(100, value));
  return (
    <div
      className={['h-2 overflow-hidden rounded-full bg-grey-200', className]
        .filter(Boolean)
        .join(' ')}
      role="progressbar"
      aria-valuenow={variant === 'determinate' ? progressValue : undefined}
      aria-valuemin={variant === 'determinate' ? 0 : undefined}
      aria-valuemax={variant === 'determinate' ? 100 : undefined}
      {...props}
    >
      <div
        className="h-full rounded-full bg-primary-500 transition-all"
        style={{ width: variant === 'determinate' ? `${progressValue}%` : '35%' }}
      />
    </div>
  );
}

type SurfaceProps = React.HTMLAttributes<HTMLDivElement> & {
  variant?: 'raised' | 'elevated' | 'outlined' | 'default' | 'flat';
  elevation?: number;
};

export const Surface = React.forwardRef<HTMLDivElement, SurfaceProps>(
  ({ children, className, variant = 'default', elevation: _elevation, ...props }, ref) => (
    <div
      ref={ref}
      className={[
        'rounded-lg bg-white dark:bg-grey-900',
        variant === 'outlined' ? 'border border-grey-200 dark:border-grey-700' : '',
        variant === 'raised' || variant === 'elevated' ? 'shadow-md' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {children}
    </div>
  )
);
Surface.displayName = 'Surface';

type CompatMenuProps = React.HTMLAttributes<HTMLDivElement> & {
  anchorEl?: HTMLElement | null;
  open?: boolean;
  onClose?: () => void;
  MenuListProps?: Record<string, unknown>;
  PaperProps?: Record<string, unknown>;
};

export function Menu({ children, open = true, className, ...props }: CompatMenuProps) {
  if (!open) return null;
  return (
    <div className={className} role="menu" {...props}>
      {children}
    </div>
  );
}

type CompatMenuItemProps = React.HTMLAttributes<HTMLDivElement> & {
  onClick?: React.MouseEventHandler<HTMLDivElement>;
  disabled?: boolean;
  value?: string | number;
};

export function MenuItem({
  children,
  className,
  disabled,
  onClick,
  ...props
}: CompatMenuItemProps) {
  return (
    <div
      className={[
        'cursor-pointer px-3 py-2 text-sm hover:bg-grey-100',
        disabled ? 'pointer-events-none opacity-50' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      onClick={disabled ? undefined : onClick}
      role="menuitem"
      {...props}
    >
      {children}
    </div>
  );
}

type AppBarProps = CompatElementProps & {
  position?: string;
  variant?: string;
};

export function AppBar({ children, className, ...props }: AppBarProps) {
  return (
    <header className={className} {...props}>
      {children}
    </header>
  );
}

export function Toolbar({ children, className, ...props }: CompatElementProps) {
  return (
    <div className={className} {...props}>
      {children}
    </div>
  );
}

type ToggleButtonGroupProps = {
  children?: React.ReactNode;
  className?: string;
  value?: string | string[];
  exclusive?: boolean;
  onChange?: (event: React.MouseEvent<HTMLElement>, value: string | null) => void;
  size?: 'small' | 'medium' | 'large' | string;
  disabled?: boolean;
  orientation?: 'horizontal' | 'vertical' | string;
  'aria-label'?: string;
};

type ToggleChildProps = {
  value?: string;
  selected?: boolean;
  disabled?: boolean;
  onClick?: React.MouseEventHandler<HTMLButtonElement>;
};

export function ToggleButtonGroup({
  children,
  className,
  value,
  onChange,
  disabled,
  orientation,
  'aria-label': ariaLabel,
}: ToggleButtonGroupProps) {
  const enhancedChildren = React.Children.map(children, (child) => {
    if (!React.isValidElement<ToggleChildProps>(child)) return child;
    const childValue = child.props.value;
    const selected = Array.isArray(value)
      ? childValue !== undefined && value.includes(childValue)
      : childValue === value;

    return React.cloneElement(child, {
      selected,
      disabled: disabled || child.props.disabled,
      onClick: (event: React.MouseEvent<HTMLButtonElement>) => {
        child.props.onClick?.(event);
        if (childValue !== undefined) {
          onChange?.(event, childValue);
        }
      },
    });
  });

  return (
    <div
      aria-label={ariaLabel}
      className={className}
      data-orientation={orientation}
      data-value={Array.isArray(value) ? value.join(',') : value}
      role="group"
    >
      {enhancedChildren}
    </div>
  );
}

type ToggleButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  value: string;
  selected?: boolean;
};

export function ToggleButton({
  children,
  className,
  value,
  selected,
  type = 'button',
  ...props
}: ToggleButtonProps) {
  return (
    <button
      className={className}
      data-selected={selected}
      data-value={value}
      type={type}
      {...props}
    >
      {children}
    </button>
  );
}

type DisclosureProps = CompatElementProps & {
  in?: boolean;
  severity?: 'info' | 'success' | 'warning' | 'error';
  icon?: React.ReactNode;
  action?: React.ReactNode;
};

export function Collapse({ children, in: open = true, className }: DisclosureProps) {
  if (!open) return null;
  return <div className={className}>{children}</div>;
}

export function Fade({ children, in: open = true, className }: DisclosureProps) {
  if (!open) return null;
  return <>{className ? <span className={className}>{children}</span> : children}</>;
}

export function Alert({
  children,
  className,
  severity = 'info',
  icon,
  action,
}: DisclosureProps) {
  return (
    <div className={className} data-severity={severity} role="status">
      {icon}
      {children}
      {action}
    </div>
  );
}

type InteractiveListProps = CompatElementProps & {
  dense?: boolean;
};

export function InteractiveList({ children, className }: InteractiveListProps) {
  return <div className={className}>{children}</div>;
}

type ListItemProps = CompatElementProps & {
  dense?: boolean;
  onClick?: React.MouseEventHandler<HTMLDivElement>;
};

export function ListItem({ children, className, dense: _dense, onClick }: ListItemProps) {
  return (
    <div className={className} onClick={onClick}>
      {children}
    </div>
  );
}

type ListItemTextProps = {
  primary?: React.ReactNode;
  secondary?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
};

export function ListItemText({
  primary,
  secondary,
  children,
  className,
}: ListItemTextProps) {
  return (
    <span className={className}>
      {children ?? (
        <>
          {primary && <span className="block">{primary}</span>}
          {secondary && <span className="block text-xs text-grey-500">{secondary}</span>}
        </>
      )}
    </span>
  );
}

type FormControlProps = CompatElementProps & {
  fullWidth?: boolean;
};

export function FormControl({ children, className, fullWidth }: FormControlProps) {
  const resolvedClassName = [fullWidth ? 'w-full' : '', className]
    .filter(Boolean)
    .join(' ');
  return <div className={resolvedClassName}>{children}</div>;
}

export function InputLabel({ children, className }: CompatElementProps) {
  return <label className={className}>{children}</label>;
}

type DialogProps = CompatElementProps & {
  open?: boolean;
  onClose?: () => void;
  size?: 'sm' | 'md' | 'lg' | string;
  fullWidth?: boolean;
};

export function Dialog({
  children,
  className,
  open = true,
  onClose,
  size,
  fullWidth,
}: DialogProps) {
  if (!open) return null;
  return (
    <BaseDialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onClose?.();
      }}
      size={size === 'sm' || size === 'md' || size === 'lg' ? size : 'md'}
      className={className}
    >
      <div className={fullWidth ? 'w-full' : undefined}>{children}</div>
    </BaseDialog>
  );
}

export function DialogContent({ children, className }: CompatElementProps) {
  return <div className={className}>{children}</div>;
}

export function DialogActions({ children, className }: CompatElementProps) {
  return <div className={className}>{children}</div>;
}

type DrawerProps = {
  children?: React.ReactNode;
  className?: string;
  anchor?: 'left' | 'right' | 'top' | 'bottom' | string;
  position?: 'left' | 'right' | 'top' | 'bottom';
  open?: boolean;
  onClose?: () => void;
  onOpenChange?: (open: boolean) => void;
  style?: React.CSSProperties;
};

export const Drawer = React.forwardRef<HTMLDivElement, DrawerProps>(
  (
    {
      children,
      className,
      anchor,
      position,
      open,
      onClose,
      onOpenChange,
      style,
    },
    ref
  ) => {
    const resolvedPosition =
      position ??
      (anchor === 'left' || anchor === 'top' || anchor === 'bottom'
        ? anchor
        : 'right');

    return (
      <BaseDrawer
        ref={ref}
        open={open}
        onOpenChange={(nextOpen) => {
          onOpenChange?.(nextOpen);
          if (!nextOpen) onClose?.();
        }}
        position={resolvedPosition}
        className={className}
      >
        <div style={style}>{children}</div>
      </BaseDrawer>
    );
  }
);
Drawer.displayName = 'Drawer';

type AvatarProps = CompatElementProps & {
  alt?: string;
  src?: string;
};

export function Avatar({ children, className, alt, src }: AvatarProps) {
  const fallbackAlt = alt ?? (typeof children === 'string' ? children : 'Avatar');
  return (
    <BaseAvatar alt={fallbackAlt} src={src} className={className}>
      {children}
    </BaseAvatar>
  );
}

type SelectProps = React.SelectHTMLAttributes<HTMLSelectElement> & {
  label?: string;
  fullWidth?: boolean;
};

export function Select({
  children,
  className,
  fullWidth,
  ...props
}: SelectProps) {
  return (
    <select
      className={[fullWidth ? 'w-full' : '', className]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {children}
    </select>
  );
}

type InputAdornmentProps = CompatElementProps & {
  position?: 'start' | 'end' | string;
};

export function InputAdornment({
  children,
  className,
  position,
}: InputAdornmentProps) {
  return (
    <span className={className} data-position={position}>
      {children}
    </span>
  );
}

export const ListItemButton = ListItem;

type PopperProps = CompatElementProps & {
  open?: boolean;
  anchorEl?: HTMLElement | null;
  placement?: string;
  style?: React.CSSProperties;
};

export function Popper({ children, className, open = true, style }: PopperProps) {
  if (!open) return null;
  return <div className={className} style={style}>{children}</div>;
}

export {
  AvatarGroup,
  Badge,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  DialogTitle,
  Divider,
  Spinner,
  Stack,
  TextField,
  Typography,
};
