import React from 'react';

import {
  Tab as BaseTab,
  TabPanel,
  Tabs as BaseTabs,
  TabsList,
  type TabProps as BaseTabProps,
  type TabsProps as BaseTabsProps,
} from './Tabs.baseui';

export interface TabsProps
  extends Omit<BaseTabsProps, 'onValueChange' | 'size' | 'variant'> {
  shape?: 'rounded' | 'soft' | 'pill' | 'square';
  size?: 'small' | 'medium' | 'large';
  variant?: 'default' | 'pills' | 'underline' | 'scrollable';
  onChange?: (event: React.SyntheticEvent, value: number | string) => void;
}

const mapSize = (size: TabsProps['size']): BaseTabsProps['size'] =>
  size === 'large' ? 'large' : size === 'small' ? 'small' : 'medium';

const mapVariant = (variant: TabsProps['variant']): BaseTabsProps['variant'] =>
  variant === 'pills'
    ? 'pills'
    : variant === 'underline'
      ? 'underline'
      : 'standard';

export const Tabs = React.forwardRef<HTMLDivElement, TabsProps>(
  ({ onChange, size = 'medium', variant = 'default', ...props }, ref) => {
    return (
      <BaseTabs
        ref={ref}
        size={mapSize(size)}
        variant={mapVariant(variant)}
        onValueChange={(value) =>
          onChange?.({} as React.SyntheticEvent, value)
        }
        {...props}
      />
    );
  }
);

Tabs.displayName = 'Tabs';

export interface TabProps extends Omit<BaseTabProps, 'children'> {
  label?: React.ReactNode;
  icon?: React.ReactNode;
  children?: React.ReactNode;
}

export const Tab = React.forwardRef<HTMLButtonElement, TabProps>(
  ({ label, icon, children, ...props }, ref) => (
    <BaseTab ref={ref} startIcon={icon} {...props}>
      {children ?? label}
    </BaseTab>
  )
);

Tab.displayName = 'Tab';

export { TabPanel, TabsList };
export default Tabs;
