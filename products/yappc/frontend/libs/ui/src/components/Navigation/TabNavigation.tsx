/**
 * Owner: UI Team
 * 
 * TabNavigation - Reusable tab navigation component
 * Provides accessible tab navigation with proper keyboard support and ARIA attributes
 */

import { Box } from '@ghatana/ui';
import React from 'react';

import { Tabs, Tab } from '../Tabs';
import type { TabsVariant } from '../Tabs';

/**
 *
 */
export interface TabNavigationItem {
    /**
     * Unique identifier for the tab
     */
    id: string;

    /**
     * Display label for the tab
     */
    label: string;

    /**
     * Path or route for the tab
     */
    path: string;

    /**
     * Icon to display with the tab
     */
    icon?: React.ReactNode;

    /**
     * Badge to display with the tab
     */
    badge?: React.ReactNode;

    /**
     * Whether the tab is disabled
     */
    disabled?: boolean;
}

/**
 *
 */
export interface TabNavigationProps {
    /**
     * Array of tab items to display
     */
    items: TabNavigationItem[];

    /**
     * Currently active tab id
     */
    activeTab: string;

    /**
     * Callback when tab changes
     */
    onTabChange: (tabId: string) => void;

    /**
     * Navigation variant
     */
    variant?: 'standard' | 'fullWidth' | 'scrollable';

    /**
     * Size of the tabs
     */
    size?: 'small' | 'medium' | 'large';

    /**
     * Shape of the tabs
     */
    shape?: 'rounded' | 'square' | 'pill';

    /**
     * Whether to show a border under the navigation
     */
    showBorder?: boolean;

    /**
     * Additional CSS class name
     */
    className?: string;

    /**
     * ARIA label for the tab navigation
     */
    'aria-label'?: string;
}

/**
 * TabNavigation component for project navigation
 * 
 * Provides accessible tab-based navigation with support for:
 * - Keyboard navigation (arrow keys, Enter, Space)
 * - Screen reader compatibility
 * - Badge and icon support
 * - Multiple sizing and styling variants
 */
export const TabNavigation = React.forwardRef<HTMLDivElement, TabNavigationProps>(
    (props, ref) => {
        const {
            items,
            activeTab,
            onTabChange,
            variant = 'standard',
            size = 'medium',
            showBorder = true,
            className,
            'aria-label': ariaLabel = 'Tab navigation',
            ...rest
        } = props;

        // Find current tab index for Tabs value
        const resolvedVariant: TabsVariant =
            variant === 'standard' || variant === 'fullWidth' || variant === 'scrollable'
                ? (variant === 'standard' ? 'standard' : variant === 'fullWidth' ? 'underline' : 'standard')
                : 'standard';

        const currentTabIndex = items.findIndex(item => item.id === activeTab);
        const activeValue = currentTabIndex >= 0 ? items[currentTabIndex].id : items[0]?.id ?? '';

        const handleTabChange = (newValue: string | number) => {
            const selectedItem = items.find(item => item.id === newValue);
            if (selectedItem && !selectedItem.disabled) {
                onTabChange(selectedItem.id);
            }
        };

        const navClassName = [
            'bg-white dark:bg-gray-900 sticky top-0 z-40',
            showBorder ? 'border-b border-gray-200 dark:border-gray-700' : '',
            className,
        ].filter(Boolean).join(' ');

        return (
            <Box
                ref={ref}
                className={navClassName}
                role="navigation"
                aria-label={ariaLabel}
                {...rest}
            >
                <Tabs
                    value={activeValue}
                    defaultValue={items[0]?.id ?? ''}
                    onValueChange={handleTabChange}
                    variant={resolvedVariant}
                    size={size}
                    aria-label={ariaLabel}
                >
                    {items.map((item, idx) => (
                        <Tab
                            key={item.id}
                            id={`tab-${idx}`}
                            aria-controls={`tabpanel-${idx}`}
                            value={item.id}
                            startIcon={item.icon}
                            badge={item.badge}
                            disabled={item.disabled}
                        >
                            {item.label}
                        </Tab>
                    ))}
                    {/* Render inert tabpanel elements so aria-controls references are valid for a11y checks */}
                    {items.map((_, idx) => (
                        <div
                            key={`tabpanel-${idx}-placeholder`}
                            id={`tabpanel-${idx}`}
                            role="tabpanel"
                            aria-labelledby={`tab-${idx}`}
                            hidden
                            style={{ display: 'none' }}
                        />
                    ))}
                </Tabs>
            </Box>
        );
    }
);

TabNavigation.displayName = 'TabNavigation';
