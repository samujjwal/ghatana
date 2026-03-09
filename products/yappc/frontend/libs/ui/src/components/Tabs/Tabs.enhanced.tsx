/**
 * Enhanced Tabs Component (Organism)
 *
 * A navigation component with tabbed interface using design tokens.
 * Built on @ghatana/ui Tabs for enhanced functionality.
 *
 * @packageDocumentation
 */

import { Tabs as BaseTabs, Tab as BaseTab, Box, Badge } from '@ghatana/ui';
import {
  borderRadiusSm,
  borderRadiusMd,
  borderRadiusFull,
  spacingSm,
  spacingMd,
  spacingLg,
  fontWeightMedium,
  fontWeightBold,
} from '@ghatana/yappc-shared-ui-core/tokens';
import React from 'react';

/**
 * Props for the enhanced Tabs component.
 */
export interface TabsProps extends React.HTMLAttributes<HTMLDivElement> {
  /**
   * Tabs shape variant using design tokens
   * - rounded: Standard rounded (4px)
   * - soft: Softer rounded (8px)
   * - pill: Full rounded indicator
   * - square: No rounding
   */
  shape?: 'rounded' | 'soft' | 'pill' | 'square';

  /**
   * Tabs size variant
   * All sizes maintain WCAG 2.1 minimum 44px touch targets
   */
  size?: 'small' | 'medium' | 'large';

  /**
   * Tabs orientation
   */
  orientation?: 'horizontal' | 'vertical';

  /** Currently selected tab value */
  value?: number | string;

  /** Change handler */
  onChange?: (event: React.SyntheticEvent, value: number | string) => void;

  /** Tab variant style */
  variant?: 'default' | 'pills' | 'underline' | 'scrollable';

  /** Allow scrolling */
  scrollButtons?: boolean | 'auto';
}

/** Map size to Tailwind classes */
const tabSizeClasses: Record<string, string> = {
  small: 'min-h-[44px] text-sm',
  medium: 'min-h-[48px] text-base',
  large: 'min-h-[56px] text-lg',
};

/**
 * Tabs Component
 *
 * An organism component for tabbed navigation with design token integration.
 *
 * ## Features
 * - WCAG 2.1 AA compliant (44px minimum touch targets)
 * - Design token integration
 * - Four shape variants (rounded, soft, pill, square)
 * - Three size variants (small, medium, large)
 * - Horizontal and vertical orientations
 * - Keyboard navigation (Arrow keys, Home, End)
 * - Accessible ARIA attributes
 *
 * @example Basic Usage
 * ```tsx
 * <Tabs value={tab} onChange={handleChange}>
 *   <Tab label="Tab 1" />
 *   <Tab label="Tab 2" />
 *   <Tab label="Tab 3" />
 * </Tabs>
 * ```
 */
export const Tabs = React.forwardRef<HTMLDivElement, TabsProps>((props, ref) => {
  const { children, shape = 'rounded', size = 'medium', className, ...rest } = props;

  const tabsClassName = [
    tabSizeClasses[size] || tabSizeClasses.medium,
    className,
  ].filter(Boolean).join(' ');

  return (
    <BaseTabs ref={ref} className={tabsClassName} size={size === 'small' ? 'sm' : size === 'large' ? 'lg' : 'md'} {...rest}>
      {children}
    </BaseTabs>
  );
});

Tabs.displayName = 'Tabs';

// ============================================================================
// Tab Component
// ============================================================================

/**
 * Props for individual Tab items.
 */
export interface TabProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Tab label */
  label?: React.ReactNode;

  /** Tab value */
  value?: number | string;

  /** Whether the tab is disabled */
  disabled?: boolean;

  /**
   * Icon to display at the start of the tab
   */
  startIcon?: React.ReactNode;

  /**
   * Icon to display at the end of the tab
   */
  endIcon?: React.ReactNode;

  /**
   * Badge count to display with the tab
   */
  badgeCount?: number;

  /**
   * Badge color variant
   */
  badgeColor?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';

  /** Icon for the tab */
  icon?: React.ReactNode;
}

/**
 * Tab Component
 *
 * Individual tab item with icon and badge support.
 */
export const Tab = React.forwardRef<HTMLDivElement, TabProps>((props, ref) => {
  const {
    children,
    startIcon,
    endIcon,
    badgeCount,
    badgeColor = 'primary',
    label,
    className,
    icon,
    ...rest
  } = props;

  // Create custom label with icons and badge if provided
  const customLabel =
    startIcon || endIcon || badgeCount !== undefined ? (
      <Box className="flex items-center gap-2">
        {startIcon && (
          <Box component="span" className="flex items-center" aria-hidden="true">
            {startIcon}
          </Box>
        )}
        <Box component="span">{label}</Box>
        {badgeCount !== undefined && (
          <Badge badgeContent={badgeCount} color={badgeColor} aria-label={`${badgeCount} items`} />
        )}
        {endIcon && (
          <Box component="span" className="flex items-center" aria-hidden="true">
            {endIcon}
          </Box>
        )}
      </Box>
    ) : (
      label
    );

  const tabClassName = [
    'normal-case font-medium min-w-[90px] mr-2',
    'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-[-2px] focus-visible:rounded',
    'contrast-more:font-black',
    'motion-reduce:transition-none',
    className,
  ].filter(Boolean).join(' ');

  return <BaseTab ref={ref} label={customLabel} icon={icon} className={tabClassName} {...rest} />;
});

Tab.displayName = 'Tab';

// ============================================================================
// TabPanel Component
// ============================================================================

/**
 *
 */
export interface TabPanelProps {
  /**
   * The value of the currently selected tab
   */
  value: number | string;

  /**
   * The index of this tab panel
   */
  index: number | string;

  /**
   * The content of the tab panel
   */
  children: React.ReactNode;

  /**
   * Keep mounted when hidden (for performance)
   */
  keepMounted?: boolean;

  /**
   * Additional props
   */
  [key: string]: unknown;
}

/**
 * TabPanel Component
 *
 * Container for tab content with proper ARIA attributes.
 *
 * @example
 * ```tsx
 * <TabPanel value={tab} index={0}>
 *   <Typography>Tab 1 Content</Typography>
 * </TabPanel>
 * ```
 */
export const TabPanel = (props: TabPanelProps) => {
  const { children, value, index, keepMounted = false, ...other } = props;

  const isActive = value === index;

  return (
    <div
      role="tabpanel"
      hidden={!isActive}
      id={`tabpanel-${index}`}
      aria-labelledby={`tab-${index}`}
      {...other}
    >
      {(isActive || keepMounted) && (
        <Box className="p-6">{children}</Box>
      )}
    </div>
  );
};

TabPanel.displayName = 'TabPanel';

export default Tabs;
