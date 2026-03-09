/**
 * Flashit Mobile App - Main Entry Point
 * React Native app using Expo
 *
 * @doc.type application
 * @doc.purpose Mobile interface for Flashit context capture
 * @doc.layer product
 * @doc.pattern MobileApp
 */

import React, { useEffect } from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';
import { mobileAtoms } from './src/state/localAtoms';
import Navigation from './src/navigation';
import ErrorBoundary from './src/components/ErrorBoundary';
import { migrateBooleanSettings } from './src/utils/storageMigration';

// Create query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export default function App() {
  useEffect(() => {
    // Run migration on app startup
    migrateBooleanSettings();
  }, []);

  return (
    <ErrorBoundary>
      <SafeAreaProvider>
        <QueryClientProvider client={queryClient}>
          <JotaiProvider>
            <Navigation />
            <StatusBar style="auto" />
          </JotaiProvider>
        </QueryClientProvider>
      </SafeAreaProvider>
    </ErrorBoundary>
  );
}

