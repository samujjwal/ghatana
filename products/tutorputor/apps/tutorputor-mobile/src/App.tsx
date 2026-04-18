/**
 * TutorPutor Mobile App
 *
 * Main application component with navigation and providers.
 *
 * @doc.type component
 * @doc.purpose Root mobile app component
 * @doc.layer product
 * @doc.pattern App Root
 */

import React, { useEffect, useState } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { database } from './storage/SQLiteStorage';
import { syncService } from './services/BackgroundSyncService';
import { useNetworkStatus } from './hooks/useOffline';

import { TabNavigator } from './navigation/TabNavigator';
import { OfflineBanner } from './components/OfflineBanner';
import { SyncStatusBar } from './components/SyncStatusBar';

// Create React Query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,
      staleTime: 5 * 60 * 1000, // 5 minutes
      gcTime: 10 * 60 * 1000, // 10 minutes
    },
  },
});

/**
 * Main App Component
 */
export function App(): JSX.Element {
  const [isReady, setIsReady] = useState(false);
  const { isConnected } = useNetworkStatus();

  // Initialize app
  useEffect(() => {
    async function initialize() {
      try {
        // Initialize SQLite database
        await database.init();
        
        // Initialize sync service
        const apiBaseUrl = process.env.API_BASE_URL || 'https://api.tutorputor.com';
        await syncService.init({
          apiBaseUrl,
          getAuthToken: async () => {
            // MOBILE_PLATFORM: Implement secure storage (e.g., Keychain on iOS, Keystore on Android)
            // For web/desktop PWA, use localStorage/sessionStorage
            return localStorage.getItem('auth_token') ?? null;
          },
          maxConcurrent: 3,
          retryBaseDelayMs: 1000,
          onSyncStart: () => {
            console.log('[Sync] Started');
          },
          onSyncComplete: (result) => {
            console.log('[Sync] Completed:', result);
          },
          onNetworkChange: (connected) => {
            console.log('[Network] Status changed:', connected);
          },
        });
        
        setIsReady(true);
      } catch (error) {
        console.error('[App] Initialization failed:', error);
        // Still set ready to show error screen
        setIsReady(true);
      }
    }

    initialize();

    // Cleanup
    return () => {
      syncService.stop();
      database.close();
    };
  }, []);

  if (!isReady) {
    // Could show splash screen here
    return <></>;
  }

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <NavigationContainer>
          <OfflineBanner isOffline={!isConnected} />
          <SyncStatusBar />
          <TabNavigator />
        </NavigationContainer>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}

export default App;
