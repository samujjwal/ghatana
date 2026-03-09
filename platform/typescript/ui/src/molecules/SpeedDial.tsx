import * as React from 'react';

// ──────────────────────────────────────────────────────────────────────────
// SpeedDialAction
// ──────────────────────────────────────────────────────────────────────────

export interface SpeedDialActionProps {
  /** Icon to display */
  icon: React.ReactNode;
  /** Tooltip label */
  tooltipTitle?: string;
  /** Click handler */
  onClick?: (e: React.MouseEvent) => void;
  /** Whether tooltip is always visible @default false */
  tooltipOpen?: boolean;
  /** Additional CSS classes */
  className?: string;
}

export const SpeedDialAction = React.forwardRef<HTMLButtonElement, SpeedDialActionProps>(
  ({ icon, tooltipTitle, onClick, tooltipOpen = false, className }, ref) => {
    return (
      <div className="relative flex items-center gap-2">
        {tooltipTitle && (tooltipOpen || true) && (
          <span className="absolute right-full mr-2 whitespace-nowrap rounded bg-neutral-800 px-2 py-1 text-xs text-white opacity-0 shadow-md transition-opacity group-hover:opacity-100 dark:bg-neutral-700">
            {tooltipTitle}
          </span>
        )}
        <button
          ref={ref}
          type="button"
          aria-label={tooltipTitle}
          title={tooltipTitle}
          onClick={onClick}
          className={`flex h-10 w-10 items-center justify-center rounded-full bg-white text-neutral-700 shadow-md transition-transform hover:scale-110 hover:bg-neutral-50 dark:bg-neutral-700 dark:text-neutral-200 dark:hover:bg-neutral-600 ${className ?? ''}`}
        >
          {icon}
        </button>
      </div>
    );
  },
);
SpeedDialAction.displayName = 'SpeedDialAction';

// ──────────────────────────────────────────────────────────────────────────
// SpeedDialIcon
// ──────────────────────────────────────────────────────────────────────────

export interface SpeedDialIconProps {
  /** Icon when closed @default '+' */
  icon?: React.ReactNode;
  /** Icon when open @default '×' */
  openIcon?: React.ReactNode;
  /** Whether SpeedDial is open */
  open?: boolean;
  /** Additional CSS classes */
  className?: string;
}

export const SpeedDialIcon: React.FC<SpeedDialIconProps> = ({
  icon,
  openIcon,
  open = false,
  className,
}) => {
  return (
    <span
      className={`inline-flex items-center justify-center transition-transform duration-200 ${open ? 'rotate-45' : 'rotate-0'} ${className ?? ''}`}
    >
      {open ? (openIcon ?? '×') : (icon ?? '+')}
    </span>
  );
};
SpeedDialIcon.displayName = 'SpeedDialIcon';

// ──────────────────────────────────────────────────────────────────────────
// SpeedDial
// ──────────────────────────────────────────────────────────────────────────

export interface SpeedDialProps {
  /** Actions to display */
  children?: React.ReactNode;
  /** Whether to show the actions */
  open?: boolean;
  /** Called when open state changes */
  onOpen?: (event: React.SyntheticEvent, reason: string) => void;
  /** Called when close state changes */
  onClose?: (event: React.SyntheticEvent, reason: string) => void;
  /** FAB icon */
  icon?: React.ReactNode;
  /** Aria label for the FAB */
  ariaLabel: string;
  /** Direction actions expand @default 'up' */
  direction?: 'up' | 'down' | 'left' | 'right';
  /** Whether the SpeedDial is hidden */
  hidden?: boolean;
  /** Sx / style prop */
  sx?: React.CSSProperties;
  /** Additional CSS classes */
  className?: string;
  /** FAB props */
  FabProps?: Record<string, unknown>;
}

const directionClasses: Record<string, string> = {
  up: 'flex-col-reverse bottom-full mb-2',
  down: 'flex-col top-full mt-2',
  left: 'flex-row-reverse right-full mr-2',
  right: 'flex-row left-full ml-2',
};

/**
 * SpeedDial — floating action button with expandable actions.
 * Drop-in replacement for MUI SpeedDial using Tailwind CSS.
 *
 * @example
 * ```tsx
 * <SpeedDial ariaLabel="Actions" icon={<SpeedDialIcon />}>
 *   <SpeedDialAction icon={<CopyIcon />} tooltipTitle="Copy" onClick={handleCopy} />
 *   <SpeedDialAction icon={<SaveIcon />} tooltipTitle="Save" onClick={handleSave} />
 * </SpeedDial>
 * ```
 */
export const SpeedDial = React.forwardRef<HTMLDivElement, SpeedDialProps>(
  (
    {
      children,
      open: controlledOpen,
      onOpen,
      onClose,
      icon,
      ariaLabel,
      direction = 'up',
      hidden = false,
      sx,
      className,
      FabProps,
    },
    ref,
  ) => {
    const [internalOpen, setInternalOpen] = React.useState(false);
    const isOpen = controlledOpen !== undefined ? controlledOpen : internalOpen;

    const handleToggle = (e: React.MouseEvent) => {
      if (isOpen) {
        setInternalOpen(false);
        onClose?.(e, 'toggle');
      } else {
        setInternalOpen(true);
        onOpen?.(e, 'toggle');
      }
    };

    const handleMouseEnter = (e: React.MouseEvent) => {
      if (controlledOpen === undefined) {
        setInternalOpen(true);
        onOpen?.(e, 'mouseEnter');
      }
    };

    const handleMouseLeave = (e: React.MouseEvent) => {
      if (controlledOpen === undefined) {
        setInternalOpen(false);
        onClose?.(e, 'mouseLeave');
      }
    };

    if (hidden) return null;

    return (
      <div
        ref={ref}
        className={`relative inline-flex items-center ${className ?? ''}`}
        style={sx}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
      >
        <button
          type="button"
          aria-label={ariaLabel}
          aria-expanded={isOpen}
          aria-haspopup="menu"
          onClick={handleToggle}
          className="flex h-14 w-14 items-center justify-center rounded-full bg-blue-600 text-white shadow-lg transition-colors hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:bg-blue-500 dark:hover:bg-blue-600"
          {...(FabProps as Record<string, unknown>)}
        >
          {icon ?? <SpeedDialIcon open={isOpen} />}
        </button>

        {isOpen && (
          <div
            className={`absolute flex gap-2 ${directionClasses[direction]}`}
            role="menu"
          >
            {children}
          </div>
        )}
      </div>
    );
  },
);

SpeedDial.displayName = 'SpeedDial';
