import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import AppNavigator from './src/navigation/AppNavigator';
import notificationService from './src/services/notifications';
import { RoleContext, ROLE_CONFIG } from '@ghatana/dcmaar-dashboard-core';

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
  }, []);

  const roleConfig = ROLE_CONFIG['parent'];

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <RoleContext.Provider value={roleConfig}>
          <AppNavigator />
        </RoleContext.Provider>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
};

export default App;
