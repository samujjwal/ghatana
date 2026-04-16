import React, { type ErrorInfo, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { SafeAreaView, StyleSheet, Text } from 'react-native';
import AppNavigator from './src/navigation/AppNavigator';
import notificationService from './src/services/notifications';
import { RoleContext, ROLE_CONFIG } from '@dcmaar/dashboard-core';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30000,
    },
  },
});

class AppErrorBoundary extends React.Component<{ children: ReactNode }, { hasError: boolean }> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): { hasError: boolean } {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('Guardian Parent mobile boundary caught an error', error, errorInfo);
  }

  override render(): ReactNode {
    if (this.state.hasError) {
      return (
        <SafeAreaView style={styles.errorScreen}>
          <Text style={styles.errorTitle}>Guardian Parent</Text>
          <Text style={styles.errorMessage}>Something went wrong. Restart the app to continue.</Text>
        </SafeAreaView>
      );
    }

    return this.props.children;
  }
}

const App: React.FC = () => {
  React.useEffect(() => {
    // Initialize notifications
    notificationService.initialize();
    notificationService.requestPermissions();
  }, []);

  const roleConfig = ROLE_CONFIG['parent'];

  return (
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <SafeAreaProvider>
          <RoleContext.Provider value={roleConfig}>
            <AppNavigator />
          </RoleContext.Provider>
        </SafeAreaProvider>
      </QueryClientProvider>
    </AppErrorBoundary>
  );
};

export default App;

const styles = StyleSheet.create({
  errorScreen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
    backgroundColor: '#f8fafc',
  },
  errorTitle: {
    fontSize: 24,
    fontWeight: '700',
    color: '#111827',
  },
  errorMessage: {
    marginTop: 12,
    fontSize: 16,
    color: '#374151',
    textAlign: 'center',
  },
});
