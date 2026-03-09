import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
// @ts-expect-error - TypeScript module resolution issue, but Metro handles this correctly
import AppNavigator from './navigation/AppNavigator';
// @ts-expect-error - TypeScript module resolution issue, but Metro handles this correctly
import notificationService from './services/notifications';
import { RoleContext, ROLE_CONFIG } from '@ghatana/dcmaar-dashboard-core';
import {
  startEventSync,
  stopEventSync,
  startPolicyNotifications,
  stopPolicyNotifications,
} from '@ghatana/dcmaar-agent-react-native/agent';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30000,
    },
  },
});

const App: React.FC = () => {
  React.useEffect(() => {
    // Initialize notifications
    notificationService.initialize();
    notificationService.requestPermissions();

    // Initialize shared Guardian agent services
    startEventSync();
    startPolicyNotifications();

    return () => {
      stopEventSync();
      stopPolicyNotifications();
    };
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <RoleContext.Provider value={ROLE_CONFIG['child']}>
          <AppNavigator />
        </RoleContext.Provider>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
};

export default App;
