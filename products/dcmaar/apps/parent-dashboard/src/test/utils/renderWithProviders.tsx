import { type ReactElement, type ReactNode, useMemo } from 'react';
import { MemoryRouter, type MemoryRouterProps } from 'react-router-dom';
import { render, type RenderOptions, type RenderResult } from '@testing-library/react';
import { Provider as JotaiProvider } from 'jotai';
import { RoleContext, ROLE_CONFIG, type RoleDefinition, type UserRole } from '@ghatana/dcmaar-dashboard-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

interface DashboardTestProvidersProps {
  children: ReactNode;
  role?: UserRole;
  roleConfig?: RoleDefinition;
  withRouter?: boolean;
  routerProps?: MemoryRouterProps;
  queryClient?: QueryClient;
}

export function DashboardTestProviders({
  children,
  role = 'parent',
  roleConfig,
  withRouter = true,
  routerProps,
  queryClient
}: DashboardTestProvidersProps): ReactElement {
  const value = roleConfig ?? ROLE_CONFIG[role];
  const client = useMemo(
    () =>
      queryClient ??
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: false,
          },
          mutations: {
            retry: false,
          },
        },
      }),
    [queryClient]
  );

  const content = (
    <QueryClientProvider client={client}>
      <RoleContext.Provider value={value}>
        <JotaiProvider>{children}</JotaiProvider>
      </RoleContext.Provider>
    </QueryClientProvider>
  );

  if (withRouter) {
    return <MemoryRouter {...routerProps}>{content}</MemoryRouter>;
  }

  return content;
}

interface RenderWithDashboardProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  role?: UserRole;
  roleConfig?: RoleDefinition;
  withRouter?: boolean;
  routerProps?: MemoryRouterProps;
  queryClient?: QueryClient;
}

export function renderWithDashboardProviders(
  ui: ReactElement,
  {
    role,
    roleConfig,
    withRouter,
    routerProps,
    queryClient,
    ...renderOptions
  }: RenderWithDashboardProvidersOptions = {}
): RenderResult {
  return render(ui, {
    wrapper: ({ children }) => (
      <DashboardTestProviders
        role={role}
        roleConfig={roleConfig}
        withRouter={withRouter}
        routerProps={routerProps}
      >
        {children}
      </DashboardTestProviders>
    ),
    ...renderOptions
  });
}
