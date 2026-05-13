import { useMemo } from 'react';
import type { ProductShellConfig } from './types';

export function createProductShellConfig(config: ProductShellConfig): ProductShellConfig {
  return config;
}

export function useProductShellConfig(config: ProductShellConfig): ProductShellConfig {
  return useMemo(
    () => config,
    [
      config.productName,
      config.logo,
      config.routes,
      config.currentRole,
      config.roleOrder,
      config.roleLabels,
      config.roleDescriptions,
      config.availableRoles,
      config.roleSelectorTitle,
      config.roleSelectorLabel,
      config.roleSelectorDisclosureNote,
      config.onRoleChange,
      config.onSearch,
      config.notifications,
      config.onNotificationAction,
      config.activeOperationsCount,
      config.onActiveOperationsClick,
      config.sidebarFooter,
      config.headerActions,
    ],
  );
}
