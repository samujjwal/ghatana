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

import { API_BASE_URL, SYNC_MAX_CONCURRENT, SYNC_RETRY_DELAY_MS } from './config';
import { database } from './storage/SQLiteStorage';
import { initSessionStorage, hasValidSession, installNativeSessionStorageShim } from './storage/NativeSessionStorage';
import { getMmkvEncryptionKey } from './storage/SecureKeyManager';
import { syncService } from './services/BackgroundSyncService';
import { useNetworkStatus } from './hooks/useOffline';

import { TabNavigator } from './navigation/TabNavigator';
import { OfflineBanner } from './components/OfflineBanner';
import { SyncStatusBar } from './components/SyncStatusBar';
import { LoginScreen } from './screens/LoginScreen';

// Create React Query client outside the component so it survives re-renders
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
export function App(): React.ReactElement {
  const [isReady, setIsReady] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const { isConnected } = useNetworkStatus();

  // Initialize app
  useEffect(() => {
    async function initialize() {
      try {
        // Resolve the per-device encryption key from the system keychain before
        // initialising any storage that holds sensitive data.
        const encryptionKey = await getMmkvEncryptionKey();
        initSessionStorage(encryptionKey);

        installNativeSessionStorageShim();

        // Initialize SQLite database
        await database.init();
        
        // Check if there is an existing valid session
        const authenticated = hasValidSession();
        setIsAuthenticated(authenticated);

        if (authenticated) {
          // Initialize sync service only when authenticated
          await syncService.init({
            apiBaseUrl: API_BASE_URL,
            getAuthToken: async () => {
              const { getSecureToken } = await import('./storage/NativeSessionStorage');
              return getSecureToken('access');
            },
            maxConcurrent: SYNC_MAX_CONCURRENT,
            retryBaseDelayMs: SYNC_RETRY_DELAY_MS,
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
        }
        
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

  if (!isAuthenticated) {
    return (
      <SafeAreaProvider>
        <LoginScreen onLoginSuccess={() => setIsAuthenticated(true)} />
      </SafeAreaProvider>
    );
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
